package com.soyunju.logcollector.perf;

import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.es.KbArticleEsRepository;
import com.soyunju.logcollector.es.KbArticleEsService;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.repository.kb.KbAddendumRepository;
import com.soyunju.logcollector.repository.kb.KbArticleRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.kb.crud.KbDraftService;
import com.soyunju.logcollector.service.kb.crud.KbEventOutboxProcessorService;
import com.soyunju.logcollector.service.kb.crud.LcIgnoreOutboxProcessorService;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.lc.redis.LogToRedis;
import com.soyunju.logcollector.service.logfixer.LogFixerWebhookService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 고급 복원력 & 동시성 & 신뢰도 임계치 테스트
 *
 * [Block 1] 동시성 (Concurrency)
 *   T1 - 동일 logHash 20건 동시 요청 → ErrorLog 1개, Incident 1개 (race condition 방지)
 *   T2 - 20건 동시 완료 후 repeatCount == 20 (카운트 누락 없음)
 *
 * [Block 2] LogFixer 신뢰도 임계치 (Confidence Threshold)
 *   T3 - confidence=0.35 (< 0.4) → 400 차단 + 저장 안됨
 *   T4 - confidence=0.6  (≥ 0.4) → 200 정상 저장
 *   T5 - confidence 미제공       → 200 기본 허용
 *
 * [Block 3] Redis Fallback (마지막 실행 - Redis 컨테이너 종료)
 *   T6 - Redis 강제 종료 후 10건 push → DB Fallback → 유실 0건
 *   T7 - Redis 장애 중 동일 로그 5건 → dedup 정상 동작 (Incident 1개)
 *
 * [실행]
 *   export RUN_INTEGRATION_TEST=true
 *   ./gradlew test --tests "AdvancedResilienceTest"
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AdvancedResilienceTest {

    // ═══════════════════════════════════════════════════════════════
    //  Report
    // ═══════════════════════════════════════════════════════════════
    record TestResult(String id, String category, boolean passed, String detail) {}
    static final List<TestResult> RESULTS = Collections.synchronizedList(new ArrayList<>());

    // ═══════════════════════════════════════════════════════════════
    //  Mocks
    // ═══════════════════════════════════════════════════════════════
    @MockBean KbArticleEsRepository kbArticleEsRepository;
    @MockBean KbArticleEsService kbArticleEsService;
    @MockBean KbEventOutboxProcessorService kbEventOutboxProcessorService;
    @MockBean LcIgnoreOutboxProcessorService lcIgnoreOutboxProcessorService;
    @MockBean LogFixerWebhookService logFixerWebhookService;

    // ═══════════════════════════════════════════════════════════════
    //  Containers
    // ═══════════════════════════════════════════════════════════════
    @Container
    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("testdb").withUsername("test").withPassword("test");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.lc.jdbc-url", MARIADB::getJdbcUrl);
        r.add("spring.datasource.lc.username", MARIADB::getUsername);
        r.add("spring.datasource.lc.password", MARIADB::getPassword);
        r.add("spring.datasource.lc.driver-class-name", MARIADB::getDriverClassName);
        r.add("spring.datasource.kb.jdbc-url", MARIADB::getJdbcUrl);
        r.add("spring.datasource.kb.username", MARIADB::getUsername);
        r.add("spring.datasource.kb.password", MARIADB::getPassword);
        r.add("spring.datasource.kb.driver-class-name", MARIADB::getDriverClassName);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        r.add("logfixer.webhook.url", () -> "");
        r.add("slack.webhook.url", () -> "");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Autowired
    // ═══════════════════════════════════════════════════════════════
    @Autowired MockMvc mockMvc;
    @Autowired ErrorLogCrdService errorLogCrdService;
    @Autowired LogToRedis logToRedis;
    @Autowired IncidentRepository incidentRepository;
    @Autowired KbAddendumRepository kbAddendumRepository;
    @Autowired KbArticleRepository kbArticleRepository;
    @Autowired KbDraftService kbDraftService;
    @Autowired ErrorLogRepository errorLogRepository;
    @Autowired ErrorLogHostRepository errorLogHostRepository;

    static final String SVC = "resilience-test-svc";

    @BeforeEach
    void cleanUp() {
        // DB 초기화만 수행 (Redis 연결 불필요 → Fallback 테스트와 공존 가능)
        kbAddendumRepository.deleteAll();
        kbArticleRepository.deleteAll();
        incidentRepository.deleteAll();
        errorLogHostRepository.deleteAll();
        errorLogRepository.deleteAll();
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLOCK 1: 동시성 (Concurrency)
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("T1 [동시성] 동일 logHash 20건 동시 요청 → ErrorLog 1개, Incident 1개")
    void t1_concurrency_sameHashProducesSingleRecord() throws Exception {
        int threadCount = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch allDone  = new CountDownLatch(threadCount);
        ExecutorService pool    = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCnt = new AtomicInteger(0);

        ErrorLogRequest req = buildReq(SVC, "Concurrent same error message body", "host-1", "ERROR");

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();   // 모두 대기
                    errorLogCrdService.saveLog(req);
                    successCnt.incrementAndGet();
                } catch (Exception ignored) {
                    // @Retryable이 처리 실패한 나머지는 무시 (최소 1건은 성공)
                } finally {
                    allDone.countDown();
                }
            });
        }

        startGate.countDown();  // 동시 출발
        boolean finished = allDone.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("20건 처리가 15초 안에 완료돼야 함").isTrue();

        // ErrorLog: logHash unique constraint → 반드시 1개
        assertThat(errorLogRepository.count()).isEqualTo(1);

        // Incident: KB 비동기 처리 대기
        String logHash = errorLogRepository.findAll().get(0).getLogHash();
        await().timeout(java.time.Duration.ofSeconds(10))
               .until(() -> incidentRepository.findByLogHash(logHash).isPresent());

        assertThat(incidentRepository.count()).isEqualTo(1);

        int repeatCount = errorLogRepository.findAll().get(0).getRepeatCount();
        recordResult("T1", "동시성", true,
                "20 스레드 동시 → ErrorLog=1개, Incident=1개, repeatCount=" + repeatCount
                        + " (성공=" + successCnt.get() + ")");
    }

    @Test @Order(11)
    @DisplayName("T2 [동시성] 20건 동시 완료 후 repeatCount == 20 (카운트 누락 없음)")
    void t2_concurrency_repeatCountExact() throws Exception {
        int threadCount = 20;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch allDone  = new CountDownLatch(threadCount);
        ExecutorService pool    = Executors.newFixedThreadPool(threadCount);

        ErrorLogRequest req = buildReq(SVC, "Concurrent repeat count exact check", "host-1", "ERROR");

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                try {
                    startGate.await();
                    errorLogCrdService.saveLog(req);
                } catch (Exception ignored) {
                } finally {
                    allDone.countDown();
                }
            });
        }

        startGate.countDown();
        allDone.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        // MariaDB ON DUPLICATE KEY UPDATE repeat_count + 1 → 원자적으로 20회 증가
        var log = errorLogRepository.findAll().get(0);
        assertThat(log.getRepeatCount()).isEqualTo(threadCount);

        recordResult("T2", "동시성", true,
                "repeatCount=" + log.getRepeatCount() + " (기대값=" + threadCount + ") ✓ 카운트 누락 없음");
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLOCK 2: LogFixer 신뢰도 임계치 (Confidence Threshold)
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("T3 [신뢰도] confidence=0.35 (< 0.4) → 400 차단 + Addendum 저장 안됨")
    void t3_lowConfidenceAddendumBlocked() throws Exception {
        Long kbArticleId = createKbArticle("Low confidence test error");

        String body = """
                {
                    "content": "LogFixer 저신뢰도 분석: 원인 불명확",
                    "createdBy": "system",
                    "confidence": 0.35
                }
                """;

        mockMvc.perform(post("/api/kb/{kbArticleId}/addendums", kbArticleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        // 저장 안됨 확인
        assertThat(kbAddendumRepository.findByKbArticle_IdOrderByCreatedAtDesc(kbArticleId)).isEmpty();

        recordResult("T3", "신뢰도", true,
                "confidence=0.35 → 400 Bad Request + Addendum 0건 (차단 확인)");
    }

    @Test @Order(21)
    @DisplayName("T4 [신뢰도] confidence=0.6 (>= 0.4) → 200 정상 저장")
    void t4_highConfidenceAddendumAllowed() throws Exception {
        Long kbArticleId = createKbArticle("High confidence test error");

        String body = """
                {
                    "content": "LogFixer 고신뢰도 분석: DB 커넥션 풀 고갈 - HikariCP maxPoolSize 확인 필요",
                    "createdBy": "system",
                    "confidence": 0.6
                }
                """;

        mockMvc.perform(post("/api/kb/{kbArticleId}/addendums", kbArticleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(kbAddendumRepository.findByKbArticle_IdOrderByCreatedAtDesc(kbArticleId)).hasSize(1);

        recordResult("T4", "신뢰도", true,
                "confidence=0.6 → 200 OK + Addendum 1건 저장됨");
    }

    @Test @Order(22)
    @DisplayName("T5 [신뢰도] confidence 미제공 → 200 기본 허용 (기존 호환성 유지)")
    void t5_noConfidenceFieldAllowed() throws Exception {
        Long kbArticleId = createKbArticle("No confidence field test error");

        String body = """
                {
                    "content": "기존 형식 (confidence 없음) - 호환성 유지",
                    "createdBy": "user"
                }
                """;

        mockMvc.perform(post("/api/kb/{kbArticleId}/addendums", kbArticleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(kbAddendumRepository.findByKbArticle_IdOrderByCreatedAtDesc(kbArticleId)).hasSize(1);

        recordResult("T5", "신뢰도", true,
                "confidence=null → 200 OK + 저장됨 (기존 호환성 유지)");
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLOCK 3: Redis Fallback
    //  ※ Redis 컨테이너를 종료하므로 반드시 마지막 순서에 실행
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(99)
    @DisplayName("T6 [Redis장애] Redis 강제 종료 후 10건 push → DB Fallback → 유실 0건")
    void t6_redisFallback_zeroLogLoss() {
        // Redis 컨테이너 종료 (장애 시뮬레이션)
        REDIS.stop();

        int count = 10;
        for (int i = 0; i < count; i++) {
            // push() 내부: Redis 연결 실패 → safeFallbackToDb() → errorLogCrdService.saveLog()
            logToRedis.push(buildReq(SVC, "Redis fallback unique error " + i, "host-" + i, "ERROR"));
        }

        // safeFallbackToDb는 동기 호출이므로 push 루프 후 즉시 확인
        long savedCount = errorLogRepository.count();
        assertThat(savedCount).isEqualTo(count);

        recordResult("T6", "Redis장애", savedCount == count,
                "Redis 종료 후 push " + count + "건 → DB=" + savedCount + "건 (유실=" + (count - savedCount) + "건) ✓ 유실 0건");
    }

    @Test @Order(100)
    @DisplayName("T7 [Redis장애] Redis 장애 중 동일 로그 5건 → dedup 정상 동작 (Incident 1개)")
    void t7_redisFallback_dedupMaintained() {
        // Redis는 T6에서 이미 종료된 상태 → @BeforeEach는 DB만 정리하므로 영향 없음
        int count = 5;
        ErrorLogRequest req = buildReq(SVC, "Redis fallback dedup check error", "host-1", "ERROR");

        for (int i = 0; i < count; i++) {
            logToRedis.push(req);
        }

        // DB 레벨 unique upsert → 1개 저장, repeatCount=5
        assertThat(errorLogRepository.count()).isEqualTo(1);
        var log = errorLogRepository.findAll().get(0);
        assertThat(log.getRepeatCount()).isEqualTo(count);

        recordResult("T7", "Redis장애", true,
                "Redis 장애 중 중복 " + count + "건 → ErrorLog=1개, repeatCount=" + log.getRepeatCount() + " ✓ dedup 유지");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════

    private ErrorLogRequest buildReq(String svc, String msg, String host, String level) {
        return ErrorLogRequest.builder()
                .serviceName(svc).message(msg).hostName(host)
                .logLevel(level).occurredTime(LocalDateTime.now())
                .build();
    }

    /** Incident + KbArticle(DRAFT) 생성 후 kbArticleId 반환 */
    private Long createKbArticle(String message) {
        ErrorLogResponse resp = errorLogCrdService.saveLog(buildReq(SVC, message, "host-1", "ERROR"));
        String logHash = resp.getLogHash();
        await().timeout(java.time.Duration.ofSeconds(5))
               .until(() -> incidentRepository.findByLogHash(logHash).isPresent());
        Long incidentId = incidentRepository.findByLogHash(logHash).orElseThrow().getId();
        return kbDraftService.createSystemDraft(incidentId);
    }

    private void recordResult(String id, String category, boolean passed, String detail) {
        RESULTS.add(new TestResult(id, category, passed, detail));
    }

    // ═══════════════════════════════════════════════════════════════
    //  Report
    // ═══════════════════════════════════════════════════════════════

    @AfterAll
    static void printReport() {
        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║          고급 복원력 & 동시성 & 신뢰도 임계치 테스트 리포트                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");

        System.out.println("║ [1] 동시성 (Concurrency)                                                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        printByCategory("동시성");

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [2] LogFixer 신뢰도 임계치 (confidence < 0.4 차단)                                 ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        printByCategory("신뢰도");

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [3] Redis 장애 Fallback (로그 유실 0건 검증)                                        ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        printByCategory("Redis장애");

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");

        long pass  = RESULTS.stream().filter(TestResult::passed).count();
        long total = RESULTS.size();
        System.out.printf("║  총 %d개 | 통과: %d | 실패: %d | 성공률: %.1f%%%n",
                total, pass, total - pass, pass * 100.0 / total);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════╝\n");

        System.out.println("=== 상세 결과 ===");
        RESULTS.forEach(r -> System.out.printf("  %s [%-6s] %-3s : %s%n",
                r.passed() ? "✓ PASS" : "✗ FAIL",
                r.category(), r.id(), r.detail()));
    }

    private static void printByCategory(String cat) {
        RESULTS.stream().filter(r -> r.category().equals(cat))
               .forEach(r -> System.out.printf("║  %s %-3s : %s%n",
                       r.passed() ? "✓" : "✗", r.id(), r.detail()));
    }
}
