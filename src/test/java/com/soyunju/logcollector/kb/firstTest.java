package com.soyunju.logcollector.kb;

import com.soyunju.logcollector.domain.kb.Incident;
import com.soyunju.logcollector.domain.kb.enums.IncidentStatus;
import com.soyunju.logcollector.repository.kb.IncidentRepository;
import com.soyunju.logcollector.service.kb.crud.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
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
@ActiveProfiles({"dev", "local"})
@Transactional
public class firstTest {

    @Autowired
    private IncidentService incidentService;
    @Autowired
    private IncidentRepository incidentRepository;

    @BeforeEach
    void clean() {
        incidentRepository.deleteAll();
    }

    @Test
    @DisplayName("Incident upsert: 동일 log_hash면 1건 유지 + repeatCount 증가 + lastOccurredAt 갱신")
    void upsert_repeatCount_and_lastOccurredAt() {
        String logHash = "test-hash-1";
        LocalDateTime t1 = LocalDateTime.now().minusMinutes(5);
        LocalDateTime t2 = LocalDateTime.now();

        incidentService.recordOccurrence(
                logHash, "Test-App",
                "first summary", null,
                "E001", "ERROR", t1
        );

        incidentService.recordOccurrence(
                logHash, "Test-App",
                "second summary", null,
                "E002", "ERROR", t2
        );

        Incident incident = incidentRepository.findByLogHash(logHash).orElseThrow();

        // 콘솔 출력 추가
        System.out.println("\n===== [TEST 1] Incident Upsert 검증 =====");
        printVerification("Total Count", 1L, incidentRepository.count());
        printVerification("Repeat Count", 2, incident.getRepeatCount());
        printVerification("First Occurred At", t1, incident.getFirstOccurredAt());
        printVerification("Last Occurred At", t2, incident.getLastOccurredAt());
        printVerification("Summary (Keep First)", "first summary", incident.getSummary());
        printVerification("Error Code (Keep First)", "E001", incident.getErrorCode());

        assertThat(incidentRepository.count()).isEqualTo(1);
        assertThat(incident.getRepeatCount()).isEqualTo(2);
        assertThat(incident.getFirstOccurredAt()).isEqualTo(t1);
        assertThat(incident.getLastOccurredAt()).isEqualTo(t2);

        // 정책: 비어있을 때만 채움 -> 첫 값 유지
        assertThat(incident.getSummary()).isEqualTo("first summary");
        assertThat(incident.getErrorCode()).isEqualTo("E001");
    }

    @Test
    @DisplayName("빈 값일 때만 summary/stackTrace/errorCode가 채워진다")
    void fill_only_when_blank() {
        String logHash = "test-hash-2";

        incidentService.recordOccurrence(
                logHash, "Test-App",
                null, null,
                null, "ERROR", LocalDateTime.now()
        );

        incidentService.recordOccurrence(
                logHash, "Test-App",
                "summary later", "stack later",
                "E999", "ERROR", LocalDateTime.now()
        );

        Incident incident = incidentRepository.findByLogHash(logHash).orElseThrow();

        // 콘솔 출력 추가
        System.out.println("\n===== [TEST 2] Blank Field 채우기 검증 =====");
        printVerification("Summary", "summary later", incident.getSummary());
        printVerification("Stack Trace", "stack later", incident.getStackTrace());
        printVerification("Error Code", "E999", incident.getErrorCode());

        assertThat(incident.getSummary()).isEqualTo("summary later");
        assertThat(incident.getStackTrace()).isEqualTo("stack later");
        assertThat(incident.getErrorCode()).isEqualTo("E999");
    }

    @Test
    @DisplayName("RESOLVED 이후 동일 log_hash 재발 시 OPEN으로 재오픈된다")
    void reopen_after_resolved() {
        String logHash = "test-hash-3";

        incidentService.recordOccurrence(
                logHash, "Test-App",
                "summary", null,
                "E001", "ERROR", LocalDateTime.now()
        );

        incidentService.markResolved(logHash, LocalDateTime.now());

        Incident resolved = incidentRepository.findByLogHash(logHash).orElseThrow();

        System.out.println("\n===== [TEST 3-1] RESOLVED 상태 확인 =====");
        printVerification("Status", IncidentStatus.RESOLVED, resolved.getStatus());

        assertThat(resolved.getStatus()).isEqualTo(IncidentStatus.RESOLVED);

        // 재발
        incidentService.recordOccurrence(
                logHash, "Test-App",
                "new summary", null,
                "E002", "ERROR", LocalDateTime.now()
        );

        Incident reopened = incidentRepository.findByLogHash(logHash).orElseThrow();

        System.out.println("\n===== [TEST 3-2] 재발 시 상태 확인 =====");
        printVerification("Status (Reopened)", IncidentStatus.OPEN, reopened.getStatus());
        printVerification("Resolved At (Should be null)", null, reopened.getResolvedAt());
        printVerification("Repeat Count", 2, reopened.getRepeatCount());

        assertThat(reopened.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(reopened.getResolvedAt()).isNull();
        assertThat(reopened.getRepeatCount()).isEqualTo(2);
    }

    /**
     * 기대값과 실제값을 예쁘게 출력해주는 헬퍼 메서드
     */
    private void printVerification(String fieldName, Object expected, Object actual) {
        System.out.printf("[%-25s] -> Expected: %-15s | Actual: %s%n", fieldName, expected, actual);
    }
}
