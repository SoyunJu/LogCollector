package com.soyunju.logcollector.service.lc.redis;

import com.soyunju.logcollector.config.LogCollectorRedisProperties;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.monitornig.LcMetrics;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogToRedis {

    private final RedisTemplate<String, ErrorLogRequest> redisTemplate;
    private final LogCollectorRedisProperties redisProperties;
    // Redis 장애 시 로그 유실 방지용(폴백)
    private final ErrorLogCrdService errorLogCrdService;
    private final LcMetrics lcMetrics;

    /**
     * 로그를 Redis 큐에 적재
     * - Redis 정상: 큐 적재 + TTL 갱신
     * - Redis 장애: DB로 바로 저장(유실 방지)
     */
    public void push(ErrorLogRequest dto) {
        try {
            lcMetrics.incRedisEnqueueSuccess();
            redisTemplate.opsForList().rightPush(redisProperties.getQueueKey(), dto);
            redisTemplate.expire(redisProperties.getQueueKey(), redisProperties.queueTtl());
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 연결 실패 → DB direct fallback. msg={}", e.getMessage());
            lcMetrics.incRedisEnqueueFailure("redis_connection");
            safeFallbackToDb(dto);

        } catch (Exception e) {
            log.warn("Redis push 실패 → DB direct fallback. msg={}", e.getMessage());
            lcMetrics.incRedisEnqueueFailure("exception");
            safeFallbackToDb(dto);
        }
    }

    private void safeFallbackToDb(ErrorLogRequest dto) {
        try {
            errorLogCrdService.saveLog(dto);
            lcMetrics.incDbFallback("success");
        } catch (Exception ex) {
            log.error("DB fallback 실패(로그 유실 가능). msg={}", ex.getMessage(), ex);
            lcMetrics.incDbFallback("failure");
        }
    }
}
