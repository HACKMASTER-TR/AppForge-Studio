---
type: architecture
status: draft
project: AppForge Studio
created: 2026-09-20
updated: 2026-09-21
last_verified: 2026-09-21
confidence: medium
tags:
  - pro
  - security
  - staging
related:
  - "[[Security_And_Entitlements]]"
  - "[[Database_Map]]"
  - "[[Hot_Context]]"
source_files:
  - "build-service/tests/pro_second_device_package_contract.test.js"
  - ".github/workflows/android-debug.yml"
  - "android-app/app/build.gradle.kts"
  - "cloudflare/control-plane/migrations/0005_pro_lifecycle.sql"
  - ".github/workflows/pro-cloudflare-auth-preflight.yml"
  - ".github/workflows/pro-cloudflare-dry-run.yml"
  - ".github/workflows/pro-cloudflare-staging-deploy.yml"
  - ".github/workflows/pro-staging-http-matrix.yml"
  - ".github/workflows/pro-staging-live-audit.yml"
  - ".github/scripts/pro_cloudflare_auth_preflight.py"
  - "cloudflare/control-plane/src/pro_redemption.mjs"
  - "cloudflare/control-plane/tests/pro_reactivation_postcommit_contract.test.mjs"
  - "build-service/tests/pro_recovery_interruption_contract.test.js"
  - "build-service/tests/pro_live_status_replay_contract.test.js"
  - "cloudflare/control-plane/src/device_proof.mjs"
  - "android-app/app/src/main/java/com/appforge/studio/security/ProCodeClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/ProInstallationProof.kt"
---

# Admin-issued Pro code lifecycle (staging only)

## Authoritative contract

- A code is random, hashed at rest and redeemed only once. A consumed code never becomes issued again.
- `pro_admin_grants` is the current grant for a registered installation. The `0005` trigger archives a previously revoked grant in `pro_admin_grant_history` when a new code is activated on the same installation. The old revoker and timestamps remain auditable.
- Existing P-256 key material is never rotated, deleted or exported for recovery/reactivation. `ownership-challenge` resolves a public SPKI to an installation and gives a short-lived challenge; the device signs a domain-separated recover/reactivate message. A successful recovery for a revoked grant recovers only the installation ID, not Pro access.
- A failed conditional SQL operation must abort the whole `D1.batch()`: the last `pro_redemption_receipts` INSERT uses a NOT NULL receipt identifier gated on the expected active grant and redemption transaction. A zero-row update alone does not abort SQL.
- Android only enables Pro after a fresh device-signed challenge and HTTPS server status result. There is **no independent signature on the HTTPS status response**.
- The accountless installation path and the legacy account/Play purchase path must remain separate. A revoked admin grant does not in itself revoke an independently valid Google Play purchase.

## Gates

- Local Worker and SQLite tests are provisional until actual D1 staging and device acceptance.
- Verify migration order 0001 through 0005 and actual staging schema before deploy. Never touch production API, failover, Play Production, or existing active Google admin row.
- Run Kotlin compilation, real second-device redemption/revocation/restart, interrupted-activation recovery, revoked-key reactivation, challenge replay and offline checks separately.
- Refresh while the app is open is best-effort; protected server routes must independently enforce active grants.
- Keep `main`, Git history, existing .appforge directories, Termux backups and Android app data intact.

## Actual staging schema status — 2026-09-20

- D1 `appforge-control-plane-db` received 0002–0005 SQL manually via Cloudflare Console.
- All 16 expected objects/columns checked OK; `PRAGMA foreign_key_check` returned no violations.
- `admin_identities`: one active admin preserved.
- Console required a parenthesized CASE expression in the grant-archive trigger; the source migration now matches it.
- `d1_migrations` does not exist. Do not run `wrangler d1 migrations apply` or invent ledger entries before reconciling the existing schema and migration history.
- Staging Worker deployment completed; Cloudflare showed version `a1f3c746...` receiving 100% traffic. Real-device acceptance remains pending.

## Cloudflare credential preflight

- A feature-branch-only GitHub Actions check validates the active
  account token and reads only `appforge-control-plane` settings.
- It compares the existing `DB` D1 binding against the GitHub
  Database ID secret and checks Google variable names without
  printing any configuration values.
- No deploy or migration is part of this workflow.
- A passing read-only check does not itself prove deployment
  permission or reconcile the missing `d1_migrations` history.


## Staging Worker bundle dry-run

- The feature-only GitHub workflow rechecks the selected
  `appforge-control-plane` Worker and its exact `DB` D1 binding.
- Wrangler bundles the Pro routes using `--dry-run`; it does not
  deploy the Worker or apply D1 migrations.
- The temporary configuration uses `keep_vars = true` and does not
  copy Google variable values into GitHub source.
- Passing this check is not evidence of an actual Worker deploy,
  migration-ledger reconciliation, or real-device acceptance.


## Controlled staging deployment

- Only a separate, explicit marker-only feature-branch commit
  may trigger the staging deployment workflow.
