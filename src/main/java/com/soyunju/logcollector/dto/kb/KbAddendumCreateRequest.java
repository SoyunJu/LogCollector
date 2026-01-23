package com.soyunju.logcollector.dto.kb;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KbAddendumCreateRequest {
    private String title;
    private String content;
    private String status;
    private String createdBy;

}
