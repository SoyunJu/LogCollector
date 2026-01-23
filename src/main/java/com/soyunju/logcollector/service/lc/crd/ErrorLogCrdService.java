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
import com.soyunju.logcollector.service.kb.crud.IncidentService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
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
    private final KbDraftService kbDraftService;
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
    @Transactional(transactionManager = "lcTransactionManager")
    public ErrorLogResponse saveLog(ErrorLogRequest dto) {
        LocalDateTime occurredTime =
                (dto.getOccurredTime() != null) ? dto.getOccurredTime() : LocalDateTime.now();

        // 1. 로그 레벨 확정 (CRITICAL/FATAL 우선 적용)
        String inferred = logProcessor.inferLogLevel(dto.getMessage());
        String effectiveLevel;
        if (!StringUtils.hasText(dto.getLogLevel())) {
            effectiveLevel = inferred;
        } else {
            if (inferred.equals("CRITICAL") || inferred.equals("FATAL")) {
                effectiveLevel = inferred;
            } else {
                effectiveLevel = dto.getLogLevel();
            }
        }
        // 2. 수집 대상 여부
        if (!logProcessor.isTargetLevel(effectiveLevel)) {
            if (StringUtils.hasText(dto.getLogLevel())) {
                throw new IllegalArgumentException("수집 대상 로그 레벨이 아닙니다: " + dto.getLogLevel());
            }
            return null;
        }
        // 3. 해시 생성 및 중복 확인
        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());

        // 4. Host 집계 Upsert
        String hostName = (dto.getHostName() == null || dto.getHostName().isBlank()) ? "UNKNOWN_HOST" : dto.getHostName();
        int hostUpsertResult = errorLogHostRepository.upsertHostCounter(logHash, dto.getServiceName(), hostName, null, occurredTime);
        boolean isNewHost = (hostUpsertResult == 1);

        // 5. ErrorLog Upsert
        String errorCode = LogNormalization.generateErrorCode(dto.getMessage(), dto.getStackTrace());
        String summary = logProcessor.extractSummary(dto.getMessage());

        int logUpsertResult = errorLogRepository.upsertErrorLog(
                dto.getServiceName(), hostName, effectiveLevel.toUpperCase(),
                dto.getMessage(), dto.getStackTrace(), occurredTime,
                errorCode, summary, logHash
        );
        //  insert=1, update=2 return
        boolean isNewIncident = (logUpsertResult == 1);

        ErrorLog targetLog = errorLogRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Log not found after upsert"));
        // 영향 host agg
        long impactedHostCount = errorLogHostRepository.countHostsByLogHash(logHash);
        // 6. Incident 연동
        Incident incident = incidentService.recordOccurrence(
                logHash,
                dto.getServiceName(),
                targetLog.getSummary(),
                dto.getStackTrace(),
                errorCode,
                dto.getLogLevel(),
                occurredTime
        );
        // KB Draft 생성
        if (targetLog.getRepeatCount() >= 10) {
            kbDraftService.createSystemDraft(incident.getId());
        }
        try {
            if (!logProcessor.isTargetLevel(effectiveLevel)) {
                lcMetrics.incSaveLog("skipped");
                return null;
            }
            // host agg -> KB Draft
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
        ErrorLog errorLog = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로그 ID: " + logId));

        ErrorStatus from = errorLog.getStatus();
        errorLog.setStatus(status);
        lcMetrics.incStatusChange(from, status);

        ErrorStatus es = status;
        if (from != null && es != null && from != es && !isAllowedTransition(from, es)) {
            throw new InvalidStatusTransitionException("허용되지 않는 상태 전이: " + from + " -> " + es);
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
                        errorLog.setAcknowledgedBy("Operations Manager"); // TODO : 하드코딩에서 계정으로 변경
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
                                incident -> kbDraftService.createSystemDraft(incident.getId()),
                                () -> log.warn("로그 해시({})에 해당하는 인시던트를 찾을 수 없어 KB 초안을 생성하지 못했습니다.", errorLog.getLogHash())
                        );

                incidentService.updateStatus(errorLog.getLogHash(), com.soyunju.logcollector.domain.kb.enums.IncidentStatus.RESOLVED);
            }
            case NEW -> {
                errorLog.setResolvedAt(null);
                errorLog.setAcknowledgedAt(null);
                errorLog.setAcknowledgedBy(null);
                incidentService.updateStatus(errorLog.getLogHash(), com.soyunju.logcollector.domain.kb.enums.IncidentStatus.OPEN);
            }
            case IGNORED -> { }
        }

        auditLogService.write(
                "ERRORLOG_STATUS_CHANGED",
                "ERROR_LOG",
                String.valueOf(logId),
                actor,
                "from=" + from + ", to=" + es + ", logHash=" + errorLog.getLogHash()
        );
    }

    @Transactional
    public void deleteLogs(List<Long> logIds) { // TODO : 권한 부여
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