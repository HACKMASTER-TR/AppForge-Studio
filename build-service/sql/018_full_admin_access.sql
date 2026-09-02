-- AppForge owner/full-admin account.
-- Migration idempotenttir; her deploy'da güvenle tekrar çalışabilir.

UPDATE appforge_users
SET
    role = 'admin',
    is_active = TRUE,
    updated_at = NOW()
WHERE LOWER(email) =
      LOWER('28550040284a@gmail.com');

INSERT INTO appforge_pro_entitlements(
    user_id,
    status,
    source,
    product_id,
    purchase_token_hash,
    expires_at,
    granted_at,
    updated_at
)
SELECT
    id,
    'active',
    'admin_full_access',
    'appforge_admin_full_access',
    NULL,
    NULL,
    NOW(),
    NOW()
FROM appforge_users
WHERE LOWER(email) =
      LOWER('28550040284a@gmail.com')
ON CONFLICT(user_id)
DO UPDATE SET
    status = 'active',
    source = 'admin_full_access',
    product_id = 'appforge_admin_full_access',
    purchase_token_hash = NULL,
    expires_at = NULL,
    updated_at = NOW();
