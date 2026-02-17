package com.soyunju.logcollector.domain.lc;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "error_log_hosts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_error_log_hosts_loghash_host", columnNames = {"log_hash", "host_name"})
        },
        indexes = {
                @Index(name = "ix_error_log_hosts_host_last", columnList = "host_name,last_occurrence_time"),
                @Index(name = "ix_error_log_hosts_loghash_last", columnList = "log_hash,last_occurrence_time")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ErrorLogHost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_hash", length = 64, nullable = false)
    private String logHash;

    @Column(name = "service_name", length = 50, nullable = false)
    private String serviceName;

    @Column(name = "host_name", length = 100, nullable = false)
    private String hostName;

    @Column(name = "ip", length = 45)
    private String ip;

    @Column(name = "first_occurrence_time", nullable = false)
    private LocalDateTime firstOccurredTime;

    @Column(name = "last_occurrence_time", nullable = false)
    private LocalDateTime lastoccurredTime;

    @Column(name = "repeat_count", nullable = false)
    private Integer repeatCount;
}
