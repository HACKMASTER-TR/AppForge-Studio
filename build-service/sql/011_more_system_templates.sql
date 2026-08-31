INSERT INTO appforge_templates (
    slug,
    name,
    description,
    category,
    config,
    is_system
)
VALUES

-- =========================================================
-- 1. ETKİLEŞİM
-- =========================================================
(
    'interaction-toolkit',
    'Etkileşim Araçları',
    'Toast, notification, vibrate ve kullanıcı etkileşimi için başlangıç şablonu.',
    'interaction',
    '{
        "sourceMode":"LOCAL",
        "orientation":"portrait",
        "features":{
            "fileUpload":false,
            "downloads":false,
            "fullscreen":false,
            "offlineCache":true
        }
    }'::jsonb,
    TRUE
),

-- =========================================================
-- 2. STARTER LIBRARIES
-- =========================================================
(
    'bootstrap-starter',
    'Bootstrap Başlangıç',
    'Bootstrap library ile mobil uyumlu hazır başlangıç arayüzü.',
    'libraries',
    '{
        "sourceMode":"LOCAL",
        "orientation":"portrait",
        "features":{
            "fileUpload":true,
            "downloads":true,
            "fullscreen":false,
            "offlineCache":true
        }
    }'::jsonb,
    TRUE
),

-- =========================================================
-- 3. REKLAMLAR
-- =========================================================
(
    'admob-starter',
    'AdMob Başlangıç',
    'AdMob banner ve reklam entegrasyonu için hazırlanmış temel proje.',
    'ads',
    '{
        "sourceMode":"LOCAL",
        "orientation":"portrait",
        "features":{
            "fileUpload":false,
            "downloads":false,
            "fullscreen":false,
            "offlineCache":true
        }
    }'::jsonb,
    TRUE
),

-- =========================================================
-- 4. CİHAZ
-- =========================================================
(
    'device-api-kit',
    'Cihaz API Kiti',
    'Camera, location, clipboard, share ve device özellikleri için hazır başlangıç.',
    'device',
    '{
        "sourceMode":"LOCAL",
        "orientation":"portrait",
        "features":{
            "fileUpload":true,
            "downloads":true,
            "camera":true,
            "offlineCache":true
        }
    }'::jsonb,
    TRUE
),

-- =========================================================
-- 5. SENSÖRLER
-- =========================================================
(
    'sensor-dashboard',
    'Sensör Paneli',
    'Accelerometer, gyroscope, magnetometer ve sensor verileri için örnek panel.',
    'sensors',
    '{
        "sourceMode":"LOCAL",
        "orientation":"portrait",
        "features":{
            "fileUpload":false,
            "downloads":false,
            "fullscreen":false,
            "offlineCache":true
        }
    }'::jsonb,
    TRUE
),

-- =========================================================
-- 6. SİSTEM
-- =========================================================
(
    'system-info',
    'Sistem Bilgileri',
    'Battery, permission, brightness ve system bilgilerini göstermek için başlangıç.',
    'system',
    '{
        "sourceMode":"LOCAL",
        "orientation":"portrait",
        "features":{
            "fileUpload":false,
            "downloads":false,
            "fullscreen":false,
            "offlineCache":true
        }
    }'::jsonb,
    TRUE
),

-- =========================================================
-- 7. PANEL
-- =========================================================
(
    'native-api-dashboard',
    'Native API Paneli',
    'Camera, QR, paylaşım ve cihaz araçlarını tek dashboard içinde sunan panel.',
    'panels',
    '{
        "sourceMode":"LOCAL",
        "orientation":"portrait",
        "features":{
            "fileUpload":true,
            "downloads":true,
            "camera":true,
            "fullscreen":false,
            "offlineCache":true
        }
    }'::jsonb,
    TRUE
)

ON CONFLICT (slug)
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    category = EXCLUDED.category,
    config = EXCLUDED.config,
    is_system = TRUE,
    updated_at = NOW();
