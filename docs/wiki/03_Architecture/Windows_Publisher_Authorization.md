---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-02
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - windows
  - publisher-signing
  - security
  - cloudflare
  - staging
related:
  - "[[Security_And_Entitlements]]"
  - "[[Deployment_And_CI]]"
  - "[[Current_Status]]"
  - "[[Decision_Index]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningAuthorizationClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningProvider.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPublisherSigningStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/GoogleAdminIdentityClient.kt"
  - "cloudflare/control-plane/src/index.mjs"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/tests/windows_publisher_signing_grant.test.mjs"
  - "quality/tests/windows_publisher_server_authorization_contract.test.js"
  - ".github/workflows/pro-cloudflare-staging-deploy.yml"
---
# Windows Publisher Authorization

## Authoritative security contract

Windows publisher signing is an owner-only, fail-closed operation.

Android must hold a currently server-verified Google administrator session. A
local email, AppForge account, device identifier, encrypted token candidate or
UI state is insufficient to grant publisher signing authority.

Before certificate material is used, the client hashes the exact unsigned EXE
with SHA-256 and requests an HTTPS authorization grant containing the fixed
purpose `windows-publisher-signing-v1`, Build ID, artifact SHA-256 and a fresh
random request nonce.

The Cloudflare control plane re-verifies the Google ID token, hashes the stable
Google subject, and requires an active `admin_identities` row in D1.

An issued grant is short-lived, artifact-bound and one-time. Consumption must
match the original purpose, Build ID, artifact hash and nonce. Reuse is
rejected as replay.

## Certificate boundary

PKCS12/PFX material and its transient password remain on the device. They are
not uploaded to the authorization service.

The provider re-hashes the unsigned EXE before authorization and again after
copying it into the isolated signing workspace. Certificate material is only
materialized after authorization succeeds.

A requested signing failure removes the final EXE artifact rather than
publishing an unsigned result.

## D1 persistence

No new migration was added for publisher authorization.

The implementation reuses `audit_events` for issued and consumed grant
evidence. Migrations 0001 through 0005 remain the complete reconciled staging
migration set.

## 2026-10-02 staging acceptance

Source implementation commit: `53895ff5cabc33cf822754fbd55036dae3ae4758`.

The Cloudflare-only source was promoted as
`63d32e68e4f97454b0bde79ddde5dcd87e49252e`.

Controlled staging deploy marker commit:
`7736c3564210727ce75ea13a50124c11a23d36da`.

GitHub Actions controlled staging deployment run `36971590024` completed
successfully. Health, D1 reachability, existing Pro routes and new signing
route method guards passed. No D1 migration was applied.

A debug-only physical Android acceptance build from commit
`54d564eb530cba4749785769010cc8f21c5fadc4` was built by run `36974764595`.

Physical acceptance proved:

- server-verified Google administrator authority;
- grant issue;
- rejection after artifact SHA mutation;
- valid one-time grant consumption;
- replay rejection.

No ID token, private signing key, PKCS12 password, nonce or raw grant material
is stored in this wiki.

## Remaining boundaries

PR #64 remains draft and unmerged.

Production custom-domain signing remains disabled until separately reviewed.

Real Windows Authenticode end-to-end signing with configured publisher material
is still a separate physical acceptance gate.
