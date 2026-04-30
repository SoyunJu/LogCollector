package com.soyunju.logcollector.domain.kb;

import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxAction;
import com.soyunju.logcollector.domain.kb.enums.LcIgnoreOutboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "lc_ignore_outbox")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LcIgnoreOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "log_hash", nullable = false, length = 64)
    private String logHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private LcIgnoreOutboxAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LcIgnoreOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Lob
    @Column(name = "last_error")
    private String lastError;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static LcIgnoreOutbox requestIgnore(String logHash) {
        LcIgnoreOutbox o = new LcIgnoreOutbox();
        o.setLogHash(logHash);
        o.setAction(LcIgnoreOutboxAction.IGNORE);
        o.setStatus(LcIgnoreOutboxStatus.PENDING);
        o.setAttemptCount(0);
        return o;
    }

    public static LcIgnoreOutbox requestUnignore(String logHash) {
        LcIgnoreOutbox o = new LcIgnoreOutbox();
        o.setLogHash(logHash);
        o.setAction(LcIgnoreOutboxAction.UNIGNORE);
        o.setStatus(LcIgnoreOutboxStatus.PENDING);
        o.setAttemptCount(0);
        return o;
    }

}
