# LogCollector 로컬 실행 가이드

## 0. 전제
- JDK / Gradle Wrapper(gradlew) 사용
- Docker 설치(DB/Redis를 컨테이너로 실행)
- 기본 애플리케이션 포트: 8082

---
## 1. 로컬 개발 표준(권장)
> DB/Redis는 Docker로 실행하고, 애플리케이션은 bootRun으로 실행합니다.

### 1) Docker Compose 실행 (DB/Redis)
```bash
docker compose -f infra/compose/compose.dev.yaml up -d
```

### 2) 애플리케이션 실행 (local profile)
```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```
---

## 2. Mock(AI)로 실행 (dev profile 추가 옵션)
> AI 연동을 실제 API 호출 대신 Mock 구현으로 테스트하려면 dev 프로파일을 추가합니다.
```bash
./gradlew bootRun --args='--spring.profiles.active=local,dev'
```
---
