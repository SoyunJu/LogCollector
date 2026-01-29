# Run Local (Backend Only)

> 이 문서는 **로컬 개발/검증 목적**으로 LogCollector **백엔드만** 실행하는 방법을 설명합니다.
>
> ⚠️ **중요**
>
> * `run-local` 환경에서는 **React 프론트엔드(UI)를 제공하지 않습니다.**
> * UI 확인은 `run-docker` 또는 `run-k8s` 환경에서만 가능합니다.
> * 로컬 환경에서는 **API / Actuator / curl 기반 검증**만 수행합니다.

---

## 0. 사전 조건 (Prerequisites)

* JDK 17+
* Gradle Wrapper (`./gradlew`)
* Docker Desktop (MariaDB, Redis 사용)
* 포트 사용 가능 여부

  * Backend: `8082`
  * MariaDB: `3306`
  * Redis: `6379`

---

## 1. 인프라 실행 (DB / Redis)

로컬 백엔드는 **외부 인프라만 Docker로 실행**합니다.

```bash
# 인프라 기동 (DB, Redis, Grafana, Prometheus)
docker compose -f infra/compose/compose.dev.yaml up -d

# 상태 확인
docker compose -f infra/compose/compose.dev.yaml ps
```

### 1-1. MariaDB 초기 상태 확인

```bash
docker exec -it logcollector-mariadb mariadb -uroot -proot -e "SHOW DATABASES;"
```

정상 예시:

```
information_schema
logcollector
knowledge_base
mysql
performance_schema
sys
```

---

## 2. Backend 실행 (local profile)

```bash
./gradlew bootRun --args="--spring.profiles.active=local"
```

정상 기동 시 확인 포인트:

* 포트: `8082`
* 로그에 다음 메시지 포함

```
Started LogCollectorApplication
Tomcat initialized with port 8082
```

---

## 3. Health Check

```bash
curl http://localhost:8082/actuator/health
```

정상 예시:

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

---

## 4. API 검증 (curl 기반)

### 4-1. 로그 수집 API

```bash
curl -X POST http://localhost:8082/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "TEST-SVC",
    "hostName": "pc-verify",
    "message": "RedisCommandTimeoutException",
    "stackTrace": "java.lang.RuntimeException: test",
    "logLevel": "ERROR"
  }'
```

정상 응답:

* HTTP `202 Accepted`
* Redis enqueue 로그 출력

---

### 4-2. Incident 생성 여부 확인

```bash
curl http://localhost:8082/api/incidents
```

---

## 5. 로컬 환경 제한 사항

* ❌ React UI 미제공 (`/` 접근 시 legacy static html 또는 비활성)
* ❌ Slack 알림 비활성 (설정 없음 시 에러 로그 발생 가능)
* ❌ AI 실제 호출 없음

  * `MockAiAnalysisService` 사용

---

## 6. 언제 run-local 을 사용하는가

* 빠른 백엔드 로직 검증
* JPA / Redis / Scheduler 동작 확인
* API 시나리오 테스트
* CI 실패 원인 로컬 재현

UI / 전체 흐름 검증은 반드시 아래 문서를 참고:

* `run-docker.md`
* `run-k8s.md`

---

## 7. 트러블슈팅

### 7-1. DB 권한 오류

```
Access denied for user 'kb_root'
```

→ `compose.dev.yaml` 초기화 볼륨 제거 후 재기동

```bash
docker compose -f infra/compose/compose.dev.yaml down -v
docker compose -f infra/compose/compose.dev.yaml up -d
```

---

### 7-2. AI Bean 주입 오류

```
No qualifying bean of type 'AiAnalysisService'
```

→ `MockAiAnalysisService`에 `@Profile("local")` 포함 필요

---

## 8. 요약

| 항목         | run-local  |
| ---------- | ---------- |
| Backend    | O          |
| DB / Redis | O (Docker) |
| React UI   | ❌          |
| curl 검증    | O          |
| 포트         | 8082       |

이 문서는 **검증자 기준으로 가장 빠른 재현 환경**을 제공하는 것이 목적입니다.
