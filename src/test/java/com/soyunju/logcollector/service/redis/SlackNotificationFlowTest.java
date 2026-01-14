package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"dev", "local"}) // DB 정보(local)와 MockAI(dev) 환경 사용
public class SlackNotificationFlowTest {

    @Autowired private LogToRedis logToRedis;
    @Autowired private RedisToDB redisToDB; // Consumer
    @Autowired private RedisTemplate<String, Object> redisTemplate;
    @Autowired private ErrorLogRepository errorLogRepository;

    @Test
    @DisplayName("슬랙 알림 연동 테스트: 신규 발생 vs 반복 발생")
    void slackNotificationTest() {
        // 동일한 메시지를 가진 에러 데이터 준비 (해시가 동일하게 생성됨)
        String uniqueMessage = "TEST임1111111111" + LocalDateTime.now();
        ErrorLogRequest request = new ErrorLogRequest();
        request.setServiceName("1111 SERVICE 11");
        request.setHostName("auth-server-01");
        // request.setErrorCode("SLACK-500");
        request.setMessage(uniqueMessage);
        request.setLogLevel("ERROR");
       // request.setOccurredAt(LocalDateTime.now());

        System.out.println("\n===== [시나리오 1] 신규 에러(New Incident) 발생 =====");
        System.out.println("1. Redis에 로그 Push...");
        logToRedis.push(request);

        System.out.println("2. Consumer(RedisToDB) 실행...");
        redisToDB.consume();

        System.out.println(">>> 결과 확인: '신규 인시던트 슬랙 알림 전송 완료' 로그가 찍혔어야 함 (isNew: true)");

        // ---------------------------------------------------------

        System.out.println("\n===== [시나리오 2] 동일한 에러(Repeated Error) 다시 발생 =====");
        System.out.println("1. 동일한 해시를 가진 로그를 다시 Redis에 Push...");
        logToRedis.push(request); // 위와 동일한 객체 푸시

        System.out.println("2. Consumer(RedisToDB) 다시 실행...");
        redisToDB.consume();

        System.out.println(">>> 결과 확인: '비동기 로그 처리 완료'만 찍히고 슬랙 알림 로그는 없어야 함 (isNew: false)");

        // ---------------------------------------------------------

        System.out.println("\n===== [최종 검증] DB 상태 확인 =====");
        errorLogRepository.findAll().stream()
                .filter(log -> log.getMessage().equals(uniqueMessage))
                .findFirst()
                .ifPresent(log -> {
                    System.out.println(">>> [DB] Incident Hash: " + log.getLogHash());
                    System.out.println(">>> [DB] Repeat Count: " + log.getRepeatCount());
                    System.out.println(">>> [DB] Impacted Hosts: " + log.getHostName());

                    // 반복 횟수가 2여야 함
                    assertThat(log.getRepeatCount()).isEqualTo(2);
                });

        System.out.println("============================================\n");
    }
}