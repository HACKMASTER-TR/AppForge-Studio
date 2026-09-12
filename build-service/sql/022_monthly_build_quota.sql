/*
 * AppForge Pro Monthly quota foundation.
 *
 * Base plan:
 *   50 successful distinct projects / subscription cycle
 *   100 successful builds / subscription cycle
 *
 * Failed/cancelled builds never consume successful-build quota.
 * Ledger rows are intentionally independent from appforge_builds deletion:
 * deleting build history must NOT restore consumed quota.
 */

ALTER TABLE appforge_projects
ADD COLUMN IF NOT EXISTS package_locked_at TIMESTAMPTZ;


/*
 * Successful build ledger.
 *
 * build_id is deliberately NOT a foreign key to appforge_builds.
 * Historical build deletion must not restore quota.
 */
CREATE TABLE IF NOT EXISTS appforge_pro_monthly_build_usage (
    build_id UUID PRIMARY KEY,

    user_id UUID NOT NULL
        REFERENCES appforge_users(id)
        ON DELETE CASCADE,

    cycle_end TIMESTAMPTZ NOT NULL,

    project_id UUID
        REFERENCES appforge_projects(id)
        ON DELETE SET NULL,

    package_name TEXT NOT NULL,

    completed_at TIMESTAMPTZ
        NOT NULL
        DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_appforge_monthly_build_usage_user_cycle
ON appforge_pro_monthly_build_usage (
    user_id,
    cycle_end,
    completed_at
);


/*
 * Reservation ledger prevents concurrent builds from exceeding
 * the monthly successful-build allowance.
 *
 * A reservation consumes no final quota until SUCCESS.
 */
CREATE TABLE IF NOT EXISTS appforge_pro_monthly_build_reservations (
    build_id UUID PRIMARY KEY,

    user_id UUID NOT NULL
        REFERENCES appforge_users(id)
        ON DELETE CASCADE,

    cycle_end TIMESTAMPTZ NOT NULL,

    project_id UUID
        REFERENCES appforge_projects(id)
        ON DELETE SET NULL,

    package_name TEXT NOT NULL,

    created_at TIMESTAMPTZ
        NOT NULL
        DEFAULT NOW(),

    expires_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_appforge_monthly_build_res_user_cycle
ON appforge_pro_monthly_build_reservations (
    user_id,
    cycle_end,
    expires_at
);


/*
 * Once a project has a successful build its package identity
 * can be marked locked. Actual enforcement is performed by
 * the application layer in the next stage.
 */
CREATE INDEX IF NOT EXISTS idx_appforge_projects_package_locked
ON appforge_projects (
    user_id,
    package_name
)
WHERE package_locked_at IS NOT NULL;
