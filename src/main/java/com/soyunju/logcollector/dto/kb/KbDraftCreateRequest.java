package com.soyunju.logcollector.dto.kb;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KbDraftCreateRequest {
    private String incidentTitle;
    private String content; // null이면 템플릿으로 생성
}
