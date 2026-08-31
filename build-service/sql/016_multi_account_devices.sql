-- AppForge Studio aynı hesabı Android, Windows ve Web'de
-- kullanabilsin. Cihaz kimliği yine yalnızca tek hesaba ait olabilir.

ALTER TABLE appforge_account_devices
DROP CONSTRAINT IF EXISTS appforge_account_devices_user_id_key;

CREATE INDEX IF NOT EXISTS idx_account_devices_user_last_seen
ON appforge_account_devices(user_id, last_seen_at DESC);
