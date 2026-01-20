package com.soyunju.logcollector.service.kb.crd;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public Optional<Incident> findByLogHash(String logHash) {
        return incidentRepository.findByLogHash(logHash);
    }

    // [추가] 인시던트 전체 목록 조회
    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<com.soyunju.logcollector.dto.kb.IncidentResponse> findAll(org.springframework.data.domain.Pageable pageable) {
        return incidentRepository.findAll(pageable).map(com.soyunju.logcollector.dto.kb.IncidentResponse::from);
    }

    @Transactional(
            transactionManager = "kbTransactionManager",
            propagation = Propagation.REQUIRES_NEW
    )
    public Incident recordOccurrence(

            String logHash, String serviceName, String summary,
            String stackTrace, String errorCode, String logLevel, LocalDateTime occurredAt
    ) {

        log.info("[INCIDENT][ENTER] logHash={} time={}", logHash, occurredAt);

        LocalDateTime ts = (occurredAt != null) ? occurredAt : LocalDateTime.now();

        // 1. ErrorLevel 매핑 및 DB ENUM 값 검증 (ERROR, EXCEPTION, FATAL, WARN 중 하나여야 함)
        String level = mapErrorLevel(logLevel).name();

        // 2. Native Upsert 수행
        incidentRepository.upsertIncident(
                logHash, serviceName, summary, stackTrace, errorCode, level, ts
        );

        log.info("[INCIDENT][UPSERT] logHash={}", logHash);

        // 3. 반영 결과 조회
        return incidentRepository.findByLogHash(logHash)
                .orElseThrow(() -> new IllegalStateException("Incident Upsert 실패: " + logHash));
    }

    public void markResolved(String logHash, LocalDateTime resolvedAt) {
        incidentRepository.findByLogHash(logHash).ifPresent(incident -> {
            incident.setStatus(IncidentStatus.RESOLVED);
            incident.setResolvedAt(resolvedAt != null ? resolvedAt : LocalDateTime.now());
            incidentRepository.save(incident);
        });
    }

    private ErrorLevel mapErrorLevel(String logLevel) {
        if (logLevel == null) return ErrorLevel.ERROR;
        String v = logLevel.trim().toUpperCase();
        try {
            return ErrorLevel.valueOf(v);
        } catch (Exception e) {
            return ErrorLevel.ERROR;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }



}
