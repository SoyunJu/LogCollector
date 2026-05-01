-- incident 에 tenant_id · app_id 추가
ALTER TABLE incident
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER id,
    ADD COLUMN IF NOT EXISTS app_id    VARCHAR(64) NOT NULL DEFAULT 'default' AFTER tenant_id;

CREATE INDEX IF NOT EXISTS idx_tenant_app ON incident (tenant_id, app_id);

-- kb_article 에 tenant_id · app_id 추가
ALTER TABLE kb_article
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER id,
    ADD COLUMN IF NOT EXISTS app_id    VARCHAR(64) NOT NULL DEFAULT 'default' AFTER tenant_id;

-- kb_addendum 에 tenant_id · app_id 추가
ALTER TABLE kb_addendum
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' AFTER id,
    ADD COLUMN IF NOT EXISTS app_id    VARCHAR(64) NOT NULL DEFAULT 'default' AFTER tenant_id;