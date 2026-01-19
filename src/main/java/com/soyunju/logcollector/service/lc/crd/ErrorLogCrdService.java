package com.soyunju.logcollector.service.lc.crd;

import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
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
            retryFor = { ConcurrencyFailureException.class },
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    public ErrorLogResponse saveLog(ErrorLogRequest dto) {
        LocalDateTime occurredTime = (dto.getOccurredTime() != null) ? dto.getOccurredTime() : LocalDateTime.now();
        // 디버깅 추가 1
        log.info("[SAVELOG][ENTER] service={} level={} occurredTime={}",
                dto.getServiceName(), dto.getLogLevel(), dto.getOccurredTime());

        if (!logProcessor.isTargetLevel(dto.getLogLevel())) {
            // 디버깅 추가 2
            log.info("[SAVELOG][SKIP] not target level: {}", dto.getLogLevel());
            return null;
        }

        log.info("[SAVELOG][HASH_INPUT] service={} msg={} st={}",
                dto.getServiceName(), dto.getMessage(), dto.getStackTrace());

        String normalizedMsg = LogNormalization.normalizeMessage(dto.getMessage());
        String normalizedSt  = LogNormalization.normalizeMessage(dto.getStackTrace());

        log.info("[SAVELOG][HASH_INPUT_NORM] service={} msgNorm={} stNorm={}",
                dto.getServiceName(), normalizedMsg, normalizedSt);

        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());
       // 디버깅 추가 3
        log.info("[SAVELOG][HASH] logHash={}", logHash);

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

        // 디버깅 추가 4
        log.info("[SAVELOG][HOST] upsertResult={} isNewHost={}",
                hostUpsertResult, hostUpsertResult == 1);


        // MySQL/MariaDB: 신규 insert는 1, update는 2를 반환함
        boolean isNewIncident = (logUpsertResult == 1);

        // 디버깅 추가 5
        log.info("[SAVELOG][ERRORLOG] upsertResult={} isNewIncident={}",
                logUpsertResult, isNewIncident);

        // 3. 결과 반환을 위한 엔티티 조회
        ErrorLog targetLog = errorLogRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Log not found after upsert"));

        // 디버깅 추가 6
        log.info("[SAVELOG][ERRORLOG] persisted id={} status={}",
                targetLog.getId(), targetLog.getStatus());

        long impactedHostCount = errorLogHostRepository.countHostsByLogHash(logHash);

        // 디버깅 추가 7
        log.info("*******[SAVELOG][INCIDENT][CALL]***** logHash={} service={} level={}",
                logHash, dto.getServiceName(), dto.getLogLevel());

        // 4. Incident 연동 (IncidentService도 동일하게 Upsert 방식으로 수정 권장)
        incidentService.recordOccurrence(
                logHash, dto.getServiceName(), targetLog.getSummary(),
                dto.getStackTrace(), errorCode, dto.getLogLevel(), occurredTime
        );
        // 디버깅 추가 8
        log.info("******[SAVELOG][INCIDENT][DONE]***** logHash={}", logHash);

        return logProcessor.convertToResponse(targetLog, impactedHostCount, isNewIncident, isNewHost);
    }

    @Transactional
    public void updateStatus(Long logId, ErrorStatus status) {
        ErrorLog log = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로그 ID: " + logId));

        log.setStatus(status);

        switch (status) {
            case ACKNOWLEDGED -> {
                if (log.getAcknowledgedAt() == null) {
                    log.setAcknowledgedAt(LocalDateTime.now());
                }
                if (log.getAcknowledgedBy() == null || log.getAcknowledgedBy().isBlank()) {
                    log.setAcknowledgedBy("Operations Manager"); // 임시 고정값 추후 인사연동이나 로그인 정보 등으로 연동
                }
                // ACK는 해결이 아니므로 resolvedAt 제거
                log.setResolvedAt(null);
            }
            case RESOLVED -> {
                LocalDateTime now = LocalDateTime.now();
                log.setResolvedAt(now);
                // incident status 도 변경
                incidentService.markResolved(log.getLogHash(), now);
                // incident 존재시 system draft 생성 --> TODO : 정책화로 옮길 계획이면 추후 제거, 이관 필요
                incidentRepository.findByLogHash(log.getLogHash())
                        .ifPresent(incident -> kbArticleService.createSystemDraft(incident.getId()));
            }
            case NEW -> {
                // 수동 재오픈시 정합성 위해 초기화
                log.setResolvedAt(null);
                log.setAcknowledgedAt(null);
                log.setAcknowledgedBy(null);
            }
            case IGNORED -> {
                // 추가 처리 없음
            }
        }
    }

    @Transactional
    public void deleteLogs(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return;
        }

        errorLogRepository.deleteAllByIdInBatch(logIds);
    }

}