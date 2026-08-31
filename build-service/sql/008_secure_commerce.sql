CREATE TABLE IF NOT EXISTS appforge_play_purchases (
    purchase_token_hash TEXT PRIMARY KEY,
    package_name TEXT NOT NULL,
    product_id TEXT NOT NULL,
    product_type TEXT NOT NULL
        CHECK (product_type IN ('inapp','subs')),
    play_state TEXT NOT NULL,
    entitlement BOOLEAN NOT NULL DEFAULT FALSE,
    acknowledgement_state TEXT,
    consumption_state TEXT,
    expiry_time TIMESTAMPTZ,
    test_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    processed_by_server BOOLEAN NOT NULL DEFAULT FALSE,
    first_verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_verified_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    verification_count INTEGER NOT NULL DEFAULT 1,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_play_purchases_package_product
ON appforge_play_purchases(package_name, product_id, last_verified_at DESC);

CREATE INDEX IF NOT EXISTS idx_play_purchases_entitlement
ON appforge_play_purchases(entitlement, last_verified_at DESC);
