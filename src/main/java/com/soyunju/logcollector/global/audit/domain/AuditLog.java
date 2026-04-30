package com.soyunju.logcollector.domain.audit;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_target", columnList = "targetType,targetKey,createdAt")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventType;   // 예: ERRORLOG_STATUS_CHANGED, AUTO_DRAFT_CREATED
    private String targetType;  // 예: ERROR_LOG, INCIDENT, KB_ARTICLE
    private String targetKey;   // 예: logId / incidentId / kbId

    private String actor;       // 헤더 X-Actor, 없으면 system
    @Column(length = 2000)
    private String detail;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
