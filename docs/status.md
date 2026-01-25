# Status Specification (LC ↔ Incident ↔ KB)

본 문서는 LogCollector(LC) DB와 KnowledgeBase(KB) DB가 분리된 구조에서,
각 도메인의 상태(Status) 정의/전이(Transition)/동기화(Sync)/스케줄 정책을 **단일 기준**으로 명세한다.

- **LC DB**: `error_logs`, `error_log_hosts` (로그 수집/정규화/해싱 중심. 현재는 검증용 View 포함)
- **KB DB**: `incident`, `kb_article`, `kb_addendum`
- **Incident가 단일 SoT(Source of Truth)** 이며, “확인/진행/해결/종료” 프로세스 상태는 Incident가 가진다.
- DB 컬럼은 `VARCHAR(32)`로 저장하고, 허용 값/전이 규칙은 애플리케이션 계층에서 통제한다.

---

## A. Status 정의 (의미 1줄)

### 1) ErrorStatus (LC / `error_logs.status`) 
> 확인/진행은 Incident에서만 관리한다.

| Status | 의미 |
|---|---|
| `NEW` | 에러 로그 발생(초기 상태) |
| `RESOLVED` | 해결 처리됨(LC 관점 최종 처리) |
| `IGNORED` | 노이즈로 무시 |

---

### 2) IncidentStatus (KB / `incident.status`) **(SoT)**
| Status | 의미 |
|---|---|
| `OPEN` | 인시던트 오픈 |
| `IN_PROGRESS` | 조치/분석 진행 |
| `RESOLVED` | 기술적으로 해결 |
| `CLOSED` | 종료(안정 구간/정리 완료 후 최종 종료) |
| `IGNORED` | 무시 |

---

### 3) KbStatus (KB / `kb_article.status`)
| Status | 의미 |
|---|---|
| `DRAFT` | 초안 |
| `IN_PROGRESS` | 작성/수정 진행 |
| `PUBLISHED` | 게시 |
| `ARCHIVED` | 안전한 삭제 처리(불가역) |

KB는 “새 버전 Draft 생성”을 하지 않는다.  
후속 조치/해결 기록은 `kb_addendum`로 누적한다.

---

## B. 전이(Transition) 규칙

### 1) ErrorStatus 전이 (LC)
- `NEW -> RESOLVED | IGNORED`

> **ACK 관련 전이는 사용하지 않는다.**  
> 확인/진행은 Incident에서만 처리한다.

#### IGNORED 관련 정책(현재/예정)
- 현재 IGNORED는 “재수집하지 않음”을 기대값으로 한다.
- 따라서 IGNORED 상태의 로그는 재발생 이벤트로 인해 자동 reopen 되지 않는다(수정/정비 예정).

---

### 2) IncidentStatus 전이 (KB / SoT)

#### 일반 전이
- `OPEN -> IN_PROGRESS | IGNORED`
- `IN_PROGRESS -> RESOLVED | IGNORED`
- `RESOLVED -> CLOSED`

#### 재발생(Recurrence) 전이 (정책 이벤트)
- 재발생 시: `RESOLVED -> OPEN`
- 재발생 시: `CLOSED -> OPEN`

> 구현 근거: `IncidentRepository.upsertIncident(...)`의 ON DUPLICATE KEY UPDATE에서  
> status가 `RESOLVED` 또는 `CLOSED`이면 `OPEN`으로 되돌리고,
> `resolved_at/close_eligible_at/closed_at`를 NULL로 리셋하며,
> `reopened_at`를 갱신한다.

---

### 3) KbStatus 전이 (KB Article)
- `DRAFT -> IN_PROGRESS -> PUBLISHED -> ARCHIVED`
- `ARCHIVED`는 불가역 상태

---

## C. 도메인 간 동기화 규칙 (SoT = Incident)

### 원칙
- “상태 프로세스”는 Incident가 단일 SoT이다.
- LC의 ErrorStatus는 최소한의 상태만 유지하며, 업무 프로세스 상태(확인/진행)는 갖지 않는다.

### Incident → ErrorStatus (권장 동기화)
Incident 상태가 변경될 때 LC 상태를 동기화할 수 있다(구현 정책에 따름).

| IncidentStatus | ErrorStatus |
|---|---|
| `OPEN` | `NEW` |
| `IN_PROGRESS` | `NEW` *(LC에는 진행 상태가 없으므로 NEW 유지)* |
| `RESOLVED` | `RESOLVED` |
| `CLOSED` | `RESOLVED` |
| `IGNORED` | `IGNORED` |

> NOTE: 위 동기화는 “원칙”이며, 실제 구현은 Outbox 기반(예: `LcIgnoreOutbox`) 또는 별도 브리지로 수행한다.

---

## D. Draft 생성 정책 (KbDraftService / DraftPolicyService)

KB는 `incident` ↔ `kb_article` **1:1** 구조이며, `log_hash`가 유니크 기준이다.

Draft 생성 트리거는 2개가 **병존**한다.

### 1) Incident RESOLVED 시 Draft 생성
- Incident 상태가 `RESOLVED`로 변경될 때 시스템 Draft 생성 시도
- 단, 이미 Draft가 존재하면 **스킵**(idempotent)

### 2) Auto Draft Policy (사전 Draft)
- 조건에 따라 자동 Draft 생성(예: host spread, high recur)
- 정책 값은 설정으로 관리한다.

예시(`application.properties`):
- `draft.policy.host-spread-threshold=3`
- `draft.policy.high-recur-threshold=10`

---

## E. CLOSE 처리 (Scheduler 기반)

CLOSE 처리는 스케줄러가 서비스 호출을 통해 수행한다. (주기 자체는 문서에 기재하지 않음)

### 1) RESOLVED 시 closeEligibleAt 설정
Incident가 `RESOLVED`가 될 때:
- `resolvedAt`이 없으면 현재 시각으로 세팅
- `closeEligibleAt = resolvedAt + 2 hours` 세팅

### 2) Close 후보 선정
Close 후보는 다음 조건을 만족하는 Incident이다.

- `i.status = :status` (일반적으로 `RESOLVED`)
- `i.closeEligibleAt IS NOT NULL`
- `i.closeEligibleAt <= :threshold`
- `i.closedAt IS NULL`

> 후보 조회 구현: `findCloseCandidates(status, threshold)`

---

## F. 구현 강제 규칙

1. Incident는 상태 프로세스의 단일 SoT이며, LC는 “수집/정규화/해싱” 중심으로 분리한다.
2. ErrorStatus에서 ACKNOWLEDGED는 제거(또는 미사용)하며, 확인/진행은 Incident에서만 표현한다.
3. 재발생은 예외 전이로 취급하며, `RESOLVED/CLOSED -> OPEN`을 허용한다.
4. KB는 단일 Article 구조로 유지하며, 추가 조치는 Addendum로 누적한다.
5. Draft 생성 트리거는 “RESOLVED 시”와 “Auto Policy”가 병존하며, Draft 존재 시 스킵한다.
6. CLOSE 처리는 스케줄러 기반이며, `closeEligibleAt` 조건을 충족하는 RESOLVED Incident를 CLOSED로 전이한다.

---
