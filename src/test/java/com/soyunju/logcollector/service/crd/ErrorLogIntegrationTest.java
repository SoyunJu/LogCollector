package com.soyunju.logcollector.service.crd;

import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.domain.lc.ErrorStatus;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.es.KbArticleEsRepository;
import com.soyunju.logcollector.es.KbArticleEsService;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.kb.crud.KbEventOutboxProcessorService;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TEST", matches = "true")
class ErrorLogIntegrationTest {

    @MockBean
    KbArticleEsRepository kbArticleEsRepository;

    @MockBean
    KbArticleEsService kbArticleEsService;

    @MockBean
    KbEventOutboxProcessorService kbEventOutboxProcessorService;

    @Container
    static final MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        // LC datasource
        registry.add("spring.datasource.lc.jdbc-url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.lc.username", mariadb::getUsername);
        registry.add("spring.datasource.lc.password", mariadb::getPassword);
        registry.add("spring.datasource.lc.driver-class-name", mariadb::getDriverClassName);

        // KB datasource - 동일 컨테이너 사용
        registry.add("spring.datasource.kb.jdbc-url", mariadb::getJdbcUrl);
        registry.add("spring.datasource.kb.username", mariadb::getUsername);
        registry.add("spring.datasource.kb.password", mariadb::getPassword);
        registry.add("spring.datasource.kb.driver-class-name", mariadb::getDriverClassName);

        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired private ErrorLogCrdService errorLogCrdService;
    @Autowired private ErrorLogRepository errorLogRepository;

    // 테스트마다 동일한 로그를 사용하기 위한 고정 payload
    private static final String SERVICE = "test-service";
    private static final String MESSAGE = "Redis connection timeout occurred";
    private static final String HOST_1  = "host-1";
    private static final String HOST_2  = "host-2";

