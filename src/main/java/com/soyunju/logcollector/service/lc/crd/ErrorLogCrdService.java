package com.soyunju.logcollector.service.lc.crd;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
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
import com.soyunju.logcollector.service.kb.crud.IncidentBridgeService;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import com.soyunju.logcollector.service.lc.processor.LogNormalization;
import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import com.soyunju.logcollector.service.lc.redis.IgnoredLogHashStore;
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

    @org.springframework.beans.factory.annotation.Value("${draft.policy.host-spread-threshold:3}")
    private int hostSpreadThreshold;

    @org.springframework.beans.factory.annotation.Value("${draft.policy.high-recur-threshold:10}")
    private int highRecurThreshold;

    private final ErrorLogRepository errorLogRepository;
    private final ErrorLogHostRepository errorLogHostRepository;
    private final LogProcessor logProcessor;
    private final IgnoredLogHashStore ignoredLogHashStore;

    // KB 연동을 위한 서비스 및 레파지토리 주입
    private final KbDraftService kbDraftService;
    private final IncidentBridgeService incidentBridgeService;
    private final IncidentRepository incidentRepository;

    private final AuditLogService auditLogService;
    private final LcMetrics lcMetrics;

    @Transactional(readOnly = true)
    public Optional<ErrorLog> findByLogHash(String logHash) {
        return errorLogRepository.findByLogHash(logHash);
    }

    @Transactional(readOnly = true)
    public boolean isIgnored(String logHash) {
        return incidentRepository.findByLogHash(logHash)
                .map(incident -> incident.getStatus() == IncidentStatus.IGNORED)
                .orElse(false);
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

        // 2. 수집 대상 여부 (Level 필터링)
        if (!logProcessor.isTargetLevel(effectiveLevel)) {
            if (StringUtils.hasText(dto.getLogLevel())) {
                throw new IllegalArgumentException("수집 대상 로그 레벨이 아닙니다: " + dto.getLogLevel());
            }
            return null;
        }

        // 3. 해시 생성
        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());

        // [최적화] 3-1. IGNORED 상태 확인 및 시간 갱신 (Atomic Update)
        // touchLastOccurredIfIgnored:
        //   - 반환값 > 0: 이미 존재하며 IGNORED 상태임 (시간 갱신 완료) -> 수집 중단
        //   - 반환값 = 0: 존재하지 않거나, IGNORED가 아님 -> 정상 수집 진행
        int ignoredUpdateCount = incidentRepository.touchLastOccurredIfIgnored(logHash, occurredTime);

        if (ignoredUpdateCount > 0) {
            // KB Incident는 이미 갱신되었으므로, LC ErrorLog의 시간도 동기화
            errorLogRepository.findByLogHash(logHash).ifPresent(el -> {
                el.setLastOccurredTime(occurredTime);
            });

            // IGNORED 상태이므로 수집 중단
            return null;
        }

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
        // insert=1, update=2 return
        boolean isNewIncident = (logUpsertResult == 1);

        ErrorLog targetLog = errorLogRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Log not found after upsert"));

        // 영향 host agg
        int impactedHostCount = errorLogHostRepository.countHostsByLogHash(logHash);

        // 6. Incident 연동
        // (참고: incrementIfNotIgnored는 IncidentService 내부 로직이나
        //  recordOccurrence 안에서 활용하는 것이 좋습니다. 여기서는 Incident 객체가 필요하므로 기존 흐름 유지)
        Incident incident = null;
        try {
            incident = incidentBridgeService.recordOccurrence(
                    logHash,
                    dto.getServiceName(),
                    targetLog.getSummary(),
                    dto.getStackTrace(),
                    errorCode,
                    effectiveLevel,
                    occurredTime
            );
        } catch (RuntimeException e) {
            log.warn("[INCIDENT][SKIP] recordOccurrence failed. logHash={}, err={}", logHash, e.toString());
        }

        // KB Draft 생성 (Incident가 정상적으로 존재할 때만)
        if (incident != null) {
            int repeatCount = (targetLog.getRepeatCount() == null) ? 1 : targetLog.getRepeatCount();

            boolean matched = (impactedHostCount >= hostSpreadThreshold) || (repeatCount >= highRecurThreshold);
            if (matched) {
                DraftReason reason = (impactedHostCount >= hostSpreadThreshold)
                        ? DraftReason.HOST_SPREAD
                        : DraftReason.HIGH_RECUR;
                try {
                    kbDraftService.createSystemDraft(incident.getId(), impactedHostCount, repeatCount, reason);
                } catch (RuntimeException e) {
                    log.warn("[DRAFT][SKIP] createSystemDraft failed. incidentId={}, logHash={}, hostCount={}, repeatCount={}, reason={}, err={}",
                            incident.getId(), logHash, impactedHostCount, repeatCount, reason, e.toString());
                }
            }
        }

        try {
            // Metrics 처리
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


    @Transactional(transactionManager = "lcTransactionManager")
    public void updateStatus(Long logId, ErrorStatus status) {
        updateStatus(logId, status, null);
    }

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
            case RESOLVED -> {
                LocalDateTime now = LocalDateTime.now();
                errorLog.setResolvedAt(now);

                incidentBridgeService.markResolved(errorLog.getLogHash(), now);

                incidentRepository.findByLogHash(errorLog.getLogHash())
                        .ifPresentOrElse(
                                incident -> {
                                    try {
                                        kbDraftService.createSystemDraft(incident.getId());
                                    } catch (RuntimeException e) {
                                        log.warn("[DRAFT][SKIP] createSystemDraft on RESOLVED failed. incidentId={}, logHash={}, err={}",
                                                incident.getId(), errorLog.getLogHash(), e.toString());
                                    }
                                },
                                () -> log.warn("로그 해시({})에 해당하는 인시던트를 찾을 수 없어 KB 초안을 생성하지 못했습니다.", errorLog.getLogHash())
                        );

                incidentBridgeService.updateStatus(errorLog.getLogHash(), com.soyunju.logcollector.domain.kb.enums.IncidentStatus.RESOLVED);
            }
            case NEW -> {
                errorLog.setResolvedAt(null);
                incidentBridgeService.updateStatus(errorLog.getLogHash(), com.soyunju.logcollector.domain.kb.enums.IncidentStatus.OPEN);
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

    @Transactional(transactionManager = "lcTransactionManager")
    public void deleteLogs(List<Long> logIds) { // TODO : 권한 부여
        if (logIds == null || logIds.isEmpty()) {
            return;
        }
        errorLogRepository.deleteAllByIdInBatch(logIds);
    }

    private boolean isAllowedTransition(ErrorStatus from, ErrorStatus to) {
        if (from == null || to == null) return false;
        if (from == to) return true;

        return switch (from) {
            case NEW -> (to == ErrorStatus.RESOLVED || to == ErrorStatus.IGNORED);
            case RESOLVED -> (to == ErrorStatus.NEW || to == ErrorStatus.IGNORED);
            case IGNORED -> (to == ErrorStatus.NEW || to == ErrorStatus.RESOLVED);
        };
    }

}