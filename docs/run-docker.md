# LogCollector Docker 실행 가이드

## 0. 전제
- Docker Desktop 설치 및 실행 중
- 프로젝트 루트 경로에서 명령 실행

---
## 1. 전체 시스템 실행 (Full Stack)
> React 프론트엔드, Spring Boot 백엔드, 그리고 인프라(DB, Redis, Monitoring)를 모두 Docker 컨테이너로 실행합니다.

### 1) Docker Compose 실행
`infra/compose/compose.yaml`은 백엔드/프론트엔드 이미지를 빌드하고 인프라와 함께 실행합니다.

```bash
docker compose -f infra/compose/compose.yaml up -d --build
```
## 2. 서비스 접속 정보
> 컨테이너가 정상적으로 실행된 후 아래 주소로 접속할 수 있습니다.
``` bash
Frontend,http://localhost  ,웹 대시보드 (Port 80)
Backend API,http://localhost:8080  ,API 서버
Grafana,http://localhost:3000   ,모니터링 (ID/PW: admin/admin)
Prometheus,http://localhost:9090   ,메트릭 수집
```

## 3. 컨테이너 종료
```bash
docker compose -f infra/compose/compose.yaml down
```

