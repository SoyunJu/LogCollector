package com.soyunju.logcollector.domain.kb;

import com.soyunju.logcollector.domain.kb.enums.CreatedBy;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "kb_addendum")
public class KbAddendum {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK: kb_article_id
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "kb_article_id", nullable = false)
    private KbArticle kbArticle;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false)
    private CreatedBy createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (createdBy == null) createdBy = CreatedBy.user;
    }
}
