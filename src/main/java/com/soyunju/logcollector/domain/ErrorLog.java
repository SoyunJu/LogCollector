package com.soyunju.logcollector.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "error_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ErrorLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_status")
    @Builder.Default
    private AnalysisStatus analysisStatus = AnalysisStatus.PENDING; //

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ErrorStatus status = ErrorStatus.NEW;

    private String errorCode; // 추가됨
    private String summary;   // 추가됨

    @CreationTimestamp
    @Column(name = "resolved_at", precision = 3, updatable = true)
    private LocalDateTime resolved_at; // 아직 미사용 ( 장애 해결 시각 업데이트 할지 말지 )

    @Column(name = "log_hash", length = 64)
    private String logHash; // 서비스명 + 메시지 해시값

    @Column(name = "repeat_count")
    @Builder.Default
    private Integer repeatCount = 1; // 누적 발생 횟수

    @Column(name = "last_occurrence_time")
    private LocalDateTime lastOccurrenceTime; // 마지막 발생 시간
}