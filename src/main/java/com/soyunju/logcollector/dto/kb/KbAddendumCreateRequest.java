package com.soyunju.logcollector.dto.kb;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class KbAddendumCreateRequest {
    private String title;
    private String content;
    private String status;
    @Schema(
            example = "user",
            allowableValues = {"system", "user", "admin"},
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String createdBy;

}
