package com.soyunju.logcollector.service.crd;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.repository.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import com.soyunju.logcollector.service.processor.LogProcessor;
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
        if (!logProcessor.isTargetLevel(dto.getLogLevel())) return null;

        LocalDateTime now = LocalDateTime.now();
        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());
        String hostName = (dto.getHostName() == null || dto.getHostName().isBlank()) ? "UNKNOWN_HOST" : dto.getHostName();

        // 1. host별 집계 upsert
        errorLogHostRepository.upsertHostCounter(logHash, dto.getServiceName(), hostName,null, now);

        // 2. incident 조회 및 처리
        Optional<ErrorLog> existingLog = errorLogRepository.findByLogHash(logHash);

        ErrorLog targetLog;
        if (existingLog.isPresent()) {
            targetLog = existingLog.get();
            targetLog.setRepeatCount(targetLog.getRepeatCount() + 1);
            targetLog.setLastOccurredTime(now);
            targetLog.setStatus(ErrorStatus.NEW);
        } else {
            targetLog = ErrorLog.builder()
                    .serviceName(dto.getServiceName())
                    .hostName(hostName)
                    .logLevel(dto.getLogLevel().toUpperCase())
                    .message(dto.getMessage())
                    .stackTrace(dto.getStackTrace())
                    .errorCode(logProcessor.generateErrorCode(dto.getMessage()))
                    .summary(logProcessor.extractSummary(dto.getStackTrace() != null ? dto.getStackTrace() : dto.getMessage()))
                    .logHash(logHash)
                    .repeatCount(1)
                    .occurredTime(now)
                    .lastOccurredTime(now)
                    .build();
            targetLog = errorLogRepository.save(targetLog);
        }

        long impactedHostCount = errorLogHostRepository.countHostsByLogHash(logHash);
        return logProcessor.convertToResponse(targetLog, impactedHostCount);
    }

    @Transactional
    public void updateStatus(Long logId, ErrorStatus status) {
        ErrorLog log = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 로그 ID: " + logId));

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