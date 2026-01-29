# LogCollector & KnowledgeBase

로그를 수집하는 데서 끝나지 않고, 에러를 사건(Incident)으로 관리하고 대응 과정을 지식(KnowledgeBase)으로 축적하는 시스템입니다.

---

## Quick Demo (Visual)

1) 대시보드 접속  
- http://localhost

2) 로그 유입 및 Incident 생성 확인

3) Grafana 확인  
- http://localhost:3000 (admin / admin)

---

## Verification Paths

이 프로젝트는 실행 환경별로 검증 경로를 분리합니다.

### 1) Local (run-local)
- 목적: IDE 기반 개발/디버깅
- 문서: docs/run-local.md
- 프론트엔드 제공 없음
- curl 기반 API 검증

---

### 2) Docker (run-docker) — 주요 검증 경로
- 목적: 다른 PC에서도 동일하게 재현 가능한 실행
- 문서: docs/run-docker.md
- API Base URL: http://localhost:8080/api

검증 도구:
- test-api.sh
- test-api.ps1
- api-test.http

빠른 검증:

```bash
docker compose -f infra/compose/compose.yaml up -d --build
./test-api.sh
```

---

### 3) Kubernetes (run-k8s)
- 목적: 클러스터 내부 통합 테스트(잡 기반)
- 문서: docs/verify-k8s.md
- README의 "CLI Verification (Automated)"는 이 경로를 의미합니다.

---

## CLI Verification (Automated) — Kubernetes

쿠버네티스 클러스터 내부에서 통합 테스트를 수행합니다.

```bash
make test
```

성공 기준: 터미널 마지막에 `>>> ALL TESTS PASSED!` 메시지가 출력되면 정상입니다.

---

## Project Goal — 왜 만들었는가

운영 환경의 로그는 대부분 일회성으로 소비됩니다. 그 결과 동일한 장애가 반복되어도 대응 경험은 개인의 기억에만 의존하게 됩니다.

이 프로젝트의 목표는 다음 두 가지입니다.

- Noise Reduction  
  반복 로그를 하나의 Incident로 관리

- Knowledge Assetization  
  장애 대응 경험을 시스템의 지식 자산으로 축적

---

## Architecture Overview

- LogCollector: 로그 수집 및 정규화
- Incident: 운영 관점의 사건 관리
- KbArticle: 지식의 단일 진실 소스(Writer of Truth)

재발 시 Incident는 reopen 되고, 기존 KbArticle은 이력을 누적합니다.

---

## Status Model

### Incident
- OPEN
- IN_PROGRESS
- RESOLVED
- CLOSED
- IGNORED

### KbArticle
- DRAFT
- IN_PROGRESS
- PUBLISHED
- ARCHIVED

상세 정책: docs/status.md

---

## Tech Stack
- Java 17 / Spring Boot 3
- JPA / MariaDB / Redis
- Docker / Docker Compose
- (Optional) Kubernetes

---

## How to Run
- Local: docs/run-local.md
- Docker: docs/run-docker.md
- Kubernetes: docs/run-k8s.md / docs/verify-k8s.md
