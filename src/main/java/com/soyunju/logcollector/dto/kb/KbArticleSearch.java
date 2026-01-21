package com.soyunju.logcollector.dto.kb;

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
