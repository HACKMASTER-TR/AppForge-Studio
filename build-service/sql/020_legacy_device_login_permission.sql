ALTER TABLE appforge_users
ADD COLUMN IF NOT EXISTS
  allow_legacy_device_login BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN
  appforge_users.allow_legacy_device_login
IS
  'ADMIN-controlled exception allowing login from legacy AppForge clients that do not send X-AppForge-Device-ID.';
