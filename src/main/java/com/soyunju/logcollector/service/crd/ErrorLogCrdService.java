package com.soyunju.logcollector.service.crd;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.repository.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import com.soyunju.logcollector.service.processor.LogProcessor;
import com.soyunju.logcollector.util.LogNormalization;
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

            // 재발생 시각/횟수/상태 갱신
            targetLog.setRepeatCount(targetLog.getRepeatCount() + 1);
            targetLog.setStatus(ErrorStatus.NEW);
            targetLog.setOccurredTime(occurredTime);
            targetLog.setLastOccurredTime(occurredTime);

            // firstOccurredTime은 "신규 생성 시"에만 세팅하고, 기존에는 건드리지 않음

            if (targetLog.getErrorCode() == null || targetLog.getErrorCode().isBlank()) {
                targetLog.setErrorCode(errorCode);
            }

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
        ErrorLog log = errorLogRepository.findById(logId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로그 ID: " + logId));

        log.setStatus(status);
    }

    @Transactional
    public void deleteLogs(List<Long> logIds) {
        if (logIds == null || logIds.isEmpty()) {
            return;
        }

        errorLogRepository.deleteAllByIdInBatch(logIds);
    }

}