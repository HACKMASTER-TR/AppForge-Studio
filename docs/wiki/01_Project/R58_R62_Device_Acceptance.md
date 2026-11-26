---
type: status
status: draft
project: AppForge Studio
created: 2026-11-26
updated: 2026-11-26
last_verified: 2026-11-26
confidence: medium
tags:
  - device-acceptance
  - r58-r62
source_files: []
---

# R58–R62 / v579 cihaz kabulü

Tek original-identity profil: `-PappforgeR58IntegratedDeviceCandidate=true`. Paket `com.appforge.studio`, versionCode 579, versionName 5.0.77-r58-r62-integrated. Eski izole/Play/release profilleri değiştirilmez. R31 sertifikası doğrulanmadan imzalı aday kabul edilmez.

## Kabul kapıları

- R58: imzalı ve imzasız CI, paket/sürüm/sertifika ve SHA doğrulaması; Stability Gate sıfır hata.
- R59: ARM64 Expo 54/RN 0.81 kilitli bağımlılık ve prebuild referansı; cihazda AppForge içinden gerçek örnek APK derlemesi ve Metro olmadan açılış.
- R60: ARM64 R56 PRoot/linker referansı; cihazda API26 Rust JNI hedefi, 16 KiB ELF ve gerçek örnek APK çalıştırması.
- R61: ARM64 MinGW C ve C++ PE32+ çapraz derleme referansı; cihazda yalnız imzalı R2 paketler ile Windows Native modül kurulumu, EXE üretimi; Windows üzerinde gerçek EXE açılışı ayrı kanıt.
- R62: telefon üzerinde v578 üzerine v579 güncellemesi, eski projeler/ayarlar/modüller ve başarısız kurulum temizliği, yönetici terminal erişimi (normal kullanıcı erişimi reddedilir), mobil veri bağlantısı ve regresyon matrisi.

GitHub referans koşuları cihaz kanıtı değildir. Herhangi bir fiziksel modül testi başarısız olursa log, hata kodu ve mevcut modül durumu alınır; başarısız adaydan aktif modül silinmez. ADB, Termux, uygulama kaldırma, veri silme, zorla modül sıfırlama yok. Örnekler ayrı test projesinde çalıştırılır. Fiziksel kabul yapılmadan main veya Play dağıtımı yok. Bilinmeyen fiziksel hata kök nedenleri doğrulanmış sayılmaz.
