---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - builds
  - device-build
related:
  - "[[System_Architecture]]"
  - "[[Deployment_And_CI]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/BuildRuntimeState.kt"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/AndroidLinuxRuntimeManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxRuntimeFoundation.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectTechnologyDetector.kt"
  - "quality/tests/device_only_cutover_contract.test.js"
---

# Build Architecture

The active AppForge project-build path is device-local.

`DeviceBuildEngine` executes builds through AppForge's packaged rootless Linux
runtime. There is no remote Worker queue in the normal Android project-build
path.

The engine automatically routes supported projects without exposing an engine
selector to ordinary users.

Initial device-build targets:

- HTML / CSS / JavaScript
- npm static Web builds including React, Vue, Svelte and Vite-style projects
- Kotlin / Java Android Gradle projects
- Python Android projects through the packaged Chaquopy template

The user-facing build lifecycle is intentionally simple:

`Hazırlanıyor -> Derleniyor -> İmzalanıyor -> Hazır`

APK and AAB outputs remain local. Unsupported technologies fail locally with
a capability explanation rather than silently falling back to cloud Workers.

Real-device validation is required before this migration is shipping-complete.

## Runtime V3 and multi-output direction

`DeviceBuildRuntimeV3` owns the dedicated clean build rootfs lifecycle.

Current READY engines remain:

- webview-static
- node-web
- android-gradle
- python-android

The capability registry also models future Android NDK, React Native, Expo,
Flutter/Dart, .NET Android, .NET MAUI, Windows Web and Unity requirements.

APK/AAB are currently proven. Windows Portable EXE is an explicit artifact
target but must remain gated until a local Windows packager passes actual
Windows acceptance.

No unvalidated engine or artifact may trigger a hidden cloud fallback.
