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

## V4.1 isolated Integrity staging

The existing `appforge-control-plane` Worker must not be used as the
Play Integrity staging deployment target because Production exact routes
already target that Worker.

Physical Play Integrity acceptance therefore uses a distinct Worker:

`appforge-integrity-staging`

This isolated Worker exposes only:

- `GET /health`
- `GET /api/security/config`
- `POST /api/security/attest`

It has:

- no D1 binding
- no Custom Domain
- no Production routes
- no admin endpoints
- no Pro endpoints
- no Play publishing capability

Deployment is marker-triggered and requires a marker-only commit.
Creating the workflow does not deploy it.

### V4.2 atomic first staging deployment

The first deployment of `appforge-integrity-staging` must not use
`wrangler secret put`.

`wrangler secret put` creates and immediately deploys a Worker version,
which would introduce deployment mutations before the explicitly guarded
deployment step.

V4.2 instead creates an ephemeral secrets JSON file only inside the
GitHub Actions runner and performs one:

`wrangler deploy --secrets-file ...`

operation.

The temporary secrets file:

- is never committed;
- is mode 0600;
- is not printed;
- is deleted immediately after deployment;
- never reaches the repository or workflow artifacts.

The deployment remains isolated from:

- `appforge-control-plane`
- D1
- Custom Domains
- Production routes
- Play Production

### V4.3 isolated staging dry-run path correction

The first physical isolated staging attempt stopped before deployment at
the Wrangler dry-run.

Failure class:

`C_TOOL_CONFIG_PATH`

The generated Wrangler configuration had been placed under
`RUNNER_TEMP` while its `main` entry remained:

`src/integrity_staging_index.mjs`

Relative Worker source paths must resolve from the configuration
location. V4.3 therefore creates the temporary Wrangler configuration
inside:

`cloudflare/control-plane/`

next to the Worker `src/` tree.

No Worker deployment occurred during the failed attempt. The code/secrets
deploy and live smoke steps were skipped.

V4.3 also surfaces a restricted Wrangler diagnostic on future dry-run
failures and removes the transient config after deployment.

### V4.5 live deploy diagnostic hardening

Retry run `37170941870` proved that the Wrangler source-path correction
worked:

`ISOLATED_WORKER_DRY_RUN=PASS`

The run then failed inside the live atomic deploy command before the live
smoke test. Because Wrangler stdout/stderr had been redirected only to a
runner-local file, the exact Cloudflare error was not visible.

Failure class at this stage:

`C_TOOL_DEPLOY_DIAGNOSTIC_HIDDEN`

V4.5 adds:

- a second non-mutating dry-run using the real ephemeral secrets file;
- explicit `SECRET_PAYLOAD_DRY_RUN`;
- a redacted Wrangler diagnostic on live deployment failure;
- exact-value secret redaction;
- PEM and long credential-like value redaction.

No staging deployment is triggered by this source-fix commit.

The previous failed live command was attempted, so existence of a partial
isolated Worker state must remain UNKNOWN until Cloudflare state is
audited or a subsequent guarded deployment succeeds.


### V4.6 dynamic live smoke URL

Bootstrap retry run `37173145170` proved the Cloudflare
deployment boundary is now valid:

- `ISOLATED_WORKER_DRY_RUN=PASS`;
- `SECRET_PAYLOAD_DRY_RUN=PASS`;
- `ISOLATED_WORKER_DEPLOY=PASS`;
- `ATOMIC_CODE_AND_SECRETS_DEPLOY=PASS`;
- ephemeral secret file removal passed.

The subsequent first-deploy live smoke failed before application
validation because the first `/health` request returned HTTP 404.

A later successful guarded deployment proved that Wrangler resolved the
same workers.dev hostname that had previously been hard-coded. Therefore
the evidence does not support the earlier conclusion that the hostname
itself was wrong.

Best-fit diagnosis after the successful retry:

`C_WORKERS_DEV_FIRST_DEPLOY_ACTIVATION`

This is treated as a likely first-deploy workers.dev activation /
propagation condition rather than a proven hostname error.

V4.6 still removes the hard-coded workers.dev base URL as a robustness
improvement. The successful Wrangler deployment output is parsed for the
exact `appforge-integrity-staging` workers.dev URL, the result is
validated, passed to the following step through `GITHUB_ENV`, and then
used by the live smoke test.

