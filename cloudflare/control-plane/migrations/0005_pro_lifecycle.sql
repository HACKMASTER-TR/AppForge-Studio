-- STAGING ONLY. Apply 0001..0005 in order; do not change production bindings.
-- Keep one current grant per installation and immutable records of prior grants.
-- A per-challenge random consumption marker prevents same-second replay.
ALTER TABLE installation_challenges ADD COLUMN consumption_id TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_installation_challenge_consumption
  ON installation_challenges(consumption_id)
  WHERE consumption_id IS NOT NULL;
CREATE TABLE IF NOT EXISTS pro_admin_grant_history (
  activation_code_id TEXT PRIMARY KEY NOT NULL REFERENCES pro_activation_codes(id),
  installation_id TEXT NOT NULL REFERENCES installations(id),
  granted_at INTEGER NOT NULL,
  revoked_at INTEGER NOT NULL,
  revoked_by_hash TEXT,
  superseded_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_pro_grant_history_installation
  ON pro_admin_grant_history(installation_id, superseded_at DESC);

-- Any change of the code linked to the current grant archives the old
-- revoked grant in the SAME transaction. Never overwrite live grants.
CREATE TRIGGER IF NOT EXISTS trg_archive_reactivated_pro_grant
BEFORE UPDATE OF activation_code_id ON pro_admin_grants
WHEN OLD.activation_code_id <> NEW.activation_code_id
BEGIN
  SELECT CASE WHEN OLD.state <> 'revoked' OR NEW.state <> 'active'
    THEN RAISE(ABORT, 'grant_must_be_revoked') END;
  INSERT INTO pro_admin_grant_history
    (activation_code_id, installation_id, granted_at,
     revoked_at, revoked_by_hash, superseded_at)
  VALUES (OLD.activation_code_id, OLD.installation_id,
          OLD.granted_at, OLD.revoked_at, OLD.revoked_by_hash,
          NEW.updated_at);
END;

-- The final NOT NULL receipt is a transactional guard: when a preceding
-- conditional D1 batch statement affected zero rows, the final INSERT
-- aborts SQL (rather than merely returning meta.changes=0), rolling back
-- the entire batch. A receipt never grants Pro by itself.
CREATE TABLE IF NOT EXISTS pro_redemption_receipts (
  redemption_id TEXT PRIMARY KEY NOT NULL,
  activation_code_id TEXT NOT NULL UNIQUE REFERENCES pro_activation_codes(id),
  installation_id TEXT NOT NULL REFERENCES installations(id),
  created_at INTEGER NOT NULL
);
