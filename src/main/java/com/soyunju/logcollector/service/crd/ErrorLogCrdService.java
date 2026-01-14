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

    @Transactional
    public ErrorLogResponse saveLog(ErrorLogRequest dto) {
        if (!logProcessor.isTargetLevel(dto.getLogLevel())) return null;

        LocalDateTime now = LocalDateTime.now();

        // 1. incident hash 생성 (기존 generateLogHash X)
        String logHash = logProcessor.generateIncidentHash(
                dto.getServiceName(),
                dto.getMessage(),
                dto.getStackTrace()
        );

        String hostName = (dto.getHostName() == null || dto.getHostName().isBlank())
                ? "UNKNOWN_HOST"
                : dto.getHostName();

        // 2. host별 집계 upsert (영향 서버 누락 방지)
        errorLogHostRepository.upsertHostCounter(
                logHash,
                dto.getServiceName(),
                hostName,
                null,   // ip 없음
                now
        );

        // 3. incident 단건 조회 (hash는 유일)
        Optional<ErrorLog> existingLog = errorLogRepository.findByLogHash(logHash);

        if (existingLog.isPresent()) {
            ErrorLog log = existingLog.get();
            log.setRepeatCount(log.getRepeatCount() + 1);
            log.setLastOccurrenceTime(now);
            log.setStatus(ErrorStatus.NEW);

            long impactedHostCount =
                    errorLogHostRepository.countHostsByLogHash(logHash);

            return logProcessor.convertToResponse(log, impactedHostCount);
        }

        // 4. 신규 incident 저장
        ErrorLog errorLog = ErrorLog.builder()
                .serviceName(dto.getServiceName())
                .hostName(hostName)
                .logLevel(dto.getLogLevel().toUpperCase())
                .message(dto.getMessage())
                .stackTrace(dto.getStackTrace())
                .errorCode(logProcessor.generateErrorCode(dto.getMessage()))
                .summary(
                        logProcessor.extractSummary(
                                dto.getStackTrace() != null
                                        ? dto.getStackTrace()
                                        : dto.getMessage()
                        )
                )
                .logHash(logHash)
                .repeatCount(1)
                .lastOccurrenceTime(now)
                .build();

        ErrorLog saved = errorLogRepository.save(errorLog);

        long impactedHostCount =
                errorLogHostRepository.countHostsByLogHash(logHash);

        return logProcessor.convertToResponse(saved, impactedHostCount);
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "UNKNOWN_HOST" : s;
    }

    public void updateStatus(Long logId, com.soyunju.logcollector.domain.ErrorStatus newStatus) {
        ErrorLog errorLog = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("해당 로그 부재: " + logId));
        errorLog.setStatus(newStatus);
    }

    public void deleteLogs(List<Long> logIds) {
        errorLogRepository.deleteAllByIdInBatch(logIds);
    }
}
