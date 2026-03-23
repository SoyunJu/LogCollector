# LogCollector 테스트 & 성능 검증 정리

## 목차
1. [개요](#1-개요)
2. [테스트 인프라](#2-테스트-인프라)
3. [PerformanceAndBehaviorTest](#3-performanceandbehaviortest)
4. [LogFixerIntegrationTest](#4-logfixerintegrationtest)
5. [성능 SLO 기준](#5-성능-slo-기준)
6. [실행 방법](#6-실행-방법)

---

## 1. 개요

| 항목 | 내용 |
|---|---|
| 테스트 파일 수 | 2개 |
| 총 테스트 케이스 | 29개 |
| 테스트 분류 | 정규화 · 기본동작 · 성능 · KB연동 · LogFixer연동 · 내결함성 |
| 성능 SLO 적용 | 4개 항목 |
| 실행 조건 | `RUN_INTEGRATION_TEST=true` 환경변수 필요 |

```
src/test/java/com/soyunju/logcollector/perf/
├── PerformanceAndBehaviorTest.java   # 16개 (정규화 · 기본동작 · 성능 · KB연동)
└── LogFixerIntegrationTest.java      # 13개 (LogFixer 연동 · 내결함성)
```

---

## 2. 테스트 인프라

두 테스트 클래스 모두 **Testcontainers** 기반으로 실제 서비스 환경을 재현한다.

| 컨테이너 | 이미지 | 용도 |
|---|---|---|
| MariaDB | `mariadb:11.4` | LC/KB 양쪽 데이터소스 (동일 인스턴스) |
| Redis | `redis:7-alpine` | 로그 큐 (`error-log-queue`) |

**Mock 처리 (실제 외부 연동 제외)**

| Bean | 처리 방식 | 이유 |
|---|---|---|
| `KbArticleEsRepository` | `@MockBean` | Elasticsearch 미기동 |
| `KbArticleEsService` | `@MockBean` | Elasticsearch 미기동 |
| `LcIgnoreOutboxProcessorService` | `@MockBean` | 스케줄러 간섭 방지 |
| `LogFixerWebhookService` | `@MockBean` (LogFixer 테스트만) | ArgumentCaptor로 캡처 |
| `KbEventOutboxProcessorService` | **실제 Bean** (LogFixer 테스트) | Outbox 재처리 직접 검증 |

---

## 3. PerformanceAndBehaviorTest

> 파일: `PerformanceAndBehaviorTest.java` | 총 **16개** 테스트

### Block 1 — 정규화 정확성 (2개)

| ID | 테스트명 | 검증 내용 |
|---|---|---|
| T1 | UUID/IP/타임스탬프 제거 후 동일 해시 | 가변 값 제거 후 `hash1 == hash2` |
| T2 | 다른 메시지 → 다른 해시 | `hash1 != hash2` |

### Block 2 — 기본 동작 (6개)

| ID | 테스트명 | 검증 내용 |
|---|---|---|
| T3 | 신규 로그 | `isNew=true`, `repeatCount=1` |
| T4 | 동일 로그 3회 반복 | `repeatCount=3`, `isNew=false` |
| T5 | RESOLVED → 재발 | 상태 `NEW` 복귀 |
| T6 | 다른 호스트 감지 | `isNewHost=true`, `impactedHostCount ≥ 2` |
| T7 | IGNORED 로그 재수집 | 상태 `IGNORED` 유지 |
| T8 | INFO 레벨 로그 거부 | `IllegalArgumentException` 발생 |

### Block 3 — 성능 처리량 (4개)

| ID | 테스트명 | 규모 | 단언 조건 |
|---|---|---|---|
| T9 | 처리속도: 서로 다른 로그 100건 직접 저장 | 100건 | `logsPerSec > 0` |
| T10 | 중복 처리속도: 동일 로그 100건 | 100건 | `logsPerSec > 0` |
| T11 | 최대 그룹화: 동일 로그 500건 → Incident 1개 | 500건 | `repeatCount == 500` |
| T12 | Redis 파이프라인: Push → Poll → DB | 50건 | `savedCount ≥ 45` (오차 허용) |

> **참고**: T9·T10·T12는 처리량(`건/초`)을 리포트에 출력하며, 환경별 편차가 크기 때문에
> 하드 기준 없이 `> 0` 만 단언하고 실측값을 추이 관찰용으로 기록한다.

### Block 4 — KB/Incident 연동 (4개)

| ID | 테스트명 | 검증 내용 |
|---|---|---|
| T13 | Incident 자동 생성 (비동기) | `saveLog()` 후 최대 5초 내 Incident `OPEN` |
| T14 | Incident repeatCount 추적 | 3회 저장 후 `repeatCount ≥ 3` |
| T15 | Draft 트리거: 3 호스트 확산 | 3개 호스트 → Incident 생성 확인 |
| T16 | Draft 트리거: 10회 반복 | `repeatCount ≥ 10` |

---

## 4. LogFixerIntegrationTest

> 파일: `LogFixerIntegrationTest.java` | 총 **13개** 테스트
> `@AutoConfigureMockMvc` 추가 — MockMvc로 콜백 엔드포인트 직접 호출

### Block 1 — LC → LogFixer (Webhook 발송, 5개)

`LogFixerWebhookService`를 `@MockBean`으로 선언하고 `ArgumentCaptor`로 호출 인수를 캡처한다.
비동기 처리(KbEventListener → kbEventExecutor 스레드)는 **Awaitility** (timeout 5s)로 대기.

| ID | 테스트명 | 검증 내용 | SLO |
|---|---|---|---|
| T1 | webhook `sendIncident()` 호출 확인 | 호출 여부 + 비동기 지연 측정 | **≤ 2000ms** |
| T2 | payload 필드 검증 | `logHash` / `serviceName` / `repeatCount` / `impactedHostCount` / `logLevel` | — |
| T3 | 반복 발생 시 `repeatCount` 증가 | 4회 저장 후 `repeatCount == 4` | — |
| T4 | 호스트 확산 시 `impactedHostCount` 증가 | 3개 호스트 후 `impactedHostCount ≥ 3` | — |
| T5 | webhook 실패해도 로그 저장 정상 | `RuntimeException` 던져도 `saveLog()` 성공 | — |

### Block 2 — LogFixer → LC (REST 콜백 수신, 5개)

MockMvc로 LogFixer가 호출하는 LC 엔드포인트를 직접 테스트한다.

| ID | 엔드포인트 | 테스트 내용 | SLO |
|---|---|---|---|
| T6 | `PATCH /api/incidents/{hash}/status?newStatus=RESOLVED` | Incident 상태 `RESOLVED` 전환 + `resolvedAt` 기록 | **≤ 500ms** |
| T7 | 위 엔드포인트 + 재발 시나리오 | `RESOLVED` → 동일 에러 재발 → `OPEN` 자동 복귀 | — |
| T8 | `POST /api/kb/{id}/addendums` | LogFixer 분석 결과 Addendum 저장 확인 | **≤ 500ms** |
| T9 | `GET /api/kb/articles/byhash/{hash}` | logHash로 KbArticleId 조회 (RAG 컨텍스트) | **≤ 200ms** |
| T10 | `GET /api/kb/articles/byhash/{unknown}` | 존재하지 않는 logHash → `404` | — |

### Block 3 — 내결함성: KbEventOutbox 재처리 (3개)

Outbox를 직접 DB에 적재한 뒤 `KbEventOutboxProcessorService.process()`를 수동 호출하여 검증.

| ID | 테스트명 | 검증 내용 |
|---|---|---|
| T11 | 정상 재처리 | PENDING → Incident 생성 완료 + Outbox `SUCCESS` |
| T12 | 최대 재시도 초과 | `attemptCount=4` + 잘못된 payload → `FAILED` |
| T13 | 재처리 실패 중간 상태 | `attemptCount=1` + 잘못된 payload → `PENDING` 유지 + `nextRetryAt` 설정 + `attemptCount=2` |

---

## 5. 성능 SLO 기준

SLO(Service Level Objective)가 적용된 항목은 `assertThat(실측값).isLessThanOrEqualTo(SLO)` 로 단언한다.
기준 초과 시 테스트가 **실패**한다.

| 측정 지점 | SLO 기준 | 적용 테스트 | 비고 |
|---|---|---|---|
| `saveLog()` → `sendIncident()` 비동기 호출 | **2000ms** | T1 | Testcontainers 환경 기준 |
| `PATCH /api/incidents/{hash}/status` 응답 | **500ms** | T6 | MockMvc 내부 호출 |
| `POST /api/kb/{id}/addendums` 응답 | **500ms** | T8 | MockMvc 내부 호출 |
| `GET /api/kb/articles/byhash/{hash}` 응답 | **200ms** | T9 | MockMvc 내부 호출 |

### 리포트 출력 형식

테스트 실행 후 콘솔에 다음과 같은 SLO 테이블이 출력된다.

```
╠══════════════════════════════════════════════════════════════════════════════════╣
║ [성능 SLO 검증]                         실측값       SLO 기준    판정             ║
╠══════════════════════════════════════════════════════════════════════════════════╣
║  Webhook 비동기 발송 (saveLog→sendIncident)    312ms  ≤  2000ms   ✓ PASS  ║
║  RESOLVED 콜백 응답 (PATCH /status)             48ms  ≤   500ms   ✓ PASS  ║
║  Addendum POST 응답 (/addendums)                63ms  ≤   500ms   ✓ PASS  ║
║  KB 조회 응답       (GET /byhash)               21ms  ≤   200ms   ✓ PASS  ║
╠══════════════════════════════════════════════════════════════════════════════════╣
```

---

## 6. 실행 방법

### 사전 조건

- Docker 실행 중 (Testcontainers가 MariaDB · Redis 컨테이너를 자동 시작)
- Java 21+, Gradle

### 전체 통합 테스트 실행

```bash
export RUN_INTEGRATION_TEST=true
./gradlew test
```

### 파일별 실행

```bash
# PerformanceAndBehaviorTest 단독
export RUN_INTEGRATION_TEST=true
./gradlew test --tests "com.soyunju.logcollector.perf.PerformanceAndBehaviorTest"

# LogFixerIntegrationTest 단독
export RUN_INTEGRATION_TEST=true
./gradlew test --tests "com.soyunju.logcollector.perf.LogFixerIntegrationTest"
```

### 특정 테스트 케이스만

```bash
export RUN_INTEGRATION_TEST=true
./gradlew test --tests "com.soyunju.logcollector.perf.LogFixerIntegrationTest.t6_resolvedCallbackChangesIncidentStatus"
```

### 환경변수 없이 실행 시

`@EnabledIfEnvironmentVariable(named = "RUN_INTEGRATION_TEST", matches = "true")` 조건에 의해
두 테스트 클래스 모두 **자동으로 Skip** 된다. 일반 빌드(`./gradlew build`)에 영향 없음.
