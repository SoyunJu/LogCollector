package com.soyunju.logcollector.admin.service;

import com.soyunju.logcollector.admin.domain.ApiKey;
import com.soyunju.logcollector.admin.repository.ApiKeyRepository;
import com.soyunju.logcollector.global.security.apikey.ApiKeyPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final ApiKeyRepository   apiKeyRepository;
    private final StringRedisTemplate redisTemplate;

    private static final String CACHE_PREFIX = "apikey:";
    private static final long   CACHE_TTL_SEC = 300; // 5분


    public Optional<ApiKeyPrincipal> validate(String rawApiKey) {
        String hashedKey = hash(rawApiKey);

        // Redis 캐시 조회
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + hashedKey);
        if (cached != null) {
            return Optional.of(deserialize(cached));
        }

        // DB 조회
        Optional<ApiKey> apiKey = apiKeyRepository.findByApiKey(hashedKey);
        if (apiKey.isEmpty()) {
            return Optional.empty();
        }

        ApiKey key = apiKey.get();
        if (!key.isValid()) {
            log.debug("유효하지 않은 API Key: tenantId={}, revoked={}, expired={}",
                    key.getTenantId(), key.isRevoked(), key.isExpired());
            return Optional.empty();
        }

        // 캐시 저장
        ApiKeyPrincipal principal = new ApiKeyPrincipal(
                key.getId(), key.getTenantId(), key.getAppId(), key.getPlan()
        );
        redisTemplate.opsForValue().set(
                CACHE_PREFIX + hashedKey,
                serialize(principal),
                CACHE_TTL_SEC,
                TimeUnit.SECONDS
        );

        return Optional.of(principal);
    }


    // del api -> clear cache
    public void evictCache(String hashedKey) {
        redisTemplate.delete(CACHE_PREFIX + hashedKey);
    }


    // utils

    private String hash(String rawKey) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    // 직렬화
    private String serialize(ApiKeyPrincipal p) {
        return p.getTenantId() + "|" + p.getAppId() + "|" + p.getPlan() + "|" + p.getApiKeyId();
    }

    private ApiKeyPrincipal deserialize(String value) {
        String[] parts = value.split("\\|");
        return new ApiKeyPrincipal(Long.valueOf(parts[3]), parts[0], parts[1], parts[2]);
    }
}