CREATE TABLE IF NOT EXISTS appforge_pro_entitlements (
    user_id UUID PRIMARY KEY REFERENCES appforge_users(id) ON DELETE CASCADE,
    status TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active','revoked','expired')),
    source TEXT NOT NULL DEFAULT 'google_play',
    product_id TEXT,
    purchase_token_hash TEXT,
    expires_at TIMESTAMPTZ,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_pro_entitlements_status
ON appforge_pro_entitlements(status, expires_at);

CREATE TABLE IF NOT EXISTS appforge_integrity_audits (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES appforge_users(id) ON DELETE SET NULL,
    action TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    app_recognition_verdict TEXT,
    app_licensing_verdict TEXT,
    device_verdicts JSONB NOT NULL DEFAULT '[]'::jsonb,
    certificate_sha256 JSONB NOT NULL DEFAULT '[]'::jsonb,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_integrity_audits_user_created
ON appforge_integrity_audits(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_integrity_audits_failed
ON appforge_integrity_audits(passed, created_at DESC);
