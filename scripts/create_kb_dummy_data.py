#!/usr/bin/env python3
"""
LogCollector KB 더미 데이터 생성 스크립트
=========================================
API를 통해 실제 플로우를 시뮬레이션하여 RAG에 활용 가능한 KB 데이터를 생성합니다.

플로우:
  로그 수집(POST /api/logs)
    └─▶ Redis 큐 → RedisToDB 소비(async, 500ms)
          └─▶ ErrorLog 저장 + LogSavedEvent 발행
                └─▶ KbEventListener → Incident upsert
                      └─▶ (조건 충족 시) SystemDraft + KbArticle 생성

  Incident 상태 변경(PATCH /api/incidents/{hash}/status)
    └─▶ RESOLVED 전이 시 → KbArticle(DRAFT) 자동 생성

  KbArticle 흐름:
    DRAFT ──(addendum 추가)──▶ IN_PROGRESS ──(status PUBLISHED)──▶ PUBLISHED

시나리오 구성:
  1. Redis Timeout       - HIGH_RECUR(12회), IN_PROGRESS  → KbArticle IN_PROGRESS
  2. DB Pool Exhausted   - HOST_SPREAD(4호스트), IN_PROGRESS → KbArticle IN_PROGRESS
  3. OutOfMemoryError    - RESOLVED 자동 draft  → KbArticle PUBLISHED
  4. HTTP 502 Bad GW     - RESOLVED 자동 draft  → KbArticle PUBLISHED
  5. NullPointerException- OPEN, draft 수동 생성 → KbArticle DRAFT
  6. Lock Wait Timeout   - HOST_SPREAD(3호스트), IN_PROGRESS → KbArticle IN_PROGRESS

실행:
  python3 scripts/create_kb_dummy_data.py [--base-url http://localhost:8080]
"""

import argparse
import sys
import time
import json
import urllib.request
import urllib.error
import urllib.parse

# ─────────────────────────────────────────────
# 설정
# ─────────────────────────────────────────────
DEFAULT_BASE_URL = "http://localhost:8080"
WAIT_FOR_QUEUE   = 3    # Redis 소비 + KB 이벤트 처리 대기(초)
WAIT_SHORT       = 1    # 짧은 대기(초)


# ─────────────────────────────────────────────
# HTTP 헬퍼
# ─────────────────────────────────────────────
class HttpClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")

    def _request(self, method: str, path: str, body=None, params=None):
        url = self.base_url + path
        if params:
            url += "?" + urllib.parse.urlencode(params)

        data = json.dumps(body).encode("utf-8") if body is not None else None
        headers = {"Content-Type": "application/json", "Accept": "application/json"}

        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                raw = resp.read().decode("utf-8")
                if raw:
                    return resp.status, json.loads(raw)
                return resp.status, None
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8")
            print(f"  [HTTP {e.code}] {method} {path} → {raw[:200]}")
            return e.code, None
        except Exception as e:
            print(f"  [ERROR] {method} {path} → {e}")
            return 0, None

    def post(self, path, body=None, params=None):
        return self._request("POST", path, body, params)

    def get(self, path, params=None):
        return self._request("GET", path, params=params)

    def patch(self, path, params=None):
        return self._request("PATCH", path, params=params)

    def delete(self, path):
        return self._request("DELETE", path)