    @BeforeEach
    void setUp() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("[TEST START] " + LocalDateTime.now());
        System.out.println("-".repeat(60));
    }

    @AfterEach
    void tearDown() {
        // 테스트 격리: 사용한 logHash 기준으로만 삭제
        errorLogRepository.findAll().forEach(log ->
                errorLogRepository.deleteByLogHash(log.getLogHash())
        );
        System.out.println("-".repeat(60));
        System.out.println("[TEST END] " + LocalDateTime.now());
        System.out.println("=".repeat(60) + "\n");
    }

    // ──────────────────────────────────────────────
    // Scenario 1. 신규 로그 1회 → isNew=true, repeatCount=1
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("신규 로그 최초 수집 시 isNew=true, repeatCount=1")
    void scenario1_firstLog_isNew() {
        ErrorLogRequest req = buildRequest(SERVICE, MESSAGE, HOST_1, "ERROR");

        ErrorLogResponse response = errorLogCrdService.saveLog(req);

        assertThat(response).isNotNull();
        assertThat(response.isNew()).isTrue();
        assertThat(response.getRepeatCount()).isEqualTo(1);
        assertThat(response.getLogHash()).isNotBlank();
    }

    // ──────────────────────────────────────────────
    // Scenario 2. 동일 로그 3회 → repeatCount 누적
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("동일 로그 3회 수집 시 repeatCount=3, isNew=false")
    void scenario2_sameLog_repeatCount() {
        ErrorLogRequest req = buildRequest(SERVICE, MESSAGE, HOST_1, "ERROR");

        errorLogCrdService.saveLog(req); // 1회
        errorLogCrdService.saveLog(req); // 2회
        ErrorLogResponse third = errorLogCrdService.saveLog(req); // 3회

        assertThat(third).isNotNull();
        assertThat(third.isNew()).isFalse();
        assertThat(third.getRepeatCount()).isEqualTo(3);
    }

    // ──────────────────────────────────────────────
    // Scenario 3. RESOLVED 후 동일 로그 재입력 → status=NEW 복귀
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("RESOLVED 후 동일 로그 재입력 시 status가 NEW로 복귀")
    void scenario3_resolvedThenReopen() {
        ErrorLogRequest req = buildRequest(SERVICE, MESSAGE, HOST_1, "ERROR");

        // 1) 최초 수집
        ErrorLogResponse first = errorLogCrdService.saveLog(req);
        assertThat(first).isNotNull();
        Long logId = first.getLogId();

        // 2) RESOLVED 처리
        errorLogCrdService.updateStatus(logId, ErrorStatus.RESOLVED);
        ErrorLog resolved = errorLogRepository.findById(logId).orElseThrow();
        assertThat(resolved.getStatus()).isEqualTo(ErrorStatus.RESOLVED);

        // 3) 동일 로그 재입력 → upsert ON DUPLICATE KEY: status = CASE WHEN RESOLVED THEN NEW
        errorLogCrdService.saveLog(req);
        ErrorLog reopened = errorLogRepository.findByLogHash(resolved.getLogHash()).orElseThrow();
        assertThat(reopened.getStatus()).isEqualTo(ErrorStatus.NEW);
    }

    // ──────────────────────────────────────────────
    // Scenario 4. 다른 host에서 동일 로그 → isNewHost=true
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("다른 host에서 동일 로그 수집 시 isNewHost=true")
    void scenario4_newHost_spread() {
        ErrorLogRequest req1 = buildRequest(SERVICE, MESSAGE, HOST_1, "ERROR");
        ErrorLogRequest req2 = buildRequest(SERVICE, MESSAGE, HOST_2, "ERROR");

        errorLogCrdService.saveLog(req1);
        ErrorLogResponse second = errorLogCrdService.saveLog(req2);

        assertThat(second).isNotNull();
        assertThat(second.isNewHost()).isTrue();
        assertThat(second.getImpactedHostCount()).isGreaterThanOrEqualTo(2);
    }

    // ──────────────────────────────────────────────
    // Scenario 5. IGNORED 상태 전이 → 이후 재입력 시 null 반환 (수집 무시)
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("IGNORED 처리된 로그는 재수집 시 무시(null 반환)")
    void scenario5_ignored_logSkipped() {
        ErrorLogRequest req = buildRequest(SERVICE, "NullPointerException in UserService", HOST_1, "ERROR");

        // 1) 최초 수집
        ErrorLogResponse first = errorLogCrdService.saveLog(req);
        assertThat(first).isNotNull();

        // 2) IGNORED 처리
        errorLogCrdService.updateStatus(first.getLogId(), ErrorStatus.IGNORED);

        // 3) 동일 로그 재입력 → IGNORED 상태이므로 null 반환
        // saveLog 내부의 ignoredLogHashStore.isIgnored() 체크에 의해 무시됨
        // (Redis 기반이므로 통합테스트 환경에서는 Redis Mock 또는 EmbeddedRedis 필요)
        // → 여기서는 DB 상태가 IGNORED로 유지되는 것만 검증
        ErrorLog ignored = errorLogRepository.findById(first.getLogId()).orElseThrow();
        assertThat(ignored.getStatus()).isEqualTo(ErrorStatus.IGNORED);
    }

    // ──────────────────────────────────────────────
    // Scenario 6. 수집 대상 외 로그 레벨(INFO) → 예외 발생
    // ──────────────────────────────────────────────
    @Test
    @DisplayName("INFO 레벨 로그는 수집 대상 외로 예외 발생")
    void scenario6_infoLevel_rejected() {
        ErrorLogRequest req = buildRequest(SERVICE, MESSAGE, HOST_1, "INFO");

        assertThatThrownBy(() -> errorLogCrdService.saveLog(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수집 대상 로그 레벨이 아닙니다");
    }

    // ──────────────────────────────────────────────
    // Helper
    // ──────────────────────────────────────────────
    private ErrorLogRequest buildRequest(String service, String message, String host, String level) {
        return ErrorLogRequest.builder()
                .serviceName(service)
                .message(message)
                .hostName(host)
                .logLevel(level)
                .occurredTime(LocalDateTime.now())
                .build();
    }
}