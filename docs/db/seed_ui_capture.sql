/* =========================================================
   UI CAPTURE SEED (LogCollector)
   - DB: knowledge_base (incident, kb_article, kb_addendum, system_draft)
   - DB: logcollector   (error_log_hosts)
   ========================================================= */

-- ========== 0) CLEANUP ==========
SET FOREIGN_KEY_CHECKS = 0;

-- knowledge_base cleanup
USE knowledge_base;

DELETE FROM kb_addendum;
DELETE FROM kb_article;
DELETE FROM system_draft;
DELETE FROM incident;

-- logcollector cleanup
USE logcollector;
DELETE FROM error_log_hosts;

SET FOREIGN_KEY_CHECKS = 1;


-- ========== 1) INSERT INCIDENTS (knowledge_base) ==========
USE knowledge_base;

INSERT INTO incident
(id, close_eligible_at, closed_at, created_at, created_by, error_code, error_level,
 first_occurred_at, incident_title, last_occurred_at, log_hash, reopened_at,
 repeat_count, resolved_at, service_name, stack_trace, status, summary, updated_at)
VALUES
    (1001, NULL, NULL, NOW(6), 'system', 'REDIS_TIMEOUT', 'ERROR',
     DATE_SUB(NOW(6), INTERVAL 4 DAY), 'Redis timeout spikes on CAPTURE-API', DATE_SUB(NOW(6), INTERVAL 5 MINUTE),
     'hash_capture_redis_timeout_001', NULL, 23, NULL, 'CAPTURE-API',
     'io.lettuce.core.RedisCommandTimeoutException: Command timed out after 500ms
  at com.soyunju.logcollector.service.redis.RedisQueueService.enqueue(RedisQueueService.java:72)', 'OPEN',
     'Redis enqueue latency increased; needs infra check.', NOW(6)),

    (1002, NULL, NULL, NOW(6), 'system', 'DB_POOL', 'CRITICAL',
     DATE_SUB(NOW(6), INTERVAL 6 DAY), 'DB connection pool exhausted', DATE_SUB(NOW(6), INTERVAL 20 MINUTE),
     'hash_capture_db_pool_002', NULL, 8, NULL, 'CAPTURE-ORDER',
     'java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available
  at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:196)', 'IN_PROGRESS',
     'DB pool saturation during burst traffic.', NOW(6)),

    (1003, NULL, NULL, NOW(6), 'system', 'NPE', 'EXCEPTION',
     DATE_SUB(NOW(6), INTERVAL 2 DAY), 'NullPointerException in publish()', DATE_SUB(NOW(6), INTERVAL 2 HOUR),
     'hash_capture_npe_publish_003', NULL, 5, NULL, 'CAPTURE-AUTH',
     'java.lang.NullPointerException: Cannot invoke "String.length()" because "x" is null
  at com.soyunju.logcollector.service.KbArticleService.publish(KbArticleService.java:204)', 'OPEN',
     'NPE likely caused by missing field in DTO.', NOW(6)),

    (1004, NULL, NULL, NOW(6), 'system', 'UPSTREAM_502', 'WARN',
     DATE_SUB(NOW(6), INTERVAL 8 DAY), 'Upstream 502 intermittently', DATE_SUB(NOW(6), INTERVAL 1 DAY),
     'hash_capture_upstream_502_004', NULL, 41, DATE_SUB(NOW(6), INTERVAL 1 DAY), 'CAPTURE-PAYMENT',
     'org.springframework.web.client.HttpServerErrorException$BadGateway: 502 Bad Gateway
  at com.soyunju.logcollector.client.ExternalClient.call(ExternalClient.java:55)', 'RESOLVED',
     'Intermittent 502 resolved after upstream fix.', NOW(6)),

    (1005, NULL, DATE_SUB(NOW(6), INTERVAL 10 DAY), NOW(6), 'system', 'OOM', 'FATAL',
     DATE_SUB(NOW(6), INTERVAL 15 DAY), 'OutOfMemoryError on batch job', DATE_SUB(NOW(6), INTERVAL 10 DAY),
     'hash_capture_oom_005', NULL, 3, DATE_SUB(NOW(6), INTERVAL 10 DAY), 'CAPTURE-SEARCH',
     'java.lang.OutOfMemoryError: Java heap space
  at com.soyunju.logcollector.batch.BatchRunner.run(BatchRunner.java:88)', 'CLOSED',
     'Closed after heap tuning + load reduction.', NOW(6)),

    (1006, NULL, NULL, NOW(6), 'system', 'LOCK_TIMEOUT', 'ERROR',
     DATE_SUB(NOW(6), INTERVAL 1 DAY), 'Lock wait timeout on transaction', DATE_SUB(NOW(6), INTERVAL 30 MINUTE),
     'hash_capture_lock_timeout_006', NULL, 12, NULL, 'CAPTURE-ORDER',
     'Lock wait timeout exceeded; try restarting transaction
  at com.soyunju.logcollector.repository.IncidentRepository.save(IncidentRepository.java:61)', 'OPEN',
     'Potential hot row / long txn in DB.', NOW(6)),

    (1007, NULL, NULL, NOW(6), 'system', 'RECUR', 'WARN',
     DATE_SUB(NOW(6), INTERVAL 12 DAY), 'Recurring warning - noisy but stable', DATE_SUB(NOW(6), INTERVAL 3 DAY),
     'hash_capture_recur_warn_007', NULL, 120, NULL, 'CAPTURE-NOTI',
     NULL, 'IGNORED',
     'Ignored due to low severity / high noise.', NOW(6)),

    (1008, NULL, NULL, NOW(6), 'system', 'REOPEN', 'ERROR',
     DATE_SUB(NOW(6), INTERVAL 20 DAY), 'Resolved then reopened due to recurrence', DATE_SUB(NOW(6), INTERVAL 10 MINUTE),
     'hash_capture_reopen_008', DATE_SUB(NOW(6), INTERVAL 1 HOUR), 57, DATE_SUB(NOW(6), INTERVAL 7 DAY), 'CAPTURE-API',
     'Recurred after deploy rollback; needs regression check.', 'OPEN',
     'Was resolved, then recurrence detected.', NOW(6));

