package com.soyunju.logcollector.dto.lc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "로그 수집 요청 DTO (Redis 큐 적재용)")
public class ErrorLogRequest {

    @NotBlank(message = "서비스명은 필수입니다.")
    @Schema(example = "CAPTURE-API", requiredMode = Schema.RequiredMode.REQUIRED)
    private String serviceName;

    @Schema(example = "host-01", nullable = true)
    private String hostName;

    @Schema(example = "ERROR", nullable = true, description = "없으면 message 기반 추론")
    private String logLevel;

    @NotBlank(message = "에러 메시지는 필수입니다.")
    @Size(min = 10, message = "에러 메시지는 최소 10자 이상이어야 합니다.")
    @Schema(example = "NullPointerException 발생: userId is null", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(
            example = "java.lang.NullPointerException: ...\n\tat com.example.CaptureService.save(CaptureService.java:42)\n\tat ...",
            nullable = true
    )
    private String stackTrace;

    @Schema(example = "2026-02-18T00:10:00", nullable = true, description = "입력 없으면 현재 시간")
    private LocalDateTime occurredTime;

    @Schema(nullable = true, description = "정규화 후 생성")
    private String logHash;
}
