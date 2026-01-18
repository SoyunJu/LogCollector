package com.soyunju.logcollector.service.kb.crd;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public Optional<Incident> findByLogHash(String logHash) {
        return incidentRepository.findByLogHash(logHash);
    }

    public Incident recordOccurrence(
            String logHash,
            String serviceName,
            String summary,
            String stackTrace,
            String errorCode,
            String logLevel,
            LocalDateTime occurredAt
    ) {
        LocalDateTime ts = (occurredAt != null) ? occurredAt : LocalDateTime.now();
        ErrorLevel level = mapErrorLevel(logLevel);

        Incident incident = incidentRepository.findByLogHash(logHash)
                .orElseGet(() -> Incident.builder()
                        .logHash(logHash)
                        .serviceName(serviceName)
                        .status(IncidentStatus.OPEN)
                        .errorLevel(level)
                        .repeatCount(0) // 아래에서 +1
                        .firstOccurredAt(ts)
                        .lastOccurredAt(ts)
                        .build());

        if (incident.getFirstOccurredAt() == null) incident.setFirstOccurredAt(ts);

        // last / count 갱신
        incident.setLastOccurredAt(ts);
        incident.setRepeatCount((incident.getRepeatCount() == null ? 0 : incident.getRepeatCount()) + 1);

        // 재발시 OPEN
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            incident.setStatus(IncidentStatus.OPEN);
            incident.setResolvedAt(null);
        }

        if (isBlank(incident.getServiceName()) && !isBlank(serviceName)) {
            incident.setServiceName(serviceName);
        }

        // 비어있을 때만 insert
        if (isBlank(incident.getSummary()) && !isBlank(summary)) incident.setSummary(summary);
        if (isBlank(incident.getStackTrace()) && !isBlank(stackTrace)) incident.setStackTrace(stackTrace);
        if (isBlank(incident.getErrorCode()) && !isBlank(errorCode)) incident.setErrorCode(errorCode);

        if (incident.getErrorLevel() == null) incident.setErrorLevel(level);

        return incidentRepository.save(incident);
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
