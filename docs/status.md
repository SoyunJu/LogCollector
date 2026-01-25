# Status Specification (LC ↔ Incident ↔ KB)

본 문서는 LogCollector(LC) DB와 KnowledgeBase(KB) DB가 분리된 구조에서,
각 도메인의 상태(Status) 정의/전이(Transition)/동기화(Sync)/스케줄 정책을 **단일 기준**으로 명세한다.

- **LC DB**: `error_logs`, `error_log_hosts` (수집/정규화/해싱 중심)
- **KB DB**: `incident`, `kb_article`, `kb_addendum`
- **Incident가 단일 SoT(Source of Truth)** 이며, “확인/진행/해결/종료” 프로세스 상태는 Incident가 가진다.
- DB 컬럼은 `VARCHAR(32)`로 저장하고, 허용 값/전이 규칙은 애플리케이션 계층에서 통제한다.

---

## A. Status 정의 (의미 1줄)

### 1) ErrorStatus (LC / `error_logs.status`)
> 확인/진행은 Incident에서만 관리한다. ErrorLog에는 ACK 상태가 없다.

| Status | 의미 |
|---|---|
| `NEW` | 에러 로그 발생(초기 상태) |
| `RESOLVED` | 해결 처리됨(LC 관점 최종 처리) |
| `IGNORED` | 수집/저장 대상에서 제외(차단) |

---

### 2) IncidentStatus (KB / `incident.status`) **(SoT)**

| Status | 의미 |
|---|---|
| `OPEN` | 인시던트 오픈 |
| `IN_PROGRESS` | 조치/분석 진행 |
| `RESOLVED` | 기술적으로 해결 |
| `CLOSED` | 종료(안정 구간/정리 완료 후 최종 종료) |
| `IGNORED` | 무시(수집/저장 차단을 의미) |

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

- `IGNORED`는 **수집/저장 차단**을 의미한다.
- IGNORED 상태에서는 동일 로그가 재발생해도 **LC 저장/KB upsert가 발생하지 않는다.**
- UNIGNORE(해제) 후에만 다시 수집/저장이 가능하다.

---

### 2) IncidentStatus 전이 (KB / SoT)

#### 일반 전이
- `OPEN -> IN_PROGRESS | IGNORED`
- `IN_PROGRESS -> RESOLVED | IGNORED`
- `RESOLVED -> CLOSED`

#### 재발생(Recurrence) 전이
- 재발생 시: `RESOLVED -> OPEN`
- 재발생 시: `CLOSED -> OPEN`

> 단, **IGNORED 상태에서는 재발생 이벤트가 들어오지 않도록(수집 차단)** 하므로,
> IGNORED -> OPEN 재발생 전이는 발생하지 않는다.
> IGNORED 해제(UNIGNORE) 이후 다음 발생부터 OPEN으로 다시 생성/갱신된다.

---

### 3) KbStatus 전이 (KB Article)
- `DRAFT -> IN_PROGRESS -> PUBLISHED -> ARCHIVED`
- `ARCHIVED`는 불가역 상태

---

## C. 도메인 간 동기화 규칙 (SoT = Incident)

### 원칙
- 상태 프로세스는 Incident가 단일 SoT이다.
- LC ErrorStatus는 최소 상태만 유지하며, 진행/확인은 갖지 않는다.

### Incident → ErrorStatus (동기화)
Incident 상태 변경 시 LC 상태를 동기화할 수 있다(구현 정책에 따름).

| IncidentStatus | ErrorStatus |
|---|---|
| `OPEN` | `NEW` |
| `IN_PROGRESS` | `NEW` *(LC에 진행 상태 없음)* |
| `RESOLVED` | `RESOLVED` |
| `CLOSED` | `RESOLVED` |
| `IGNORED` | `IGNORED` |

---

## D. Draft 생성 정책

KB는 `incident` ↔ `kb_article` **1:1** 구조이며, `log_hash`가 유니크 기준이다.

Draft 생성 트리거(병존):
1) Incident가 `RESOLVED`로 변경될 때 Draft 생성 시도(존재하면 스킵)
2) Auto Draft Policy(예: host spread, high recur)

---

## E. CLOSE 처리 (Scheduler 기반)

### 1) RESOLVED 시 closeEligibleAt 설정
Incident가 `RESOLVED`가 될 때:
- `resolvedAt`이 없으면 현재 시각으로 세팅
- `closeEligibleAt = resolvedAt + N` 세팅

### 2) Close 후보 선정(단일 조건)
Close 후보:
- `status = 'RESOLVED'`
- `close_eligible_at IS NOT NULL`
- `close_eligible_at <= now`
- `closed_at IS NULL`

스케줄러는 “후보 조회 + CLOSED 전이”만 담당한다.

---

## F. 구현 강제 규칙

1. Incident는 상태 프로세스의 단일 SoT이다.
2. ErrorStatus에는 ACKNOWLEDGED가 없다.
3. 재발생은 예외 전이로 `RESOLVED/CLOSED -> OPEN`을 허용한다.
4. Draft 생성 트리거는 “RESOLVED 시”와 “Auto Policy”가 병존하며, Draft 존재 시 스킵한다.
5. CLOSE는 `closeEligibleAt` 조건을 만족하는 RESOLVED Incident를 CLOSED로 전이한다.
6. **IGNORE는 수집/저장 차단**을 의미하며, 해제(UNIGNORE) 전에는 동일 로그가 들어와도 저장/업서트되지 않는다.
