---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - open-questions
related:
  - "[[Current_Status]]"
source_files:
  - "build-service/src/fastSigningKey.js"
  - "build-service/tests/fast_signing_key.test.js"
  - ".appforge/runtime-blockers.json"
---

# Open Questions

1. There is no source-verified replacement for the removed privacy or account-deletion product pages. Their historical content is intentionally excluded from the wiki; any new product or legal requirement needs its own evidence and approval.
2. Is the `fast_signing_key.test.js` byte-length failure a stale fixture, an intentional key change, or a source defect? It was not caused by dependency installation.
3. Is BUG-7 still active after the latest APK is installed and manually tested on a physical device?
4. Should the tracked APK and historical `.bak` files be retained, archived outside Git, or removed in a separately approved cleanup?

## Cloudflare accountless rollout blockers — 2026-09-19

- How will an installation prove possession of a key (or which free quotas
  remain device-local) without trusting `X-AppForge-Device-ID`?
- Which Google Play products, package and service credentials are actually
  configured? Design secure token refresh, restore, linked purchases and RTDN.
- Which verified Google OIDC `sub` will be explicitly provisioned as admin,
  and how will the issuer/audience/signature be validated?
- Android session/registration, Pro purchasing, admin, and templates need
  a source-verified accountless integration and physical device acceptance.
- D1 schema has not been migrated remotely; `workers.dev` and custom-domain
  cutover are blocked until the above checks pass.

- Phase 5: configure the real Play Console non-consumable product ID, package
  name and server-side verifier/acknowledgement and refund reconciliation.
- Phase 5: migrate all remaining account-dependent app areas and retired
  workspace/template/owner flows without deleting locally encrypted data.
- Phase 5: implement separate verified Google OIDC admin authorization.
- Phase 5: full-repo Kotlin/CI gate and real Play test purchase/restore are
  required; the staged API must not be switched to the production domain.
