INSERT INTO appforge_templates (slug, name, description, category, config, is_system)
VALUES
('visual-designer', 'Görsel Sayfa Tasarımcısı', 'Metin, kart ve buton bloklarıyla çalışan sürüklemeden basit sayfa prototipi.', 'starters', '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"networkState":true}}'::jsonb, TRUE),
('personnel-tracker', 'Personel Takibi', 'Personel durumu, vardiya ve hızlı arama paneli.', 'business', '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"notifications":true,"networkState":true}}'::jsonb, TRUE),
('qr-menu', 'QR Menü ve Sipariş', 'QR tarama, ürün kartları ve yerel sepet akışı.', 'commerce', '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"camera":true,"qrScanner":true,"networkState":true}}'::jsonb, TRUE),
('education-quiz', 'Eğitim ve Quiz', 'Çalışan soru, puan ve sonraki soru akışı.', 'productivity', '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"networkState":true}}'::jsonb, TRUE),
('firebase-login', 'Firebase Giriş Başlangıcı', 'Firebase yapılandırmasına hazır giriş ve hesap ekranı taslağı.', 'starters', '{"sourceMode":"LOCAL","orientation":"portrait","features":{"offlineCache":true,"networkState":true},"firebase":{"analytics":true}}'::jsonb, TRUE)
ON CONFLICT (slug) DO UPDATE SET
name = EXCLUDED.name, description = EXCLUDED.description, category = EXCLUDED.category,
config = EXCLUDED.config, is_system = TRUE, updated_at = NOW();
