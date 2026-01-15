package com.soyunju.logcollector.service.redis;

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

    private static final String LOG_QUEUE_KEY = "error-log-queue";

    // DLQ 키(처리 실패한 로그)
    private static final String DLQ_KEY = "error-log-queue:dlq";
    // DLQ는 분석을 위해 더 길게 보관
    private static final Duration DLQ_TTL = Duration.ofDays(1);

    // 1초당 20개 처리
    private static final int BATCH_SIZE = 20;

    private final RedisTemplate<String, ErrorLogRequest> redisTemplate;
    private final ErrorLogCrdService errorLogCrdService;
    private final SlackService slackService;

    /**
     * fixedDelay: 이전 소비 작업이 끝난 뒤 N ms 후 다음 실행(과부하 방지)
     * - 1초마다 최대 BATCH_SIZE 개 처리
     */
    @Scheduled(fixedDelay = 1000)
    public void consumeBatch() {
        try {
            int processed = 0;

            for (int i = 0; i < BATCH_SIZE; i++) {
                //leftPop
                ErrorLogRequest request =
                        redisTemplate.opsForList()
                                .leftPop(LOG_QUEUE_KEY, Duration.ofSeconds(2));
                if (request == null) break;

                processed++;
                handleOne(request);
            }

            if (processed > 0) {
                log.debug("Redis batch consume 완료: {}건", processed);
            }

        } catch (RedisConnectionFailureException e) {
            // Redis 자체 장애: 다음 스케줄에서 재시도
            log.warn("Redis 연결 실패로 consume 스킵. msg={}", e.getMessage());

        } catch (Exception e) {
            log.error("Redis consumeBatch 중 예외. msg={}", e.getMessage(), e);
        }
    }

    // 1건 처리
    private void handleOne(ErrorLogRequest request) {
        try {
            ErrorLogResponse response = errorLogCrdService.saveLog(request);
            if (response == null) return;

            boolean shouldNotify =
                    response.isNew() ||
                            response.isNewHost() || // 확산
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
            // DB 저장 실패/예상치 못한 오류 → DLQ로 이동
            log.error("로그 처리 실패 → DLQ 적재. msg={}", e.getMessage(), e);
            pushToDlq(request);
        }
    }

    private void pushToDlq(ErrorLogRequest request) {
        try {
            redisTemplate.opsForList().rightPush(DLQ_KEY, request);
            redisTemplate.expire(DLQ_KEY, DLQ_TTL);
        } catch (Exception e) {
            // Redis까지 실패하면 유실 가능 → 운영 알람 대상
            log.error("DLQ 적재 실패(로그 유실 가능). msg={}", e.getMessage(), e);
        }
    }

    private String determineTitle(ErrorLogResponse response) {
        if (response.isNew()) {
            return "🚨 *[신규 에러 발생]*";
        }
        if (response.isNewHost()) {
            return "⚠️ *[에러 확산 감지]*";
        }
        return "🔥 *[다건 발생 경고]*";
    }
}
