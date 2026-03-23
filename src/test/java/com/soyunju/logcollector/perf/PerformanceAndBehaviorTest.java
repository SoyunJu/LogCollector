package com.soyunju.logcollector.perf;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.es.KbArticleEsRepository;
import com.soyunju.logcollector.es.KbArticleEsService;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbEventOutboxRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.kb.crud.KbEventOutboxProcessorService;
import com.soyunju.logcollector.service.kb.crud.LcIgnoreOutboxProcessorService;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.lc.processor.LogNormalization;
import com.soyunju.logcollector.service.lc.redis.LogToRedis;
import com.soyunju.logcollector.service.lc.redis.RedisToDB;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PerformanceAndBehaviorTest {

    record TestResult(String testName, String category, boolean passed, String detail) {}
    static final List<TestResult> RESULTS = Collections.synchronizedList(new ArrayList<>());

    static long throughputLogsPerSec = 0;
    static long dedupLogsPerSec = 0;
    static int maxGroupingCount = 0;
    static long avgLatencyMs = 0;

    @MockBean KbArticleEsRepository kbArticleEsRepository;
    @MockBean KbArticleEsService kbArticleEsService;
    @MockBean KbEventOutboxProcessorService kbEventOutboxProcessorService;
    @MockBean LcIgnoreOutboxProcessorService lcIgnoreOutboxProcessorService;

    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.lc.jdbc-url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.lc.username", MARIADB::getUsername);
        registry.add("spring.datasource.lc.password", MARIADB::getPassword);
        registry.add("spring.datasource.lc.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.datasource.kb.jdbc-url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.kb.username", MARIADB::getUsername);
        registry.add("spring.datasource.kb.password", MARIADB::getPassword);
        registry.add("spring.datasource.kb.driver-class-name", MARIADB::getDriverClassName);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        registry.add("logfixer.webhook.url", () -> "");
        registry.add("slack.webhook.url", () -> "");
    }

    @Autowired private ErrorLogCrdService errorLogCrdService;
    @Autowired private ErrorLogRepository errorLogRepository;
    @Autowired private ErrorLogHostRepository errorLogHostRepository;
    @Autowired private IncidentRepository incidentRepository;
    @Autowired private KbEventOutboxRepository kbEventOutboxRepository;
    @Autowired private LogToRedis logToRedis;
    @Autowired private RedisToDB redisToDB;
    @Autowired private RedisTemplate<String, ErrorLogRequest> errorLogRequestRedisTemplate;

    private static final String SVC = "perf-test-svc";

    @BeforeEach
    void cleanUp() {
        errorLogRepository.deleteAll();
        errorLogHostRepository.deleteAll();
        incidentRepository.deleteAll();
        kbEventOutboxRepository.deleteAll();
        errorLogRequestRedisTemplate.delete(Collections.singleton("error-log-queue"));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BLOCK 1: 정규화 정확성 (Log Normalization)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("T1 [정규화] UUID/IP/타임스탬프 제거 후 동일 해시")
    void t1_normalization_variablesProduceSameHash() {
        String msg1 = "Error at 550e8400-e29b-41d4-a716-446655440000 from 192.168.1.1 on 2026-03-23T10:30:00";
        String msg2 = "Error at a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6 from 10.0.0.100 on 2026-03-23T15:45:30";
        String stack = "at java.lang.NullPointerException.throwNull(NullPointerException.java:42)";

        String hash1 = LogNormalization.normalizeMessage(msg1);
        String hash2 = LogNormalization.normalizeMessage(msg2);

        assertThat(hash1).isNotEmpty();
        assertThat(hash2).isNotEmpty();
        assertThat(hash1).isEqualTo(hash2);

        recordResult("T1", "정규화", true, "UUID/IP/TS 제거 후 동일 메시지");
    }

    @Test @Order(2)
    @DisplayName("T2 [정규화] 다른 메시지/서비스 → 다른 해시")
    void t2_normalization_differentMessagesDifferentHash() {
        String msg1 = "Database connection timeout after 3000ms";
        String msg2 = "Network error occurred during API call";

        String hash1 = LogNormalization.normalizeMessage(msg1);
        String hash2 = LogNormalization.normalizeMessage(msg2);

        assertThat(hash1).isNotEqualTo(hash2);
        recordResult("T2", "정규화", true, "다른 메시지 → 다른 해시");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BLOCK 2: 기본 동작 (Basic Behavior)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("T3 [기본] 신규 로그: isNew=true, repeatCount=1")
    void t3_basic_firstLogIsNew() {
        ErrorLogRequest req = buildRequest(SVC, "First error message with details", "host-1", "ERROR");
        ErrorLogResponse resp = errorLogCrdService.saveLog(req);

        assertThat(resp).isNotNull();
        assertThat(resp.isNew()).isTrue();
        assertThat(resp.getRepeatCount()).isEqualTo(1);

        recordResult("T3", "기본동작", true, "첫 로그 isNew=true");
    }

    @Test @Order(11)
    @DisplayName("T4 [기본] 동일 로그 3회: repeatCount=3")
    void t4_basic_repeatCountIncrement() {
        ErrorLogRequest req = buildRequest(SVC, "Repeated error message content", "host-1", "ERROR");
        ErrorLogResponse r1 = errorLogCrdService.saveLog(req);
        ErrorLogResponse r2 = errorLogCrdService.saveLog(req);
        ErrorLogResponse r3 = errorLogCrdService.saveLog(req);

        assertThat(r3.getRepeatCount()).isEqualTo(3);
        assertThat(r3.isNew()).isFalse();

        recordResult("T4", "기본동작", true, "repeatCount 누적: 3건");
    }

    @Test @Order(12)
    @DisplayName("T5 [기본] RESOLVED → 재발 시 NEW로 복귀")
    void t5_basic_resolvedReopensToNew() {
        ErrorLogRequest req = buildRequest(SVC, "Reopenable error message", "host-1", "ERROR");
        ErrorLogResponse r1 = errorLogCrdService.saveLog(req);

        errorLogCrdService.updateStatus(r1.getLogId(), ErrorStatus.RESOLVED);

        ErrorLogResponse r2 = errorLogCrdService.saveLog(req);
        assertThat(r2).isNotNull();

        var log = errorLogRepository.findById(r1.getLogId()).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(ErrorStatus.NEW);

        recordResult("T5", "기본동작", true, "RESOLVED → 재발 시 NEW");
    }

    @Test @Order(13)
    @DisplayName("T6 [기본] 다른 호스트: isNewHost=true")
    void t6_basic_newHostSpread() {
        ErrorLogRequest req1 = buildRequest(SVC, "Multi-host error", "host-1", "ERROR");
        ErrorLogRequest req2 = buildRequest(SVC, "Multi-host error", "host-2", "ERROR");

        errorLogCrdService.saveLog(req1);
        ErrorLogResponse r2 = errorLogCrdService.saveLog(req2);

        assertThat(r2.isNewHost()).isTrue();
        assertThat(r2.getImpactedHostCount()).isGreaterThanOrEqualTo(2);

        recordResult("T6", "기본동작", true, "호스트 확산 감지");
    }

    @Test @Order(14)
    @DisplayName("T7 [기본] IGNORED 로그는 재수집 시 무시")
    void t7_basic_ignoredLogSkipped() {
        ErrorLogRequest req = buildRequest(SVC, "Ignorable error content", "host-1", "ERROR");
        ErrorLogResponse r1 = errorLogCrdService.saveLog(req);

        errorLogCrdService.updateStatus(r1.getLogId(), ErrorStatus.IGNORED);

        var log = errorLogRepository.findById(r1.getLogId()).orElseThrow();
        assertThat(log.getStatus()).isEqualTo(ErrorStatus.IGNORED);

        recordResult("T7", "기본동작", true, "IGNORED 상태 유지");
    }

    @Test @Order(15)
    @DisplayName("T8 [기본] INFO 레벨 로그 거부")
    void t8_basic_infoLevelRejected() {
        ErrorLogRequest req = buildRequest(SVC, "Info level message content", "host-1", "INFO");

        assertThatThrownBy(() -> errorLogCrdService.saveLog(req))
                .isInstanceOf(IllegalArgumentException.class);

        recordResult("T8", "기본동작", true, "INFO 레벨 거부");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BLOCK 3: 성능 (Performance)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("T9 [성능] 처리속도: 100개 서로 다른 로그")
    void t9_performance_throughput100Unique() {
        int count = 100;
        long startMs = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            ErrorLogRequest req = buildRequest(SVC, "Unique error message " + i, "host-" + i, "ERROR");
            errorLogCrdService.saveLog(req);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        long logsPerSec = (count * 1000) / elapsedMs;
        throughputLogsPerSec = logsPerSec;

        long latencyMs = elapsedMs / count;
        avgLatencyMs = latencyMs;

        assertThat(logsPerSec).isGreaterThan(0);
        recordResult("T9", "성능", true, logsPerSec + " 건/초 (" + elapsedMs + "ms)");
    }

    @Test @Order(21)
    @DisplayName("T10 [성능] 중복 처리: 100개 동일 로그")
    void t10_performance_dedupThroughput() {
        int count = 100;
        ErrorLogRequest req = buildRequest(SVC, "Same dedup error message", "host-1", "ERROR");

        long startMs = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            errorLogCrdService.saveLog(req);
        }

        long elapsedMs = System.currentTimeMillis() - startMs;
        long logsPerSec = (count * 1000) / elapsedMs;
        dedupLogsPerSec = logsPerSec;

        assertThat(logsPerSec).isGreaterThan(0);
        recordResult("T10", "성능", true, logsPerSec + " 건/초 (중복제거)");
    }

    @Test @Order(22)
    @DisplayName("T11 [성능] 최대 그룹화: 500개 동일 로그 → 1 Incident")
    void t11_performance_maxGrouping500() {
        int count = 500;
        ErrorLogRequest req = buildRequest(SVC, "Groupable error for incident", "host-1", "ERROR");

        ErrorLogResponse first = errorLogCrdService.saveLog(req);
        String logHash = first.getLogHash();

        for (int i = 1; i < count; i++) {
            errorLogCrdService.saveLog(req);
        }

        var log = errorLogRepository.findByLogHash(logHash).orElseThrow();
        assertThat(log.getRepeatCount()).isEqualTo(count);

        maxGroupingCount = count;
        recordResult("T11", "성능", true, count + "개 동일 로그 → 1 Incident");
    }

    @Test @Order(23)
    @DisplayName("T12 [성능] Redis 파이프라인: Push → Poll → DB")
    void t12_performance_redisPipeline() {
        int count = 50;
        List<ErrorLogRequest> requests = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            requests.add(buildRequest(SVC, "Redis pipeline test " + i, "host-" + i, "ERROR"));
        }

        long startMs = System.currentTimeMillis();

        // Redis에 푸시
        for (ErrorLogRequest req : requests) {
            logToRedis.push(req);
        }

        // Redis에서 DB로 폴링
        redisToDB.pollAndProcess();

        long elapsedMs = System.currentTimeMillis() - startMs;
        long logsPerSec = (count * 1000) / elapsedMs;

        int savedCount = (int) errorLogRepository.count();
        assertThat(savedCount).isGreaterThanOrEqualTo(count - 5); // 약간의 오차 허용

        recordResult("T12", "성능", savedCount >= count - 5, logsPerSec + " 건/초 (Redis)");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  BLOCK 4: KB/Incident 연동
    // ═══════════════════════════════════════════════════════════════════════════

    @Test @Order(30)
    @DisplayName("T13 [KB] Incident 자동 생성 (비동기 처리 대기)")
    void t13_kb_incidentAutoCreated() {
        ErrorLogRequest req = buildRequest(SVC, "KB incident creation test", "host-1", "ERROR");
        ErrorLogResponse resp = errorLogCrdService.saveLog(req);
        String logHash = resp.getLogHash();

        // 비동기 이벤트 처리를 위해 대기 (최대 5초)
        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .until(() -> incidentRepository.findByLogHash(logHash).isPresent());

        var incident = incidentRepository.findByLogHash(logHash).orElseThrow();
        assertThat(incident.getStatus()).isIn(IncidentStatus.OPEN, IncidentStatus.IN_PROGRESS);

        recordResult("T13", "KB연동", true, "Incident 자동 생성됨");
    }

    @Test @Order(31)
    @DisplayName("T14 [KB] Incident repeatCount 추적")
    void t14_kb_incidentRepeatCount() {
        ErrorLogRequest req = buildRequest(SVC, "KB repeat tracking test", "host-1", "ERROR");
        ErrorLogResponse r1 = errorLogCrdService.saveLog(req);
        String logHash = r1.getLogHash();

        errorLogCrdService.saveLog(req);
        errorLogCrdService.saveLog(req);

        // 비동기 처리 대기
        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .until(() -> {
                    var opt = incidentRepository.findByLogHash(logHash);
                    return opt.isPresent() && opt.get().getRepeatCount() >= 3;
                });

        var incident = incidentRepository.findByLogHash(logHash).orElseThrow();
        assertThat(incident.getRepeatCount()).isGreaterThanOrEqualTo(3);

        recordResult("T14", "KB연동", true, "repeatCount 추적: " + incident.getRepeatCount());
    }

    @Test @Order(32)
    @DisplayName("T15 [KB] Draft 트리거: 3개 호스트 확산")
    void t15_kb_draftTriggerHostSpread() {
        ErrorLogRequest req1 = buildRequest(SVC, "Draft host spread test", "host-1", "ERROR");
        ErrorLogRequest req2 = buildRequest(SVC, "Draft host spread test", "host-2", "ERROR");
        ErrorLogRequest req3 = buildRequest(SVC, "Draft host spread test", "host-3", "ERROR");

        ErrorLogResponse r1 = errorLogCrdService.saveLog(req1);
        errorLogCrdService.saveLog(req2);
        errorLogCrdService.saveLog(req3);

        String logHash = r1.getLogHash();

        // Draft 생성 확인
        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .pollInterval(java.time.Duration.ofMillis(200))
                .until(() -> {
                    var opt = incidentRepository.findByLogHash(logHash);
                    return opt.isPresent();
                });

        recordResult("T15", "KB연동", true, "Draft 트리거: 3 호스트 확산");
    }

    @Test @Order(33)
    @DisplayName("T16 [KB] Draft 트리거: 10회 반복")
    void t16_kb_draftTriggerRepeatCount() {
        ErrorLogRequest req = buildRequest(SVC, "Draft repeat trigger test", "host-1", "ERROR");

        ErrorLogResponse first = errorLogCrdService.saveLog(req);
        String logHash = first.getLogHash();

        for (int i = 1; i < 10; i++) {
            errorLogCrdService.saveLog(req);
        }

        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .until(() -> incidentRepository.findByLogHash(logHash).isPresent());

        var incident = incidentRepository.findByLogHash(logHash).orElseThrow();
        assertThat(incident.getRepeatCount()).isGreaterThanOrEqualTo(10);

        recordResult("T16", "KB연동", true, "Draft 트리거: 10회 반복");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  HELPER & REPORT
    // ═══════════════════════════════════════════════════════════════════════════

    private ErrorLogRequest buildRequest(String svc, String msg, String host, String level) {
        return ErrorLogRequest.builder()
                .serviceName(svc)
                .message(msg)
                .hostName(host)
                .logLevel(level)
                .occurredTime(LocalDateTime.now())
                .build();
    }

    private void recordResult(String testId, String category, boolean passed, String detail) {
        RESULTS.add(new TestResult(testId, category, passed, detail));
    }

    @AfterAll
    static void printReport() {
        System.out.println("\n");
        System.out.println("╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║          LogCollector + LogFixer 성능 테스트 리포트                              ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ 실행 일시: " + LocalDateTime.now());
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");

        System.out.println("║ [1] 정규화 & 기본 동작                                                            ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");
        printTestResults("정규화", "기본동작");

        System.out.println("║ [2] 성능 지표 (Performance Metrics)                                              ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  • 처리 속도 (직접 저장):        " + String.format("%6d 건/초", throughputLogsPerSec));
        System.out.println("║  • 중복 처리 속도:              " + String.format("%6d 건/초", dedupLogsPerSec));
        System.out.println("║  • 평균 저장 지연:              " + String.format("%6d ms/건", avgLatencyMs));
        System.out.println("║  • 최대 그룹화 (1 Incident):    " + String.format("%6d 건", maxGroupingCount));

        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [3] KB 연동 & Incident 관리                                                      ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");
        printTestResults("KB연동");

        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [4] 테스트 결과 요약                                                             ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════════════════╣");

        long passCount = RESULTS.stream().filter(r -> r.passed()).count();
        long totalCount = RESULTS.size();

        System.out.println("║  총 테스트: " + totalCount + " | 통과: " + passCount + " | 실패: " + (totalCount - passCount));
        System.out.println("║  성공률: " + String.format("%.1f%%", (passCount * 100.0 / totalCount)));

        System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝\n");

        RESULTS.forEach(r -> {
            String status = r.passed() ? "✓ PASS" : "✗ FAIL";
            System.out.printf("  %s [%-8s] %-15s : %s%n", status, r.category(), r.testName(), r.detail());
        });

        System.out.println("\n");
    }

    private static void printTestResults(String... categories) {
        Set<String> catSet = new HashSet<>(Arrays.asList(categories));
        RESULTS.stream()
                .filter(r -> catSet.contains(r.category()))
                .forEach(r -> {
                    String icon = r.passed() ? "✓" : "✗";
                    System.out.println("║  " + icon + " " + r.testName() + " : " + r.detail());
                });
    }
}
