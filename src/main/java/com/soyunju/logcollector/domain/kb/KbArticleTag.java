package com.soyunju.logcollector.domain.kb;

import jakarta.persistence.*;
import lombok.*;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "kb_article_tag")
@IdClass(KbArticleTagId.class)
public class KbArticleTag {

    @Id
    @Column(name = "kb_article_id", nullable = false)
    private Long kbArticleId;

    @Id
    @Column(name = "kb_tag_id", nullable = false)
    private Long kbTagId;
}
