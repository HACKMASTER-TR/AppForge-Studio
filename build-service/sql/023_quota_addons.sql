/*
 * AppForge Pro Monthly consumable quota add-ons.
 *
 * +10 => +10 projects / +20 successful builds
 * +25 => +25 projects / +50 successful builds
 * +50 => +50 projects / +100 successful builds
 *
 * Grants belong to exactly one subscription cycle.
 * No rollover.
 *
 * purchase_token_hash is globally unique so one Google Play
 * purchase token can never grant quota twice.
 */

CREATE TABLE IF NOT EXISTS appforge_quota_addon_redemptions (
    purchase_token_hash TEXT PRIMARY KEY,

    user_id UUID NOT NULL
        REFERENCES appforge_users(id)
        ON DELETE CASCADE,

    product_id TEXT NOT NULL,

    cycle_end TIMESTAMPTZ NOT NULL,

    project_bonus INTEGER NOT NULL
        CHECK(project_bonus > 0),

    build_bonus INTEGER NOT NULL
        CHECK(build_bonus > 0),

    status TEXT NOT NULL
        DEFAULT 'pending'
        CHECK(
            status IN (
                'pending',
                'verified',
                'granted',
                'rejected'
            )
        ),

    test_purchase BOOLEAN NOT NULL
        DEFAULT FALSE,

    play_verified_at TIMESTAMPTZ,

    granted_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT NOW(),

    updated_at TIMESTAMPTZ NOT NULL
        DEFAULT NOW(),

    last_error TEXT
);


CREATE INDEX IF NOT EXISTS idx_appforge_quota_addons_user_cycle
ON appforge_quota_addon_redemptions (
    user_id,
    cycle_end,
    status
);


CREATE INDEX IF NOT EXISTS idx_appforge_quota_addons_product
ON appforge_quota_addon_redemptions (
    product_id,
    created_at
);
