package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LogToRedis {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LOG_QUEUE_KEY = "error-log-queue";

    /**
     * 로그 데이터를 Redis List(Queue)의 오른쪽으로 Push 합니다.
     */
    public void push(ErrorLogRequest dto) {
        redisTemplate.opsForList().rightPush(LOG_QUEUE_KEY, dto);
    }
}