package com.soyunju.logcollector.domain.kb;

import com.soyunju.logcollector.domain.kb.enums.DraftReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "system_draft")
public class SystemDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK: incident_id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Column(name = "host_count", nullable = false)
    private Integer hostCount;

    @Column(name = "repeat_count", nullable = false)
    private Integer repeatCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private DraftReason reason;

    @Column(name = "created_kb_article_id")
    private Long createdKbArticleId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
