---
type: architecture
status: draft
project: AppForge Studio
created: 2026-09-20
updated: 2026-09-20
last_verified: 2026-09-20
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