# ─────────────────────────────────────────────
# 도메인 헬퍼
# ─────────────────────────────────────────────
class DummyDataCreator:
    def __init__(self, http: HttpClient):
        self.http = http

    # ── 로그 수집 ──────────────────────────────
    def post_log(self, service_name: str, host_name: str, log_level: str,
                 message: str, stack_trace: str) -> bool:
        status, _ = self.http.post("/api/logs", {
            "serviceName": service_name,
            "hostName":    host_name,
            "logLevel":    log_level,
            "message":     message,
            "stackTrace":  stack_trace,
        })
        return status in (200, 202)

    # ── 로그 조회 (logHash 획득) ────────────────
    def get_log_hash(self, service_name: str) -> str | None:
        status, body = self.http.get("/api/logs", params={
            "serviceName": service_name,
            "size": 1,
            "page": 0,
        })
        if status != 200 or not body:
            return None
        content = body.get("content", [])
        if not content:
            return None
        return content[0].get("logHash")

    def get_log_id(self, service_name: str) -> int | None:
        status, body = self.http.get("/api/logs", params={
            "serviceName": service_name,
            "size": 1,
            "page": 0,
        })
        if status != 200 or not body:
            return None
        content = body.get("content", [])
        if not content:
            return None
        return content[0].get("logId")

    # ── Incident 조회 ──────────────────────────
    def get_incident(self, log_hash: str) -> dict | None:
        status, body = self.http.get(f"/api/incidents/{log_hash}")
        if status != 200 or not body:
            return None
        return body

    def update_incident_status(self, log_hash: str, new_status: str) -> bool:
        status, _ = self.http.patch(
            f"/api/incidents/{log_hash}/status",
            params={"newStatus": new_status},
        )
        return status in (200, 204)

    # ── KB Draft & Article ─────────────────────
    def create_kb_draft(self, incident_id: int) -> int | None:
        status, body = self.http.post("/api/kb/draft", params={"incidentId": incident_id})
        if status == 200 and body is not None:
            return body  # Long id
        return None

    def add_addendum(self, kb_article_id: int, content: str, created_by: str = "user") -> bool:
        status, _ = self.http.post(
            f"/api/kb/articles/{kb_article_id}",
            body={"content": content, "createdBy": created_by},
        )
        return status in (200, 201)

    def update_kb_status(self, kb_article_id: int, kb_status: str) -> bool:
        status, _ = self.http.patch(
            f"/api/kb/articles/{kb_article_id}/status",
            params={"status": kb_status},
        )
        return status in (200, 204)

    def find_kb_by_incident(self, incident_id: int) -> int | None:
        """KB article id를 incident_id 기반으로 탐색"""
        status, body = self.http.get("/api/kb", params={"size": 100, "page": 0})
        if status != 200 or not body:
            return None
        for article in body.get("content", []):
            if article.get("incidentId") == incident_id:
                return article.get("id")
        return None

    # ── 헬스체크 ───────────────────────────────
    def health_check(self) -> bool:
        status, _ = self.http.get("/actuator/health")
        return status == 200

    # ── 테스트 데이터 정리 ──────────────────────
    def cleanup(self, log_hash: str):
        self.http.delete(f"/api/test/data/by-log-hash/{log_hash}")


# ─────────────────────────────────────────────
# 시나리오 정의
# ─────────────────────────────────────────────
def step(msg: str):
    print(f"  → {msg}")


def scenario_header(num: int, title: str, desc: str):
    print(f"\n{'='*60}")
    print(f"[시나리오 {num}] {title}")
    print(f"  {desc}")
    print(f"{'='*60}")


