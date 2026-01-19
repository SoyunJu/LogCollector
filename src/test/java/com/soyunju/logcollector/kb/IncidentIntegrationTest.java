package com.soyunju.logcollector.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.testcontainers.enabled=false",
                "spring.test.database.replace=none"
        }
)
@TestPropertySource(properties = {
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.use_sql_comments=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
@Transactional
@ActiveProfiles({"dev", "local"})
public class IncidentIntegrationTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    private ErrorLogCrdService errorLogCrdService;

    @Autowired
    private IncidentRepository incidentRepository;


    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    private void clearDatabase() {
        System.out.println("[CLEANUP] 시작: 모든 테스트 데이터 초기화 중...");

        // 외래 키 제약 조건 잠시 해제 (필요한 경우)
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        // 데이터 삭제 (TRUNCATE는 ID 자동 증가값도 초기화합니다)
        jdbcTemplate.execute("TRUNCATE TABLE incident");
        jdbcTemplate.execute("TRUNCATE TABLE error_logs");
        jdbcTemplate.execute("TRUNCATE TABLE error_log_hosts");

        // 외래 키 제약 조건 다시 설정
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        System.out.println("[CLEANUP] 완료: DB가 깨끗한 상태입니다.");
    }


    private String resolveRealLogHashByMarker(String marker) {
        return jdbcTemplate.queryForObject(
                "SELECT log_hash FROM error_logs WHERE message LIKE ? ORDER BY id DESC LIMIT 1",
                String.class,
                "%"+ marker + "%"
        );
    }

    private void dumpDbState(String logHash) {
        Integer incidentCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incident WHERE log_hash = ?", Integer.class, logHash);
        Integer errorLogCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM error_logs WHERE log_hash = ?", Integer.class, logHash);
        Integer hostCnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM error_log_hosts WHERE log_hash = ?", Integer.class, logHash);

        System.out.println("  >> [DB COUNT CHECK]");
        System.out.println("     - Incident Row Count : " + incidentCnt);
        System.out.println("     - ErrorLog Row Count : " + errorLogCnt);
        System.out.println("     - Host Count         : " + hostCnt);
    }

    private void printHeader(String title) {
        System.out.println("\n======================================================================");
        System.out.println(" TEST: " + title);
        System.out.println("======================================================================");
    }

    @Test
    @DisplayName("1. 에러 로그 1회 요청 시 Incident 1건이 생성되어야 한다.")
    void test_SinglePost_CreatesIncident() {
        printHeader("1. Single Post Creation");

        // given
        String marker = "UNIQUE_HASH_1";
        ErrorLogRequest request = createRequest(marker);

        // when
        errorLogCrdService.saveLog(request);

        String realHash = resolveRealLogHashByMarker(marker);
        System.out.println("  >> Resolved LogHash: " + realHash);
        dumpDbState(realHash);

        // then
        Incident incident = incidentRepository.findByLogHash(realHash).orElseThrow();
        System.out.println("  >> [ASSERTION] RepeatCount: " + incident.getRepeatCount() + " (Expected: 1)");
        System.out.println("  >> [ASSERTION] Status     : " + incident.getStatus() + " (Expected: OPEN)");

        assertThat(incident.getRepeatCount()).isEqualTo(1);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    @DisplayName("2. 동일 해시 10회 요청 시 repeatCount는 10이어야 하고 시각이 갱신되어야 한다.")
    void test_MultiplePosts_IncrementsCount() throws InterruptedException {
        printHeader("2. Multiple Posts (10 times) Increment");

        // given
        String logHashMarker = "REPEAT_HASH_10";
        ErrorLogRequest request = createRequest(logHashMarker);

        // when
        System.out.print("  >> Posting 10 times: ");
        for (int i = 0; i < 10; i++) {
            errorLogCrdService.saveLog(request);
            Thread.sleep(10);
            System.out.print(".");
        }
        System.out.println(" Done.");

        String realHash = resolveRealLogHashByMarker(logHashMarker);
        System.out.println("  >> Resolved LogHash: " + realHash);
        dumpDbState(realHash);

        // then
        Incident incident = incidentRepository.findByLogHash(realHash).orElseThrow();
        System.out.println("  >> [ASSERTION] RepeatCount: " + incident.getRepeatCount() + " (Expected: 10)");
        System.out.println("  >> [ASSERTION] Timestamps : First=" + incident.getFirstOccurredAt() + ", Last=" + incident.getLastOccurredAt());

        assertThat(incident.getRepeatCount()).isEqualTo(10);
        assertThat(incident.getLastOccurredAt()).isAfter(incident.getFirstOccurredAt());
    }

    @Test
    @DisplayName("3. [원자성 검증] 20개 스레드 동시 요청 시에도 데이터 정합성이 유지되어야 한다.")
    void test_ConcurrentPosts_Atomicity() throws InterruptedException {
        printHeader("3. Concurrent Atomic Upsert (20 Threads)");

        // given
        String logHashMarker = "CONCURRENT_HASH";
        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ErrorLogRequest request = createRequest(logHashMarker);

        // when
        System.out.println("  >> Executing " + threadCount + " concurrent requests...");
        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    errorLogCrdService.saveLog(request);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        String realHash = resolveRealLogHashByMarker(logHashMarker);
        System.out.println("  >> Resolved LogHash: " + realHash);
        dumpDbState(realHash);

        // then
        Incident incident = incidentRepository.findByLogHash(realHash).orElseThrow();
        System.out.println("  >> [ASSERTION] Final RepeatCount: " + incident.getRepeatCount() + " (Expected: " + threadCount + ")");

        assertThat(incident.getRepeatCount()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("4. RESOLVED 처리 시 Incident 상태와 해결 시각이 동기화되어야 한다.")
    void test_StatusSync_Resolved() {
        printHeader("4. Status Synchronization (RESOLVED)");

        // given
        String logHashMarker = "SYNC_HASH";
        errorLogCrdService.saveLog(createRequest(logHashMarker));
        errorLogCrdService.saveLog(createRequest(logHashMarker));

        String realHash = resolveRealLogHashByMarker(logHashMarker);
        ErrorLog log = errorLogCrdService.findByLogHash(realHash).orElseThrow();
        System.out.println("  >> Resolved LogHash: " + realHash + " (Log ID: " + log.getId() + ")");

        // when
        System.out.println("  >> Updating Status to RESOLVED...");
        errorLogCrdService.updateStatus(log.getId(), ErrorStatus.RESOLVED);

        // then
        Incident incident = incidentRepository.findByLogHash(realHash).orElseThrow();
        System.out.println("  >> [ASSERTION] Incident Status: " + incident.getStatus() + " (Expected: RESOLVED)");
        System.out.println("  >> [ASSERTION] Resolved At   : " + incident.getResolvedAt());

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();
    }

    private ErrorLogRequest createRequest(String hash) {
        return ErrorLogRequest.builder()
                .serviceName("test-service")
                .logLevel("ERROR")
                .message("Test Message: " + hash)
                .stackTrace("Test StackTrace: " + hash)
                .occurredTime(LocalDateTime.now())
                .build();

    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

}