- Ordinary source or workflow pushes do not trigger deployment.
- The deployment target is `appforge-control-plane`, using the
  existing `DB` D1 binding and `keep_vars = true`.
- The workflow runs a fresh read-only target check and Wrangler
  dry-run before attempting deployment.
- It does not apply migrations or reconcile `d1_migrations`.
- Post-deploy health and route checks are required. Device
  acceptance remains a separate gate.


## Post-deployment read-only audit

- The feature-branch audit separately verifies the existing
  Cloudflare `DB` binding and Google variable names without
  printing configuration values.
- The live HTTP transport is `curl`; the former Python
  `urllib` request path is no longer used by this audit.
- On 2026-09-21, commit
  `d7fe56ffcdbd94a264bf402230cb941b66a2642d`
  completed the Live Audit successfully.
- `/health` returned HTTP 200 JSON with `ok=true` and
  `database=reachable`.
- GET `/api/pro/code/ownership-challenge` returned the
  expected HTTP 405 JSON `method_not_allowed`, proving the
  route is present while the audit remains read-only.
- The preceding HTTP Matrix also verified default curl,
  AppForge User-Agent and browser User-Agent profiles
  against `/health`; all three received HTTP 200 JSON with
  D1 reachable. The earlier `urllib` 403 is therefore not
  treated as a general GitHub Runner connectivity block.
- The audit performed no database writes, migration apply,
  deployment-marker change or new Worker deployment.
- The earlier Wrangler deploy command returned failure even
  though Cloudflare subsequently showed the deployed Worker
  version receiving 100% traffic. The suppressed temporary
  error log was not recovered, so that command's exact exit
  cause remains open.
- Real-device Pro activation, revocation, restart, recovery,
  replay and offline acceptance remain separate gates.

## Real-device lifecycle checkpoint — 2026-09-21

- Initial admin-code activation passed on a physical Android device.
- Fresh server verification survived an app restart without manual
  verification.
- Administrator grant revocation removed Pro access and the active
  refresh loop failed closed.
- Keystore ownership recovery preserved the existing installation
  identity and did not restore a revoked entitlement.
- A new-code reactivation committed successfully in D1, but the
  Worker returned a false-negative HTTP 409 `code_unavailable`.
  A subsequent ownership recovery observed the grant as active,
  proving that the transaction had committed.
- Root cause was the reactivation response path treating every D1
  batch statement as requiring exactly `meta.changes === 1`.
  The source now keeps `receiptGuard` as the atomic rollback guard
  and post-verifies the committed code, active grant and redemption
  receipt instead of trusting exact statement metadata.
- The source fix still requires staging deployment and physical-device
  reactivation retest before reactivation acceptance can be marked
  complete.

## Isolated first-activation interruption test — device accepted

- Existing AppForge data, Keystore key and active Pro grant
  on the phone must remain untouched.
- An opt-in debug APK uses a separate `.prorecovery` package,
  with separate preferences and Keystore ownership.
- The test simulates interruption after successful initial
  redeem and before local installation ID persistence.
- Reopening the test app and recovering with its unchanged
  key must restore the installation identity and pass fresh
  HTTPS status verification.
- This is controlled error simulation, not actual process death.
- Normal debug and release activation paths remain unchanged.
- This does not count as second-device acceptance.


## 2026-09-21 physical-device acceptance update

- The corrected staging Worker was deployed and direct
  same-installation reactivation passed without recovery.
- Automatic verification passed after restart.
- Offline restart failed closed; online restart restored
  Pro through fresh server verification.
- A separately packaged debug APK on the same phone
  simulated interruption after successful initial redeem
  and before local installation ID persistence.
- The simulated interruption left Pro disabled.
- Reopening that test APK and using ownership recovery
  restored the installation ID using its unchanged key.
- Fresh HTTPS status verified Pro after recovery.
- Automatic verification also passed on the next restart.
- Real process death and a second physical Android device
  remain untested. Do not count the separate APK as a
  second physical device.
- Local challenge/code replay regression tests passed.
  Live staging challenge replay remains a separate gate.
- No new migration was applied and Play Production,
  main and appforge-failover remained untouched.

## Live staging challenge replay — pending

- The isolated Pro Recovery debug APK tests replay after
  a successful device-bound installation recovery.
- The same signed status payload must succeed once,
  then return HTTP 409 `challenge_unavailable`.
- A fresh challenge must still verify Pro afterwards.
- No new activation code, grant revocation or key export.
- No challenge IDs, nonces or signatures in logs.
- Local contract tests are not live staging acceptance.
- Real process death and second-device tests remain open.

## Second physical device staging APK — pending

- The Redmi staging APK uses an isolated `.prodevice2`
  application ID and must not replace the Google Play app.
- Recovery interruption and replay-test UI are disabled
  because `PRO_RECOVERY_TEST=false` for this build.
- Recovery and second-device build flags are mutually exclusive.
- The second phone must create its own private Keystore key.
- Physical-device activation and restart remain untested.
- No Worker deployment, D1 migration or Play publishing.
