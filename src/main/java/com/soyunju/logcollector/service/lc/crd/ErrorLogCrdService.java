package com.soyunju.logcollector.service.lc.crd;

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
        LocalDateTime occurredTime = (dto.getOccurredTime() != null) ? dto.getOccurredTime() : LocalDateTime.now();

       // dto.getServiceName(), dto.getLogLevel(), dto.getOccurredTime());

        if (!logProcessor.isTargetLevel(dto.getLogLevel())) {
            return null;
        }
        // dto.getServiceName(), dto.getMessage(), dto.getStackTrace());

        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());

        String hostName = (dto.getHostName() == null || dto.getHostName().isBlank()) ? "UNKNOWN_HOST" : dto.getHostName();

        // 1. Host 집계 Upsert
        int hostUpsertResult = errorLogHostRepository.upsertHostCounter(logHash, dto.getServiceName(), hostName, null, occurredTime);
        boolean isNewHost = (hostUpsertResult == 1);

        // 2. ErrorLog Upsert (Atomic 처리)
        String errorCode = LogNormalization.generateErrorCode(dto.getMessage(), dto.getStackTrace());
        String summary = logProcessor.extractSummary(dto.getMessage());

        int logUpsertResult = errorLogRepository.upsertErrorLog(
                dto.getServiceName(), hostName, dto.getLogLevel().toUpperCase(),
                dto.getMessage(), dto.getStackTrace(), occurredTime,
                errorCode, summary, logHash
        );

        // 신규 insert는 1, update는 2를 반환함
        boolean isNewIncident = (logUpsertResult == 1);

        // 3. 결과 반환을 위한 엔티티 조회
        ErrorLog targetLog = errorLogRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Log not found after upsert"));

        long impactedHostCount = errorLogHostRepository.countHostsByLogHash(logHash);

        // 4. Incident 연동 (IncidentService도 동일하게 Upsert 방식으로 수정 권장)
        incidentService.recordOccurrence(
                logHash, dto.getServiceName(), targetLog.getSummary(),
                dto.getStackTrace(), errorCode, dto.getLogLevel(), occurredTime
        );
        try {
            if (!logProcessor.isTargetLevel(dto.getLogLevel())) {
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
        ErrorLog log = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로그 ID: " + logId));

        ErrorStatus from = log.getStatus();
        log.setStatus(status);
        lcMetrics.incStatusChange(from, status);

        ErrorStatus to = status;
        if (from != null && to != null && from != to && !isAllowedTransition(from, to)) {
            throw new InvalidStatusTransitionException("허용되지 않는 상태 전이: " + from + " -> " + to);
        }

        log.setStatus(status);

        switch (status) {
            case ACKNOWLEDGED -> {
                if (log.getAcknowledgedAt() == null) {
                    log.setAcknowledgedAt(LocalDateTime.now());
                }
                if (log.getAcknowledgedBy() == null || log.getAcknowledgedBy().isBlank()) {
                    if (actor != null && !actor.isBlank()) {
                        log.setAcknowledgedBy(actor);
                    } else {
                        log.setAcknowledgedBy("Operations Manager");
                    }
                }
                log.setResolvedAt(null);
            }
            case RESOLVED -> {
                LocalDateTime now = LocalDateTime.now();
                log.setResolvedAt(now);

                incidentService.markResolved(log.getLogHash(), now);

                incidentRepository.findByLogHash(log.getLogHash())
                        .ifPresent(incident -> kbArticleService.createSystemDraft(incident.getId()));
            }
            case NEW -> {
                log.setResolvedAt(null);
                log.setAcknowledgedAt(null);
                log.setAcknowledgedBy(null);
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
                "from=" + from + ", to=" + to + ", logHash=" + log.getLogHash()
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