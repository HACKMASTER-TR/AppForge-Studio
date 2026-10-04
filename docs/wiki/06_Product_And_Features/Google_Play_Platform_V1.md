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

## V3 fail-closed Play operations

### Production release contract

Future Play Production artifacts are created as DRAFT first.

No GitHub release event directly serves the new AppForge version to all users.

Activation sequence:

DRAFT
-> 5%
-> 20%
-> 50%
-> 100%

Each Production state mutation requires:

- exact versionCode
- exact operation
- explicit confirmation token
- fresh Google Play edit
- edit validation
- edit commit

The rollout can be halted and resumed while staged.

### App Recovery

The Recovery workflow defaults to read-only `list`.

Supported explicitly gated operations:

- create DRAFT Remote In-App Update recovery for one exact versionCode
- deploy a known recovery action ID
- cancel a known recovery action ID

A recovery draft is never deployed automatically.

### Advanced Play Integrity

`play_integrity_policy.mjs` contains the fail-closed policy evaluator for:

- MEETS_DEVICE_INTEGRITY
- App Access Risk
- Play Protect
- Recent Device Activity
- Device Attributes

The policy module is not equivalent to live server verification.

It remains BACKEND_GATED until:

1. optional verdicts are enabled in Play Console,
2. the server-side Play Integrity token decode path is migrated and verified,
3. production routing for the audited security endpoint is explicitly approved.

Missing optional verdicts deny critical actions in the policy evaluator.

## V4 server-side Play Integrity verification

V4 adds the real backend verification architecture without deploying it.

### Request binding

Android Standard Integrity requests use:

`SHA-256("appforge-integrity-v1|action|nonce|timestamp")`

The backend independently recomputes this material before trusting the
Google-decoded `requestHash`.

The previous userId-based material was removed because normal AppForge
users are accountless and the backend did not independently receive that
value.

### Google server decode

Backend flow:

1. Android requests a Standard Integrity token.
2. Android sends the encrypted token and binding fields to AppForge.
3. AppForge obtains a Google OAuth access token with the `playintegrity`
   scope from dedicated service-account credentials.
4. AppForge calls `com.appforge.studio:decodeIntegrityToken`.
5. AppForge verifies:
   - requestPackageName
   - requestHash
   - Google token timestamp freshness
   - PLAY_RECOGNIZED
   - MEETS_DEVICE_INTEGRITY
   - App Access Risk
   - Play Protect
   - Recent Device Activity
6. Only a fully accepted verdict may produce a short-lived
   HMAC-signed AppForge integrity session.

### Device Recall

Device Recall bits are decoded, but V4 does not assign a security meaning
to bitFirst/bitSecond/bitThird.

Their semantics must be explicitly defined before they can block or allow
an operation.

### Required Worker secrets / vars

No values belong in Git.

Required before staging activation:

- PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
- PLAY_INTEGRITY_SERVICE_ACCOUNT_EMAIL
- PLAY_INTEGRITY_SERVICE_ACCOUNT_PRIVATE_KEY
- PLAY_INTEGRITY_SERVICE_ACCOUNT_KEY_ID (optional)
- PLAY_INTEGRITY_SESSION_SECRET

V4 source/CI does not deploy the Worker, mutate D1, enable Play Console
verdicts, or add a Production route.
