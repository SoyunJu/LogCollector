# Verification Scenarios (Baseline)

본 문서는 LogCollector 시스템의 **현재 코드 기준 동작**을 재현 가능하고 판정 가능한 형태로 고정하기 위한 검증 시나리오이다.

- **실행 환경**: docker-compose (local)
- **기준 원칙**
    - Incident가 상태 프로세스의 단일 SoT
    - ErrorLog에는 ACK 상태가 없음
    - RESOLVED 이후 CLOSE는 스케줄러가 처리
    - 동일 log_hash 재발생 시 RESOLVED/CLOSED → OPEN (reopen)

---

## 공통 전제

- Backend API: `http://localhost:8080`
- 테스트용 `log_hash`는 동일 payload 전송 시 자동 생성됨
- 아래 curl 예시는 shell 기준이며 `test.http`와 1:1 대응됨

---

## Scenario 1. 신규 로그 1회 → Incident OPEN 생성

### 목적
신규 로그 수집 시 Incident가 생성되고 OPEN 상태로 시작하는지 확인

### Step
```bash
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "test-service",
    "logLevel": "ERROR",
    "message": "Redis timeout occurred",
    "hostName": "host-1"
  }'
```

### Verify (DB)
```sql
SELECT
  status,
  repeat_count,
  first_occurred_at,
  last_occurred_at,
  resolved_at,
  closed_at
FROM incident
ORDER BY created_at DESC
LIMIT 1;
```

### Expect
- `status` = 'OPEN'
- `repeat_count` = 1
- `first_occurred_at` = `last_occurred_at`
- `resolved_at` IS NULL
- `closed_at` IS NULL

---

## Scenario 2. 동일 해시 반복 발생 → repeatCount / lastOccurred 갱신

### 목적
동일 `log_hash` 로그 재발생 시 Incident 누적 갱신 확인

### Step
```bash
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "test-service",
    "logLevel": "ERROR",
    "message": "Redis timeout occurred",
    "hostName": "host-2"
  }'
```

### Verify (DB)
```sql
SELECT
  status,
  repeat_count,
  last_occurred_at
FROM incident
WHERE service_name = 'test-service'
ORDER BY updated_at DESC
LIMIT 1;
```

### Expect
- `status` = 'OPEN'
- `repeat_count` >= 2
- `last_occurred_at` 증가

---

## Scenario 3. RESOLVED 처리 → Incident RESOLVED + Draft 생성 + closeEligible 세팅

### 목적
Incident RESOLVED 전이 시 후속 액션 검증

### Step
```bash
# {logHash}는 실제 생성된 해시값으로 대체
curl -X PATCH http://localhost:8080/api/incidents/{logHash}/status?newStatus=RESOLVED
```

### Verify 1 (Incident)
```sql
SELECT
  status,
  resolved_at,
  close_eligible_at,
  closed_at
FROM incident
WHERE log_hash = '{logHash}';
```

### Verify 2 (Draft)
```sql
SELECT
  status,
  incident_id
FROM kb_article
WHERE incident_id = (
  SELECT id FROM incident WHERE log_hash = '{logHash}'
);
```

### Expect
- `incident.status` = 'RESOLVED'
- `resolved_at` IS NOT NULL
- `close_eligible_at` IS NOT NULL
- `closed_at` IS NULL
- `kb_article.status` = 'DRAFT' (이미 존재 시 생성 스킵)

---

## Scenario 4. RESOLVED 이후 재발생 → REOPEN (OPEN) + 필드 리셋

### 목적
해결된 Incident에서 동일 해시 로그 재발생 시 reopen 로직 검증

### Step
```bash
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "test-service",
    "logLevel": "ERROR",
    "message": "Redis timeout occurred",
    "hostName": "host-3"
  }'
```

### Verify (DB)
```sql
SELECT
  status,
  repeat_count,
  reopened_at,
  resolved_at,
  close_eligible_at,
  closed_at
FROM incident
WHERE log_hash = '{logHash}';
```

### Expect
- `status` = 'OPEN'
- `reopened_at` IS NOT NULL
- `resolved_at` IS NULL
- `close_eligible_at` IS NULL
- `closed_at` IS NULL
- `repeat_count` 증가

---

## Scenario 5. Scheduler에 의한 CLOSE 처리 (선택)

### 목적
closeEligibleAt 조건 충족 시 자동 CLOSED 전환 검증

### Precondition
- `close.delay.minutes` = 1~2
- Incident 상태 = RESOLVED

### Step
```bash
sleep 120
```

### Verify (DB)
```sql
SELECT
  status,
  closed_at
FROM incident
WHERE log_hash = '{logHash}';
```

### Expect
- `status` = 'CLOSED'
- `closed_at` IS NOT NULL

---

## 판정 기준 요약

| Scenario | 판정 포인트 |
| :--- | :--- |
| 1 | 신규 Incident OPEN 생성 |
| 2 | repeatCount / lastOccurred 누적 |
| 3 | RESOLVED + Draft + closeEligible |
| 4 | 재발생 시 OPEN 복귀 |
| 5 | Scheduler에 의한 CLOSED |

본 문서는 이후 모든 리팩토링 및 정책 변경 시 **회귀 테스트 기준선**으로 사용한다.