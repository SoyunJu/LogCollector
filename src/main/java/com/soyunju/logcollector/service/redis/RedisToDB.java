package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.service.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.notification.SlackService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisToDB {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ErrorLogCrdService errorLogCrdService;
    private final SlackService slackService;

    private static final String LOG_QUEUE_KEY = "error-log-queue";

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        while (true) {
            // Redis List의 왼쪽(Head)에서 데이터를 하나씩 꺼냄
            ErrorLogRequest request = (ErrorLogRequest) redisTemplate.opsForList().leftPop(LOG_QUEUE_KEY);

            if (request == null) {
                break; // 큐가 비어있으면 루프 종료
            }

            try {
                // 1. DB 저장 및 중복/확산 판별 로직 수행
                ErrorLogResponse response = errorLogCrdService.saveLog(request);

                if (response == null) {
                    continue;
                }

                // 2. 알림 조건 판별 (운영 효율을 위한 트리거 설계)
                // - 최초 발생했거나(isNew), 기존 에러가 새로운 서버로 번졌거나(isNewHost),
                // - 한 곳에서 10번 반복되어 임계치에 도달했을 때 알림 발송
                boolean shouldNotify =
                        response.isNew() ||
                                response.isNewHost() ||
                                response.getRepeatCount() == 10;

                if (shouldNotify) {
                    // 상황별 맞춤 타이틀 생성
                    String title = determineTitle(response);

                    // 요약 메시지에 현재 누적 발생 횟수를 포함하여 시각화 강화
                    String summaryWithCount = String.format("%s\n(현재 누적 발생: %d회)",
                            response.getSummary(), response.getRepeatCount());

                    // 슬랙 전송 호출
                    slackService.sendErrorNotification(
                            title,
                            response.getServiceName(),
                            summaryWithCount,
                            response.getImpactedHostCount()
                    );

                    log.info("슬랙 알림 전송 완료 [{}]: {}", title, response.getLogHash());
                }

                log.debug("비동기 로그 처리 완료: {}", response.getServiceName());

            } catch (Exception e) {
                // 처리 실패 시 에러 로그를 남기고 다음 데이터로 진행 (시스템 안정성 확보)
                log.error("비동기 로그 저장 중 오류 발생: {}", e.getMessage());
            }
        }
    }

    private String determineTitle(ErrorLogResponse response) {
        if (response.isNew()) {
            return "🚨 *[신규 에러 발생]*";
        }
        if (response.isNewHost()) {
            return "⚠️ *[에러 확산 감지]*";
        }
        return "🔥 *[다건 발생 경고]*"; // repeatCount == 10 인 경우
    }
}