ALTER TABLE incident AUTO_INCREMENT = 2000;


-- ========== 2) INSERT ERROR_LOG_HOSTS (logcollector) ==========
USE logcollector;

INSERT INTO error_log_hosts
(id, first_occurrence_time, host_name, ip, last_occurrence_time, log_hash, repeat_count, service_name)
VALUES
    (20001, DATE_SUB(NOW(6), INTERVAL 4 DAY), 'pod-1', '10.0.0.11', DATE_SUB(NOW(6), INTERVAL 5 MINUTE),  'hash_capture_redis_timeout_001', 12, 'CAPTURE-API'),
    (20002, DATE_SUB(NOW(6), INTERVAL 4 DAY), 'pod-2', '10.0.0.12', DATE_SUB(NOW(6), INTERVAL 7 MINUTE),  'hash_capture_redis_timeout_001', 11, 'CAPTURE-API'),

    (20003, DATE_SUB(NOW(6), INTERVAL 6 DAY), 'pod-3', '10.0.0.13', DATE_SUB(NOW(6), INTERVAL 20 MINUTE), 'hash_capture_db_pool_002', 5, 'CAPTURE-ORDER'),
    (20004, DATE_SUB(NOW(6), INTERVAL 6 DAY), 'pod-4', '10.0.0.14', DATE_SUB(NOW(6), INTERVAL 23 MINUTE), 'hash_capture_db_pool_002', 3, 'CAPTURE-ORDER'),

    (20005, DATE_SUB(NOW(6), INTERVAL 2 DAY), 'pod-2', '10.0.0.12', DATE_SUB(NOW(6), INTERVAL 2 HOUR),    'hash_capture_npe_publish_003', 5, 'CAPTURE-AUTH'),

    (20006, DATE_SUB(NOW(6), INTERVAL 8 DAY), 'pod-1', '10.0.0.11', DATE_SUB(NOW(6), INTERVAL 1 DAY),     'hash_capture_upstream_502_004', 22, 'CAPTURE-PAYMENT'),
    (20007, DATE_SUB(NOW(6), INTERVAL 8 DAY), 'pod-5', '10.0.0.15', DATE_SUB(NOW(6), INTERVAL 1 DAY),     'hash_capture_upstream_502_004', 19, 'CAPTURE-PAYMENT'),

    (20008, DATE_SUB(NOW(6), INTERVAL 15 DAY), 'pod-6', '10.0.0.16', DATE_SUB(NOW(6), INTERVAL 10 DAY),   'hash_capture_oom_005', 3, 'CAPTURE-SEARCH'),

    (20009, DATE_SUB(NOW(6), INTERVAL 1 DAY), 'pod-3', '10.0.0.13', DATE_SUB(NOW(6), INTERVAL 30 MINUTE), 'hash_capture_lock_timeout_006', 7, 'CAPTURE-ORDER'),
    (20010, DATE_SUB(NOW(6), INTERVAL 1 DAY), 'pod-4', '10.0.0.14', DATE_SUB(NOW(6), INTERVAL 31 MINUTE), 'hash_capture_lock_timeout_006', 5, 'CAPTURE-ORDER'),

    (20011, DATE_SUB(NOW(6), INTERVAL 12 DAY), 'pod-2', '10.0.0.12', DATE_SUB(NOW(6), INTERVAL 3 DAY),    'hash_capture_recur_warn_007', 80, 'CAPTURE-NOTI'),
    (20012, DATE_SUB(NOW(6), INTERVAL 12 DAY), 'pod-1', '10.0.0.11', DATE_SUB(NOW(6), INTERVAL 3 DAY),    'hash_capture_recur_warn_007', 40, 'CAPTURE-NOTI'),

    (20013, DATE_SUB(NOW(6), INTERVAL 20 DAY), 'pod-5', '10.0.0.15', DATE_SUB(NOW(6), INTERVAL 10 MINUTE),'hash_capture_reopen_008', 57, 'CAPTURE-API');

