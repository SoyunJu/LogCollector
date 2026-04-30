package com.soyunju.logcollector.global.security.jwt;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    private static final String CLAIM_ROLE      = "role";
    private static final String PREFIX_REFRESH   = "refresh:";
    private static final String PREFIX_BLACKLIST = "blacklist:";


    // Create Token
    public String generateAccessToken(Long userId, String role) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getAccessTokenExpiryMs());

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey())
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Date now    = new Date();
        Date expiry = new Date(now.getTime() + jwtProperties.getRefreshTokenExpiryMs());

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey())
                .compact();

        // Redis 저장
        redisTemplate.opsForValue().set(
                PREFIX_REFRESH + userId,
                token,
                jwtProperties.getRefreshTokenExpiryMs(),
                TimeUnit.MILLISECONDS
        );

        return token;
    }



    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return !isBlacklisted(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 JWT: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateRefreshToken(Long userId, String token) {
        String stored = redisTemplate.opsForValue().get(PREFIX_REFRESH + userId);
        if (stored == null || !stored.equals(token)) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }



    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get(CLAIM_ROLE, String.class);
    }

    public long getRemainingExpiryMs(String token) {
        Date expiration = parseClaims(token).getExpiration();
        return expiration.getTime() - System.currentTimeMillis();
    }


    // Logout (BlackList)
    public void blacklist(String token) {
        long remainingMs = getRemainingExpiryMs(token);
        if (remainingMs > 0) {
            redisTemplate.opsForValue().set(
                    PREFIX_BLACKLIST + token,
                    "1",
                    remainingMs,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(PREFIX_REFRESH + userId);
    }


    // Utils

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX_BLACKLIST + token));
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.getSecret()));
    }
}