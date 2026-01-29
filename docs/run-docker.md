# LogCollector Docker 실행 가이드 (run-docker)

## 0. 전제
- Docker Desktop 설치 및 실행 중
- 프로젝트 루트 경로에서 명령 실행
- 본 문서는 개발/검증 목적 기준으로 작성됨
- 프론트엔드는 검증용이며 필수 요소 아님

---

## 1. Prerequisites
- Docker 24+
- Docker Compose v2
- 메모리 8GB 이상 권장

---

## 2. 전체 시스템 실행 (Full Stack)

```bash
docker compose -f infra/compose/compose.yaml up -d --build
```

### 2-1) 실행 상태 확인

```bash
docker compose -f infra/compose/compose.yaml ps
```

### 2-2) Backend Health Check

```bash
curl http://localhost:8080/actuator/health
```

---

## 3. 접속 정보

| 대상 | 주소 |
|---|---|
| Frontend | http://localhost |
| Backend API | http://localhost:8080 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

참고:
- Docker 환경에서 API Base URL은 다음을 사용합니다.
  - http://localhost:8080/api

---

## 4. Quick Demo (Visual Verification)

1) 브라우저에서 다음 주소로 접속합니다.
- http://localhost

2) Logs / Incidents / KB 화면을 확인합니다.

3) Grafana에서 지표를 확인합니다.
- http://localhost:3000
- ID/PW: admin / admin

---

## 5. CLI Verification (Docker 환경 증빙)

### 5-1) Shell Script (권장)

```bash
./test-api.sh
```

환경 변수로 직접 지정하는 경우:

```bash
BASE_URL="http://localhost:8080/api" ./test-api.sh
```

### 5-2) PowerShell

```powershell
.\test-api.ps1
```

### 5-3) IntelliJ HTTP Client
- 파일: api-test.http
- 실행 순서:
  1) POST /api/logs
  2) GET /api/incidents

---

## 6. 단건 로그 테스트 (curl)

```bash
curl -X POST http://localhost:8080/api/logs \
  -H "Content-Type: application/json" \
  -d '{
    "serviceName": "test-service",
    "logLevel": "ERROR",
    "message": "Redis timeout occurred",
    "hostName": "local-test"
  }'
```

---

## 7. 종료 및 초기화

컨테이너 종료:

```bash
docker compose -f infra/compose/compose.yaml down
```

볼륨 포함 전체 초기화(데이터 삭제):

```bash
docker compose -f infra/compose/compose.yaml down -v
```

---

## 8. Kubernetes 검증과의 관계
Docker 검증은 로컬 재현 및 빠른 확인 목적입니다.  
쿠버네티스 기반 통합 테스트는 아래 문서를 따릅니다.

- docs/verify-k8s.md
