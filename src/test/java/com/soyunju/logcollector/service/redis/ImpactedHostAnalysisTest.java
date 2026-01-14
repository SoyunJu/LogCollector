package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles({"dev", "local"})
public class ImpactedHostAnalysisTest {

    @Autowired
    private LogToRedis logToRedis;
    @Autowired private RedisToDB redisToDB;
    @Autowired private ErrorLogRepository errorLogRepository;
    @Autowired private com.soyunju.logcollector.repository.ErrorLogHostRepository errorLogHostRepository;

    // [추가] 테스트 간 데이터 격리를 위해 매번 실행 전 데이터를 비웁니다.
    @org.junit.jupiter.api.BeforeEach
    void cleanUp() {
        errorLogHostRepository.deleteAll(); // 순서 중요: 자식 테이블 먼저
        errorLogRepository.deleteAll();
    }

    @Test
    @DisplayName("영향 범위 지표 검증: 단일 서버 반복 vs 다수 서버 확산")
    void testImpactedHostCountLogic() {
        String errorMessage = "테스트 오류 호스트네임 덮어써야할지?";
        String serviceName = "크리티컬서비스";

        System.out.println("\n[CASE 1] 단일 서버(Host-B)에서 동일 에러 2회 반복 =====");
        processLog("Host-B", errorMessage, serviceName);
        processLog("Host-B", errorMessage + " at " + System.currentTimeMillis(), serviceName);

        verifyData(errorMessage, 2, 1, "로컬 이슈 (Repeat 2, Host 1)");

        System.out.println("\n[CASE 2] 다른 서버(Host-C)에서 동일 에러 발생 =====");
        processLog("Host-C", errorMessage, serviceName);

        verifyData(errorMessage, 3, 2, "시스템 전파 중 (Repeat 3, Host 2)");
    }

    private void processLog(String host, String msg, String service) {
        ErrorLogRequest req = new ErrorLogRequest();
        req.setServiceName(service);
        req.setHostName(host);
        req.setMessage(msg);
        req.setLogLevel("ERROR");
        logToRedis.push(req);
        redisToDB.consume();
    }

    private void verifyData(String msg, int expectedRepeat, int expectedHosts, String scenarioName) {
        // [수정] msg 변수를 사용하여 현재 테스트 중인 데이터를 정확히 찾습니다.
        errorLogRepository.findAll().stream()
                .filter(l -> l.getMessage().contains("테스트 오류"))
                .findFirst()
                .ifPresent(log -> {
                    // 실제 DB에서 이 해시로 기록된 호스트 수를 카운트합니다.
                    long currentImpactedCount = errorLogHostRepository.countHostsByLogHash(log.getLogHash());

                    System.out.println("[" + scenarioName + "]");
                    System.out.println(">>> Total Repeat Count: " + log.getRepeatCount());
                    System.out.println(">>> Impacted Host Count: " + currentImpactedCount);

                    assertThat(log.getRepeatCount()).as(scenarioName + " - RepeatCount 실패").isEqualTo(expectedRepeat);
                    assertThat(currentImpactedCount).as(scenarioName + " - HostCount 실패").isEqualTo((long)expectedHosts);
                });
    }
}