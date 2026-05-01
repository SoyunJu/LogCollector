package com.soyunju.logcollector.global.audit.repository;

import com.soyunju.logcollector.global.audit.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
