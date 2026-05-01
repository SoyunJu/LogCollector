package com.soyunju.logcollector.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(
        @NotBlank(message = "Refresh Token을 입력해주세요.")
        String refreshToken
) {}