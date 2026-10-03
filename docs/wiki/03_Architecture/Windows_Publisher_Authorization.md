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
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt"
  - "windows-host/tests/payload.test.cjs"
  - "windows-host/payload.cjs"
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

The Native-only post-sign validator defect was fixed and the subsequent
physical build preserved the signed Portable artifact.

That retest exposed a separate runtime boundary: Authenticode appends the PE
Certificate Table after the AppForge overlay payload. The previous Windows Host
looked for the AppForge footer at physical EOF, so the signed process started
but displayed `AppForge Windows payload imzası bulunamadı.` even though the
embedded Authenticode signer, Code Signing EKU and DigiCert RFC3161 timestamp
were present.

The local fix makes both the Windows Host reader and Android final Portable
validator aware of `IMAGE_DIRECTORY_ENTRY_SECURITY`. Unsigned Portable behavior
remains EOF-based. Android now fails closed if a signed Portable artifact can
no longer resolve its AppForge payload.

Targeted quality is `14/14 PASS` and Full Quality is `826/826 PASS`.

## Remaining boundary

The new parser still requires Windows Host CI, exact artifact pinning and a
fresh physical signed Portable rebuild before `REAL_AUTHENTICODE_END_TO_END`
can be closed.

No ID token, PKCS12 password, private key, nonce or raw grant material is
stored in this wiki.
