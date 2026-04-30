package com.soyunju.logcollector.admin.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresInMs) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresInMs / 1000);
    }
}