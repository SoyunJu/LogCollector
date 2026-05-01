-- admin_users 테이블
CREATE TABLE IF NOT EXISTS admin_users (
                                           id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                           username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(32)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    INDEX idx_username (username)
    ) ENGINE=InnoDB;

-- api_keys 테이블
CREATE TABLE IF NOT EXISTS api_keys (
                                        id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                        api_key       VARCHAR(128) NOT NULL UNIQUE,
    api_key_hint  VARCHAR(16)  NOT NULL,
    tenant_id     VARCHAR(64)  NOT NULL,
    app_id        VARCHAR(64)  NOT NULL DEFAULT 'default',
    name          VARCHAR(128) NOT NULL,
    plan          VARCHAR(32)  NOT NULL DEFAULT 'FREE',
    created_at    DATETIME     NOT NULL,
    expires_at    DATETIME,
    revoked       TINYINT(1)   NOT NULL DEFAULT 0,
    revoked_at    DATETIME,
    INDEX idx_api_key   (api_key),
    INDEX idx_tenant_id (tenant_id)
    ) ENGINE=InnoDB;

-- audit_log 테이블
CREATE TABLE IF NOT EXISTS audit_log (
                                         id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                         actor      VARCHAR(128),
    action     VARCHAR(128) NOT NULL,
    target     VARCHAR(255),
    detail     TEXT,
    created_at DATETIME(6)  NOT NULL
    ) ENGINE=InnoDB;