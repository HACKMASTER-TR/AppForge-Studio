-- AppForge production burst / queue scaling indexes.

CREATE INDEX IF NOT EXISTS idx_build_jobs_user_active
ON appforge_build_jobs(
    user_id,
    created_at DESC
)
WHERE status IN (
    'queued',
    'running'
);

CREATE INDEX IF NOT EXISTS idx_build_jobs_queued_priority
ON appforge_build_jobs(
    priority ASC,
    created_at ASC
)
WHERE status = 'queued';
