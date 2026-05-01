-- error_logs 에 tenant_id · app_id 추가
ALTER TABLE error_logs
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER id,
    ADD COLUMN IF NOT EXISTS app_id    VARCHAR(64) NOT NULL DEFAULT 'default' AFTER tenant_id;

CREATE INDEX IF NOT EXISTS idx_tenant_app ON error_logs (tenant_id, app_id);

-- error_log_hosts 에 tenant_id · app_id 추가
ALTER TABLE error_log_hosts
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER id,
    ADD COLUMN IF NOT EXISTS app_id    VARCHAR(64) NOT NULL DEFAULT 'default' AFTER tenant_id;