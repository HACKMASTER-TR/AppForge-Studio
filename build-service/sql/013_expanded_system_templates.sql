INSERT INTO appforge_templates (
    slug,
    name,
    description,
    category,
    config,
    is_system
)
VALUES
(
    'task-manager',
    'Görev Yöneticisi',
    'Görev ekleme, durum değiştirme ve yerel kayıt için mobil başlangıç.',
    'productivity',
    '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"notifications":true,"networkState":true}}'::jsonb,
    TRUE
),
(
    'inventory-panel',
    'Stok ve Envanter Paneli',
    'Ürün, stok seviyesi ve kritik stok kartları içeren yönetim paneli.',
    'business',
    '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"camera":true,"networkState":true}}'::jsonb,
    TRUE
),
(
    'booking-form',
    'Randevu ve Rezervasyon',
    'Tarih, saat ve müşteri bilgileriyle çalışan rezervasyon başlangıcı.',
    'business',
    '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"notifications":true,"networkState":true}}'::jsonb,
    TRUE
),
(
    'restaurant-menu',
    'Restoran Menüsü',
    'Kategori, ürün kartları ve sepet özeti bulunan mobil menü.',
    'commerce',
    '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"networkState":true}}'::jsonb,
    TRUE
),
(
    'event-invitation',
    'Etkinlik ve Davetiye',
    'Etkinlik ayrıntısı, katılım yanıtı, konum ve paylaşım başlangıcı.',
    'events',
    '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"location":true,"notifications":true,"javascriptBridge":true,"shareBridge":true,"networkState":true}}'::jsonb,
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
