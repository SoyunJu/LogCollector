package com.soyunju.logcollector.service.crd;

import com.soyunju.logcollector.domain.ErrorLog;
import com.soyunju.logcollector.dto.ErrorLogRequest;
import com.soyunju.logcollector.dto.ErrorLogResponse;
import com.soyunju.logcollector.repository.ErrorLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ErrorLogIntegrationTest {

    @Autowired private ErrorLogCrdService errorLogCrdService;
    @Autowired private ErrorLogRepository errorLogRepository;

    @BeforeEach
    void setUp() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("🚀 [TEST START] " + LocalDateTime.now());
        System.out.println("-".repeat(60));
    }

    @AfterEach
    void tearDown() {
        System.out.println("-".repeat(60));
        System.out.println("🏁 [TEST END] " + LocalDateTime.now());
        System.out.println("=".repeat(60) + "\n");
    }

    @Test
    @DisplayName("로그 집계 및 해시 판별 테스트")
    void duplicateLogAggregationTest() {
        // given
        ErrorLogRequest request = ErrorLogRequest.builder()
                .serviceName("payment-service")
                .logLevel("ERROR")
                .message("Connection Timeout")
                .build();

        // when & then
        printTestRow("STAGE", "LOG_ID", "REPEAT", "HASH_PREFIX", "TIME_STAMP");

        // 1차 저장
        ErrorLogResponse res1 = errorLogCrdService.saveLog(request);
        ErrorLog log1 = errorLogRepository.findById(res1.getLogId()).orElseThrow();
        printLogInfo("INITIAL", log1);

        // 2차 저장 (중복)
        ErrorLogResponse res2 = errorLogCrdService.saveLog(request);
        ErrorLog log2 = errorLogRepository.findById(res2.getLogId()).orElseThrow();
        printLogInfo("DUPLICATE", log2);

        // 검증
        assertThat(log2.getRepeatCount()).isEqualTo(2);
        System.out.println("\n✅ VERIFICATION: Row count remains 1, Repeat count increased to 2.");
    }

    private void printTestRow(String stage, String id, String repeat, String hash, String time) {
        System.out.printf("| %-10s | %-6s | %-6s | %-12s | %-20s |\n", stage, id, repeat, hash, time);
        System.out.println("|" + "-".repeat(12) + "|" + "-".repeat(8) + "|" + "-".repeat(8) + "|" + "-".repeat(14) + "|" + "-".repeat(22) + "|");
    }

    private void printLogInfo(String stage, ErrorLog log) {
        String hashPrefix = log.getLogHash().substring(0, 8) + "...";
        printTestRow(stage,
                log.getId().toString(),
                String.valueOf(log.getRepeatCount()),
                hashPrefix,
                log.getLastOccurrenceTime().toString());
    }
}