package com.soyunju.logcollector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "logcollector.redis")
public class LogCollectorRedisProperties {
    private String queueKey = "error-log-queue";
    private String dlqKey = "error-log-queue:dlq";

    private int batchSize = 20;
    private int popTimeoutSeconds = 2;

    private int queueTtlMinutes = 30;
    private int dlqTtlDays = 1;

    // consumer 폴링 주기(ms) - 기본 500ms
    private long consumerFixedDelayMs = 500;

    public Duration queueTtl() {
        return Duration.ofMinutes(queueTtlMinutes);
    }

    public Duration popTimeout() {
        return Duration.ofSeconds(popTimeoutSeconds);
    }

    public Duration dlqTtl() {
        return Duration.ofDays(dlqTtlDays);
    }
}
