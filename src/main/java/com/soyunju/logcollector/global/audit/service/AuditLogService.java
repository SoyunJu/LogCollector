package com.soyunju.logcollector.global.audit.service;

import com.soyunju.logcollector.global.audit.domain.AuditLog;
import com.soyunju.logcollector.global.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void write(String eventType, String targetType, String targetKey, String actor, String detail) {
        auditLogRepository.save(AuditLog.builder()
                .eventType(eventType)
                .targetType(targetType)
                .targetKey(targetKey)
                .actor((actor == null || actor.isBlank()) ? "system" : actor)
                .detail(detail)
                .build());
    }
}
