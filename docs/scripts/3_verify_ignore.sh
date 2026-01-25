#!/usr/bin/env sh
set -eu

# ==============================
# Config
# ==============================
BASE_URL="${BASE_URL:-http://app:8081}"

MYSQL_HOST="${MYSQL_HOST:-logcollector-db}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-root}"
MYSQL_DB="${MYSQL_DB:-knowledge_base}"

SERVICE_NAME="${SERVICE_NAME:-TESTE-API}"
MESSAGE="${MESSAGE:-[VERIFY_IGNORE] RedisCommandTimeoutException: Command timed out after 500ms.}"
STACKTRACE="${STACKTRACE:-}"
LOG_LEVEL="${LOG_LEVEL:-ERROR}"

# 비동기 반영 대기 시간 (폴링 간격)
WAIT_POLL_SEC="${WAIT_POLL_SEC:-2}"

log() { printf "\n[%s] %s\n" "$(date '+%F %T')" "$*"; }

mysql_q() {
  mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" -p"$MYSQL_PASS" "$MYSQL_DB" -N -e "$1"
}

# 상태 변경 대기 (Polling)
wait_incident_status_eq() {
  local hash="$1"
  local expected="$2"
  local label="$3"
  local i=0
  local current=""

  while [ $i -lt 20 ]; do
    current="$(mysql_q "SELECT status FROM incident WHERE log_hash='$hash' LIMIT 1;")"
    if [ "$current" = "$expected" ]; then
      log "OK: $label matches '$expected'"
      return 0
    fi
    sleep 1
    i=$((i+1))
  done

  echo "TIMEOUT: $label expected '$expected' but got '$current'" >&2
  exit 1
}

send_log() {
  curl -sS -f -H "Content-Type: application/json" -X POST "$BASE_URL/api/logs" -d "{
    \"serviceName\": \"$SERVICE_NAME\",
    \"logLevel\": \"$LOG_LEVEL\",
    \"message\": \"$MESSAGE\",
    \"stackTrace\": \"$STACKTRACE\",
    \"hostName\": \"$1\"
  }" >/dev/null
}

calc_log_hash() {
  mysql_q "SELECT log_hash FROM incident WHERE service_name='${SERVICE_NAME}' ORDER BY updated_at DESC LIMIT 1;"
}

mark_ignored() {
  curl -sS -f -X PATCH "$BASE_URL/api/incidents/$1/status?newStatus=IGNORED" >/dev/null
}

mark_unignored() {
  curl -sS -f -X PATCH "$BASE_URL/api/incidents/$1/status?newStatus=OPEN" >/dev/null
}

assert_eq() {
  if [ "$1" != "$2" ]; then
    echo "ASSERT FAIL: $3 (expected='$1', actual='$2')" >&2
    exit 1
  fi
}

# [신규] 값이 달라졌는지 확인 (시간 갱신 검증용)
assert_ne() {
  if [ "$1" = "$2" ]; then
    echo "ASSERT FAIL: $3 (expected different values, but got '$1')" >&2
    exit 1
  fi
}

# ==============================
# Main Scenario
# ==============================

log "1) Create incident"
send_log "host-1"

# 생성 안정화 대기
i=0
LOG_HASH=""
while [ $i -lt 10 ]; do
  LOG_HASH="$(calc_log_hash)"
  if [ -n "$LOG_HASH" ]; then break; fi
  sleep 1
  i=$((i+1))
done

if [ -z "$LOG_HASH" ]; then
  echo "FAIL: Failed to get LOG_HASH from DB" >&2
  exit 1
fi
log "logHash=$LOG_HASH"

wait_incident_status_eq "$LOG_HASH" "OPEN" "Initial Status"

RC0="$(mysql_q "SELECT repeat_count FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;")"
LO0="$(mysql_q "SELECT IFNULL(DATE_FORMAT(last_occurred_at,'%Y-%m-%d %H:%i:%s'),'') FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;")"

log "baseline repeat_count=$RC0, last_occurred_at=$LO0"

log "2) Mark IGNORED"
mark_ignored "$LOG_HASH"
wait_incident_status_eq "$LOG_HASH" "IGNORED" "incident.status after IGNORED"

log "3) Re-send same log (Expectation: repeat_count UNCHANGED, last_occurred_at UPDATED)"
# DB last_occurred_at이 초 단위라 너무 빨리 보내면 변경 안 보일 수 있음 -> 1초 대기
sleep 1.5
send_log "host-2"

# 잠시 대기 (비동기 반영)
sleep "$WAIT_POLL_SEC"

RC1="$(mysql_q "SELECT repeat_count FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;")"
LO1="$(mysql_q "SELECT IFNULL(DATE_FORMAT(last_occurred_at,'%Y-%m-%d %H:%i:%s'),'') FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;")"

log "Current: repeat_count=$RC1, last_occurred_at=$LO1"

# 검증 1: 카운트는 늘어나면 안 됨
assert_eq "$RC0" "$RC1" "repeat_count must not increase while IGNORED"

# 검증 2: 시간은 변해야 함 (갱신 확인)
assert_ne "$LO0" "$LO1" "last_occurred_at must update while IGNORED"

log "OK: Log ingestion suppressed (count fixed), but timestamp updated."

log "4) UNIGNORE then re-send (must ingest normally)"
mark_unignored "$LOG_HASH"
wait_incident_status_eq "$LOG_HASH" "OPEN" "incident.status after UNIGNORE"

send_log "host-3"

# 카운트 증가 확인 대기
i=0
while [ $i -lt 10 ]; do
  RC2="$(mysql_q "SELECT repeat_count FROM incident WHERE log_hash='${LOG_HASH}' LIMIT 1;")"
  if [ "$RC2" -gt "$RC0" ]; then
    log "OK: repeat_count increased to $RC2"
    break
  fi
  sleep 1
  i=$((i+1))
done

if [ "$RC2" -le "$RC0" ]; then
  echo "FAIL: repeat_count did not increase" >&2
  exit 1
fi

log "DONE"

# docker run --rm -it `
  #>>   --network compose_default `
  #>>   -e BASE_URL="http://app:8081" `
  #>>   -e MYSQL_HOST="logcollector-db" `
  #>>   -e MYSQL_PORT="3306" `
  #>>   -e MYSQL_USER="root" `
  #>>   -e MYSQL_PASS="root" `
  #>>   -e MYSQL_DB="knowledge_base" `
  #>>   -e SERVICE_NAME="TESTE-API-S4-RETRY-$(Get-Date -Format 'HHmmss')" `
  #>>   -e MESSAGE="[VERIFY_IGNORE] UpdateDateOnly" `
  #>>   -v "${PROJECT_ROOT}:/work" `
  #>>   -w /work `
  #>>   alpine:3.20 sh -lc `
  #>>   "apk add --no-cache curl mariadb-client bash && chmod +x docs/scripts/3_verify_ignore.sh && docs/scripts/3_verify_ignore.sh"