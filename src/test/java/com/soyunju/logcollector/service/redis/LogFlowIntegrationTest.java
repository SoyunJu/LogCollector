package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"dev", "local"})
public class LogFlowIntegrationTest {

    @Autowired private LogToRedis logToRedis;
    @Autowired private RedisToDB logConsumer;
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private ErrorLogRepository errorLogRepository;

    @Test
    @DisplayName("로그 수집 전체 흐름 확인 (Redis 입고 -> DB 이관)")
    void logFlowTest() {
        // [준비] 테스트 데이터 생성
        ErrorLogRequest request = new ErrorLogRequest();
        request.setServiceName("Test-System");
        // request.setErrorCode("500");
        request.setMessage("Redis to DB 통합 테스트02");
        request.setLogLevel("ERROR");
       // request.setOccurredAt(LocalDateTime.now());

        System.out.println("\n===== [1단계] Redis 입고 테스트 시작 =====");
        // [실행] Redis 큐에 데이터 삽입
        logToRedis.push(request);

        // [검증] Redis 확인
        Long size = redisTemplate.opsForList().size("error-log-queue");
        System.out.println(">>> Redis 큐 사이즈: " + size);
        assertThat(size).isGreaterThan(0);
        System.out.println(">>> 결과: Redis 입고 성공");

        System.out.println("\n===== [2단계] DB 이관 테스트 시작 =====");
        // [실행] Consumer 강제 실행 (스케줄링 대기 없이 즉시 실행)
        logConsumer.consume();
        System.out.println(">>> LogConsumer 실행 완료");

        // [검증] 로직 강화
        long totalCount = errorLogRepository.count();
        System.out.println(">>> DB 내 전체 로그 수: " + totalCount);

        // 1. 최소한 하나 이상의 데이터가 저장되었는지 확인
        assertThat(totalCount).isGreaterThan(0);

        // 2. 상세 조건으로 확인 (serviceName은 인코딩 영향이 적음)
        boolean exists = errorLogRepository.findAll().stream()
                .anyMatch(log -> log.getServiceName().equals("Test-System"));

        System.out.println(">>> [최종 확인] Test-System 서비스 로그 존재 여부: " + exists);

        // 만약 실패한다면 실제 DB에 남은 메시지가 무엇인지 출력하여 비교
        if (!exists) {
            errorLogRepository.findAll().forEach(l ->
                    System.out.println("기존 DB 데이터 -> Service: " + l.getServiceName() + ", Message: " + l.getMessage())
            );
        }
        assertThat(exists).isTrue();
    }
}