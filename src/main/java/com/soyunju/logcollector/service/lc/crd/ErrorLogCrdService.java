package com.soyunju.logcollector.service.lc.crd;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.exception.InvalidStatusTransitionException;
import com.soyunju.logcollector.monitornig.LcMetrics;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.audit.AuditLogService;
import com.soyunju.logcollector.service.kb.crd.IncidentService;
import com.soyunju.logcollector.service.kb.crd.KbArticleService;
import com.soyunju.logcollector.service.lc.processor.LogNormalization;
import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ErrorLogCrdService {

    private final ErrorLogRepository errorLogRepository;
    private final ErrorLogHostRepository errorLogHostRepository;
    private final LogProcessor logProcessor;

    // KB 연동을 위한 서비스 및 레파지토리 주입
    private final KbArticleService kbArticleService;
    private final IncidentService incidentService;
    private final IncidentRepository incidentRepository;

    private final AuditLogService auditLogService;
    private final LcMetrics lcMetrics;

    @Transactional(readOnly = true)
    public boolean isIgnored(String logHash) {
        return errorLogRepository.findByLogHash(logHash)
                .map(log -> log.getStatus() == ErrorStatus.IGNORED)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<ErrorLog> findByLogHash(String logHash) {
        return errorLogRepository.findByLogHash(logHash);
    }

    @Retryable(
            retryFor = {ConcurrencyFailureException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public ErrorLogResponse saveLog(ErrorLogRequest dto) {
        // 1. 발생 시각 결정
        LocalDateTime occurredTime =
                (dto.getOccurredTime() != null) ? dto.getOccurredTime() : LocalDateTime.now();

        // 2. 로그 레벨 확정 (입력값이 있으면 사용, 없으면 메시지에서 추론)
        String effectiveLevel = StringUtils.hasText(dto.getLogLevel())
                ? dto.getLogLevel()
                : logProcessor.inferLogLevel(dto.getMessage());

        // 3. 수집 대상 여부 검사 및 필터링
        if (!logProcessor.isTargetLevel(effectiveLevel)) {
            // 사용자가 UI 등에서 명시적으로 수집 대상이 아닌 레벨(예: INFO)을 보낸 경우 예외 발생
            if (StringUtils.hasText(dto.getLogLevel())) {
                throw new IllegalArgumentException("수집 대상 로그 레벨이 아닙니다: " + dto.getLogLevel());
            }
            // 자동 추론 결과가 INFO 등이거나 레벨이 없는 경우는 조용히 무시 (Batch/Agent 수집용)
            return null;
        }

        // 4. 해시 생성 및 중복 확인 (이후 확정된 effectiveLevel 사용)
        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());

        String hostName = (dto.getHostName() == null || dto.getHostName().isBlank()) ? "UNKNOWN_HOST" : dto.getHostName();

        // 1. Host 집계 Upsert
        int hostUpsertResult = errorLogHostRepository.upsertHostCounter(logHash, dto.getServiceName(), hostName, null, occurredTime);
        boolean isNewHost = (hostUpsertResult == 1);

        // 2. ErrorLog Upsert (Atomic 처리)
        String errorCode = LogNormalization.generateErrorCode(dto.getMessage(), dto.getStackTrace());
        String summary = logProcessor.extractSummary(dto.getMessage());

        int logUpsertResult = errorLogRepository.upsertErrorLog(
                dto.getServiceName(), hostName, effectiveLevel.toUpperCase(),
                dto.getMessage(), dto.getStackTrace(), occurredTime,
                errorCode, summary, logHash
        );

        // 신규 insert는 1, update는 2를 반환함
        boolean isNewIncident = (logUpsertResult == 1);

        // 3. 결과 반환을 위한 엔티티 조회
        ErrorLog targetLog = errorLogRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Log not found after upsert"));

        long impactedHostCount = errorLogHostRepository.countHostsByLogHash(logHash);

        // 4. Incident 연동 (수정: 반환된 Incident 객체를 변수에 할당)
        Incident incident = incidentService.recordOccurrence(
                logHash, dto.getServiceName(), targetLog.getSummary(),
                dto.getStackTrace(), errorCode, effectiveLevel, occurredTime
        );

        // 5. repeat_count가 10회 이상일 때 KB 초안 생성
        if (targetLog.getRepeatCount() >= 10) {
            // 이제 위에서 할당한 incident 변수를 사용하여 getId() 호출 가능
            kbArticleService.createSystemDraft(incident.getId());
        }

       /*  // 4. Incident 연동 (IncidentService도 동일하게 Upsert 방식으로 수정 권장)
        incidentService.recordOccurrence(
                logHash, dto.getServiceName(), targetLog.getSummary(),
                dto.getStackTrace(), errorCode, effectiveLevel, occurredTime
        );

        if (targetLog.getRepeatCount() >= 10) {
            kbArticleService.createSystemDraft(incident.getId());
        } */

        try {
            if (!logProcessor.isTargetLevel(effectiveLevel)) {
                lcMetrics.incSaveLog("skipped");
                return null;
            }
            ErrorLogResponse response = logProcessor.convertToResponse(targetLog, impactedHostCount, isNewIncident, isNewHost);
            lcMetrics.incSaveLog("success");
            return response;
        } catch (RuntimeException e) {
            lcMetrics.incSaveLog("failure");
            throw e;
        }
    }

    @Transactional
    public void updateStatus(Long logId, ErrorStatus status) {
        updateStatus(logId, status, null);
    }

    @Transactional
    public void updateStatus(Long logId, ErrorStatus status, String actor) {
        // [수정] 변수명을 log에서 errorLog로 변경하여 Slf4j 로거(log)와 충돌 방지
        ErrorLog errorLog = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로그 ID: " + logId));

        ErrorStatus from = errorLog.getStatus();
        errorLog.setStatus(status);
        lcMetrics.incStatusChange(from, status);

        ErrorStatus to = status;
        if (from != null && to != null && from != to && !isAllowedTransition(from, to)) {
            throw new InvalidStatusTransitionException("허용되지 않는 상태 전이: " + from + " -> " + to);
        }

        errorLog.setStatus(status);

        switch (status) {
            case ACKNOWLEDGED -> {
                if (errorLog.getAcknowledgedAt() == null) {
                    errorLog.setAcknowledgedAt(LocalDateTime.now());
                }
                if (errorLog.getAcknowledgedBy() == null || errorLog.getAcknowledgedBy().isBlank()) {
                    if (actor != null && !actor.isBlank()) {
                        errorLog.setAcknowledgedBy(actor);
                    } else {
                        errorLog.setAcknowledgedBy("Operations Manager");
                    }
                }
                errorLog.setResolvedAt(null);
            }
            case RESOLVED -> {
                LocalDateTime now = LocalDateTime.now();
                errorLog.setResolvedAt(now);

                incidentService.markResolved(errorLog.getLogHash(), now);

                incidentRepository.findByLogHash(errorLog.getLogHash())
                        .ifPresentOrElse(
                                incident -> kbArticleService.createSystemDraft(incident.getId()),
                                // [수정] 이제 여기서 log.warn은 Slf4j 로거를 정상적으로 참조합니다.
                                () -> log.warn("로그 해시({})에 해당하는 인시던트를 찾을 수 없어 KB 초안을 생성하지 못했습니다.", errorLog.getLogHash())
                        );
            }
            case NEW -> {
                errorLog.setResolvedAt(null);
                errorLog.setAcknowledgedAt(null);
                errorLog.setAcknowledgedBy(null);
            }
            case IGNORED -> {
                // no-op
            }
        }
        auditLogService.write(
                "ERRORLOG_STATUS_CHANGED",
                "ERROR_LOG",
                String.valueOf(logId),
                actor,
                "from=" + from + ", to=" + to + ", logHash=" + errorLog.getLogHash()
        );
    }

    @Transactional
    public void deleteLogs(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return;
        }
        errorLogRepository.deleteAllByIdInBatch(logIds);
    }

    private boolean isAllowedTransition(ErrorStatus from, ErrorStatus to) {
        return switch (from) {
            case NEW -> to == ErrorStatus.ACKNOWLEDGED || to == ErrorStatus.RESOLVED || to == ErrorStatus.IGNORED;
            case ACKNOWLEDGED -> to == ErrorStatus.RESOLVED || to == ErrorStatus.NEW || to == ErrorStatus.IGNORED;
            case RESOLVED -> to == ErrorStatus.NEW || to == ErrorStatus.IGNORED;
            case IGNORED -> to == ErrorStatus.NEW || to == ErrorStatus.ACKNOWLEDGED;
        };
    }


}