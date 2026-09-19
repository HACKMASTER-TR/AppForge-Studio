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
  - "build-service/tests/device_build_toolchain_scope_contract.test.js"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
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

- Device build provisioning now uses the Ubuntu base runtime first and scopes optional Node/npm or Python packages to the selected source engine instead of requiring the full Terminal development profile for every Android build.

- `DeviceBuildEngine` was introduced for local builds.
- `BuildApiClient` is now a compatibility facade for the local build path.
- Worker/deployment/autoscale workflows were retired.
- Firebase Messaging was removed from the AppForge Studio runtime dependency set.
- Home V2 was simplified around create, projects, AI, builds and developer tools.

- Real-device FIKSTUR TAKIP retry confirmed that the earlier Node/npm package-resolution blocker is gone. The next concrete blocker was Ubuntu APT/dpkg failing while configuring `openjdk-17-jre-headless`.
- Device build Java provisioning now avoids Ubuntu OpenJDK package post-install scripts. AppForge provisions a checksum-pinned Temurin JDK 17 archive, validates its SHA-256, exposes it as `/opt/appforge-device/jdk-17`, and passes that JAVA_HOME explicitly to Gradle.
- The device-toolchain ready marker was advanced so an older apt-JDK environment cannot be mistaken for the new deterministic toolchain.

## Current Risks / Open Questions

- Real-device FIKSTUR TAKIP acceptance exposed the first concrete device-build blocker: the universal Linux toolchain installer attempted to install Node/npm for Android builds and failed in Ubuntu package dependency resolution. The local build path now scopes optional toolchains by source engine; a fresh APK and real-device retry remain required.

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

- Real-device FIKSTUR TAKIP validation after deterministic JDK provisioning exposed another persistent-rootfs issue: stale half-configured Node/npm packages from older universal toolchain installs were being reconfigured by dpkg during unrelated Android builds.
- Non-Node device builds now preserve healthy Node packages but purge only broken/partial Node/npm package states before the base APT transaction.
- `node-web` explicitly bypasses this repair path and keeps its Node/npm toolchain.
