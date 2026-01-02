package com.soyunju.logcollector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ErrorLogRequest {
    @NotBlank(message = "서비스명은 필수입니다.")
    private String serviceName;

    private String hostName;

    @NotBlank(message = "로그 레벨은 필수입니다.")
    private String logLevel;

    @NotBlank(message = "에러 메시지는 필수입니다.")
    @Size(min = 10, message = "에러 메시지는 최소 10자 이상이어야 합니다.")
    private String message;

    private String stackTrace;
}