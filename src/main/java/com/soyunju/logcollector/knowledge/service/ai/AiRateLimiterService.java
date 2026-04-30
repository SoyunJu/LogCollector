package com.soyunju.logcollector.service.kb.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Profile("prod") // 추가: 실제 Redis 기반 제한이 필요한 prod 환경에서만 활성화
public class AiRateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String GLOBAL_LIMIT_KEY = "ai:analysis:global:count";
    private static final int MAX_DAILY_CALLS = 100;

    public boolean isAllowed() {
        Long count = redisTemplate.opsForValue().increment(GLOBAL_LIMIT_KEY);

        if (count != null && count == 1) {
            // 첫 호출 시 만료 시간 (24시간)
            redisTemplate.expire(GLOBAL_LIMIT_KEY, Duration.ofDays(1));
        }

        return count != null && count <= MAX_DAILY_CALLS;
    }

    public long getCurrentCount() {
        Object count = redisTemplate.opsForValue().get(GLOBAL_LIMIT_KEY);
        return count != null ? Long.parseLong(count.toString()) : 0L;
    }
}