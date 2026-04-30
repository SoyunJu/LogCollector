package com.soyunju.logcollector.service.admin;

import com.soyunju.logcollector.controller.admin.dto.*;
import com.soyunju.logcollector.domain.admin.AdminUser;
import com.soyunju.logcollector.domain.admin.AdminUserRepository;
import com.soyunju.logcollector.infra.security.jwt.JwtProperties;
import com.soyunju.logcollector.infra.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT_LOGGER");

    private final AdminUserRepository adminUserRepository;
    private final JwtTokenProvider    jwtTokenProvider;
    private final JwtProperties       jwtProperties;
    private final PasswordEncoder     passwordEncoder;

    public LoginResponse login(LoginRequest request) {
        AdminUser user = adminUserRepository.findByUsername(request.username())
                .orElseThrow(() -> {
                    AUDIT.info("로그인 실패 - 존재하지 않는 사용자: {}", request.username());
                    return new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
                });

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            AUDIT.info("로그인 실패 - 비밀번호 불일치: userId={}", user.getId());
            throw new IllegalArgumentException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken  = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        AUDIT.info("로그인 성공: userId={}, role={}", user.getId(), user.getRole());

        return LoginResponse.of(accessToken, refreshToken, jwtProperties.getAccessTokenExpiryMs());
    }

    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        String token  = request.refreshToken();
        Long   userId = jwtTokenProvider.getUserId(token);

        if (!jwtTokenProvider.validateRefreshToken(userId, token)) {
            throw new IllegalArgumentException("유효하지 않은 Refresh Token입니다.");
        }

        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getRole());

        return TokenRefreshResponse.of(newAccessToken, jwtProperties.getAccessTokenExpiryMs());
    }

    public void logout(String bearerToken) {
        String token  = bearerToken.substring("Bearer ".length());
        Long   userId = jwtTokenProvider.getUserId(token);

        jwtTokenProvider.blacklist(token);
        jwtTokenProvider.deleteRefreshToken(userId);

        AUDIT.info("로그아웃: userId={}", userId);
    }
}