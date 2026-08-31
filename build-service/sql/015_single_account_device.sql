CREATE TABLE IF NOT EXISTS appforge_account_devices (
    device_hash TEXT PRIMARY KEY,
    user_id UUID UNIQUE REFERENCES appforge_users(id) ON DELETE SET NULL,
    bound_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    transferred_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_account_devices_user
ON appforge_account_devices(user_id);

