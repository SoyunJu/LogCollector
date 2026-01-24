package com.soyunju.logcollector.service.redis;

import com.soyunju.logcollector.domain.lc.ErrorLog;
import com.soyunju.logcollector.dto.lc.ErrorLogRequest;
import com.soyunju.logcollector.dto.lc.ErrorLogResponse;
import com.soyunju.logcollector.repository.lc.ErrorLogHostRepository;
import com.soyunju.logcollector.repository.lc.ErrorLogRepository;
import com.soyunju.logcollector.service.lc.crd.ErrorLogCrdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test") // 테스트에서 슬랙/시크릿 불필요. 필요하면 secret 추가
class FinalNotificationIntegrationTest {

    @Autowired private ErrorLogCrdService errorLogCrdService;
    @Autowired private ErrorLogRepository errorLogRepository;
    @Autowired private ErrorLogHostRepository errorLogHostRepository;

    private static final String TEST_SERVICE = "Final-Test-Service";

    /**
     * 정규화 정책(현 LogNormalization 가정):
     * - 숫자 2자리 이상 -> <NUM>
     * - 연속 <NUM> 압축
     *
     * 따라서 아래 메시지들은 동일 signature로 묶여 동일 logHash가 되어야 함.
     */
    private static final String MSG_BASE = "Connection Refused: DB-Cluster-01";
    private static final String MSG_VARIANT_APPEND_NUM = "Connection Refused: DB-Cluster-9999 12345";
    private static final String MSG_VARIANT_OTHER_NUM = "Connection Refused: DB-Cluster-77";

    @BeforeEach
    void setUp() {
        errorLogHostRepository.deleteAll();
        errorLogRepository.deleteAll();
        println("\n[System] 테스트 데이터 초기화 완료.");
    }

    @Test
    @DisplayName("saveLog 통합 검증: 신규 -> 동일 호스트 중복 -> 신규 호스트 확산 -> 10회 임계치 + error_code 저장")
    void verifySaveLogFlowWithErrorCode() {
        // STEP 1: 신규 발생 (Host-A)
        println("\n===== [STEP 1] 신규 인시던트 발생 (Host-A) =====");
        ErrorLogResponse r1 = save("Host-A", MSG_BASE, "ERROR", null);
        assertThat(r1).isNotNull();

        ErrorLog log1 = getSingleErrorLogForService();
        long hosts1 = errorLogHostRepository.countHostsByLogHash(log1.getLogHash());

        dumpState("STEP1", log1, hosts1);
        assertThat(log1.getRepeatCount()).isEqualTo(1);
        assertThat(hosts1).isEqualTo(1);

        // error_code 저장 확인 (최소한 null/blank 아니어야 함)
        assertThat(log1.getErrorCode())
                .as("신규 insert 시 error_code가 저장되어야 합니다.")
                .isNotBlank();

        // STEP 2: 동일 호스트 중복 발생(정규화로 동일 유형이어야 함)
        println("\n===== [STEP 2] 동일 호스트 중복 발생 (정규화 동일 유형) =====");
        ErrorLogResponse r2 = save("Host-A", MSG_VARIANT_APPEND_NUM, "ERROR", null);
        assertThat(r2).isNotNull();

        ErrorLog log2 = getSingleErrorLogForService();
        long hosts2 = errorLogHostRepository.countHostsByLogHash(log2.getLogHash());

        dumpState("STEP2", log2, hosts2);

        // logHash가 동일하고 repeatCount만 증가해야 함
        assertThat(log2.getLogHash()).isEqualTo(log1.getLogHash());
        assertThat(log2.getRepeatCount()).isEqualTo(2);
        assertThat(hosts2).isEqualTo(1);

        // STEP 3: 신규 호스트로 확산 (Host-B)
        println("\n===== [STEP 3] 신규 호스트 확산 (Host-B) =====");
        ErrorLogResponse r3 = save("Host-B", MSG_VARIANT_OTHER_NUM, "ERROR", null);
        assertThat(r3).isNotNull();

        ErrorLog log3 = getSingleErrorLogForService();
        long hosts3 = errorLogHostRepository.countHostsByLogHash(log3.getLogHash());

        dumpState("STEP3", log3, hosts3);

        assertThat(log3.getLogHash()).isEqualTo(log1.getLogHash());
        assertThat(log3.getRepeatCount()).isEqualTo(3);
        assertThat(hosts3).isEqualTo(2);

        // STEP 4: 10회 임계치 도달
        println("\n===== [STEP 4] 임계치 도달 (10회 누적) =====");
        for (int i = 4; i <= 10; i++) {
            save("Host-A", MSG_BASE, "ERROR", null);
            if (i == 5 || i == 10) {
                ErrorLog li = getSingleErrorLogForService();
                long hi = errorLogHostRepository.countHostsByLogHash(li.getLogHash());
                dumpState("STEP4-i=" + i, li, hi);
            }
        }

        ErrorLog log10 = getSingleErrorLogForService();
        long hosts10 = errorLogHostRepository.countHostsByLogHash(log10.getLogHash());

        dumpState("STEP4-FINAL", log10, hosts10);

        assertThat(log10.getRepeatCount()).isEqualTo(10);
        assertThat(hosts10).isEqualTo(2);

        // DB row count: 동일 유형이면 error_logs는 1행이어야 함
        assertThat(countServiceLogs()).isEqualTo(1);
    }

