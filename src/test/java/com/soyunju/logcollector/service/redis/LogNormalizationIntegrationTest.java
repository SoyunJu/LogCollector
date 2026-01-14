package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import com.soyunju.logcollector.repository.ErrorLogHostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"dev", "local"}) // DB(local) 및 Mock(dev) 설정 로드
public class LogNormalizationIntegrationTest {

    @Autowired private LogToRedis logToRedis;
    @Autowired private RedisToDB redisToDB;
    @Autowired private ErrorLogRepository errorLogRepository;
    @Autowired private ErrorLogHostRepository errorLogHostRepository;

    @Test
    @DisplayName("정규화 및 호스트 집계 통합 테스트: 가변 메시지 중복 제거 검증")
    void normalizationAndHostCountTest() throws InterruptedException {
        // 공통 접두사 정의
        String baseMessage = "TEST임1111111111";

        System.out.println("\n===== [STEP 1] Host-A에서 최초 에러 발생 =====");
        String message1 = baseMessage + LocalDateTime.now(); // 현재 시간 포함
        ErrorLogRequest req1 = createRequest("Host-A", message1);

        logToRedis.push(req1);
        redisToDB.consume();

        // 첫 번째 발생 검증
        errorLogRepository.findAll().stream()
                .filter(l -> l.getServiceName().equals("Test-App"))
                .findFirst()
                .ifPresent(log -> {
                    System.out.println(">>> [Host-A] 생성된 Hash: " + log.getLogHash());
                    System.out.println(">>> [Host-A] Repeat Count: " + log.getRepeatCount());
                });

        // 시간 차이를 두기 위해 잠시 대기
        Thread.sleep(100);

        System.out.println("\n===== [STEP 2] Host-B에서 시간이 다른 동일 에러 발생 =====");
        String message2 = baseMessage + LocalDateTime.now(); // 다른 시간 포함
        ErrorLogRequest req2 = createRequest("Host-B", message2);

        logToRedis.push(req2);
        redisToDB.consume();

        System.out.println("\n===== [STEP 3] 최종 데이터 정합성 검증 =====");
        errorLogRepository.findAll().stream()
                .filter(l -> l.getServiceName().equals("Test-App"))
                .findFirst()
                .ifPresent(log -> {
                    long finalHostCount = errorLogHostRepository.countHostsByLogHash(log.getLogHash());

                    System.out.println(">>> [최종] 정규화된 Hash: " + log.getLogHash());
                    System.out.println(">>> [최종] 누적 Repeat Count: " + log.getRepeatCount());
                    System.out.println(">>> [최종] 영향 받는 서버 수: " + finalHostCount);

                    // 검증 포인트
                    // 1. 메시지가 달라도(시간 포함) 해시가 같아야 함 -> 데이터가 1건만 유지됨
                    assertThat(log.getRepeatCount()).isEqualTo(2);
                    // 2. 서로 다른 호스트명이므로 영향 받는 서버 수는 2여야 함
                    assertThat(finalHostCount).isEqualTo(2);

                    System.out.println(">>> [성공] 중복 제거 및 멀티 호스트 집계 완료");
                });
    }

    private ErrorLogRequest createRequest(String host, String msg) {
        ErrorLogRequest req = new ErrorLogRequest();
        req.setServiceName("Test-App");
        req.setHostName(host);
        req.setMessage(msg);
        req.setLogLevel("ERROR");
        return req;
    }
}