The duplicate `ATOMIC_CODE_AND_SECRETS_DEPLOY=PASS` log line is also
removed.

Production Worker, D1, custom domains, production routes, Play
Production and main remain outside this change.


### V4.7 physical Play Integrity acceptance harness

V4.6 established a green isolated staging deployment using a
Worker-scoped Editor token.

V4.7 adds a physical Android acceptance harness for the final
Play-installed Standard Integrity verification.

The harness is disabled by default and is enabled only when the Internal
App Sharing workflow is explicitly dispatched with:

- `integrity_acceptance=true`;
- an exact `appforge-integrity-staging.*.workers.dev` base URL.

When enabled, the normal update gate redirects to an unexported physical
acceptance Activity.

That Activity performs the real sequence:

1. read isolated staging security config;
2. prepare and request a Standard Integrity token through Google Play;
3. send the encrypted token and server-recomputable binding to
   `/api/security/attest`;
4. let the Worker call Google `decodeIntegrityToken`;
5. evaluate request, app and device verdicts;
6. issue an AppForge Integrity session only if policy allows it.

Neither the encrypted Google Integrity token nor the AppForge session
value is printed.

If the server returns `integrity_policy_denied`, V4.7 reports
`DECODE_PASS_POLICY_DENIED`. That state proves the real Standard
Integrity token reached the Worker and Google decode completed, while
also preserving the fail-closed policy boundary.

Normal release builds do not pass the acceptance Gradle property and
therefore retain normal launch behavior.

Internal App Sharing remains isolated from Play Production.

Production Worker, D1, custom domains, Production routes, Play
Production and protected main remain untouched.


### V4.7.1 branch-local Internal App Sharing trigger

The Internal App Sharing workflow is not yet present on protected
`main`, so feature-branch physical acceptance does not rely on
`workflow_dispatch`.

V4.7.1 adds a push trigger restricted to:

`.github/.play-integrity-physical-acceptance-request`

The push path is accepted only when:

- repository and feature branch are exact;
- commit message is exactly
  `share(internal): Play Integrity physical acceptance`;
- the commit changes only the marker;
- marker content equals the commit parent SHA.

The guarded push forces physical acceptance mode and targets only:

`https://appforge-integrity-staging.28550040284a.workers.dev`

The isolated Worker `/health` endpoint is validated before the AAB is
built.

The path uploads only to Google Play Internal App Sharing.
Protected main, Play Production, D1 and production Worker routes remain
outside this flow.


### V4.7.4 physical acceptance R8 isolation

The first Play-installed physical acceptance AAB successfully passed:

- guarded Internal App Sharing build;
- Google WIF authentication;
- Android Publisher authentication;
- Internal App Sharing upload;
- Google Play installation.

The application then crashed before the physical acceptance Activity
could start.

ADB captured the release-only runtime failure at process startup:

`InitializationProvider -> WorkManagerInitializer -> WorkDatabase`

The crash occurred before any Standard Integrity request was made.

Because the physical acceptance artifact is an isolated diagnostic build,
V4.7.4 disables R8 minification and resource shrinking only when:

`appforgePlayIntegrityAcceptance=true`

Normal release builds keep the property disabled by default, so their
existing R8 minification and resource shrinking remain enabled.

No Google Integrity token, AppForge Integrity session, Production track,
D1 or production Worker route is changed by this fix.


### V4.7.6 safe policy verdict diagnostics

Physical acceptance now proves:

- Standard Integrity token generation succeeds;
- Google `decodeIntegrityToken` succeeds;
- request binding is valid;
- the isolated staging Worker reaches local policy evaluation.

The remaining result is `integrity_policy_denied`.

V4.7.6 preserves the Worker's already-sanitized policy verdict object
inside a typed client exception and exposes only:

- request verified;
- Play recognition;
- device integrity;
- optional verdict readiness;
- app access risk;
- Play Protect verdict;
- recent device activity level.

The encrypted Google Integrity token and AppForge Integrity session are
not exposed or logged.

This diagnostic does not change the policy. It only identifies which
fail-closed condition is responsible for denial.

Play Production, D1, production Worker routes and protected main remain
untouched.
