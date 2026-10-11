---
type: decision
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - decisions
related:
  - "[[System_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/security/GoogleAdminIdentityClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/src/index.mjs"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/tests/google_oidc_admin.test.mjs"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ProPurchasesActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
  - "quality/tests/device_build_toolchain_scope_contract.test.js"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/build.gradle.kts"
---

# Decision Index

This active index records current architectural decisions only.
Full historical decision context is preserved in
[[archive/Decision_Index_History_2026-10-02]].

## Active decisions

### Device-local normal builds

Normal project compilation is device-local. Retired Railway/Render/remote
AppForge Worker infrastructure is not a fallback for normal user builds.

### Runtime isolation

Device Build Runtime and persistent Terminal Linux are separate trust and
lifecycle boundaries. Build-runtime replacement must not modify Terminal data.

### Deterministic toolchains

Build tools, runtime components and accepted engine families are versioned,
pinned and readiness-gated. Unsupported combinations fail closed.

### Artifact identity

APK, AAB, Windows Portable EXE and Windows Native EXE are explicit artifact
identities. Portable and Native EXE must never be inferred interchangeably.

### Windows Portable persistence

Portable browser state is appId-scoped outside disposable runtime extraction.
Stable local origin, persistence, relocation and durability contracts are
release requirements.

### Publisher signing

Publisher signing is owner-only and fail-closed. Server authorization is
one-time and artifact-bound. Local signing material remains local and is never
placed in repository or Second Brain.

### Admin identity

Administrator authority comes from server-verified Google identity plus the
active allow-list. Email, device ID, local flags or a Play entitlement do not
grant administrator privileges.

### Pro product

Normal use remains accountless. The paid product model is lifetime Pro, with
server-verified entitlement rather than local trust.

### Play Production

Manual workflow dispatch is internal-only. Production publishing requires the
strict release-tag/protected-main/version contract.

### Release governance

Source tests, CI, staging, physical acceptance and production release are
separate gates. A passing earlier gate must not be presented as later-gate
acceptance.
