package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.config.LogCollectorRedisProperties;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.service.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.notification.SlackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisToDB {

    private final RedisTemplate<String, ErrorLogRequest> redisTemplate;
    private final ErrorLogCrdService errorLogCrdService;
    private final SlackService slackService;
    private final LogCollectorRedisProperties props;

    // 이전 실행 끝난 뒤 delay 하고 다시 실행, 내부에서 blocking pop(timeout) 으로 빈 큐 대기 처리
    @Scheduled(fixedDelay = 1000)
    public void consumeBatch() {
        int processed = 0;

        try {
            for (int i = 0; i < props.getBatchSize(); i++) {
                // 빈 큐면 최대 popTimeoutSeconds 만큼 대기 후 null 반환 (busy polling 방지)
                ErrorLogRequest request = redisTemplate.opsForList()
                        .leftPop(props.getQueueKey(), Duration.ofSeconds(props.getPopTimeoutSeconds()));

                if (request == null) break;

                processed++;
                handleOne(request);
            }

            if (processed > 0) {
                log.debug("Redis batch consume 완료: {}건", processed);
            }

        } catch (RedisConnectionFailureException e) {
            // Redis 장애: 다음 스케줄에서 재시도
            log.warn("Redis 연결 실패로 consume 스킵. msg={}", e.getMessage());

        } catch (Exception e) {
            log.error("Redis consumeBatch 중 예외. msg={}", e.getMessage(), e);
        }
    }

    // 1건 처리. 실패시 DB로
    private void handleOne(ErrorLogRequest request) {
        try {
            ErrorLogResponse response = errorLogCrdService.saveLog(request);
            if (response == null) return;

            boolean shouldNotify =
                    response.isNew() ||
                            response.isNewHost() ||
                            response.getRepeatCount() == 10;

            if (shouldNotify) {
                String title = determineTitle(response);
                String summaryWithCount = String.format("%s\n(현재 누적 발생: %d회)",
                        response.getSummary(), response.getRepeatCount());

                slackService.sendErrorNotification(
                        title,
                        response.getServiceName(),
                        summaryWithCount,
                        response.getImpactedHostCount()
                );
            }

        } catch (Exception e) {
            log.error("로그 처리 실패 → DLQ 적재. msg={}", e.getMessage(), e);
            pushToDlq(request);
        }
    }

    // DLQ 적재 + TTL
    private void pushToDlq(ErrorLogRequest request) {
        try {
            redisTemplate.opsForList().rightPush(props.getDlqKey(), request);
            redisTemplate.expire(props.getDlqKey(), Duration.ofDays(props.getDlqTtlDays()));
        } catch (Exception e) {
            log.error("DLQ 적재 실패(로그 유실 가능). msg={}", e.getMessage(), e);
        }
    }

    private String determineTitle(ErrorLogResponse response) {
        if (response.isNew()) return "🚨 *[신규 에러 발생]*";
        if (response.isNewHost()) return "⚠️ *[에러 확산 감지]*";
        return "🔥 *[다건 발생 경고]*";
    }
}
