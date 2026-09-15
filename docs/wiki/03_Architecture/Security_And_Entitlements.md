---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - security
  - auth
  - billing
related:
  - "[[Database_Map]]"
  - "[[Integration_Index]]"
source_files:
  - "build-service/src/auth.js"
  - "build-service/src/security.js"
  - "build-service/src/clientHardening.js"
  - "build-service/src/playVerifier.js"
  - "android-app/app/src/main/java/com/appforge/studio/security/SecureAccountStore.kt"
---

# Security and Entitlements

Backend authentication includes password login, JWT access tokens, verification flows, TOTP, API tokens, device binding/transfer, scope checks, and admin checks. The Android client has secure account storage, device identity, signature verification, billing, and server security clients.

Client hardening includes Android policy and attestation routes, version checks, and Play purchase ownership controls. Pro and quota entitlements are server-authoritative; UI/admin state must not be treated as billing truth.

Secrets and signing material are intentionally excluded from durable project memory. Verify behavior through source and tests; verify live enforcement only with authorized live access.
