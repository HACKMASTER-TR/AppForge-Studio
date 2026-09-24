-- STAGING SCHEMA ONLY. Do not apply remotely without device/Play/admin review.
-- Replaces the un-applied 0001_accounts.sql; contains no email or password table.
PRAGMA foreign_keys = ON;

-- A public installation ID is NOT proof of possession. Privileged requests
-- must later prove control of a registered key; do not grant by ID/header.
CREATE TABLE IF NOT EXISTS installations (
  id TEXT PRIMARY KEY NOT NULL,
  key_thumbprint TEXT NOT NULL UNIQUE,
  state TEXT NOT NULL DEFAULT 'active' CHECK(state IN ('active','disabled')),
  created_at INTEGER NOT NULL,
  last_seen_at INTEGER
);

-- Only ONE non-consumable Google Play product: appforge_pro_lifetime.
-- This is a *planned* Play Console ID and must match the actual in-app product
-- before any production rollout. No subscriptions, renewals or quota add-ons.
-- Rows may be inserted/updated ONLY after server-side Google Play Developer
-- API verification. Refunds/voids must be reconciled; purchase tokens are
-- not proof of identity of a device or a Google account.
CREATE TABLE IF NOT EXISTS play_purchase_records (
  purchase_token_hash TEXT PRIMARY KEY NOT NULL,
  package_name TEXT NOT NULL,
  product_id TEXT NOT NULL CHECK(product_id = 'appforge_pro_lifetime'),
  purchase_state TEXT NOT NULL CHECK(purchase_state IN
    ('pending','purchased','canceled','refunded','revoked')),
  google_verified_at INTEGER,
  acknowledged_at INTEGER,
  revoked_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_play_purchase_state ON play_purchase_records(purchase_state);

-- Google OpenID Connect `sub` is verified against signature/issuer/audience,
-- then mapped to an explicitly provisioned subject hash; never by email alone.
CREATE TABLE IF NOT EXISTS admin_identities (
  google_subject_hash TEXT PRIMARY KEY NOT NULL,
  state TEXT NOT NULL DEFAULT 'disabled' CHECK(state IN ('active','disabled')),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS quota_events (
  id TEXT PRIMARY KEY NOT NULL,
  installation_id TEXT NOT NULL REFERENCES installations(id),
  purchase_token_hash TEXT REFERENCES play_purchase_records(purchase_token_hash),
  event_kind TEXT NOT NULL,
  idempotency_key TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_quota_installation ON quota_events(installation_id);

CREATE TABLE IF NOT EXISTS audit_events (
  id TEXT PRIMARY KEY NOT NULL,
  event_kind TEXT NOT NULL,
  actor_reference_hash TEXT,
  created_at INTEGER NOT NULL
);
