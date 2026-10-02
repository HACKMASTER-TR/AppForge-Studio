---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - hot-context
  - device-build
  - windows
  - publisher-signing
related:
  - "[[Index]]"
  - "[[Current_Status]]"
  - "[[Windows_Publisher_Authorization]]"
  - "[[Deployment_And_CI]]"
  - "[[Open_Questions]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningAuthorizationClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "cloudflare/control-plane/src/index.mjs"
  - "cloudflare/control-plane/tests/windows_publisher_signing_grant.test.mjs"
  - "quality/tests/windows_publisher_server_authorization_contract.test.js"
---
# Hot Context

## Current Focus

- Server-verified Windows publisher authorization is CLOSED / PASS through source, CI, staging deployment and physical Android acceptance.
- Second Brain is being synchronized to the verified 2026-10-02 project state.
- Normal project compilation remains device-local. Remote Railway/Render build infrastructure is retired.
- Windows Native EXE and Portable EXE remain separate engines.
- PR #61, PR #63 and PR #64 remain open/draft and must not be merged without explicit approval.

## Must Know

- Source, automated tests, CI, deployed-state evidence and physical-device evidence are separate gates.
- D1 migration ledger reconciliation is complete for migrations 0001 through 0005.
- Publisher authorization adds no D1 migration; it reuses the existing audit ledger.
- Google admin authority is server verified from a signed Google ID token plus active D1 allow-list identity. Email, device ID or local state never grants admin.
- Publisher signing fails closed if authorization, network, artifact binding, certificate validation or signature verification fails.
- `main`, Play Production and `appforge-failover` remain protected.

## Recent Important Changes

- Play Production P0 fail-closed work is source/CI accepted on draft PR #62.
- Builder V25.1 re-entry handling is closed.
- D1 migrations 0001–0005 were reconciled exactly.
- Portable EXE crash-durability CI passed; physical Windows retest is paused.
- Windows publisher authorization commit `53895ff` passed full quality and Android Debug CI.
- Staging source `63d32e6` was deployed by marker commit `7736c35`; controlled staging workflow passed.
- Physical Android live acceptance proved grant issue, artifact mismatch rejection, valid consume and replay rejection.

## Current Risks / Open Questions

- Real Windows Authenticode signing with publisher material still needs physical end-to-end acceptance.
- Portable EXE physical relaunch/update persistence retest remains paused.
- Production custom-domain signing endpoint is not enabled.
- Draft PR merge decisions remain intentionally pending.

## Read Next

- [[Windows_Publisher_Authorization]]
- [[Current_Status]]
- [[Deployment_And_CI]]
- [[Security_And_Entitlements]]
- [[Open_Questions]]
