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
  - "cloudflare/control-plane/migrations/0005_pro_lifecycle.sql"
  - ".github/workflows/pro-cloudflare-auth-preflight.yml"
  - ".github/workflows/pro-cloudflare-dry-run.yml"
  - ".github/workflows/pro-cloudflare-staging-deploy.yml"
  - ".github/workflows/pro-staging-http-matrix.yml"
  - ".github/workflows/pro-staging-live-audit.yml"
  - ".github/scripts/pro_cloudflare_auth_preflight.py"
  - "cloudflare/control-plane/src/pro_redemption.mjs"
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
