package com.soyunju.logcollector.domain.kb;

import lombok.*;

import java.io.Serializable;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class KbArticleTagId implements Serializable {
    private Long kbArticleId;
    private Long kbTagId;
}
