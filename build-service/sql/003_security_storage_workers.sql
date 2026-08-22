ALTER TABLE appforge_users
ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;

ALTER TABLE appforge_users
ADD COLUMN IF NOT EXISTS totp_secret_encrypted TEXT;

ALTER TABLE appforge_users
ADD COLUMN IF NOT EXISTS totp_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE appforge_api_tokens
ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES appforge_teams(id) ON DELETE CASCADE;

ALTER TABLE appforge_api_tokens
ADD COLUMN IF NOT EXISTS scopes JSONB NOT NULL DEFAULT '["build:read","build:write"]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_api_tokens_team
ON appforge_api_tokens(team_id, created_at DESC);

CREATE TABLE IF NOT EXISTS appforge_auth_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    token_type TEXT NOT NULL
        CHECK (token_type IN ('email_verify','password_reset')),
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_auth_tokens_user_type
ON appforge_auth_tokens(user_id, token_type, created_at DESC);

ALTER TABLE appforge_build_jobs
ADD COLUMN IF NOT EXISTS required_capabilities JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE TABLE IF NOT EXISTS appforge_workers (
    worker_id TEXT PRIMARY KEY,
    capabilities JSONB NOT NULL DEFAULT '[]'::jsonb,
    slots INTEGER NOT NULL DEFAULT 1,
    active_jobs INTEGER NOT NULL DEFAULT 0,
    hostname TEXT NOT NULL DEFAULT '',
    version TEXT NOT NULL DEFAULT '',
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_workers_last_seen
ON appforge_workers(last_seen_at DESC);
