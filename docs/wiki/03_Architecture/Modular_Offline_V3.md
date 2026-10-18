---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-18
updated: 2026-10-18
last_verified: 2026-10-18
confidence: medium
related:
  - "[[Fast_HTML_V2]]"
  - "[[Device_Build_Runtime_V3]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/FeatureSupportMatrix.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineModulePlan.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/VerifiedModuleDownload.kt"
  - "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
  - "android-app/app/src/test/java/com/appforge/studio/build/ModularOfflineV3Test.kt"
  - "android-app/app/src/test/java/com/appforge/studio/build/VerifiedModuleDownloadTest.kt"
---

# Modular Offline V3

This is an implementation boundary record, not full V3 acceptance. See
`docs/reports/fast-html-v2-modular-offline-v3-20261018.md` for remaining work and evidence.

FeatureSupportMatrix is shared by UI, analysis recommendations, readiness and
settings validation. Saved incompatible choices remain visible and block builds.
Source requirements are rechecked against the actual packaged HTML/JS snapshot.
Plain file inputs do not imply camera/microphone. Capture and unsupported APIs
produce findings, not automatic permissions. Normal HTML routing is explicit;
unsupported Fast HTML settings do not silently select the normal toolchain.

OfflineModules resolves deduplicated dependencies. Fast HTML uses verified bundled
APK/AAB templates. Android Core, standalone Node, Python, Expo and Windows Native
share only their declared dependencies; Windows Portable needs no Linux or SDK.
Selected installation replaces the former install-all operation. Core prewarm is
not proof that every arbitrary project's dependency graph is cached. Normal
builds default to offline; dependency download consent is session-only and is
never restored from imported project metadata.

Module readiness checks real critical-file SHA-256 attestations, specification
version and recursive dependencies; legacy marker presence is insufficient.
These receipts are not full ownership manifests. Existing installations remain
untouched during inspection. Node archive and Windows host downloads expose real
byte telemetry and verified resume; their atomic file promotion follows digest
verification. Other script-driven transfers remain indeterminate. Download 100%
is not installation completion. No overall percentage is invented.

Process-local operation flows survive navigation and rotation. Android process
survival, foreground download service, automatic network retry, transactional
APT/Gradle rollback, safe ownership-based removal, complete package SHA-256
migration and per-project dependency acquisition remain incomplete. The screen
states these limitations rather than providing destructive or misleading actions.
Physical Android 16 and live SDK-service acceptance must be recorded separately.
