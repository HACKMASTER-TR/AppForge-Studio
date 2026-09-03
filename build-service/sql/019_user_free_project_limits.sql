CREATE TABLE IF NOT EXISTS appforge_user_project_limits (
    user_id UUID PRIMARY KEY
        REFERENCES appforge_users(id)
        ON DELETE CASCADE,

    free_project_limit INTEGER NOT NULL
        CHECK (
            free_project_limit >= 1
            AND free_project_limit <= 100000
        ),

    updated_by UUID
        REFERENCES appforge_users(id)
        ON DELETE SET NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_appforge_user_project_limits_limit
ON appforge_user_project_limits(free_project_limit);

-- FREE test hesabına toplam 50 farklı proje hakkı.
INSERT INTO appforge_user_project_limits(
    user_id,
    free_project_limit,
    updated_by,
    updated_at
)
SELECT
    u.id,
    50,
    NULL,
    NOW()
FROM appforge_users u
WHERE LOWER(TRIM(u.email)) = 'heyomert@gmail.com'
ON CONFLICT(user_id)
DO NOTHING;
