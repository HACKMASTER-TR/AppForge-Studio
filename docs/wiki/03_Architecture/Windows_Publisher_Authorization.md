---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-02
updated: 2026-10-03
last_verified: 2026-10-03
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
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "quality/tests/windows_publisher_signing_provider_contract.test.js"
---

# Windows Publisher Authorization

## Authoritative security contract

Windows publisher signing is owner-only and fail-closed.

Android requires a currently server-verified Google administrator session.
Before certificate material is used, the exact unsigned EXE is SHA-256 hashed
and an HTTPS one-time grant is requested for
`windows-publisher-signing-v1`, the Build ID, artifact hash and fresh nonce.

The control plane re-verifies the Google identity and active administrator
allow-list entry. Grant issue and consumption are artifact-bound, short-lived
and replay protected.

PKCS12/PFX material and its transient password remain device-local. The
certificate material is encrypted at rest with Android Keystore protection and
is materialized only into the isolated signing workspace after authorization.

Requested signing failures are fail-closed and remove the final EXE artifact.

## D1 persistence

No publisher-signing migration was added. `audit_events` retains grant issue
and consume evidence. Migrations 0001 through 0005 remain the reconciled set.

## 2026-10-02 staging acceptance

Physical acceptance proved:

- server-verified Google administrator authority;
- grant issue;
- artifact mutation rejection;
- valid one-time grant consumption;
- replay rejection.

Production custom-domain signing remains disabled until separately reviewed.

## 2026-10-03 Authenticode physical acceptance

A debug-only physical test used a device-local Code Signing PKCS12 provider.

The physical signing execution proved:

- server one-time authorization consumption: PASS;
- local PKCS12 provider execution: PASS;
- SHA-256 Authenticode signing: PASS;
- DigiCert RFC3161 timestamp service: PASS;
- timestamp certificate-chain verification: PASS;
- final cryptographic signature verification inside the provider: PASS;
- debug-only self-signed publisher acceptance: PASS.

The successful signature was followed by an unrelated finalization defect:
Portable EXE was incorrectly passed to the Native-only x86-64 PE validator.
Fail-closed handling then deleted the signed final artifact.

The fix now runs `verifyWindowsX64Pe` after signing only when
`state.windowsArtifactKind == WINDOWS_NATIVE_EXE`. Publisher targeted tests are
`27/27 PASS`, Portable targeted tests are `10/10 PASS`, and Full Quality is
`825/825 PASS`.

## Remaining boundary

Real Windows Authenticode cryptographic signing is physically proven, but
complete final signed-artifact acceptance remains pending until the patched
debug APK repeats the Portable build, preserves the signed EXE and that EXE is
verified and launched on real Windows.

No ID token, PKCS12 password, private key, nonce or raw grant material is
stored in this wiki.
