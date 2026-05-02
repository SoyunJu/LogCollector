CREATE TABLE IF NOT EXISTS error_logs (
                                          id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                          service_name        VARCHAR(50)  NOT NULL,
    host_name           VARCHAR(100),
    log_level           VARCHAR(10)  NOT NULL,
    message             TEXT         NOT NULL,
    stack_trace         LONGTEXT,
    occurred_time       DATETIME(6)  NOT NULL,
    first_occurred_time DATETIME(6)  NOT NULL,
    created_at          DATETIME(6),
    analysis_status     VARCHAR(32),
    status              VARCHAR(32),
    error_code          VARCHAR(50),
    summary             TEXT,
    resolved_at         DATETIME(6),
    log_hash            VARCHAR(64)  UNIQUE,
    repeat_count        INT,
    last_occurred_time  DATETIME(6),
    updated_at          DATETIME(6),
    INDEX idx_log_hash      (log_hash),
    INDEX idx_occurred_time (occurred_time)
    ) ENGINE=InnoDB;


CREATE TABLE IF NOT EXISTS error_log_hosts (
                                               id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                               log_hash              VARCHAR(64)  NOT NULL,
    service_name          VARCHAR(50)  NOT NULL,
    host_name             VARCHAR(100) NOT NULL,
    ip                    VARCHAR(45),
    first_occurrence_time DATETIME(6)  NOT NULL,
    last_occurrence_time  DATETIME(6)  NOT NULL,
    repeat_count          INT          NOT NULL,
    UNIQUE KEY uk_error_log_hosts_loghash_host (log_hash, host_name),
    INDEX ix_error_log_hosts_host_last    (host_name, last_occurrence_time),
    INDEX ix_error_log_hosts_loghash_last (log_hash,  last_occurrence_time)
    ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admin_users (
                                           id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                           username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    INDEX idx_username (username)
    ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS api_keys (
                                        id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                        api_key      VARCHAR(128) NOT NULL UNIQUE,
    api_key_hint VARCHAR(16)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL,
    app_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    name         VARCHAR(128) NOT NULL,
    plan         VARCHAR(32)  NOT NULL DEFAULT 'FREE',
    created_at   DATETIME     NOT NULL,
    expires_at   DATETIME,
    revoked      TINYINT(1)   NOT NULL DEFAULT 0,
    revoked_at   DATETIME,
    INDEX idx_api_key   (api_key),
    INDEX idx_tenant_id (tenant_id)
    ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS audit_logs (
                                          id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                          event_type  VARCHAR(128),
    target_type VARCHAR(128),
    target_key  VARCHAR(255),
    actor       VARCHAR(128),
    detail      VARCHAR(2000),
    created_at  DATETIME(6)   NOT NULL,
    INDEX idx_audit_target (target_type, target_key, created_at)
    ) ENGINE=InnoDB;