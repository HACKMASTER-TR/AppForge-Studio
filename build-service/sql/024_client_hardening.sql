CREATE TABLE IF NOT EXISTS appforge_play_purchase_owners (
  purchase_token_hash TEXT PRIMARY KEY,
  user_id UUID NOT NULL
    REFERENCES appforge_users(id)
    ON DELETE CASCADE,
  product_id TEXT NOT NULL,
  product_type TEXT NOT NULL
    CHECK (product_type IN ('inapp', 'subs')),
  status TEXT NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'verified', 'revoked')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_appforge_play_purchase_owners_user
  ON appforge_play_purchase_owners(user_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_appforge_play_purchase_owners_product
  ON appforge_play_purchase_owners(product_id, updated_at DESC);