# ── S1: Redis Timeout (HIGH_RECUR) ────────────────────────────
def run_scenario_1(c: DummyDataCreator):
    scenario_header(
        1, "Redis Timeout - HIGH_RECUR",
        "동일 에러 12회 반복 → repeat_count >= 10 → Incident IN_PROGRESS, KbArticle IN_PROGRESS"
    )
    SERVICE   = "RAG-CAPTURE-API"
    HOST      = "pod-api-01"
    LOG_LEVEL = "ERROR"
    MESSAGE   = (
        "Redis command timed out after 500ms: "
        "RedisCommandTimeoutException in LogCollector queue service"
    )
    STACK = (
        "io.lettuce.core.RedisCommandTimeoutException: Command timed out after 500ms\n"
        "\tat io.lettuce.core.protocol.CommandHandler.handleCommandComplete(CommandHandler.java:644)\n"
        "\tat com.soyunju.logcollector.service.redis.RedisQueueService.enqueue(RedisQueueService.java:72)\n"
        "\tat com.soyunju.logcollector.service.lc.redis.LogToRedis.push(LogToRedis.java:38)\n"
        "\tat com.soyunju.logcollector.controller.lc.ErrorLogCrdController.collectLog(ErrorLogCrdController.java:63)"
    )

    # 12회 반복 전송 → repeat_count 누적
    step(f"동일 에러 12회 전송 ({SERVICE}, {HOST})")
    for i in range(12):
        ok = c.post_log(SERVICE, HOST, LOG_LEVEL, MESSAGE, STACK)
        if not ok:
            print(f"    [WARN] {i+1}번째 전송 실패")
        time.sleep(0.1)

    step(f"Redis 소비 대기 ({WAIT_FOR_QUEUE}s)")
    time.sleep(WAIT_FOR_QUEUE)

    log_hash = c.get_log_hash(SERVICE)
    if not log_hash:
        print("  [FAIL] logHash를 찾을 수 없습니다.")
        return
    step(f"logHash: {log_hash}")

    incident = c.get_incident(log_hash)
    if not incident:
        print("  [FAIL] Incident를 찾을 수 없습니다.")
        return
    incident_id = incident["id"]
    step(f"Incident ID: {incident_id}, repeatCount: {incident.get('repeatCount')}, status: {incident.get('status')}")

    # Draft 생성 (수동: incidentId=null 이슈로 자동 생성 안 됨)
    kb_id = c.create_kb_draft(incident_id)
    if not kb_id:
        print("  [FAIL] KB Draft 생성 실패")
        return
    step(f"KbArticle 생성: id={kb_id} (DRAFT)")

    # Incident → IN_PROGRESS
    ok = c.update_incident_status(log_hash, "IN_PROGRESS")
    step(f"Incident 상태 → IN_PROGRESS: {'OK' if ok else 'FAIL'}")

    # Addendum 1 - 원인 분석 (DRAFT → IN_PROGRESS 자동 전환)
    ok = c.add_addendum(
        kb_id,
        "【원인 분석】\n"
        "Redis 클라이언트(Lettuce) 타임아웃이 burst 트래픽 시 급증.\n"
        "Redis CPU 사용률이 피크 시 85%를 초과하여 커맨드 처리 지연 발생.\n"
        "현재 timeout 설정: 500ms (기본값). 네트워크 지터도 영향 가능성 있음.",
        "system",
    )
    step(f"Addendum 1(원인 분석) 추가: {'OK' if ok else 'FAIL'} → KbArticle IN_PROGRESS 자동 전환")

    # Addendum 2 - 임시 조치
    ok = c.add_addendum(
        kb_id,
        "【임시 조치】\n"
        "1. Redis timeout을 500ms → 2000ms로 상향 조정.\n"
        "2. 모니터링 알림 임계값을 CPU 70%로 하향.\n"
        "3. burst 발생 시 처리를 위해 queue batch-size를 20 → 10으로 축소.\n"
        "추가 스케일아웃 여부는 트래픽 패턴 분석 후 결정 예정.",
        "user",
    )
    step(f"Addendum 2(임시 조치) 추가: {'OK' if ok else 'FAIL'}")

    print(f"\n  ✔ S1 완료: Incident(IN_PROGRESS), KbArticle(IN_PROGRESS), Addendum x2")


# ── S2: DB Pool Exhausted (HOST_SPREAD) ───────────────────────
def run_scenario_2(c: DummyDataCreator):
    scenario_header(
        2, "DB Connection Pool Exhausted - HOST_SPREAD",
        "4개 호스트에서 동일 에러 → hostCount >= 3 → Incident IN_PROGRESS, KbArticle IN_PROGRESS"
    )
    SERVICE   = "RAG-CAPTURE-ORDER"
    LOG_LEVEL = "CRITICAL"
    MESSAGE   = (
        "HikariPool-1 - Connection is not available, request timed out after 30000ms: "
        "SQLTransientConnectionException in order processing service"
    )
    STACK = (
        "java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available\n"
        "\tat com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:196)\n"
        "\tat com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:100)\n"
        "\tat com.soyunju.logcollector.repository.order.OrderRepository.findById(OrderRepository.java:42)\n"
        "\tat com.soyunju.logcollector.service.order.OrderService.processOrder(OrderService.java:88)"
    )
    HOSTS = ["pod-order-01", "pod-order-02", "pod-order-03", "pod-order-04"]

    step(f"4개 호스트에서 각 1회 전송 (HOST_SPREAD)")
    for host in HOSTS:
        ok = c.post_log(SERVICE, host, LOG_LEVEL, MESSAGE, STACK)
        step(f"  {host}: {'OK' if ok else 'FAIL'}")
        time.sleep(0.2)

    step(f"Redis 소비 대기 ({WAIT_FOR_QUEUE}s)")
    time.sleep(WAIT_FOR_QUEUE)

    log_hash = c.get_log_hash(SERVICE)
    if not log_hash:
        print("  [FAIL] logHash를 찾을 수 없습니다.")
        return
    step(f"logHash: {log_hash}")

    incident = c.get_incident(log_hash)
    if not incident:
        print("  [FAIL] Incident를 찾을 수 없습니다.")
        return
    incident_id = incident["id"]
    step(f"Incident ID: {incident_id}, impactedHostCount: 4, status: {incident.get('status')}")

    kb_id = c.create_kb_draft(incident_id)
    if not kb_id:
        print("  [FAIL] KB Draft 생성 실패")
        return
    step(f"KbArticle 생성: id={kb_id} (DRAFT)")

    ok = c.update_incident_status(log_hash, "IN_PROGRESS")
    step(f"Incident 상태 → IN_PROGRESS: {'OK' if ok else 'FAIL'}")

    # Addendum 1 - 원인 및 진행 상황 (DRAFT → IN_PROGRESS)
    ok = c.add_addendum(
        kb_id,
        "【조사 현황】\n"
        "4개 pod 전체에서 HikariPool 타임아웃 발생. 동시 접속 급증(burst)으로 pool 소진.\n"
        "현재 max-pool-size=10, 최대 30개 스레드가 동시 접근 시도 확인.\n"
        "DB 슬로우 쿼리 로그에서 avg 2.3s 쿼리 3건 식별 → 인덱스 최적화 필요.\n"
        "조치: max-pool-size를 10 → 30으로 임시 상향, connection-timeout을 60s로 연장.",
        "user",
    )
    step(f"Addendum 1(조사 현황) 추가: {'OK' if ok else 'FAIL'} → KbArticle IN_PROGRESS 자동 전환")

    # Addendum 2 - 추가 분석
    ok = c.add_addendum(
        kb_id,
        "【추가 조사】\n"
        "슬로우 쿼리 분석 결과: order_items 테이블 full-scan 확인.\n"
        "복합 인덱스 (order_id, status) 추가 예정. migration 티켓 #ORDER-892 생성.\n"
        "단기: connection-pool 확대 적용으로 error rate 94% 감소 확인.",
        "admin",
    )
    step(f"Addendum 2(추가 조사) 추가: {'OK' if ok else 'FAIL'}")

    print(f"\n  ✔ S2 완료: Incident(IN_PROGRESS), KbArticle(IN_PROGRESS), Addendum x2")


