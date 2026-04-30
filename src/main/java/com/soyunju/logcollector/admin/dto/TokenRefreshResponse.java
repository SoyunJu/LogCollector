package com.soyunju.logcollector.admin.dto;

public record TokenRefreshResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
    public static TokenRefreshResponse of(String accessToken, long expiresInMs) {
        return new TokenRefreshResponse(accessToken, "Bearer", expiresInMs / 1000);
    }
}