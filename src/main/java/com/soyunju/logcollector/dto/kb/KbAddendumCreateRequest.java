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

    @Schema(
            example = "0.8",
            description = "LogFixer 신뢰도 점수 (0.0 ~ 1.0). 0.4 미만이면 저장 거부. 미제공 시 검증 생략."
    )
    private Double confidence;
}