# ── S3: OutOfMemoryError (RESOLVED → PUBLISHED) ────────────────
def run_scenario_3(c: DummyDataCreator):
    scenario_header(
        3, "OutOfMemoryError - RESOLVED → KbArticle PUBLISHED",
        "배치 작업 OOM → RESOLVED 상태 전이 시 자동 draft 생성 → addendum 추가 → PUBLISHED"
    )
    SERVICE   = "RAG-CAPTURE-BATCH"
    HOST      = "pod-batch-01"
    LOG_LEVEL = "FATAL"
    MESSAGE   = (
        "java.lang.OutOfMemoryError: Java heap space during batch processing - "
        "BatchRunner failed to allocate memory for large dataset"
    )
    STACK = (
        "java.lang.OutOfMemoryError: Java heap space\n"
        "\tat java.util.Arrays.copyOf(Arrays.java:3210)\n"
        "\tat java.util.ArrayList.grow(ArrayList.java:265)\n"
        "\tat com.soyunju.logcollector.batch.DataAggregator.aggregate(DataAggregator.java:124)\n"
        "\tat com.soyunju.logcollector.batch.BatchRunner.run(BatchRunner.java:88)\n"
        "\tat org.springframework.batch.core.step.tasklet.TaskletStep.execute(TaskletStep.java:233)"
    )

    step("에러 로그 3회 전송")
    for i in range(3):
        ok = c.post_log(SERVICE, HOST, LOG_LEVEL, MESSAGE, STACK)
        if not ok:
            print(f"    [WARN] {i+1}번째 전송 실패")
        time.sleep(0.1)

    step(f"Redis 소비 대기 ({WAIT_FOR_QUEUE}s)")
    time.sleep(WAIT_FOR_QUEUE)

    log_hash = c.get_log_hash(SERVICE)
    if not log_hash:
        print("  [FAIL] logHash를 찾을 수 없습니다.")
        return
    step(f"logHash: {log_hash}")

    incident = c.get_incident(log_hash)
    if not incident:
        print("  [FAIL] Incident를 찾을 수 없습니다.")
        return
    incident_id = incident["id"]
    step(f"Incident ID: {incident_id}, status: {incident.get('status')}")

    # RESOLVED → updateStatusKbOnly(RESOLVED) → kbDraftService.createSystemDraft() 자동 호출
    ok = c.update_incident_status(log_hash, "RESOLVED")
    step(f"Incident 상태 → RESOLVED: {'OK' if ok else 'FAIL'} (KbArticle DRAFT 자동 생성 트리거)")

    time.sleep(WAIT_SHORT)

    kb_id = c.find_kb_by_incident(incident_id)
    if not kb_id:
        # 자동 생성 안 된 경우 수동 생성
        step("KbArticle 자동 생성 확인 안 됨 → 수동 생성")
        kb_id = c.create_kb_draft(incident_id)
    if not kb_id:
        print("  [FAIL] KB Draft 생성 실패")
        return
    step(f"KbArticle ID: {kb_id} (DRAFT)")

    # Addendum 1 - 근본 원인 (DRAFT → IN_PROGRESS)
    ok = c.add_addendum(
        kb_id,
        "【근본 원인 분석】\n"
        "배치 작업 DataAggregator에서 전체 데이터를 메모리에 적재 후 처리하는 구조적 문제.\n"
        "일별 데이터가 5GB를 초과하는 경우 Heap(2GB 설정)을 초과하여 OOM 발생.\n"
        "JVM 옵션: -Xmx2048m → 실제 필요량: 최대 7GB (peak load 기준).",
        "system",
    )
    step(f"Addendum 1(근본 원인) 추가: {'OK' if ok else 'FAIL'}")

    # Addendum 2 - 해결책
    ok = c.add_addendum(
        kb_id,
        "【해결책 적용】\n"
        "1. Heap 증설: -Xmx2048m → -Xmx6144m (6GB 설정)\n"
        "2. 스트리밍 처리 방식으로 DataAggregator 리팩터링 (PR #472)\n"
        "   - ArrayList 전체 로드 → Cursor 기반 스트림 처리로 변경\n"
        "   - 메모리 사용량 피크: 7GB → 512MB로 감소\n"
        "3. GC 정책 변경: G1GC → ZGC (대용량 처리 성능 개선)",
        "user",
    )
    step(f"Addendum 2(해결책) 추가: {'OK' if ok else 'FAIL'}")

    # Addendum 3 - 재발 방지
    ok = c.add_addendum(
        kb_id,
        "【재발 방지 조치】\n"
        "1. 메모리 사용량 모니터링 알림: Heap 사용률 80% 초과 시 Slack 알림 추가.\n"
        "2. 배치 실행 전 데이터 볼륨 예측 로직 추가 (예측 초과 시 중단 후 알림).\n"
        "3. 월별 배치 성능 리뷰 프로세스 수립.\n"
        "최종 검증: 7일 연속 정상 동작 확인 완료. 종료 처리.",
        "admin",
    )
    step(f"Addendum 3(재발 방지) 추가: {'OK' if ok else 'FAIL'}")

    # PUBLISHED 상태로 전환 (제목 + addendum 있음 → 조건 충족)
    ok = c.update_kb_status(kb_id, "PUBLISHED")
    step(f"KbArticle 상태 → PUBLISHED: {'OK' if ok else 'FAIL'}")

    print(f"\n  ✔ S3 완료: Incident(RESOLVED), KbArticle(PUBLISHED), Addendum x3")


