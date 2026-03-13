package com.soyunju.logcollector.domain.kb;

import com.soyunju.logcollector.domain.kb.enums.KbEventOutboxStatus;
import com.soyunju.logcollector.domain.kb.enums.KbEventType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "kb_event_outbox")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KbEventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LC에서 넘어온 log_hash (재처리 키)
    @Column(name = "log_hash", nullable = false, length = 64)
    private String logHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private KbEventType eventType;

    // 이벤트 페이로드 전체를 JSON으로 직렬화해서 저장
    @Lob
    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private KbEventOutboxStatus status;

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
}