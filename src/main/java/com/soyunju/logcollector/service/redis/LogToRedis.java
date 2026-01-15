package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.service.crd.ErrorLogCrdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogToRedis {

    private static final String LOG_QUEUE_KEY = "error-log-queue";
    // 큐가 쌓일 때 자동 정리
    private static final Duration QUEUE_TTL = Duration.ofMinutes(30);

    private final RedisTemplate<String, ErrorLogRequest> redisTemplate;
    // Redis 장애 시 로그 유실 방지용(폴백)
    private final ErrorLogCrdService errorLogCrdService;

    /**
     * 로그를 Redis 큐에 적재
     * - Redis 정상: 큐 적재 + TTL 갱신
     * - Redis 장애: DB로 바로 저장(유실 방지)
     */
    public void push(ErrorLogRequest dto) {
        try {
            redisTemplate.opsForList().rightPush(LOG_QUEUE_KEY, dto);
            redisTemplate.expire(LOG_QUEUE_KEY, QUEUE_TTL);

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패 → DB direct fallback. msg={}", e.getMessage());
            safeFallbackToDb(dto);

        } catch (Exception e) {
            log.warn("Redis push 실패 → DB direct fallback. msg={}", e.getMessage());
            safeFallbackToDb(dto);
        }
    }

    private void safeFallbackToDb(ErrorLogRequest dto) {
        try {
            errorLogCrdService.saveLog(dto);
        } catch (Exception ex) {
            log.error("DB fallback 실패(로그 유실 가능). msg={}", ex.getMessage(), ex);
        }
    }
}
