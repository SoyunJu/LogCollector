package com.soyunju.logcollector.service.crd;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.domain.ErrorStatus;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
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
    private final LogProcessor logProcessor;

    @Transactional
    public ErrorLogResponse saveLog(ErrorLogRequest dto) {
        if (!logProcessor.isTargetLevel(dto.getLogLevel())) return null;

        // 1. 고유 해시 생성
        String logHash = logProcessor.generateLogHash(dto.getServiceName(), dto.getMessage());

        // 2. 중복 여부 확인
        Optional<ErrorLog> existingLog = errorLogRepository.findFirstByLogHashOrderByOccurrenceTimeDesc(logHash);

        if (existingLog.isPresent()) {
            // 3. [중복 로그] 카운트 증가 및 최종 시간 업데이트
            ErrorLog log = existingLog.get();
            log.setRepeatCount(log.getRepeatCount() + 1);
            log.setLastOccurrenceTime(LocalDateTime.now());
            log.setStatus(ErrorStatus.NEW); // 다시 발생했으므로 상태를 NEW로 리셋 가능

            return logProcessor.convertToResponse(log);
        }

        // 4. [신규 로그] 저장
        ErrorLog errorLog = ErrorLog.builder()
                .serviceName(dto.getServiceName())
                .hostName(dto.getHostName())
                .logLevel(dto.getLogLevel().toUpperCase())
                .message(dto.getMessage())
                .stackTrace(dto.getStackTrace())
                .errorCode(logProcessor.generateErrorCode(dto.getMessage()))
                .summary(logProcessor.extractSummary(dto.getStackTrace() != null ?
                        dto.getStackTrace() : dto.getMessage()))
                .logHash(logHash)
                .repeatCount(1)
                .lastOccurrenceTime(LocalDateTime.now())
                .build();

        return logProcessor.convertToResponse(errorLogRepository.save(errorLog));
    }

    public void updateStatus(Long logId, ErrorStatus newStatus) {
        ErrorLog errorLog = errorLogRepository.findById(logId)
                .orElseThrow(() -> new IllegalArgumentException("해당 로그 부재: " + logId));
        errorLog.setStatus(newStatus);
    }

    public void deleteLogs(List<Long> logIds) {
        errorLogRepository.deleteAllByIdInBatch(logIds);
    }
}

