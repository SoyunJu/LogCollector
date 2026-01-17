package com.soyunju.logcollector.service.lc.crd;

import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.kb.crd.KbArticleService;
import com.soyunju.logcollector.service.lc.processor.LogNormalization;
import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ErrorLogCrdService {

    private final ErrorLogRepository errorLogRepository;
    private final ErrorLogHostRepository errorLogHostRepository;
    private final LogProcessor logProcessor;

    // KB 연동을 위한 서비스 및 리포지토리 주입
    private final KbArticleService kbArticleService;
    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public boolean isIgnored(String logHash) {
        return errorLogRepository.findByLogHash(logHash)
                .map(log -> log.getStatus() == ErrorStatus.IGNORED)
                .orElse(false);
    }

    public ErrorLogResponse saveLog(ErrorLogRequest dto) {

        // 발생 시각 정책: request에 없으면 수집 시점(now)로 고정
        LocalDateTime occurredTime =
                (dto.getOccurredTime() != null) ? dto.getOccurredTime() : LocalDateTime.now();

        if (!logProcessor.isTargetLevel(dto.getLogLevel())) return null;

        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());
        String hostName = (dto.getHostName() == null || dto.getHostName().isBlank()) ? "UNKNOWN_HOST" : dto.getHostName();

        // Host 집계는 "관측 시각"을 넣고 싶으면 occurredTime을 사용(정책 일관)
        int upsertResult = errorLogHostRepository.upsertHostCounter(
                logHash, dto.getServiceName(), hostName, null, occurredTime
        );
        boolean isNewHost = (upsertResult == 1);

        Optional<ErrorLog> existingLog = errorLogRepository.findByLogHash(logHash);
        boolean isNewIncident = existingLog.isEmpty();

        String errorCode = LogNormalization.generateErrorCode(dto.getMessage(), dto.getStackTrace());

        ErrorLog targetLog;

        if (existingLog.isPresent()) {
            targetLog = existingLog.get();

            targetLog.setRepeatCount(targetLog.getRepeatCount() + 1);
            targetLog.setOccurredTime(occurredTime);
            targetLog.setLastOccurredTime(occurredTime);

            if (targetLog.getStatus() == ErrorStatus.RESOLVED) {
                targetLog.setStatus(ErrorStatus.NEW);
                targetLog.setResolvedAt(null);
                targetLog.setAcknowledgedAt(null);
                targetLog.setAcknowledgedBy(null);
            }
            // ACKNOWLEDGED / NEW 는 그대로 유지
            targetLog = errorLogRepository.save(targetLog);
        } else {
            targetLog = ErrorLog.builder()
                    .serviceName(dto.getServiceName())
                    .hostName(hostName)
                    .logLevel(dto.getLogLevel().toUpperCase())
                    .message(dto.getMessage())
                    .summary(logProcessor.extractSummary(dto.getMessage()))
                    .logHash(logHash)
                    .errorCode(errorCode)
                    .repeatCount(1)
                    .occurredTime(occurredTime)
                    .firstOccurredTime(occurredTime)     // 신규에만 세팅
                    .lastOccurredTime(occurredTime)
                    .status(ErrorStatus.NEW)
                    .build();

            targetLog = errorLogRepository.save(targetLog);
        }

        long impactedHostCount = errorLogHostRepository.countHostsByLogHash(logHash);
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
                log.setResolvedAt(LocalDateTime.now());

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