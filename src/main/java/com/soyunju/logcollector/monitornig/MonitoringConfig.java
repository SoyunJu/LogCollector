package com.soyunju.logcollector.monitornig;

import com.soyunju.logcollector.config.LogCollectorRedisProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
@RequiredArgsConstructor
public class MonitoringConfig {

    private final LcMetrics lcMetrics;
    private final RedisTemplate<String, ?> redisTemplate;
    private final LogCollectorRedisProperties redisProperties;

    @PostConstruct
    public void bindGauges() {
        lcMetrics.bindRedisQueueGauges(redisTemplate, redisProperties);
    }
}