# ── S4: HTTP 502 Bad Gateway (RESOLVED → PUBLISHED) ───────────
def run_scenario_4(c: DummyDataCreator):
    scenario_header(
        4, "HTTP 502 Bad Gateway - RESOLVED → KbArticle PUBLISHED",
        "결제 서비스 502 에러 → RESOLVED → 해결 방법 KB로 게시"
    )
    SERVICE   = "RAG-CAPTURE-PAYMENT"
    HOST      = "pod-payment-01"
    LOG_LEVEL = "ERROR"
    MESSAGE   = (
        "502 Bad Gateway from upstream payment gateway: "
        "HttpServerErrorException in payment processing request"
    )
    STACK = (
        "org.springframework.web.client.HttpServerErrorException$BadGateway: 502 Bad Gateway\n"
        "\tat org.springframework.web.client.DefaultResponseErrorHandler.handleError(DefaultResponseErrorHandler.java:186)\n"
        "\tat org.springframework.web.client.RestTemplate.handleResponse(RestTemplate.java:825)\n"
        "\tat com.soyunju.logcollector.client.PaymentGatewayClient.charge(PaymentGatewayClient.java:55)\n"
        "\tat com.soyunju.logcollector.service.payment.PaymentService.processPayment(PaymentService.java:103)"
    )
    HOSTS = ["pod-payment-01", "pod-payment-02"]

    step("2개 호스트에서 에러 전송")
    for host in HOSTS:
        for _ in range(3):
            ok = c.post_log(SERVICE, host, LOG_LEVEL, MESSAGE, STACK)
            time.sleep(0.1)
    step(f"총 6회 전송 완료")

    step(f"Redis 소비 대기 ({WAIT_FOR_QUEUE}s)")
    time.sleep(WAIT_FOR_QUEUE)

    log_hash = c.get_log_hash(SERVICE)
    if not log_hash:
        print("  [FAIL] logHash를 찾을 수 없습니다.")
        return
    step(f"logHash: {log_hash}")

    incident = c.get_incident(log_hash)
    if not incident:
        print("  [FAIL] Incident를 찾을 수 없습니다.")
        return
    incident_id = incident["id"]
    step(f"Incident ID: {incident_id}, status: {incident.get('status')}")

    # RESOLVED → KbArticle 자동 생성
    ok = c.update_incident_status(log_hash, "RESOLVED")
    step(f"Incident → RESOLVED: {'OK' if ok else 'FAIL'}")
    time.sleep(WAIT_SHORT)

    kb_id = c.find_kb_by_incident(incident_id)
    if not kb_id:
        step("KbArticle 수동 생성")
        kb_id = c.create_kb_draft(incident_id)
    if not kb_id:
        print("  [FAIL] KB Draft 생성 실패")
        return
    step(f"KbArticle ID: {kb_id}")

    # Addendum 1 - 장애 타임라인 (DRAFT → IN_PROGRESS)
    ok = c.add_addendum(
        kb_id,
        "【장애 타임라인】\n"
        "14:22 - 결제 요청 실패율 3% 감지 (알림 임계값 초과)\n"
        "14:25 - 상위 게이트웨이(PG사) 응답 502 확인\n"
        "14:28 - PG사 측에 장애 신고 접수\n"
        "14:45 - PG사 내부 서버 재시작으로 응답 정상화\n"
        "14:50 - 자동 재시도 로직 동작으로 밀린 요청 처리 완료",
        "system",
    )
    step(f"Addendum 1(타임라인) 추가: {'OK' if ok else 'FAIL'}")

    # Addendum 2 - 근본 원인
    ok = c.add_addendum(
        kb_id,
        "【원인 및 조치】\n"
        "원인: 외부 PG사 서버 메모리 누수로 인한 프로세스 재시작 → 일시적 502\n"
        "자사 조치:\n"
        "  1. Circuit Breaker 설정 추가 (Resilience4j)\n"
        "     - failureRateThreshold=50, waitDurationInOpenState=30s\n"
        "  2. 지수 백오프 재시도 로직 구현 (3회, 2s/4s/8s)\n"
        "  3. 폴백 응답 추가: 임시 주문 보류 처리 후 비동기 재처리",
        "user",
    )
    step(f"Addendum 2(원인/조치) 추가: {'OK' if ok else 'FAIL'}")

    # Addendum 3 - 예방책
    ok = c.add_addendum(
        kb_id,
        "【예방 및 모니터링】\n"
        "1. PG사 SLA 기준 외부 모니터링 추가 (Synthetic monitoring, 30s 주기)\n"
        "2. 결제 실패율 대시보드 Grafana 패널 추가\n"
        "3. Circuit Breaker OPEN 시 즉시 On-call 알림 연동\n"
        "4. 월별 DR 훈련에 PG 장애 시나리오 포함\n"
        "본 케이스를 팀 위키에 Runbook으로 등록 완료.",
        "admin",
    )
    step(f"Addendum 3(예방책) 추가: {'OK' if ok else 'FAIL'}")

    ok = c.update_kb_status(kb_id, "PUBLISHED")
    step(f"KbArticle → PUBLISHED: {'OK' if ok else 'FAIL'}")

    print(f"\n  ✔ S4 완료: Incident(RESOLVED), KbArticle(PUBLISHED), Addendum x3")


