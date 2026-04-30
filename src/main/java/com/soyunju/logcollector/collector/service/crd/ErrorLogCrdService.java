package com.soyunju.logcollector.service.lc.crd;

import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.event.LogResolvedEvent;
import com.soyunju.logcollector.dto.event.LogSavedEvent;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.exception.InvalidStatusTransitionException;
import com.soyunju.logcollector.monitornig.LcMetrics;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.audit.AuditLogService;
import com.soyunju.logcollector.service.lc.processor.LogNormalization;
import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import com.soyunju.logcollector.service.lc.redis.IgnoredLogHashStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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

    // KB 연동을 위한 서비스 및 레파지토리 주입 -> 서비스 분리를 위해 주석 처리
   /*  private final KbDraftService kbDraftService;
    private final IncidentBridgeService incidentBridgeService;
    private final IncidentRepository incidentRepository; */

    private final ApplicationEventPublisher eventPublisher;

    private final AuditLogService auditLogService;
    private final LcMetrics lcMetrics;

    @Transactional(readOnly = true)
    public Optional<ErrorLog> findByLogHash(String logHash) {
        return errorLogRepository.findByLogHash(logHash);
    }

    @Transactional(readOnly = true)
    public boolean isIgnored(String logHash) {
      /*  return incidentRepository.findByLogHash(logHash)
                .map(incident -> incident.getStatus() == IncidentStatus.IGNORED)
                .orElse(false); */
        return ignoredLogHashStore.isIgnored(logHash);
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

        // 3-1. IGNORED 상태 확인
        if (ignoredLogHashStore.isIgnored(logHash)) {
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

        // 6. Incident 연동을 위해 이벤트 발행
        int repeatCount = (targetLog.getRepeatCount() == null) ? 1 : targetLog.getRepeatCount();
        boolean draftNeeded = (impactedHostCount >= hostSpreadThreshold) || (repeatCount >= highRecurThreshold);
        String draftReason = (impactedHostCount >= hostSpreadThreshold) ? "HOST_SPREAD" : "HIGH_RECUR";

        eventPublisher.publishEvent(new LogSavedEvent(
                logHash,
                dto.getServiceName(),
                targetLog.getSummary(),
                dto.getStackTrace(),
                errorCode,
                effectiveLevel,
                occurredTime,
                impactedHostCount,
                repeatCount,
                null,       // incidentId: Listener에서 recordOccurrence 후 Draft 생성
                draftNeeded,
                draftReason
        ));

        // 7. Metrics 처리
        try {
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
        // errorLog.setStatus(status);
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
                //  이벤트 발행
                eventPublisher.publishEvent(new LogResolvedEvent(
                        errorLog.getLogHash(),
                        null,   // incidentId: Listener에서 markResolved 후 조회
                        now
                ));
            }
            case NEW -> {
                errorLog.setResolvedAt(null);
                // 이벤트 발행 (OPEN 전이)
                eventPublisher.publishEvent(new LogResolvedEvent(
                        errorLog.getLogHash(),
                        null,
                        null    // resolvedAt null = OPEN 전이 신호
                ));
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