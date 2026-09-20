-- Staging only. Do not apply remotely without review.
-- Records the verified administrator who revoked a grant.

ALTER TABLE pro_admin_grants
  ADD COLUMN revoked_by_hash TEXT;

CREATE INDEX IF NOT EXISTS idx_pro_grants_revoked_at
  ON pro_admin_grants(revoked_at);
