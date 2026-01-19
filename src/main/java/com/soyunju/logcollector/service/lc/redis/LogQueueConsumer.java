package com.soyunju.logcollector.service.lc.redis;

import com.soyunju.logcollector.config.LogCollectorRedisProperties;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LogQueueConsumer {

    private final RedisTemplate<String, ErrorLogRequest> redisTemplate;
    private final LogCollectorRedisProperties redisProperties;
    private final ErrorLogCrdService errorLogCrdService;


    // queueKey에서 leftPop으로 batchSize만큼 꺼내 처리
    // 처리 실패 시 DLQ로 이동 + DLQ TTL 갱신
    @Scheduled(fixedDelayString = "${logcollector.redis.consumer-fixed-delay-ms:500}")
    public void consume() {
        int processed = 0;

        for (int i = 0; i < redisProperties.getBatchSize(); i++) {
            ErrorLogRequest dto = redisTemplate.opsForList()
                    .leftPop(redisProperties.getQueueKey(), redisProperties.popTimeout());

            if (dto == null) break;

            try {
                errorLogCrdService.saveLog(dto);
                processed++;

            } catch (Exception e) {
                log.error("queue consume 실패 → DLQ로 이동. msg={}", e.getMessage(), e);

                try {
                    redisTemplate.opsForList().rightPush(redisProperties.getDlqKey(), dto);
                    redisTemplate.expire(redisProperties.getDlqKey(), redisProperties.dlqTtl());
                } catch (Exception dlqEx) {
                    log.error("DLQ 적재 실패(로그 유실 가능). msg={}", dlqEx.getMessage(), dlqEx);
                }
            }
        }
        if (processed > 0) {
            log.info("LogQueueConsumer processed={}", processed);
        }
    }
}
