---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-20
last_verified: 2026-09-20
confidence: high
tags:
  - hot-context
  - device-build
  - runtime-v3
related:
  - "[[Index]]"
  - "[[Device_Build_Runtime_V3]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "build-service/tests/device_build_runtime_v3_contract.test.js"
  - "build-service/tests/device_build_capability_matrix_contract.test.js"
---

# Hot Context

## Current Focus

- Stage accountless, admin-issued Pro code lifecycle (grant revocation, owner-key recovery and safe reactivation). **Not shipped or device-accepted.**
- Finish Clean Device Build Runtime V3.
- Validate APK and AAB on a real Android device.
- Keep Terminal Linux physically separate from project-build Linux.
- Studio Home uses a richer modern dashboard; the rejected over-minimal Home V2 layout must not return.
- App-wide UI V2 uses one navy/cyan/violet design language; runtime/toolchain internals stay out of primary user copy.
- The temporary five-build device stress UI is retired; normal single-project device build remains authoritative.

## Must Know

- Normal user project builds are device-local.
- Project builds use a versioned disposable build rootfs, not the persistent Terminal rootfs.
- Railway, Render and Supabase are not AppForge project-build infrastructure.
- GitHub remains repository/CI infrastructure.
- Google Play / Google Cloud remain distribution and billing infrastructure.
- Source, tests, CI and observed runtime behavior override wiki claims.

## Recent Important Changes

- `DeviceBuildRuntimeV3` owns the clean build-only Linux environment.
- Toolchain revision moved to V3.
- `DeviceBuildCapabilities` records engine and output readiness.
- Current proven device engines are static Web, Node Web, Android Gradle Kotlin/Java and Python/Chaquopy.
- APK and AAB are current local outputs.
- Windows Portable EXE is a first-class target but remains gated until its local packager passes Windows acceptance.
- APK ↔ EXE conversion remains a product requirement.
- Flutter/Dart, React Native, Expo, Android NDK/C++, .NET Android and MAUI are explicit future capability families.
- Unity remains external-tool-required until a supported build-host path exists.

## Current Risks / Open Questions

- Pro staging 0002–0005 schema was applied manually through the D1 Console; 16/16 objects checked, foreign_key_check returned no violations, and the one active admin was preserved. Wrangler d1_migrations is absent and must be reconciled before any migrations apply. Worker not deployed; Android Kotlin CI passed; real-device acceptance remains pending.
- A signed device challenge authenticates the Android installation; the HTTPS server status response has no separate application-level server signature.
- A revoked Pro status is checked on startup and refreshed on a best-effort 60-second loop while active; it is not an instantaneous offline revocation promise. Server-gated features must enforce their own authorization.
- Legacy account/Play billing entitlements are independent of admin-issued codes; they require separate end-to-end verification.
- Real-device APK/AAB acceptance is still required.
- Windows EXE must not be marked READY before real Windows validation.
- Future engines must not silently fall back to remote Workers.
- Toolchains must remain pinned and reproducible.

## Read Next

- [[Pro_Code_Lifecycle_Staging]]
- [[Device_Build_Runtime_V3]]
- [[System_Architecture]]
- [[Build_And_Worker_Architecture]]
- [[Android_App_Map]]
- [[Current_Status]]
