CREATE TABLE IF NOT EXISTS appforge_free_project_slots (
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    package_name TEXT NOT NULL,
    first_claimed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY(user_id, package_name)
);

CREATE INDEX IF NOT EXISTS idx_free_project_slots_user
ON appforge_free_project_slots(user_id, first_claimed_at);