ALTER TABLE error_log_hosts AUTO_INCREMENT = 30000;


-- ========== 3) INSERT KB ARTICLES (knowledge_base) ==========
USE knowledge_base;

INSERT INTO kb_article
(id, confidence_level, content, created_at, created_by, incident_title,
 last_activity_at, published_at, recur_at, status, updated_at, incident_id)
VALUES
    (3001, 72,
     'Symptoms: Redis enqueue timeout spikes under burst traffic.\nRoot cause candidates: network jitter / Redis saturation / client timeout.\nActions: increase timeout, monitor latency, scale Redis, review client settings.',
     NOW(6), 'system', 'Redis timeout spikes on CAPTURE-API',
     NOW(6), NULL, NULL, 'IN_PROGRESS', NOW(6), 1001),

    (3002, 88,
     'Issue: Upstream intermittently returned 502.\nFix: upstream deployment rollback + healthcheck tuning.\nPrevention: add circuit breaker and retries with backoff.',
     NOW(6), 'admin', 'Upstream 502 intermittently',
     NOW(6), DATE_SUB(NOW(6), INTERVAL 1 DAY), NULL, 'PUBLISHED', NOW(6), 1004),

    (3003, 65,
     'Issue: Lock wait timeout.\nMitigation: reduce transaction scope, add index, avoid long-running locks.\nNext: identify hot rows and slow queries.',
     NOW(6), 'user', 'Lock wait timeout on transaction',
     NOW(6), NULL, NULL, 'DRAFT', NOW(6), 1006);

ALTER TABLE kb_article AUTO_INCREMENT = 4000;


-- ========== 4) INSERT KB ADDENDUMS (knowledge_base) ==========
INSERT INTO kb_addendum
(id, content, created_at, created_by, kb_article_id)
VALUES
    (4001, 'Addendum: confirmed Redis CPU peaked at incident time; scale-out recommended.', NOW(6), 'system', 3001),
    (4002, 'Addendum: retry policy adjusted; error rate dropped.', NOW(6), 'user', 3002),
    (4003, 'Addendum: identified missing composite index; added index candidate (colA,colB).', NOW(6), 'admin', 3003);

ALTER TABLE kb_addendum AUTO_INCREMENT = 5000;


-- ========== 5) INSERT SYSTEM_DRAFT (knowledge_base) ==========
INSERT INTO system_draft
(id, created_at, created_kb_article_id, host_count, reason, repeat_count, incident_id)
VALUES
    (5001, NOW(6), NULL, 2, 'HOST_SPREAD', 23, 1001),
    (5002, NOW(6), 3002, 2, 'HIGH_RECUR', 41, 1004),
    (5003, NOW(6), NULL, 2, 'HIGH_RECUR', 120, 1007);

ALTER TABLE system_draft AUTO_INCREMENT = 6000;
