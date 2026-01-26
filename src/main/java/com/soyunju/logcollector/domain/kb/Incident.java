package com.soyunju.logcollector.domain.kb;

import com.soyunju.logcollector.domain.kb.enums.ErrorLevel;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "incident")
@EntityListeners(AuditingEntityListener.class)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_hash", nullable = false, unique = true, length = 64)
    private String logHash;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "incident_title")
    private String incidentTitle;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(name = "created_by")
    private String createdBy;

    @Lob
    @Column(name = "stack_trace", columnDefinition = "LONGTEXT")
    private String stackTrace;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_level", nullable = false)
    private ErrorLevel errorLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IncidentStatus status;

    @Column(name = "first_occurred_at")
    private LocalDateTime firstOccurredAt;

    @Column(name = "last_occurred_at")
    private LocalDateTime lastOccurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "repeat_count", nullable = false)
    @Builder.Default
    private Integer repeatCount = 1;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "close_eligible_at")
    private LocalDateTime closeEligibleAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "reopened_at")
    private LocalDateTime reopenedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