    private ErrorLogResponse save(String host, String message, String level, String stackTrace) {
        ErrorLogRequest dto = new ErrorLogRequest();
        dto.setServiceName(TEST_SERVICE);
        dto.setHostName(host);
        dto.setMessage(message);
        dto.setLogLevel(level);
        dto.setStackTrace(stackTrace);

        ErrorLogResponse r = errorLogCrdService.saveLog(dto);

        // saveLog 결과 플래그도 콘솔에 표시(찾기 쉽게)
        if (r != null) {
            println(String.format(
                    "[saveLog] isNew=%s, isNewHost=%s, repeat=%d, impactedHosts=%d, logHash=%s",
                    r.isNew(), r.isNewHost(), r.getRepeatCount(), r.getImpactedHostCount(), safe(r.getLogHash())
            ));
        } else {
            println("[saveLog] response=null (target level 아님 등)");
        }

        return r;
    }

    private ErrorLog getSingleErrorLogForService() {
        List<ErrorLog> logs = errorLogRepository.findAll().stream()
                .filter(l -> TEST_SERVICE.equals(l.getServiceName()))
                .toList();

        // 콘솔에서 바로 파악 가능하도록 size를 함께 출력
        println("[DB] error_logs rows for service=" + TEST_SERVICE + " -> " + logs.size());

        assertThat(logs)
                .as("정규화/해시 정책상, 동일 유형은 error_logs 테이블에 1행이어야 합니다.")
                .hasSize(1);

        return logs.get(0);
    }

    private long countServiceLogs() {
        return errorLogRepository.findAll().stream()
                .filter(l -> TEST_SERVICE.equals(l.getServiceName()))
                .count();
    }

    private void dumpState(String step, ErrorLog log, long impactedHosts) {
        println("---- [" + step + "] STATE DUMP ----");
        println("serviceName      : " + safe(log.getServiceName()));
        println("logHash          : " + safe(log.getLogHash()));
        println("repeatCount      : " + log.getRepeatCount());
        println("impactedHostCount: " + impactedHosts);
        println("errorCode        : " + safe(log.getErrorCode()));
        println("message(sample)  : " + abbreviate(log.getMessage(), 120));
        println("------------------------------");
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "null";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static String safe(String s) {
        return (s == null) ? "null" : s;
    }

    private static void println(String s) {
        System.out.println(s);
    }
}
