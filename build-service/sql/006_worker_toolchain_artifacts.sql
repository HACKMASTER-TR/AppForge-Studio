ALTER TABLE appforge_workers
ADD COLUMN IF NOT EXISTS toolchain_ok BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE appforge_workers
ADD COLUMN IF NOT EXISTS diagnostics JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE appforge_workers
ADD COLUMN IF NOT EXISTS last_error TEXT;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS worker_id TEXT;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS duration_ms BIGINT;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS artifact_manifest JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_builds_worker_id
ON appforge_builds(worker_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_workers_toolchain
ON appforge_workers(toolchain_ok, last_seen_at DESC);
