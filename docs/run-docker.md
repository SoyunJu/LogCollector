# LogCollector Docker 실행 가이드

## 0. 전제
- Docker Desktop 설치 및 실행 중
- 프로젝트 루트 경로에서 명령 실행
- 본 문서는 **개발/검증 목적** 기준으로 작성되었습니다.
- 프론트엔드는 검증용이며 필수 요소가 아닙니다.
- 최초 실행 시 Grafana 대시보드 패널이 비어 있을 수 있으며 정상 상태입니다.

---

## Prerequisites

- Docker 24+
- Docker Compose v2
- 8GB 이상 메모리 권장

---

## 1. 전체 시스템 실행 (Full Stack)
> React 프론트엔드, Spring Boot 백엔드, 그리고 인프라(DB, Redis, Monitoring)를 모두 Docker 컨테이너로 실행합니다.

### 1-1) Docker Compose 실행
`infra/compose/compose.yaml`은 백엔드/프론트엔드 이미지를 빌드하고 인프라와 함께 실행합니다.

```bash
docker compose -f infra/compose/compose.yaml up -d --build
```

---

## 2. 서비스 접속 정보
> 컨테이너가 정상적으로 실행된 후 아래 주소로 접속할 수 있습니다.
``` bash
Frontend,http://localhost  ,웹 대시보드 (Port 80)
Backend API,http://localhost:8080  ,API 서버
Grafana,http://localhost:3000   ,모니터링 (ID/PW: admin/admin)
Prometheus,http://localhost:9090   ,메트릭 수집
```

## 2-1. 정상 기동 확인 방법

- 모든 컨테이너 상태 확인
  docker compose ps

- Backend Health Check
  curl http://localhost:8080/actuator/health

- Prometheus Target 확인
  http://localhost:9090/targets 에서 logcollector-app UP

## 2-2. 초기 상태 안내

- 최초 실행 시 Incident / ErrorLog 데이터는 비어 있습니다.
- 대시보드가 비어 있어도 정상 상태입니다.


## 3. 컨테이너 종료
```bash
docker compose -f infra/compose/compose.yaml down
```
- 데이터까지 초기화하려면:
```bash
docker compose down -v
```

---

## 4. 로그 수집 테스트 (선택)

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


