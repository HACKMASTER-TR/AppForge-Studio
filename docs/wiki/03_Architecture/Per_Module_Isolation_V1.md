# AppForge — İhtiyaca Göre Bağımsız Modül Mimarisi (V1)

Durum: **FAZ 1 — yalnız planlama ve güvenli dosya alanı altyapısı**. Bu belge veya yeni kod fiziksel modülleri henüz ayrı rootfs üzerinden çalıştırmaz; mevcut kurulum motoru değiştirilmeden korunur. Özellik fiziksel testlerden önce `HAZIR` olarak ilan edilmez.

## Kullanıcı talebi

- Kullanıcı bir modül/çıktı istediğinde sadece o hedef ve zorunlu bağımlılık grafiği seçilir. `Fast HTML` için Linux/SDK indirilmez.
- Her hedef modül kendi `rootfs`, `downloads`, `apt`, `state`, `stages` dizinlerine sahip olur. Başka bir modülün **kurulu** dosyaları kullanılmaz.
- Aynı imzalı ve SHA-256 sabitli kaynak arşiv tekrar ağdan indirilmek zorunda değildir; isteyen modül için ayrı fiziksel kopya oluşturulur. Kopya `hardlink` değildir, baytları yeniden SHA-256 doğrulanır. Her modülün APT/dpkg durumunun ayrı olması zorunludur.
- Mevcut kullanıcı projeleri, AppForge V540 ortak rootfs, doğrulanmış 103 nesne ve önceki modül kayıtları değiştirilmez; yeni mimariye geçiş yalnızca açıkça yetkilendirilmiş yeni kurulumlarda yapılır.
- Modül başarısız olursa yalnızca o kurulumun sahipliğini kanıtladığı stage silinebilir; var olan rootfs/cache/başka modülün state'i silinmez.

## Bu fazda eklenenler

`ModuleIsolationV1` bağımlılık kapanışını hedef başına hesaplar, özel dizinleri güvenli şekilde hazırlar, seçilen ve SHA-256 sabitli arşivin bağımsız kopyasını güvenli dizine atomik olarak koyar. Kapsamı bir kurulum planı ve dosya sistemi temelidir: gerçek rootfs dağıtımı, araç zinciri çalıştırma, dpkg izolasyonu ve kurulumun etkinleştirilmesi **henüz bağlı değildir**.

## Sonraki geçiş kapıları

1. Her hedef için orijinal imzalı Ubuntu rootfs'i kendi alanına kurma; APK içindeki yetkilendirmeyi, imzaları ve lisans onaylarını koruma. Depolama sınırını indirme öncesinde kontrol etme.
2. Her modülün bağımlılıklarını yalnızca kendi rootfs'i içinde kurma. `apt/dpkg` durumunu, `/usr`, `/lib` ve `/opt` dosyalarını paylaşmama. Mevcut `libc6` sorununu bir başka modüle bulaştırmama.
3. Kurulum sonrası gerçek fonksiyon testleri: CMake `CMAKE_ROOT` ve `libarchive.so.13` çözümlemesi, Expo ARM64 Android C/C++ bağlantı testi, Windows MinGW POSIX C/C++ ile PE32+ üretimi.
4. `HAZIR` için sürüm, imzalı arşiv ve sahiplik mührü, fonksiyon testi ve bağımsız ortam kanıtı. İşlem süreleri ayrı, hata kodları açık.
5. Geriye uyumluluk: V540 yüklü modüller korunur, yeni modül motoru kabul edilene kadar eski install UI akışına hiç müdahale edilmez.

Kapsam dışı: üretim yayımlama, `main` birleştirme, Play Store, kullanıcı verilerini sıfırlama, cihazda ADB kurulumu ve otomatik migrasyon.
