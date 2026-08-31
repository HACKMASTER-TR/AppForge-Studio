CREATE TABLE IF NOT EXISTS appforge_teams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    slug TEXT NOT NULL UNIQUE,
    owner_user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS appforge_team_members (
    team_id UUID NOT NULL REFERENCES appforge_teams(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    role TEXT NOT NULL DEFAULT 'member'
        CHECK (role IN ('owner', 'admin', 'member', 'viewer')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(team_id, user_id)
);

CREATE TABLE IF NOT EXISTS appforge_team_invites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_id UUID NOT NULL REFERENCES appforge_teams(id) ON DELETE CASCADE,
    email TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'member'
        CHECK (role IN ('admin', 'member', 'viewer')),
    token_hash TEXT NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    created_by UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE appforge_projects
ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES appforge_teams(id) ON DELETE SET NULL;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES appforge_teams(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_projects_team
ON appforge_projects(team_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_builds_team
ON appforge_builds(team_id, created_at DESC);

CREATE TABLE IF NOT EXISTS appforge_build_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    build_id UUID NOT NULL UNIQUE REFERENCES appforge_builds(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    team_id UUID REFERENCES appforge_teams(id) ON DELETE SET NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued'
        CHECK (status IN ('queued', 'running', 'success', 'failed', 'cancelled')),
    priority INTEGER NOT NULL DEFAULT 100,
    attempts INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 2,
    worker_id TEXT,
    locked_at TIMESTAMPTZ,
    heartbeat_at TIMESTAMPTZ,
    available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_build_jobs_claim
ON appforge_build_jobs(status, available_at, priority, created_at);

CREATE INDEX IF NOT EXISTS idx_build_jobs_worker
ON appforge_build_jobs(worker_id, heartbeat_at);

CREATE TABLE IF NOT EXISTS appforge_build_events (
    id BIGSERIAL PRIMARY KEY,
    build_id UUID NOT NULL REFERENCES appforge_builds(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    team_id UUID REFERENCES appforge_teams(id) ON DELETE SET NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_build_events_user_created
ON appforge_build_events(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_build_events_team_created
ON appforge_build_events(team_id, created_at DESC);
