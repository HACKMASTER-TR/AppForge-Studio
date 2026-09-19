---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - hot-context
related:
  - "[[Index]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
---

# Hot Context

## Current Focus

- Complete the device-only build cutover.
- Validate APK and AAB creation on a real Android device.
- Rework StudioHomeV2 from the rejected over-minimal layout into a richer but still clear device-first dashboard.

## Must Know

- Normal user project builds now target the device-local PRoot/Linux engine.
- Railway, Render and Supabase are no longer AppForge build infrastructure.
- GitHub remains repository/CI infrastructure.
- Google Play / Google Cloud remain AppForge Studio distribution and billing infrastructure.
- Source code, tests, CI and runtime evidence override this wiki.

## Recent Important Changes

- `DeviceBuildEngine` was introduced for local builds.
- `BuildApiClient` is now a compatibility facade for the local build path.
- Worker/deployment/autoscale workflows were retired.
- Firebase Messaging was removed from the AppForge Studio runtime dependency set.
- Home V2 was simplified around create, projects, AI, builds and developer tools.

## Current Risks / Open Questions

- Real-device FIKSTUR TAKIP acceptance reached the device-local build preflight but failed at 0%; sanitized local build logs are required to identify the actual device-build failure.

- Real-device FIKSTUR TAKIP acceptance reached the device-local build preflight but failed at 0%; the failure screen must expose sanitized local build logs before root-cause repair.

- Real-device APK/AAB build acceptance is still required.
- ARM64 Android Build Tools provisioning must remain checksum-pinned and reproducible.
- Unsupported project families must fail clearly rather than use a hidden cloud fallback.
- Legacy backend/Worker source may remain in the repository until cleanup is verified.

## Read Next

- [[System_Architecture]]
- [[Build_And_Worker_Architecture]]
- [[Deployment_And_CI]]
- [[Android_App_Map]]
- [[Bug_Index]]
