ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS cancel_requested_at TIMESTAMPTZ;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS priority INTEGER NOT NULL DEFAULT 100;

CREATE INDEX IF NOT EXISTS idx_builds_cancel_requested
ON appforge_builds(cancel_requested)
WHERE cancel_requested = TRUE;

CREATE TABLE IF NOT EXISTS appforge_project_files (
    project_id UUID NOT NULL REFERENCES appforge_projects(id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    mime_type TEXT NOT NULL DEFAULT 'text/plain',
    content_sha256 TEXT NOT NULL,
    size_bytes INTEGER NOT NULL DEFAULT 0,
    updated_by UUID REFERENCES appforge_users(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(project_id, path)
);

CREATE INDEX IF NOT EXISTS idx_project_files_updated
ON appforge_project_files(project_id, updated_at DESC);

CREATE TABLE IF NOT EXISTS appforge_project_revisions (
    id BIGSERIAL PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES appforge_projects(id) ON DELETE CASCADE,
    created_by UUID REFERENCES appforge_users(id) ON DELETE SET NULL,
    revision_kind TEXT NOT NULL DEFAULT 'manual'
        CHECK (revision_kind IN ('manual','autosave','github_import','system')),
    message TEXT NOT NULL DEFAULT '',
    snapshot JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_project_revisions_project_created
ON appforge_project_revisions(project_id, created_at DESC);

ALTER TABLE appforge_projects
ADD COLUMN IF NOT EXISTS source_repository JSONB;

ALTER TABLE appforge_projects
ADD COLUMN IF NOT EXISTS autosave_at TIMESTAMPTZ;
