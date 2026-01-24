# LogCollector & KnowledgeBase: 로그에서 지식으로 (v1)

단순히 로그를 쌓는 시스템은 많습니다. 하지만 LogCollector는 쏟아지는 에러 로그를 **사건(Incident)** 단위로 식별하고, 대응 과정을 **지식(KnowledgeBase)** 으로 축적하여 재활용하는 것을 목표로 하는 프로젝트입니다.

단순 로그 수집기가 아니라, **"에러 발생 → 사건 관리 → 재발 대응 → 지식화"** 로 이어지는 전체 흐름을 백엔드 아키텍처 관점에서 풀어냈습니다.

---

## 1. Project Goal: 왜 만들었는가?

운영 환경의 로그는 파편화되어 있고 휘발되기 쉽습니다. 저는 이 문제를 해결하기 위해 시스템의 책임을 명확히 분리하고, 데이터가 흐르는 파이프라인을 설계했습니다.

* **Noise Reduction**: 반복되는 에러를 하나의 '사건'으로 묶어 관리합니다.
* **Knowledge Asset**: 대응 경험이 운영자의 기억에만 머물지 않고, 시스템 내에 '지식'으로 남도록 구조화합니다.

---

## 2. Architecture Principles

이 프로젝트는 **LogCollector(LC)**, **Incident**, **KbArticle** 세 가지 핵심 모듈이 유기적으로 연결된 구조를 가집니다.

### 1) Responsibility Separation (책임의 분리)
각 모듈은 명확히 정의된 역할만 수행합니다.

* **LogCollector (LC)**: 대량의 로그를 수집하고, 정규화(Normalize)하며, 해싱하여 중복을 제거하는 **전처리 전용 모듈**입니다.
* **Incident (Ops View)**: 운영자가 바라보는 사건 리스트입니다. LC와 KB 사이를 잇는 Projection 역할을 하며, 현재 진행 중인 장애 상황을 관리합니다.
* **KbArticle (Knowledge View)**: 실제 유의미한 데이터가 저장되는 **최종 진실의 원천(Writer of Truth)** 입니다.

### 2) Writer of Truth Strategy
데이터 정합성을 위해 쓰기 권한을 엄격히 관리합니다. 모든 유의미한 지식 데이터의 변경은 **KbArticle**을 기준으로 이루어지며, Incident는 이 변경 사항을 동기화(Projection) 받아 운영자에게 보여주는 역할에 집중합니다.

### 3) Reoccurrence First (재발 우선 설계)
에러는 반드시 재발한다고 가정했습니다.
동일한 `log_hash`를 가진 에러가 다시 발생하면:
* 닫혀있던 Incident는 자동으로 **Reopen** 되며,
* KbArticle은 기존 지식을 유지한 채 새로운 대응 기록을 누적할 수 있도록 상태가 회귀합니다.

---

## 3. Data Consistency & Flow

멀티 DB 환경(LC DB / KB DB)에서의 데이터 일관성을 위해 **Eventual Consistency(최종 일관성)** 모델을 채택했습니다.

* **비동기 동기화**: 실패를 허용하고 재시도를 통해 결국 동기화되는 구조입니다.
* **Idempotent Upsert**: 메시지가 중복 도달하더라도 데이터가 꼬이지 않도록 멱등성을 보장하는 쓰기 방식을 적용했습니다.

### Verified Flow (검증된 흐름)
v1 구현을 통해 다음 시나리오가 코드로 검증되었습니다.
1.  **Normal**: 로그 수집 → Incident 생성 → 정책 기반 Draft 작성 → KbArticle 지식화
2.  **Reoccur**: 동일 로그 재발 → Incident Reopen → 상태 회귀 및 이력 누적

---

## 4. Status Model

로그의 생명주기를 관리하기 위해 두 가지 상태 모델을 운용합니다.

* **Incident**: `OPEN` → `UNDERWAY` → `RESOLVED` (운영 관점)
* **KbArticle**: `OPEN` → `UNDERWAY` → `RESPONSED` → `DEFINITY` (지식 관점)

---

## 5. Scope & Tech Stack

### Intentionally Out of Scope (v1)
프로젝트의 복잡도를 조절하고 핵심 로직 검증에 집중하기 위해, 다음 항목들은 **의도적으로 v1 범위에서 제외**했습니다.
* Elasticsearch 기반의 전문 검색 (Full-text Search)
* AI 모델 학습 및 자동 추천
* 완전한 멀티 DB 트랜잭션 (2PC) - *Eventual Consistency로 대체*

### Tech Stack
* **Language**: Java 17, Spring Boot
* **Data**: JPA / Querydsl, MariaDB (LC/KB 분리), Redis
* **Infrastructure**: Docker, Docker Compose

---

## 6. How to Run

* **로컬 실행 가이드**: [`docs/run-local.md`](docs/run-local.md)
* **Docker 실행 가이드**: [`docs/run-docker.md`](docs/run-docker.md)

---

## 정리

LogCollector & KnowledgeBase는 단순한 에러 로깅 툴이 아닙니다.
**"운영의 파편화된 경험을 어떻게 시스템의 자산으로 남길 것인가?"** 라는 질문에 대해, 아키텍처와 코드로 답을 내린 결과물입니다.