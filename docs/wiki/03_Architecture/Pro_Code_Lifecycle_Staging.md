---
type: architecture
status: draft
project: AppForge Studio
created: 2026-09-20
updated: 2026-10-02
last_verified: 2026-10-02
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
  - "quality/tests/pro_process_death_contract.test.js"
  - "quality/tests/pro_second_device_package_contract.test.js"
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
  - "quality/tests/pro_recovery_interruption_contract.test.js"
  - "quality/tests/pro_live_status_replay_contract.test.js"
  - "cloudflare/control-plane/src/device_proof.mjs"
  - "android-app/app/src/main/java/com/appforge/studio/security/ProCodeClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/ProInstallationProof.kt"
---

# Admin-issued Pro code lifecycle — staging

## Current ledger state

D1 migrations 0001 through 0005 are reconciled exactly.
Windows publisher authorization adds no migration and reuses the existing audit
ledger.

## Authoritative Pro contract

- activation codes are random, hashed at rest and single-use;
- device ownership is bound to retained P-256 key material;
- recovery/reactivation uses a fresh server challenge and device signature;
- a failed conditional database transition must fail the transaction closed;
- Android enables Pro only after fresh server verification;
- accountless admin-grant and Google Play ownership paths remain distinct;
- revoked admin rights do not independently revoke a valid Play purchase.

## Accepted staging state

Staging validation includes:

- Cloudflare target/binding verification;
- controlled deployment gates;
- live read-only audits;
- second-device activation/restart verification;
- isolated process-death recovery acceptance;
- unchanged-key ownership recovery;
- server-verified status checks.

No plaintext activation code, token, private key, challenge or signing secret
belongs in Second Brain.

## Publisher authorization

Server-verified publisher authorization is accepted in staging with
artifact-bound, one-time authorization and replay rejection.

Production custom-domain publisher signing remains disabled pending explicit
review.

## Boundaries

Do not infer production readiness from staging acceptance.

Still separate:

- production publisher endpoint decision;
- real Windows Authenticode physical signing;
- signed-publisher verification;
- Play Production access and release;
- protected-main integration.

Full staging and physical-test chronology:
[[archive/Pro_Code_Lifecycle_Staging_History_2026-10-02]].