# ── S5: NullPointerException (OPEN → DRAFT) ───────────────────
def run_scenario_5(c: DummyDataCreator):
    scenario_header(
        5, "NullPointerException - OPEN → KbArticle DRAFT",
        "초기 조사 단계 incident, addendum 없는 DRAFT 상태 유지"
    )
    SERVICE   = "RAG-CAPTURE-AUTH"
    HOST      = "pod-auth-01"
    LOG_LEVEL = "ERROR"
    MESSAGE   = (
        "NullPointerException: Cannot invoke String.length() because userId is null "
        "in AuthService token validation"
    )
    STACK = (
        "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"userId\" is null\n"
        "\tat com.soyunju.logcollector.service.auth.JwtTokenValidator.validate(JwtTokenValidator.java:87)\n"
        "\tat com.soyunju.logcollector.service.auth.AuthService.authenticate(AuthService.java:134)\n"
        "\tat com.soyunju.logcollector.controller.auth.AuthController.login(AuthController.java:52)\n"
        "\tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:62)"
    )

    step("에러 로그 3회 전송")
    for i in range(3):
        ok = c.post_log(SERVICE, HOST, LOG_LEVEL, MESSAGE, STACK)
        if not ok:
            print(f"    [WARN] {i+1}번째 전송 실패")
        time.sleep(0.1)

    step(f"Redis 소비 대기 ({WAIT_FOR_QUEUE}s)")
    time.sleep(WAIT_FOR_QUEUE)

    log_hash = c.get_log_hash(SERVICE)
    if not log_hash:
        print("  [FAIL] logHash를 찾을 수 없습니다.")
        return
    step(f"logHash: {log_hash}")

    incident = c.get_incident(log_hash)
    if not incident:
        print("  [FAIL] Incident를 찾을 수 없습니다.")
        return
    incident_id = incident["id"]
    step(f"Incident ID: {incident_id}, status: {incident.get('status')} (OPEN 유지)")

    kb_id = c.create_kb_draft(incident_id)
    if not kb_id:
        print("  [FAIL] KB Draft 생성 실패")
        return
    step(f"KbArticle 생성: id={kb_id} (DRAFT, addendum 없이 유지)")

    print(f"\n  ✔ S5 완료: Incident(OPEN), KbArticle(DRAFT), 초기 조사 단계")


