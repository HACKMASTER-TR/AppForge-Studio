# Google Play Platform V1

## Amaç

AppForge Studio'nun Google Play entegrasyonlarını tek platform sözleşmesinde
toplamak.

Bu çalışma "kütüphane var = özellik aktif" varsayımını yapmaz.

Durum sınıfları:

- ACTIVE: AppForge kaynak kodunda gerçek aktif akış mevcut.
- CODE_READY: istemci entegrasyonu hazır, ürün tetikleyicisi kontrollü açılacak.
- CONSOLE_GATED: Google Play Console yapılandırması gerekir.
- API_GATED: Google Play / Developer API yetkisi ve açık operasyon gerekir.
- POST_RELEASE_DATA: gerçek Play dağıtımından sonra veri oluşur.
- PLANNED: AppForge otomasyon katmanı henüz aktive edilmemiştir.

## Mevcut aktif temel

- Google Play In-App Updates
- Play Integrity Standard API
- Google Play Billing
- Android App Bundle release hattı
- Internal testing release hattı
- GitHub OIDC -> Google WIF
- release/upload key doğrulaması

## V1 ile eklenen runtime yüzeyi

- In-App Review
- Install Referrer
- Play Feature Delivery client
- Play Asset Delivery client

Install Referrer otomatik olarak diske yazılmaz.
In-App Review otomatik tetiklenmez.
Feature Delivery için gerçek dynamic-feature modülü,
Asset Delivery için gerçek asset-pack eklenmeden özellik ACTIVE sayılmaz.

## Dağıtım ve operasyon

Aşağıdaki kabiliyetler tek master platformun parçasıdır ancak Play Production
varsayılan olarak kapalıdır:

- Closed / Open testing
- staged rollout
- rollout halt / resume / complete
- country targeting
- localized release notes
- in-app update priority
- App Recovery

Production değişikliği ayrıca açık onay gerektirir.

## Kalite

- Pre-launch Report
- Android Vitals
- Crash / ANR
- Slow startup
- Excessive wake-up
- Stuck background wakelock
- Developer Reporting API

Gerçek Vitals verileri ancak Google Play dağıtımından sonra elde edilir.

## Integrity genişletmeleri

- App Access Risk
- Play Protect
- Recent Device Activity
- Device Attributes
- Device Recall

Bu verdict'ler Console tarafında etkinleştirilmeden ACTIVE sayılamaz.

## Hedef release zinciri

Source
-> CI
-> signed AAB
-> Internal
-> Pre-launch
-> Physical acceptance
-> Closed test
-> Vitals
-> Second Brain health gate
-> staged Production
-> health check
-> advance or halt/recovery

## Güvenlik sınırı

PLAY_PRODUCTION varsayılan olarak MUTATION_DISABLED durumundadır.
Bu V1 değişikliği herhangi bir Google Play release yüklemez.

## V2 runtime activation

### In-App Review

In-App Review artık gerçek AppForge build başarısına bağlıdır.

Kurallar:

- debug build'de çalışmaz
- yalnız Google Play installer kaynağında çalışır
- en az 3 başarılı AppForge build gerekir
- aynı Build ID iki kez sayılmaz
- en fazla 120 günde bir review isteği denenir
- Play'in review penceresini gerçekten gösterip göstermediği tahmin edilmez

### Internal App Sharing

`play-internal-app-sharing.yml` yalnız workflow_dispatch ile çalışır.

Ek güvenlik:

- `INTERNAL_SHARE` açık onayı gerekir
- yalnız Internal App Sharing upload endpoint'i kullanılır
- Production track değiştirilmez
- upload sonucu URL, certificate fingerprint ve SHA-256 doğrulanır

### Android Vitals / Developer Reporting

`play-vitals-readonly.yml` yalnız okuma yapar.

İlk V2 sorguları:

- Crash Rate metric-set metadata
- ANR Rate metric-set metadata

Production release, track veya kullanıcı dağıtımı değiştirilmez.

### Install Referrer

Install Referrer runtime kütüphanesi CODE_READY olarak kalır.

Otomatik attribution/veri toplama, Privacy Policy ve Play Data Safety
eşlemesi tamamlanmadan etkinleştirilmez.
