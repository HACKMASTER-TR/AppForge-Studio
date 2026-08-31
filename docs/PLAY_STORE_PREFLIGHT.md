# Play Store Preflight Checklist

AppForge v0.6 build öncesi/sonrası kontrol listesi:

- package name geçerli mi
- versionCode pozitif mi
- versionName dolu mu
- HTTPS URL kullanılıyor mu
- özel keystore seçildiyse alias/parolalar dolu mu
- AdMob açıksa App ID girilmiş mi
- Billing açıksa ürün kimlikleri girilmiş mi
- Deep Link açıksa scheme + host girilmiş mi
- Kamera özelliği açıksa CAMERA izni üretiliyor mu
- Konum açıksa ACCESS_FINE_LOCATION izni üretiliyor mu
- Bildirim açıksa POST_NOTIFICATIONS izni üretiliyor mu
- Native vibration açıksa VIBRATE izni üretiliyor mu
- APK/AAB çıktıları başarıyla oluşmuş mu
- release build imzalı mı

Not:
Google Play Console politikaları ve veri güvenliği formu uygulamanın gerçek veri kullanımına göre ayrıca doldurulmalıdır.
