UPDATE appforge_templates
SET
    config =
        jsonb_set(
            config,
            '{features}',
            COALESCE(
                config -> 'features',
                '{}'::jsonb
            ) ||
            '{
                "notifications": true,
                "javascriptBridge": true,
                "vibrationBridge": true
            }'::jsonb,
            TRUE
        ),
    updated_at = NOW()
WHERE slug = 'interaction-toolkit';


UPDATE appforge_templates
SET
    config =
        jsonb_set(
            config,
            '{features}',
            COALESCE(
                config -> 'features',
                '{}'::jsonb
            ) ||
            '{
                "camera": true,
                "location": true,
                "javascriptBridge": true,
                "shareBridge": true,
                "clipboardBridge": true
            }'::jsonb,
            TRUE
        ),
    updated_at = NOW()
WHERE slug = 'device-api-kit';


UPDATE appforge_templates
SET
    config =
        jsonb_set(
            config,
            '{features}',
            COALESCE(
                config -> 'features',
                '{}'::jsonb
            ) ||
            '{
                "javascriptBridge": true
            }'::jsonb,
            TRUE
        ),
    updated_at = NOW()
WHERE slug = 'system-info';


UPDATE appforge_templates
SET
    config =
        jsonb_set(
            config,
            '{features}',
            COALESCE(
                config -> 'features',
                '{}'::jsonb
            ) ||
            '{
                "camera": true,
                "location": true,
                "javascriptBridge": true,
                "shareBridge": true,
                "clipboardBridge": true,
                "vibrationBridge": true,
                "qrScanner": true
            }'::jsonb,
            TRUE
        ),
    updated_at = NOW()
WHERE slug = 'native-api-dashboard';
