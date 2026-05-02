CREATE TABLE IF NOT EXISTS incident (
                                        id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                        log_hash          VARCHAR(64)  NOT NULL UNIQUE,
                                        service_name      VARCHAR(100) NOT NULL,
                                        incident_title    VARCHAR(255),
                                        summary           TEXT,
                                        created_by        VARCHAR(64),
                                        stack_trace       LONGTEXT,
                                        error_code        VARCHAR(50),
                                        error_level       VARCHAR(32)  NOT NULL,
                                        status            VARCHAR(32)  NOT NULL,
                                        first_occurred_at DATETIME(6),
                                        last_occurred_at  DATETIME(6),
                                        resolved_at       DATETIME(6),
                                        repeat_count      INT          NOT NULL DEFAULT 1,
                                        created_at        DATETIME(6)  NOT NULL,
                                        updated_at        DATETIME(6)  NOT NULL,
                                        close_eligible_at DATETIME(6),
                                        closed_at         DATETIME(6),
                                        reopened_at       DATETIME(6),
                                        INDEX idx_log_hash (log_hash),
                                        INDEX idx_status   (status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS kb_article (
                                          id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                          incident_id      BIGINT      NOT NULL,
                                          incident_title   VARCHAR(255) NOT NULL,
                                          content          LONGTEXT     NOT NULL,
                                          status           VARCHAR(32)  NOT NULL,
                                          confidence_level INT          NOT NULL DEFAULT 1,
                                          created_by       VARCHAR(32)  NOT NULL,
                                          last_activity_at DATETIME(6)  NOT NULL,
                                          created_at       DATETIME(6)  NOT NULL,
                                          updated_at       DATETIME(6)  NOT NULL,
                                          published_at     DATETIME(6),
                                          recur_at         DATETIME(6),
                                          INDEX idx_incident_id (incident_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS kb_addendum (
                                           id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                           kb_article_id BIGINT      NOT NULL,
                                           content       LONGTEXT     NOT NULL,
                                           created_by    VARCHAR(32)  NOT NULL,
                                           created_at    DATETIME(6)  NOT NULL,
                                           INDEX idx_kb_article_id (kb_article_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS kb_tag (
                                      id      BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                      keyword VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS kb_article_tag (
                                              kb_article_id BIGINT NOT NULL,
                                              kb_tag_id     BIGINT NOT NULL,
                                              PRIMARY KEY (kb_article_id, kb_tag_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS kb_event_outbox (
                                               id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                               log_hash      VARCHAR(64) NOT NULL,
                                               event_type    VARCHAR(32) NOT NULL,
                                               payload       TEXT        NOT NULL,
                                               status        VARCHAR(32) NOT NULL,
                                               attempt_count INT         NOT NULL DEFAULT 0,
                                               next_retry_at DATETIME(6),
                                               last_error    LONGTEXT,
                                               created_at    DATETIME(6) NOT NULL,
                                               updated_at    DATETIME(6) NOT NULL,
                                               INDEX idx_status_retry (status, next_retry_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lc_ignore_outbox (
                                                id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                                log_hash      VARCHAR(64) NOT NULL,
                                                action        VARCHAR(32) NOT NULL,
                                                status        VARCHAR(32) NOT NULL,
                                                attempt_count INT         NOT NULL DEFAULT 0,
                                                next_retry_at DATETIME(6),
                                                last_error    LONGTEXT,
                                                created_at    DATETIME(6) NOT NULL,
                                                updated_at    DATETIME(6) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS system_draft (
                                            id                    BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                            incident_id           BIGINT      NOT NULL,
                                            host_count            INT         NOT NULL,
                                            repeat_count          INT         NOT NULL,
                                            reason                VARCHAR(64) NOT NULL,
                                            created_kb_article_id BIGINT,
                                            created_at            DATETIME(6) NOT NULL,
                                            INDEX idx_incident_id (incident_id)
) ENGINE=InnoDB;