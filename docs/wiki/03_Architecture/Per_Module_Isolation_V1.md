---
type: architecture
status: draft
project: AppForge Studio
created: 2026-11-18
updated: 2026-11-18
last_verified: 2026-11-18
confidence: low
tags:
  - on-demand
  - module-isolation
related:
  - "[[Device_Build_Runtime_V3]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/ModuleIsolationV1.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
  - "android-app/app/src/test/java/com/appforge/studio/build/ModuleIsolationV1Test.kt"
  - "quality/tests/module_isolation_v1_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/build/IsolatedModuleRuntimeV2.kt"
  - "android-app/app/src/test/java/com/appforge/studio/build/IsolatedModuleRuntimeV2Test.kt"
  - "quality/tests/module_isolation_v2_contract.test.js"
---

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


## Faz 2 — Modül başına imzalı Ubuntu kökü (2026-11-18)

`IsolatedModuleRuntimeV2` her açıkça seçilen hedef için birbirinden ayrı Ubuntu
rootfs yolları, `var/lib/dpkg/status`, `var/lib/dpkg/info`, `var/lib/apt/lists`
ve özel indirme/state/apt alanları oluşturur. Yeni ortam eski V540 çalışma
kökünden klonlanmaz; izin verilen Ubuntu manifestindeki SHA-256 sabitli kaynak
`VerifiedLinuxRootfsInstaller` ile kurulur. İndirme önbelleği hedef rootfs
konumuna göre ayrı isimlendirilir; kurulan dosyalar başka modülün rootfs'inden
alınmaz. Android sistem TLS kökleri ve özgün vendor dpkg durumunun kanıtı yalnız
hedef rootfs içinde oluşturulur.

Bu kod **yalnız açık kullanıcı onayıyla debug kabul yapısında** çağrılabilen
bir staging yoludur. V540 mevcut kurulum akışına bağlanmamıştır; `HAZIR` statüsü
vermez, mevcut proje/kurulum dosyalarını taşımaz veya silmez. Yarım kalmış veya
sahibi belirsiz izole klasör otomatik sıfırlanmaz, incelenene kadar korunur.
Ayrı rootfs temelinin oluşturulması, Expo/Rust/MinGW paketlerinin kurulup
fonksiyon testlerini geçmiş olduğu anlamına gelmez.

**Takip:** Faz 2 gerçek ARM64 Ubuntu kurulum kabulü; Faz 3 seçilen modülün kendi
rootfs içinde imzalı APT/dpkg kurulumu ve gerçek derleme testleri; sonrasında
kontrollü varsayılan kurulum geçişi. `main`/üretim yayını ve APK kurulumuna bu
fazda dokunulmaz.
