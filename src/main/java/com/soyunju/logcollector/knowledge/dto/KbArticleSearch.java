package com.soyunju.logcollector.knowledge.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class KbArticleSearch {
    private String status;
    private String keyword;
    private Long incidentId;
    private String createdBy;
}
