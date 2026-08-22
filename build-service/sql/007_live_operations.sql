CREATE TABLE IF NOT EXISTS appforge_build_log_lines (
    id BIGSERIAL PRIMARY KEY,
    build_id UUID NOT NULL REFERENCES appforge_builds(id) ON DELETE CASCADE,
    line TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_build_log_lines_build_id
ON appforge_build_log_lines(build_id, id);

CREATE TABLE IF NOT EXISTS appforge_download_tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash TEXT NOT NULL UNIQUE,
    build_id UUID NOT NULL REFERENCES appforge_builds(id) ON DELETE CASCADE,
    output_kind TEXT NOT NULL CHECK (output_kind IN ('apk','aab')),
    created_by UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_download_tickets_expiry
ON appforge_download_tickets(expires_at)
WHERE used_at IS NULL;

CREATE TABLE IF NOT EXISTS appforge_idempotency_keys (
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    idempotency_key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    build_id UUID NOT NULL REFERENCES appforge_builds(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_expiry
ON appforge_idempotency_keys(expires_at);

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS last_log_id BIGINT NOT NULL DEFAULT 0;
