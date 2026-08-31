ALTER TABLE appforge_team_members
ADD COLUMN IF NOT EXISTS permission_overrides JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE appforge_team_invites
ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS cache_key TEXT;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS cache_hit BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_builds_cache_key
ON appforge_builds(cache_key)
WHERE cache_key IS NOT NULL;

CREATE TABLE IF NOT EXISTS appforge_build_cache (
    cache_key TEXT PRIMARY KEY,
    source_build_id UUID NOT NULL REFERENCES appforge_builds(id) ON DELETE CASCADE,
    outputs JSONB NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_build_cache_expires
ON appforge_build_cache(expires_at);

CREATE TABLE IF NOT EXISTS appforge_permission_audit (
    id BIGSERIAL PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES appforge_teams(id) ON DELETE CASCADE,
    actor_user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    target_user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    before_permissions JSONB NOT NULL DEFAULT '{}'::jsonb,
    after_permissions JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_permission_audit_team
ON appforge_permission_audit(team_id, created_at DESC);
