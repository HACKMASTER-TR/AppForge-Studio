---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-19
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - device-build
  - runtime-v3
  - multi-platform
related:
  - "[[System_Architecture]]"
  - "[[Build_And_Worker_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "build-service/tests/device_build_runtime_v3_contract.test.js"
  - "build-service/tests/device_build_offline_gradle_contract.test.js"
  - "build-service/tests/device_build_gradle_helper_runtime.test.js"
  - "build-service/tests/device_build_capability_matrix_contract.test.js"
  - "build-service/tests/device_build_aapt2_arm64_contract.test.js"
---

# Device Build Runtime V3

## Isolation boundary

Project builds use a dedicated, versioned and disposable Ubuntu rootfs under
the device-build namespace.

The AppForge Terminal Linux environment is a separate persistent developer
workspace and is never reused as a project-build rootfs.

A Device Build Runtime revision mismatch may delete and reinstall only the
dedicated build runtime. It must never purge or repair Terminal files.

## Current proven device engines

- Static HTML/CSS/JavaScript WebView
- npm-built static Web projects such as React, Vue, Svelte and Vite
- Native Android Gradle Kotlin/Java
- Python Android through Chaquopy

These remain APK/AAB device-local targets.

## Output architecture

The common artifact model covers:

- Android APK
- Android AAB
- Windows Portable EXE

A project may eventually request APK, AAB, EXE, APK+AAB or all compatible
outputs. An output must not be exposed as ready until its device-local packager
has passed CI and physical-device/platform acceptance.

## Windows and conversion target

AppForge retains the existing AppForge-managed APK -> Windows EXE and
EXE -> Android APK conversion contracts.

The long-term requirement is a local Windows Portable EXE packager. Normal user
source must not be uploaded to a Railway, Render or remote AppForge Worker as a
fallback.

Where technically valid, one shared source/project model should produce both
Android and Windows artifacts. Native platform-only APIs must be reported by
preflight instead of being silently removed.

## Engine roadmap

The V3 capability registry reserves explicit engine families for:

- Android NDK / C / C++
- React Native
- Expo
- Flutter / Dart
- .NET Android
- .NET MAUI
- Windows Web portable EXE
- Unity

Unvalidated engines remain EXPERIMENTAL, PLANNED or
EXTERNAL_TOOL_REQUIRED. They must never be presented as working merely because
their detector exists.

Unity specifically requires an officially supported build host/editor path;
the current Android-hosted Linux runtime must not pretend to provide one.

## UI requirement

The previously over-minimal StudioHomeV2 layout was rejected. Future V3 build
UI should remain clear but restore useful project/build status, output targets,
capability information and richer dashboard affordances without returning to an
unmanageable single screen.

## Offline Gradle shell regression

The installer defines one ensure-gradle helper before the
toolchain readiness shortcut. A separate runtime regression
test executes its extracted shell body in disposable test
directories with curl blocked, covering installed Gradle,
verified cache, missing cache and invalid cache checksum.

These checks do not constitute physical Android APK/AAB
acceptance or prove that project dependencies are cached.

## Offline Gradle preflight

The device build engine passes offline state to the
version-specific Gradle installer.

A verified cached distribution can be reused without
network access. An unavailable distribution fails with
APPFORGE_OFFLINE_GRADLE_MISSING instead of downloading.

The ensure-gradle helper is refreshed before the existing
toolchain readiness shortcut, preserving already installed
V3 toolchains and their readiness marker.

This is a source-level implementation. Real Android offline
APK/AAB acceptance remains pending.

## Acceptance

Shipping requires:

1. Node/contracts fail 0.
2. Android Debug CI success.
3. Clean first-install runtime test.
4. Runtime-revision replacement test.
5. Real APK and AAB build on Android.
6. Per-engine device acceptance.
7. Windows EXE acceptance on an actual Windows machine before EXE becomes READY.

## ARM64 AAPT2 compatibility correction

The first real V3 device build reached the SDK toolchain smoke test but failed
before project Gradle execution because AAPT2 could not resolve libdl.so.

The correction replaces the ARM64 native Android SDK tools with checksum-pinned
Linux-glibc ARM64 executables from the fixed Build-Tools 36.0.0 release.
The official x86_64 tools remain selected for x86_64 hosts.

AAPT2 version execution and Android SDK 37 platform resource parsing are
required before the new toolchain readiness marker is written.

This source correction requires Android CI and physical-device APK/AAB
acceptance. Static tests alone do not prove native execution.
