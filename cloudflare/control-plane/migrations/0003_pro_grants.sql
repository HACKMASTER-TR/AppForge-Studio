-- STAGING ONLY: DO NOT APPLY UNTIL REDEMPTION REVIEW.
-- Apply after 0001 and 0002, never before them.
-- Installation ID alone is not entitlement proof.

CREATE TABLE IF NOT EXISTS pro_admin_grants (
  installation_id TEXT PRIMARY KEY NOT NULL
    REFERENCES installations(id),
  activation_code_id TEXT NOT NULL UNIQUE
    REFERENCES pro_activation_codes(id),
  state TEXT NOT NULL DEFAULT 'active'
    CHECK(state IN ('active', 'revoked')),
  granted_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  revoked_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_pro_grants_state
  ON pro_admin_grants(state);

-- The public SPKI is stored separately from the installation ID.
-- The installation key thumbprint must match this exact public key.
-- The Android private key NEVER leaves Android Keystore.
CREATE TABLE IF NOT EXISTS pro_installation_keys (
  installation_id TEXT PRIMARY KEY NOT NULL
    REFERENCES installations(id),
  public_key_spki TEXT NOT NULL
);

-- Only the SHA-256 of a one-time challenge nonce is stored.
-- A later verification must atomically consume the challenge.
CREATE TABLE IF NOT EXISTS installation_challenges (
  id TEXT PRIMARY KEY NOT NULL,
  installation_id TEXT NOT NULL
    REFERENCES installations(id),
  nonce_hash TEXT NOT NULL,
  request_nonce_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  consumed_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_installation_challenges
  ON installation_challenges(
    installation_id, expires_at
  );
