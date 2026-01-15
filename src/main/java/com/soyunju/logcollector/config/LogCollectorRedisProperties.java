package com.soyunju.logcollector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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
}
