package com.soyunju.logcollector.kb;

import com.soyunju.logcollector.domain.kb.Incident;
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
public class IncidentIntegrationTest2 {

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

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    private void clearDatabase() {
        System.out.println("[CLEANUP] 시작: 모든 테스트 데이터 초기화 중...");

        // 외래 키 제약 조건 잠시 해제 (필요한 경우)
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        // kb_article이 incident를 FK로 물고 있으므로 먼저 제거 (있을 경우)
        jdbcTemplate.execute("TRUNCATE TABLE kb_article");

        // 데이터 삭제 (TRUNCATE는 ID 자동 증가값도 초기화합니다)
        jdbcTemplate.execute("TRUNCATE TABLE incident");
        jdbcTemplate.execute("TRUNCATE TABLE error_logs");
        jdbcTemplate.execute("TRUNCATE TABLE error_log_hosts");

        // 외래 키 제약 조건 다시 설정
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        System.out.println("[CLEANUP] 완료: DB가 깨끗한 상태입니다.");
    }

    // ------------------------
    // 샘플 ErrorLogRequest 생성기
    // ------------------------

    private ErrorLogRequest dbTimeoutReq(String requestId, int ms, int connId) {
        return ErrorLogRequest.builder()
                .serviceName("payment-api")
                .hostName("payment-01")
                .logLevel("ERROR")
                .message(String.format(
                        "[requestId=%s] JDBC Connection timed out after %dms (pool=HikariPool-1, connId=%d)",
                        requestId, ms, connId
                ))
                .stackTrace("""
                        org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection
                            at org.springframework.jdbc.datasource.DataSourceUtils.getConnection(DataSourceUtils.java:123)
                        Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 3000ms.
                            at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:500)
                        """)
                .occurredTime(LocalDateTime.now())
                .build();
    }

    private ErrorLogRequest npeReq(long userId, String orderId) {
        return ErrorLogRequest.builder()
                .serviceName("order-api")
                .hostName("order-02")
                .logLevel("ERROR")
                .message(String.format("userId=%d orderId=%s Failed to process order: null", userId, orderId))
                .stackTrace("""
                        java.lang.NullPointerException: Cannot invoke "String.length()" because "token" is null
                            at com.soyunju.logcollector.service.auth.TokenService.validate(TokenService.java:87)
                            at com.soyunju.logcollector.service.order.OrderService.place(OrderService.java:142)
                        """)
                .occurredTime(LocalDateTime.now())
                .build();
    }

    // ------------------------
    // 테스트
    // ------------------------

    @Test
    @DisplayName("동일 유형(DB timeout) 가변값만 다른 2건 -> 정규화/해시가 안정적이면 Incident는 1행이며 repeatCount=2여야 한다")
    void normalization_hash_should_be_stable_for_same_error_type() {
        // given
        ErrorLogRequest r1 = dbTimeoutReq("REQ-20260120-0001", 3000, 1001);
        ErrorLogRequest r2 = dbTimeoutReq("REQ-20260120-0002", 5120, 2099);

        // when
        errorLogCrdService.saveLog(r1);
        errorLogCrdService.saveLog(r2);

        // then
        // incident는 log_hash unique라서 "동일 해시"로 묶이면 행은 1개여야 함
        Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incident", Integer.class);
        assertThat(cnt).isEqualTo(1);

        Incident incident = incidentRepository.findAll().get(0);
        assertThat(incident.getRepeatCount()).isEqualTo(2);
        assertThat(incident.getServiceName()).isEqualTo("payment-api");
        assertThat(incident.getLastOccurredAt()).isNotNull();
        assertThat(incident.getFirstOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("서로 다른 유형(DB timeout vs NPE) 2건 -> Incident는 2행이어야 한다(해시 분리)")
    void different_error_type_should_create_two_incidents() {
        // given
        ErrorLogRequest r1 = dbTimeoutReq("REQ-20260120-0003", 3000, 3003);
        ErrorLogRequest r2 = npeReq(182736L, "ORD-20260120-9912");

        // when
        errorLogCrdService.saveLog(r1);
        errorLogCrdService.saveLog(r2);

        // then
        Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incident", Integer.class);
        assertThat(cnt).isEqualTo(2);
    }

    @Test
    @DisplayName("동일 유형(NPE) 5회 -> 동일 해시로 묶이면 Incident 1행 repeatCount=5여야 한다")
    void npe_same_type_should_increment_repeat_count() {
        // given
        for (int i = 1; i <= 5; i++) {
            ErrorLogRequest r = npeReq(1000L + i, "ORD-20260120-" + (9000 + i));
            errorLogCrdService.saveLog(r);
        }

        // then
        Integer cnt = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incident", Integer.class);
        assertThat(cnt).isEqualTo(1);

        Incident incident = incidentRepository.findAll().get(0);
        assertThat(incident.getRepeatCount()).isEqualTo(5);
        assertThat(incident.getServiceName()).isEqualTo("order-api");
    }
}
