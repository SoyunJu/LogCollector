package com.soyunju.logcollector.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "error_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_name", length = 50, nullable = false)
    private String serviceName;

    @Column(name = "host_name", length = 100)
    private String hostName;

    @Column(name = "log_level", length = 10, nullable = false)
    private String logLevel;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    @Column(name = "stack_trace", columnDefinition = "LONGTEXT")
    private String stackTrace;

    @CreationTimestamp
    @Column(name = "occurrence_time", precision = 3, updatable = false)
    private LocalDateTime occurrenceTime;

    @Column(name = "ai_root_cause", columnDefinition = "TEXT")
    private String aiRootCause;

    @Column(name = "ai_suggestion", columnDefinition = "TEXT")
    private String aiSuggestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status")
    @Builder.Default
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ErrorStatus status = ErrorStatus.NEW;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}