package com.soyunju.logcollector.dto.kb;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Draft 생성 입력 DTO (content는 최종 KB 본문이 아니라, stacktrace/summary seed 텍스트)")
public class KbDraftCreateRequest {

    @Schema(example = "CAPTURE-API 장애 - Redis timeout", nullable = true)
    private String incidentTitle;

    @Schema(
            nullable = true,
            description = "stacktrace 또는 summary. null이면 시스템 템플릿으로 생성",
            example = "java.lang.NullPointerException: ...\n\tat com.example.CaptureService.save(CaptureService.java:42)\n\tat ..."
    )
    private String content;

}
