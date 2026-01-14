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
        if (!logProcessor.isTargetLevel(dto.getLogLevel())) return null;

        LocalDateTime now = LocalDateTime.now();

        String logHash = logProcessor.generateIncidentHash(dto.getServiceName(), dto.getMessage(), dto.getStackTrace());

        String hostName = (dto.getHostName() == null || dto.getHostName().isBlank()) ? "UNKNOWN_HOST" : dto.getHostName();

        // 1) Host별 집계 (DB에서 신규 삽입 시 1, 기존 업데이트 시 2 반환)
        int upsertResult = errorLogHostRepository.upsertHostCounter(logHash, dto.getServiceName(), hostName, null, now);
        boolean isNewHost = (upsertResult == 1);

        // 2) Incident(에러 유형) 조회
        Optional<ErrorLog> existingLog = errorLogRepository.findByLogHash(logHash);
        boolean isNewIncident = existingLog.isEmpty();

        // 3) error_code 생성 (요구사항: 생성 가능하면 insert 시 error_code에 저장)
        // - stackTrace가 null이어도 안전
        String errorCode = LogNormalization.generateErrorCode(dto.getMessage(), dto.getStackTrace());

        ErrorLog targetLog;

        if (existingLog.isPresent()) {
            targetLog = existingLog.get();
            targetLog.setRepeatCount(targetLog.getRepeatCount() + 1);
            targetLog.setLastOccurredTime(now);
            targetLog.setStatus(ErrorStatus.NEW);

            // (권장) 기존 레코드에 error_code가 비어있다면 채움
            if (targetLog.getErrorCode() == null || targetLog.getErrorCode().isBlank()) {
                targetLog.setErrorCode(errorCode);
            }

            // 보수적으로 save 호출(트랜잭션/영속성 상태 이슈 방지)
            targetLog = errorLogRepository.save(targetLog);

        } else {
            targetLog = ErrorLog.builder().serviceName(dto.getServiceName()).hostName(hostName).logLevel(dto.getLogLevel().toUpperCase()).message(dto.getMessage()).summary(logProcessor.extractSummary(dto.getMessage())).logHash(logHash).errorCode(errorCode)         // ✅ 신규 insert 시 error_code 저장
                    .repeatCount(1).occurredTime(now).lastOccurredTime(now).status(ErrorStatus.NEW)      // 기존 코드에서 NEW로 세팅하던 흐름과 일관
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