# ── S6: Lock Wait Timeout (HOST_SPREAD + IN_PROGRESS) ─────────
def run_scenario_6(c: DummyDataCreator):
    scenario_header(
        6, "Lock Wait Timeout - HOST_SPREAD → IN_PROGRESS",
        "3개 호스트 lock timeout → HOST_SPREAD → Incident IN_PROGRESS, KbArticle IN_PROGRESS"
    )
    SERVICE   = "RAG-CAPTURE-SEARCH"
    LOG_LEVEL = "ERROR"
    MESSAGE   = (
        "Lock wait timeout exceeded; try restarting transaction - "
        "CannotAcquireLockException in search index update"
    )
    STACK = (
        "org.springframework.dao.CannotAcquireLockException: "
        "Lock wait timeout exceeded; try restarting transaction\n"
        "\tat org.springframework.orm.jpa.vendor.HibernateJpaDialect.convertHibernateAccessException"
        "(HibernateJpaDialect.java:278)\n"
        "\tat com.soyunju.logcollector.repository.search.SearchIndexRepository.save"
        "(SearchIndexRepository.java:61)\n"
        "\tat com.soyunju.logcollector.service.search.SearchIndexService.updateIndex"
        "(SearchIndexService.java:93)\n"
        "\tat com.soyunju.logcollector.service.search.SearchScheduler.runIndexUpdate"
        "(SearchScheduler.java:47)"
    )
    HOSTS = ["pod-search-01", "pod-search-02", "pod-search-03"]

    step("3개 호스트에서 각 2회 전송")
    for host in HOSTS:
        for _ in range(2):
            ok = c.post_log(SERVICE, host, LOG_LEVEL, MESSAGE, STACK)
            time.sleep(0.15)
    step(f"총 6회 전송 완료")

    step(f"Redis 소비 대기 ({WAIT_FOR_QUEUE}s)")
    time.sleep(WAIT_FOR_QUEUE)

    log_hash = c.get_log_hash(SERVICE)
    if not log_hash:
        print("  [FAIL] logHash를 찾을 수 없습니다.")
        return
    step(f"logHash: {log_hash}")

    incident = c.get_incident(log_hash)
    if not incident:
        print("  [FAIL] Incident를 찾을 수 없습니다.")
        return
    incident_id = incident["id"]
    step(f"Incident ID: {incident_id}, status: {incident.get('status')}")

    kb_id = c.create_kb_draft(incident_id)
    if not kb_id:
        print("  [FAIL] KB Draft 생성 실패")
        return
    step(f"KbArticle 생성: id={kb_id} (DRAFT)")

    ok = c.update_incident_status(log_hash, "IN_PROGRESS")
    step(f"Incident → IN_PROGRESS: {'OK' if ok else 'FAIL'}")

    # Addendum 1 - 원인 (DRAFT → IN_PROGRESS)
    ok = c.add_addendum(
        kb_id,
        "【원인 파악】\n"
        "3개 pod 동시에 search_index 테이블에 대한 lock 경합 발생.\n"
        "스케줄러가 각 pod에서 독립 실행되며 동일 Row를 동시 업데이트 시도.\n"
        "현재 @Scheduled 기반 분산 락 없이 동작 → 동시성 문제 확인.\n"
        "hot row: search_index WHERE type='GLOBAL' → 단일 Row에 모든 갱신 집중.",
        "system",
    )
    step(f"Addendum 1(원인) 추가: {'OK' if ok else 'FAIL'} → KbArticle IN_PROGRESS")

    # Addendum 2 - 조치 계획
    ok = c.add_addendum(
        kb_id,
        "【조치 계획】\n"
        "단기(진행 중):\n"
        "  - ShedLock 라이브러리 도입으로 분산 스케줄러 단일 실행 보장\n"
        "  - lock wait timeout을 50s → 5s로 단축 (빠른 실패 전략)\n"
        "중기:\n"
        "  - search_index 파티셔닝: 타입별 Row 분리 → hot row 해소\n"
        "  - 비동기 이벤트 기반 색인 업데이트로 전환 검토\n"
        "현재 ShedLock 적용 PR #881 리뷰 진행 중.",
        "admin",
    )
    step(f"Addendum 2(조치 계획) 추가: {'OK' if ok else 'FAIL'}")

    print(f"\n  ✔ S6 완료: Incident(IN_PROGRESS), KbArticle(IN_PROGRESS), Addendum x2")


