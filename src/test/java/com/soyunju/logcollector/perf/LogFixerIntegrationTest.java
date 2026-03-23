package com.soyunju.logcollector.perf;

import com.soyunju.logcollector.domain.kb.KbEventOutbox;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.domain.kb.enums.KbEventOutboxStatus;
import com.soyunju.logcollector.domain.kb.enums.KbEventType;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.dto.logfixer.LogFixerIncidentPayload;
import com.soyunju.logcollector.es.KbArticleEsRepository;
import com.soyunju.logcollector.es.KbArticleEsService;
import com.soyunju.logcollector.repository.kb.*;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.kb.crud.*;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import com.soyunju.logcollector.service.logfixer.LogFixerWebhookService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LogFixer 연동 통합 테스트
 *
 * [테스트 범위]
 *  - LC → LogFixer : Webhook 발송, payload 검증 (Mockito Captor)
 *  - LogFixer → LC : REST 콜백 수신 (MockMvc) – RESOLVED, Addendum, KB 조회
 *  - 내결함성       : KbEventOutbox 재처리, 최대 재시도 초과 → FAILED
 *
 * [실행]
 *  export RUN_INTEGRATION_TEST=true
 *  ./gradlew test --tests "LogFixerIntegrationTest"
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TEST", matches = "true")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LogFixerIntegrationTest {

    // ═══════════════════════════════════════════════════════════════
    //  Report
    // ═══════════════════════════════════════════════════════════════
    record TestResult(String id, String category, boolean passed, String detail) {}
    static final List<TestResult> RESULTS = Collections.synchronizedList(new ArrayList<>());
    static long webhookAsyncLatencyMs = 0;

    // ═══════════════════════════════════════════════════════════════
    //  Mocks
    // ═══════════════════════════════════════════════════════════════
    @MockBean KbArticleEsRepository kbArticleEsRepository;
    @MockBean KbArticleEsService kbArticleEsService;
    @MockBean LcIgnoreOutboxProcessorService lcIgnoreOutboxProcessorService;
    // LogFixerWebhookService: mock → ArgumentCaptor로 호출 캡처
    @MockBean LogFixerWebhookService logFixerWebhookService;
    // KbEventOutboxProcessorService: mock 하지 않음 (실제 재처리 테스트)

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
        // LogFixer URL: mock bean이 가로채므로 실제 연결 불필요
        r.add("logfixer.webhook.url", () -> "http://localhost:9999/api/incident");
        r.add("slack.webhook.url", () -> "");
    }

    // ═══════════════════════════════════════════════════════════════
    //  Autowired
    // ═══════════════════════════════════════════════════════════════
    @Autowired MockMvc mockMvc;
    @Autowired ErrorLogCrdService errorLogCrdService;
    @Autowired IncidentRepository incidentRepository;
    @Autowired KbArticleRepository kbArticleRepository;
    @Autowired KbAddendumRepository kbAddendumRepository;
    @Autowired KbEventOutboxRepository kbEventOutboxRepository;
    @Autowired KbEventOutboxProcessorService kbEventOutboxProcessorService;
    @Autowired KbDraftService kbDraftService;
    @Autowired ErrorLogRepository errorLogRepository;
    @Autowired ErrorLogHostRepository errorLogHostRepository;

    static final String SVC = "logfixer-test-svc";

    @BeforeEach
    void setUp() {
        errorLogRepository.deleteAll();
        errorLogHostRepository.deleteAll();
        kbAddendumRepository.deleteAll();
        kbArticleRepository.deleteAll();
        incidentRepository.deleteAll();
        kbEventOutboxRepository.deleteAll();
        reset(logFixerWebhookService);
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLOCK 1: LC → LogFixer (Webhook 발송)
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(1)
    @DisplayName("T1 [LC→LF] 신규 로그 저장 시 webhook sendIncident() 호출 검증")
    void t1_webhookCalledOnNewLog() {
        ErrorLogRequest req = buildReq(SVC, "Webhook trigger test error", "host-1", "ERROR");
        long start = System.currentTimeMillis();

        errorLogCrdService.saveLog(req);

        // 비동기 KbEventListener → logFixerWebhookService.sendIncident() 호출 대기
        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .until(() -> {
                    try { verify(logFixerWebhookService, atLeastOnce()).sendIncident(any()); return true; }
                    catch (AssertionError e) { return false; }
                });

        webhookAsyncLatencyMs = System.currentTimeMillis() - start;
        verify(logFixerWebhookService, atLeastOnce()).sendIncident(any());
        recordResult("T1", "LC→LF", true, "webhook 호출 확인 (비동기 지연: " + webhookAsyncLatencyMs + "ms)");
    }

    @Test @Order(2)
    @DisplayName("T2 [LC→LF] webhook payload 필수 필드 검증")
    void t2_webhookPayloadFields() {
        ErrorLogRequest req = buildReq(SVC, "Payload field validation error", "host-1", "ERROR");
        ErrorLogResponse resp = errorLogCrdService.saveLog(req);

        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .until(() -> {
                    try { verify(logFixerWebhookService, atLeastOnce()).sendIncident(any()); return true; }
                    catch (AssertionError e) { return false; }
                });

        ArgumentCaptor<LogFixerIncidentPayload> captor =
                ArgumentCaptor.forClass(LogFixerIncidentPayload.class);
        verify(logFixerWebhookService, atLeastOnce()).sendIncident(captor.capture());

        LogFixerIncidentPayload payload = captor.getValue();
        assertThat(payload.getLogHash()).isEqualTo(resp.getLogHash());
        assertThat(payload.getServiceName()).isEqualTo(SVC);
        assertThat(payload.getRepeatCount()).isEqualTo(1);
        assertThat(payload.getImpactedHostCount()).isEqualTo(1);
        assertThat(payload.getLogLevel()).isEqualToIgnoringCase("ERROR");

        recordResult("T2", "LC→LF", true,
                "logHash=" + payload.getLogHash().substring(0, 8) +
                "... svc=" + payload.getServiceName() +
                " repeat=" + payload.getRepeatCount() +
                " hosts=" + payload.getImpactedHostCount());
    }

    @Test @Order(3)
    @DisplayName("T3 [LC→LF] 동일 로그 반복 시 repeatCount 증가 → payload에 반영")
    void t3_payloadRepeatCountIncrements() {
        ErrorLogRequest req = buildReq(SVC, "Repeat count payload test", "host-1", "ERROR");

        errorLogCrdService.saveLog(req); // 1회
        errorLogCrdService.saveLog(req); // 2회
        errorLogCrdService.saveLog(req); // 3회
        reset(logFixerWebhookService);   // 이전 호출 리셋
        errorLogCrdService.saveLog(req); // 4회 – 이 호출만 캡처

        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .until(() -> {
                    try { verify(logFixerWebhookService, atLeastOnce()).sendIncident(any()); return true; }
                    catch (AssertionError e) { return false; }
                });

        ArgumentCaptor<LogFixerIncidentPayload> captor =
                ArgumentCaptor.forClass(LogFixerIncidentPayload.class);
        verify(logFixerWebhookService, atLeastOnce()).sendIncident(captor.capture());

        assertThat(captor.getValue().getRepeatCount()).isEqualTo(4);
        recordResult("T3", "LC→LF", true, "repeatCount=" + captor.getValue().getRepeatCount());
    }

    @Test @Order(4)
    @DisplayName("T4 [LC→LF] 호스트 확산 시 impactedHostCount 증가 → payload에 반영")
    void t4_payloadHostSpreadReflected() {
        errorLogCrdService.saveLog(buildReq(SVC, "Host spread payload test", "host-1", "ERROR"));
        errorLogCrdService.saveLog(buildReq(SVC, "Host spread payload test", "host-2", "ERROR"));
        reset(logFixerWebhookService);
        errorLogCrdService.saveLog(buildReq(SVC, "Host spread payload test", "host-3", "ERROR"));

        await()
                .timeout(java.time.Duration.ofSeconds(5))
                .until(() -> {
                    try { verify(logFixerWebhookService, atLeastOnce()).sendIncident(any()); return true; }
                    catch (AssertionError e) { return false; }
                });

        ArgumentCaptor<LogFixerIncidentPayload> captor =
                ArgumentCaptor.forClass(LogFixerIncidentPayload.class);
        verify(logFixerWebhookService, atLeastOnce()).sendIncident(captor.capture());

        assertThat(captor.getValue().getImpactedHostCount()).isGreaterThanOrEqualTo(3);
        recordResult("T4", "LC→LF", true, "impactedHostCount=" + captor.getValue().getImpactedHostCount());
    }

    @Test @Order(5)
    @DisplayName("T5 [LC→LF] webhook 실패해도 LC 로그 저장에 영향 없음 (비파괴적 실패)")
    void t5_webhookFailureDoesNotBlockLogSave() {
        doThrow(new RuntimeException("LogFixer down"))
                .when(logFixerWebhookService).sendIncident(any());

        ErrorLogRequest req = buildReq(SVC, "Webhook failure resilience error", "host-1", "ERROR");
        ErrorLogResponse resp = errorLogCrdService.saveLog(req);

        assertThat(resp).isNotNull();
        assertThat(resp.isNew()).isTrue();
        assertThat(errorLogRepository.findByLogHash(resp.getLogHash())).isPresent();

        recordResult("T5", "LC→LF", true, "webhook 예외에도 로그 저장 정상 완료");
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLOCK 2: LogFixer → LC (콜백 수신)
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(10)
    @DisplayName("T6 [LF→LC] PATCH /api/incidents/{logHash}/status?newStatus=RESOLVED")
    void t6_resolvedCallbackChangesIncidentStatus() throws Exception {
        ErrorLogResponse resp = errorLogCrdService.saveLog(
                buildReq(SVC, "Resolved callback test error", "host-1", "ERROR"));
        String logHash = resp.getLogHash();

        await().timeout(java.time.Duration.ofSeconds(5))
                .until(() -> incidentRepository.findByLogHash(logHash).isPresent());

        // LogFixer가 RESOLVED 콜백 전송
        mockMvc.perform(patch("/api/incidents/{logHash}/status", logHash)
                        .param("newStatus", "RESOLVED"))
                .andExpect(status().isOk());

        await().timeout(java.time.Duration.ofSeconds(3))
                .until(() -> incidentRepository.findByLogHash(logHash)
                        .map(i -> i.getStatus() == IncidentStatus.RESOLVED)
                        .orElse(false));

        var incident = incidentRepository.findByLogHash(logHash).orElseThrow();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).isNotNull();

        recordResult("T6", "LF→LC", true, "RESOLVED 전환 + resolvedAt 기록됨");
    }

    @Test @Order(11)
    @DisplayName("T7 [LF→LC] RESOLVED → 재발 시 OPEN 자동 복귀")
    void t7_resolvedIncidentReopensOnRecurrence() throws Exception {
        ErrorLogRequest req = buildReq(SVC, "Reopen after resolved test", "host-1", "ERROR");
        ErrorLogResponse r1 = errorLogCrdService.saveLog(req);
        String logHash = r1.getLogHash();

        await().timeout(java.time.Duration.ofSeconds(5))
                .until(() -> incidentRepository.findByLogHash(logHash).isPresent());

        // LogFixer RESOLVED 콜백
        mockMvc.perform(patch("/api/incidents/{logHash}/status", logHash)
                        .param("newStatus", "RESOLVED"))
                .andExpect(status().isOk());

        await().timeout(java.time.Duration.ofSeconds(3))
                .until(() -> incidentRepository.findByLogHash(logHash)
                        .map(i -> i.getStatus() == IncidentStatus.RESOLVED).orElse(false));

        // 동일 에러 재발 → OPEN 복귀
        errorLogCrdService.saveLog(req);

        await().timeout(java.time.Duration.ofSeconds(5))
                .until(() -> incidentRepository.findByLogHash(logHash)
                        .map(i -> i.getStatus() == IncidentStatus.OPEN).orElse(false));

        var incident = incidentRepository.findByLogHash(logHash).orElseThrow();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.OPEN);

        recordResult("T7", "LF→LC", true, "RESOLVED → 재발 → OPEN 자동 복귀");
    }

    @Test @Order(12)
    @DisplayName("T8 [LF→LC] POST /api/kb/{kbArticleId}/addendums - LogFixer 분석 결과 저장")
    void t8_logfixerAddendumSaved() throws Exception {
        ErrorLogResponse resp = errorLogCrdService.saveLog(
                buildReq(SVC, "Addendum save test error", "host-1", "ERROR"));
        String logHash = resp.getLogHash();

        await().timeout(java.time.Duration.ofSeconds(5))
                .until(() -> incidentRepository.findByLogHash(logHash).isPresent());

        Long incidentId = incidentRepository.findByLogHash(logHash).orElseThrow().getId();
        Long kbArticleId = kbDraftService.createSystemDraft(incidentId);

        String body = """
                {
                    "content": "LogFixer 분석: NullPointerException - userId 파라미터 누락. 해결: 기본값 처리 추가",
                    "createdBy": "system"
                }
                """;

        mockMvc.perform(post("/api/kb/{kbArticleId}/addendums", kbArticleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        var addendums = kbAddendumRepository.findByKbArticle_IdOrderByCreatedAtDesc(kbArticleId);
        assertThat(addendums).hasSize(1);
        assertThat(addendums.get(0).getContent()).contains("LogFixer 분석");

        recordResult("T8", "LF→LC", true, "Addendum 1건 저장 완료");
    }

    @Test @Order(13)
    @DisplayName("T9 [LF→LC] GET /api/kb/articles/byhash/{logHash} - KB 조회 (RAG 컨텍스트)")
    void t9_logfixerKbLookupByHash() throws Exception {
        ErrorLogResponse resp = errorLogCrdService.saveLog(
                buildReq(SVC, "KB lookup test error", "host-1", "ERROR"));
        String logHash = resp.getLogHash();

        await().timeout(java.time.Duration.ofSeconds(5))
                .until(() -> incidentRepository.findByLogHash(logHash).isPresent());

        Long incidentId = incidentRepository.findByLogHash(logHash).orElseThrow().getId();
        kbDraftService.createSystemDraft(incidentId);

        mockMvc.perform(get("/api/kb/articles/byhash/{logHash}", logHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kbArticleId").isNumber());

        recordResult("T9", "LF→LC", true, "logHash로 KbArticleId 조회 성공");
    }

    @Test @Order(14)
    @DisplayName("T10 [LF→LC] 존재하지 않는 logHash → 404")
    void t10_unknownLogHashReturns404() throws Exception {
        mockMvc.perform(get("/api/kb/articles/byhash/deadbeef00000000unknown"))
                .andExpect(status().isNotFound());

        recordResult("T10", "LF→LC", true, "알 수 없는 logHash → 404 반환");
    }

    // ═══════════════════════════════════════════════════════════════
    //  BLOCK 3: 내결함성 (KbEventOutbox 재처리)
    // ═══════════════════════════════════════════════════════════════

    @Test @Order(20)
    @DisplayName("T11 [내결함성] KbEventOutbox 재처리 → Incident 정상 생성")
    void t11_kbEventOutboxRetryCreatesIncident() {
        String logHash = UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        // 직접 outbox 적재 (마치 KB 이벤트 처리 실패한 것처럼)
        kbEventOutboxRepository.save(KbEventOutbox.builder()
                .logHash(logHash)
                .eventType(KbEventType.LOG_SAVED)
                .payload(buildLogSavedPayload(logHash))
                .status(KbEventOutboxStatus.PENDING)
                .attemptCount(0)
                .build());

        // 재처리 실행
        kbEventOutboxProcessorService.process(LocalDateTime.now());

        // Incident 생성 확인
        var incident = incidentRepository.findByLogHash(logHash);
        assertThat(incident).isPresent();
        assertThat(incident.get().getStatus()).isEqualTo(IncidentStatus.OPEN);

        // Outbox 상태 SUCCESS 확인
        var outbox = kbEventOutboxRepository.findAll().stream()
                .filter(o -> o.getLogHash().equals(logHash)).findFirst();
        assertThat(outbox).isPresent();
        assertThat(outbox.get().getStatus()).isEqualTo(KbEventOutboxStatus.SUCCESS);

        recordResult("T11", "내결함성", true,
                "Outbox 재처리 → Incident 생성 + status=SUCCESS");
    }

    @Test @Order(21)
    @DisplayName("T12 [내결함성] 잘못된 payload Outbox → 재시도 후 FAILED")
    void t12_kbEventOutboxMaxRetryFailed() {
        String logHash = "badbad0000000000invalid";

        kbEventOutboxRepository.save(KbEventOutbox.builder()
                .logHash(logHash)
                .eventType(KbEventType.LOG_SAVED)
                .payload("THIS_IS_NOT_JSON")   // 역직렬화 실패 유도
                .status(KbEventOutboxStatus.PENDING)
                .attemptCount(4)               // max(5)-1 → 다음 시도가 마지막
                .build());

        kbEventOutboxProcessorService.process(LocalDateTime.now());

        var outbox = kbEventOutboxRepository.findAll().stream()
                .filter(o -> o.getLogHash().equals(logHash)).findFirst();
        assertThat(outbox).isPresent();
        assertThat(outbox.get().getStatus()).isEqualTo(KbEventOutboxStatus.FAILED);

        recordResult("T12", "내결함성", true, "최대 재시도(5회) 초과 → FAILED");
    }

    @Test @Order(22)
    @DisplayName("T13 [내결함성] Outbox 재처리 실패 → nextRetryAt 설정 + PENDING 유지")
    void t13_kbEventOutboxRetryFailedSetsNextRetryAt() {
        String logHash = "retry0000000000pending1";

        kbEventOutboxRepository.save(KbEventOutbox.builder()
                .logHash(logHash)
                .eventType(KbEventType.LOG_SAVED)
                .payload("NOT_VALID_JSON")
                .status(KbEventOutboxStatus.PENDING)
                .attemptCount(1)               // 아직 재시도 가능
                .build());

        kbEventOutboxProcessorService.process(LocalDateTime.now());

        var outbox = kbEventOutboxRepository.findAll().stream()
                .filter(o -> o.getLogHash().equals(logHash)).findFirst();
        assertThat(outbox).isPresent();
        assertThat(outbox.get().getStatus()).isEqualTo(KbEventOutboxStatus.PENDING);
        assertThat(outbox.get().getNextRetryAt()).isNotNull();     // 재시도 시각 설정됨
        assertThat(outbox.get().getAttemptCount()).isEqualTo(2);   // 시도 횟수 증가

        recordResult("T13", "내결함성", true,
                "실패 후 PENDING 유지 + nextRetryAt=" + outbox.get().getNextRetryAt());
    }

    // ═══════════════════════════════════════════════════════════════
    //  Helper
    // ═══════════════════════════════════════════════════════════════

    private ErrorLogRequest buildReq(String svc, String msg, String host, String level) {
        return ErrorLogRequest.builder()
                .serviceName(svc).message(msg).hostName(host)
                .logLevel(level).occurredTime(LocalDateTime.now())
                .build();
    }

    private String buildLogSavedPayload(String logHash) {
        return """
                {
                    "logHash": "%s",
                    "serviceName": "outbox-retry-svc",
                    "summary": "Outbox retry test",
                    "stackTrace": null,
                    "errorCode": "GEN_ERR",
                    "effectiveLevel": "ERROR",
                    "occurredTime": "2026-03-23T10:00:00",
                    "impactedHostCount": 1,
                    "repeatCount": 1,
                    "incidentId": null,
                    "draftNeeded": false,
                    "draftReason": null
                }
                """.formatted(logHash);
    }

    private void recordResult(String id, String category, boolean passed, String detail) {
        RESULTS.add(new TestResult(id, category, passed, detail));
    }

    @AfterAll
    static void printReport() {
        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║          LogFixer 연동 통합 테스트 리포트                                          ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");

        System.out.println("║ [1] LC → LogFixer (Webhook 발송)                                                  ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        printByCategory("LC→LF");

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [2] LogFixer → LC (REST 콜백 수신)                                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        printByCategory("LF→LC");

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [3] 내결함성 (KbEventOutbox 재처리)                                                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        printByCategory("내결함성");

        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║ [성능 지표]                                                                        ║");
        System.out.println("║  • Webhook 비동기 발송 지연 (saveLog → sendIncident 호출): " + webhookAsyncLatencyMs + "ms");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");

        long pass = RESULTS.stream().filter(TestResult::passed).count();
        long total = RESULTS.size();
        System.out.printf("║  총 %d개 | 통과: %d | 실패: %d | 성공률: %.1f%%\n",
                total, pass, total - pass, pass * 100.0 / total);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════╝\n");

        System.out.println("=== 상세 결과 ===");
        RESULTS.forEach(r ->
                System.out.printf("  %s [%-6s] %-4s : %s%n",
                        r.passed() ? "✓ PASS" : "✗ FAIL",
                        r.category(), r.id(), r.detail()));
    }

    private static void printByCategory(String cat) {
        RESULTS.stream().filter(r -> r.category().equals(cat))
                .forEach(r -> System.out.printf("║  %s %-4s : %s%n",
                        r.passed() ? "✓" : "✗", r.id(), r.detail()));
    }
}
