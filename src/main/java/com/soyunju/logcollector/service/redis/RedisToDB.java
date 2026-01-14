package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.service.crd.ErrorLogCrdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.RedisTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisToDB {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ErrorLogCrdService errorLogCrdService;
    private static final String LOG_QUEUE_KEY = "error-log-queue";

    /**
     * 1초마다 Redis 큐를 확인하여 대기 중인 로그를 처리합니다.
     * 데이터가 있으면 모두 처리할 때까지 반복합니다.
     */
    @Scheduled(fixedDelay = 1000)
    public void consume() {
        while (true) {
            // Redis List의 왼쪽(Head)에서 데이터를 하나씩 꺼냄
            ErrorLogRequest request = (ErrorLogRequest) redisTemplate.opsForList().leftPop(LOG_QUEUE_KEY);

            if (request == null) {
                break; // 큐가 비어있으면 루프 종료
            }

            try {
                // 기존 DB 저장 로직 수행 (해시 생성, Host 업데이트, 로그 저장/업데이트 포함)
                errorLogCrdService.saveLog(request);
                log.debug("비동기 로그 처리 완료: {}", request.getServiceName());
            } catch (Exception e) {
                // 처리 실패 시 에러 로그를 남기고 다음 데이터로 진행
                log.error("비동기 로그 저장 중 오류 발생: {}", e.getMessage());
                // 필요 시 실패한 데이터를 별도의 에러 큐(Dead Letter Queue)로 이동시키는 로직 추가 가능
            }
        }
    }
}