-- Staging only. Apply separately after tests and migration review.
-- Never stores a plaintext activation code.
CREATE TABLE IF NOT EXISTS pro_activation_codes (
  id TEXT PRIMARY KEY NOT NULL,
  code_hash TEXT NOT NULL UNIQUE,
  state TEXT NOT NULL DEFAULT 'issued'
    CHECK(state IN ('issued', 'redeemed', 'revoked')),
  created_by_hash TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  redeemed_at INTEGER,
  redeemed_by_thumbprint TEXT,
  redemption_id TEXT UNIQUE,
  revoked_at INTEGER,
  revoked_by_hash TEXT
);

CREATE INDEX IF NOT EXISTS idx_pro_codes_created
  ON pro_activation_codes(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_pro_codes_state
  ON pro_activation_codes(state, expires_at);