# ─────────────────────────────────────────────
# 메인
# ─────────────────────────────────────────────
def main():
    parser = argparse.ArgumentParser(description="LogCollector KB 더미 데이터 생성")
    parser.add_argument(
        "--base-url",
        default=DEFAULT_BASE_URL,
        help=f"API 서버 URL (기본: {DEFAULT_BASE_URL})",
    )
    parser.add_argument(
        "--skip-health-check",
        action="store_true",
        help="헬스체크 생략",
    )
    args = parser.parse_args()

    http = HttpClient(args.base_url)
    c    = DummyDataCreator(http)

    print("=" * 60)
    print(" LogCollector KB 더미 데이터 생성 스크립트")
    print(f" 대상 서버: {args.base_url}")
    print("=" * 60)

    # 헬스 체크
    if not args.skip_health_check:
        print("\n[헬스 체크]")
        if not c.health_check():
            print("  [FAIL] 서버가 응답하지 않습니다. --base-url 또는 서버 상태를 확인하세요.")
            sys.exit(1)
        print("  서버 정상 응답 확인")

    scenarios = [
        run_scenario_1,   # Redis Timeout - HIGH_RECUR, IN_PROGRESS
        run_scenario_2,   # DB Pool - HOST_SPREAD, IN_PROGRESS
        run_scenario_3,   # OOM - RESOLVED → PUBLISHED
        run_scenario_4,   # 502 - RESOLVED → PUBLISHED
        run_scenario_5,   # NPE - OPEN, DRAFT
        run_scenario_6,   # Lock Timeout - HOST_SPREAD, IN_PROGRESS
    ]

    failed = 0
    for fn in scenarios:
        try:
            fn(c)
        except Exception as e:
            print(f"\n  [ERROR] 시나리오 실행 중 예외 발생: {e}")
            failed += 1
        time.sleep(WAIT_SHORT)

    print("\n" + "=" * 60)
    print(f" 완료: {len(scenarios) - failed}/{len(scenarios)} 시나리오 성공")
    print()
    print(" 생성된 데이터 요약:")
    print("  시나리오 1: RAG-CAPTURE-API    │ Incident IN_PROGRESS │ KbArticle IN_PROGRESS (addendum x2)")
    print("  시나리오 2: RAG-CAPTURE-ORDER  │ Incident IN_PROGRESS │ KbArticle IN_PROGRESS (addendum x2)")
    print("  시나리오 3: RAG-CAPTURE-BATCH  │ Incident RESOLVED    │ KbArticle PUBLISHED   (addendum x3)")
    print("  시나리오 4: RAG-CAPTURE-PAYMENT│ Incident RESOLVED    │ KbArticle PUBLISHED   (addendum x3)")
    print("  시나리오 5: RAG-CAPTURE-AUTH   │ Incident OPEN        │ KbArticle DRAFT       (addendum x0)")
    print("  시나리오 6: RAG-CAPTURE-SEARCH │ Incident IN_PROGRESS │ KbArticle IN_PROGRESS (addendum x2)")
    print()
    print(" RAG 활용 포인트:")
    print("  - PUBLISHED KbArticle + addendum → RAG retrieval context로 직접 활용")
    print("  - IN_PROGRESS KbArticle + addendum → 진행 중 지식으로 참고")
    print("  - IncidentService.analyze(logHash) 호출 시 최신 addendum 3개 반환")
    print("=" * 60)

    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
