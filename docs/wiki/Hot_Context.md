---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-27
last_verified: 2026-09-27
confidence: high
tags:
  - hot-context
  - device-build
  - runtime-v3
related:
  - "[[Index]]"
  - "[[Device_Build_Runtime_V3]]"
  - "[[Current_Status]]"
  - "[[Bug_Index]]"
  - "[[Standing_Delivery_Authorization]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "android-app/app/src/main/assets/device-build/build-expo.sh"
  - "quality/tests/expo54_device_probe_contract.test.js"
---
# Hot Context

## Current Focus

- Device Build Runtime V3 is physically accepted for React/Vite, native Android Java/Kotlin, and Python/Chaquopy.
- Expo SDK 54 / React Native 0.81 remains EXPERIMENTAL until the installed standalone APK opens successfully.
- V8 physically clears the build pipeline: the Expo fixture reaches 100% and produces both APK and AAB.

## Recent Important Changes

- Prefab direct execution is physically cleared.
- ARM64 CMake/Ninja now executes successfully far enough to reach NDK compiler detection.
- V4 cleared rootfs CMake staging and the physical fixture now produces both APK and AAB.

## Expo Physical Evidence — 2026-09-27

- Runtime AGP is `8.11.0`; NDK is `27.1.12297006`.
- Prefab direct execution is physically cleared: generated `prefab_command` has `#!/bin/sh`; Java direct start/RC and shell probe all pass with RC `0`.
- ARM64 CMake/Ninja host compatibility is physically confirmed: CMake now runs instead of failing `bad machine`.
- CMake reaches compiler detection and selects `/opt/appforge-device/android-sdk/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin/clang`.
- That official Linux NDK host executable cannot run natively on the ARM64 AppForge Ubuntu host.
- V3 physically cleared the `-lgcc` runtime blocker; CMake now identifies Clang 18.1.3 and reaches its real compiler `try_compile` stage.
- Installer acceptance requires an `aarch64-linux-android24` C link probe, C++ compile probe, and LLVM archive probe before the ARM64 NDK readiness marker is written.
- V9 physical crash evidence proves `index.android.bundle` exists and Application startup completes before React Native context creation fails.

## Must Know

- Source, tests, CI, and observed runtime behavior override wiki claims.
- GitHub remains repository and CI infrastructure; Railway, Render, and Supabase are not normal AppForge project-build infrastructure.
- Standing fail-stop delivery authorization is recorded in [[Standing_Delivery_Authorization]].
- D1 migrations remain blocked until migration history is reconciled.

## Current Risks / Open Questions

- V9 runtime evidence reaches `ReactContextInitParams` with a missing JS executor while AppForge has `hermesEnabled=false`. V10 packages the Hermes runtime without host AOT. The first V10 physical rebuild fail-stopped before Gradle because Expo SDK 54 no longer emits an explicit `isHermesEnabled` override in MainApplication. V10.1 supports both the older Hermes-property shape and SDK 54's `isNewArchEnabled` anchor.

- V9 root cause is the legacy React Native JS executor path: RN 0.81 runtime reaches `ReactContextInitParams` with a missing executor while AppForge intentionally has `hermesEnabled=false`. V10 keeps host Hermes AOT disabled, explicitly packages `hermes-android`, forces runtime Hermes in the generated MainApplication, and preserves crash evidence.
- Additional NDK host executables may become visible after Clang advances.
- SDK XML/platform-location warnings remain secondary unless they become build-blocking.

## Safety Boundaries

- Never continue past a failed mandatory local, CI, device, security, migration, release, or production gate.
- Preserve `.appforge/` state, `appforge-*backup-*` directories, and physical acceptance evidence.
- Do not use reset/clean/checkout to discard local work.
- Do not mark Expo READY until physical APK build and installed runtime acceptance both pass.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
- [[Standing_Delivery_Authorization]]
