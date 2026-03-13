package com.soyunju.logcollector.service.lc.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.monitornig.LcMetrics;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.lc.notification.SlackService;
import com.soyunju.logcollector.service.lc.processor.LogProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisToDB {

    private final ErrorLogCrdService errorLogCrdService;
    private final RedisTemplate<String, ErrorLogRequest> errorLogRequestRedisTemplate;
    private final LogProcessor logProcessor;
    private final SlackService slackService;
    private final LcMetrics lcMetrics;
    private final ObjectMapper objectMapper;

    @Value("${logcollector.redis.queue-key:errorlog:queue}")
    private String queueKey;

    @Value("${logcollector.redis.dlq-key:errorlog:dlq}")
    private String dlqKey;

    @Value("${logcollector.redis.dlq-ttl-seconds:86400}")
    private long dlqTtlSeconds;

    @Value("${logcollector.redis.batch-size:50}")
    private int batchSize;

    @Value("${logcollector.redis.pop-timeout-seconds:2}")
    private long popTimeoutSeconds;

    @Scheduled(fixedDelayString = "${logcollector.redis.consumer-fixed-delay-ms:200}")
    public void pollAndProcess() {
        List<ErrorLogRequest> batch = popBatch(batchSize);
        if (batch.isEmpty()) return;

        for (ErrorLogRequest dto : batch) {
            handleOne(dto);
        }
    }

    private List<ErrorLogRequest> popBatch(int batchSize) {
        List<ErrorLogRequest> batch = new ArrayList<>(batchSize);

        for (int i = 0; i < batchSize; i++) {
            try {
                // 1. Redis에서 객체 가져오기 (List Operation)
                Object rawEntry = errorLogRequestRedisTemplate.opsForList().leftPop(queueKey);

                if (rawEntry == null) break; // 큐가 비었으면 중단

                ErrorLogRequest request = null;

                // 2. 타입 변환 (LinkedHashMap -> DTO)
                if (rawEntry instanceof java.util.LinkedHashMap) {
                    request = objectMapper.convertValue(rawEntry, ErrorLogRequest.class);
                } else if (rawEntry instanceof ErrorLogRequest) {
                    request = (ErrorLogRequest) rawEntry;
                } else {
                    log.warn("Unknown type in Redis: {}", rawEntry.getClass());
                    continue;
                }

                if (request != null) {
                    batch.add(request);
                }
            } catch (Exception e) {
                log.error("Redis pop failed", e);
            }
        }
        return batch;
    }

    // 1건 처리. 실패시 DLQ로
    private void handleOne(ErrorLogRequest request) {
        var lagSample = lcMetrics.startPersistLagTimer();

        try {
            String logHash = logProcessor.generateIncidentHash(
                    request.getServiceName(), request.getMessage(), request.getStackTrace()
            );

            ErrorLogResponse response = errorLogCrdService.saveLog(request);
            if (response == null) {
                lcMetrics.recordPersistLagSeconds(lagSample, "skipped");
                return;
            }

            lcMetrics.incConsumeProcessed();
            lcMetrics.recordPersistLagSeconds(lagSample, "success");

            boolean shouldNotify =
                    response.isNew() ||
                            response.isNewHost() ||
                            response.getRepeatCount() == 10;

            if (shouldNotify) {
                lcMetrics.incSlackNotify("sent");

                String title = determineTitle(response);
                String summaryWithCount = String.format(
                        "%s\n(현재 누적 발생: %d회)",
                        response.getSummary(),
                        response.getRepeatCount()
                );

                slackService.sendErrorNotification(
                        title,
                        response.getServiceName(),
                        summaryWithCount,
                        response.getImpactedHostCount()
                );
            }

        } catch (Exception e) {
            log.error("로그 처리 실패 → DLQ 적재. serviceName={} msg={}",
                    safe(request.getServiceName()), e.getMessage(), e);
            lcMetrics.recordPersistLagSeconds(lagSample, "failure");
            pushToDlq(request);
        }
    }

    // DLQ 적재 + TTL 갱신
    private void pushToDlq(ErrorLogRequest request) {
        try {
            errorLogRequestRedisTemplate.opsForList().rightPush(dlqKey, request);
            errorLogRequestRedisTemplate.expire(dlqKey, Duration.ofSeconds(dlqTtlSeconds));
        } catch (Exception e) {
            log.error("DLQ 적재 실패(로그 유실 가능). msg={}", e.getMessage(), e);
        }
    }

    private String determineTitle(ErrorLogResponse response) {
        if (response.isNew()) return "🚨 *[신규 에러 발생]*";
        if (response.isNewHost()) return "⚠️ *[에러 확산 감지]*";
        return "🔥 *[다건 발생 경고]*";
    }

    private String safe(String s) {
        return (s == null) ? "null" : s;
    }
}
