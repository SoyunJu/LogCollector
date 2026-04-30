package com.soyunju.logcollector.monitornig;

import com.soyunju.logcollector.config.LogCollectorRedisProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
@RequiredArgsConstructor
public class MonitoringConfig {

    private final LcMetrics lcMetrics;
    private final StringRedisTemplate stringRedisTemplate; // ✅ 모호성 제거
    private final LogCollectorRedisProperties redisProperties;

    @PostConstruct
    public void bindGauges() {
        lcMetrics.bindRedisQueueGauges(stringRedisTemplate, redisProperties);
    }
}
