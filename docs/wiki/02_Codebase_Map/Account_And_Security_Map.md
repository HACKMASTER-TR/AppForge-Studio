---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-16
last_verified: 2026-09-16
confidence: high
tags:
  - account
  - security
  - billing
related:
  - "[[Security_And_Entitlements]]"
  - "[[Terminal_And_Developer_Tools]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/security/SecureAccountStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioDeviceIdentity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/net/AppForgeAccountClient.kt"
  - "build-service/src/auth.js"
  - "build-service/src/security.js"
  - "build-service/src/clientHardening.js"
  - "build-service/src/playVerifier.js"
  - "build-service/src/proEntitlements.js"
---

# Account and Security Map

`SecureAccountStore` is the Android persistence boundary for account and external-connection material. Its source declares AES/GCM storage and normalizes the provider identity before recovering connection or pending-authorization data. `StudioDeviceIdentity`, `StudioBillingManager`, `StudioSecurityClient`, and `AppForgeAccountClient` are adjacent client-side account/security surfaces.

The backend separates auth, security, client hardening, Play verification, and Pro-entitlement modules. Server authority is required for authentication, entitlement, and quota decisions; a client screen or local state is not proof of an active entitlement.

Do not put token values, signing material, personal data, provider health, or legal assertions into the wiki. Validate the particular trust boundary through source and tests, and use authorized live access only when current remote state is required.


`OwnerAccessPolicy` is also the authoritative Android gate for owner-only Terminal visibility and routing. Free and Pro non-owner accounts must not receive a Terminal UI entry or be able to enter the Terminal route through restored state or external callbacks.
