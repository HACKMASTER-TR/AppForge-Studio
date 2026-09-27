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
- Expo SDK 54 / React Native 0.81 remains `EXPERIMENTAL`; `READY_OUTPUTS=ZERO` until an AppForge-built fixture installs and launches successfully.
- V8 physically proved the Expo build pipeline can produce APK and AAB on device.

## Recent Important Changes

- V11.1 finalizes the Expo Hermes acceptance contract while keeping the physical READY gate unchanged.

## Expo Evidence — 2026-09-27

- Runtime AGP `8.11.0`, NDK `27.1.12297006`, native ARM64 CMake/Ninja, compiler-rt, rootfs CMake staging, API 36 SDK isolation, and Prefab execution are preserved.
- V9 proved `assets/index.android.bundle` is embedded and startup reaches `APPLICATION_READY`; React context creation then failed.
- V10.1 proved the Hermes Java executor factory exists but physical APK audit showed `lib/arm64-v8a/libhermes.so` missing, causing SoLoader to fall through to `/vendor/lib64/libhermes.so`.
- V11 changes the acceptance packaging contract to `hermesEnabled=true` so Android Hermes native runtime is packaged. Host Hermes AOT remains skipped; AppForge manually embeds the Expo JS bundle generated with pinned Node 22.
- V11.1 keeps `appforgeAcceptance` in React Native `debuggableVariants` only to suppress unsupported host `hermesc` work. The Android build type itself remains `debuggable=false`.
- V11.1 contract tests require `hermesEnabled=true`; the stale `hermesEnabled=false` assertion is removed.

## Must Know

- Source, tests, CI, and observed device behavior override wiki claims.
- GitHub remains repository and CI infrastructure; retired remote project-build backends stay retired.
- Standing fail-stop delivery authorization is recorded in [[Standing_Delivery_Authorization]].
- D1 migrations remain blocked until migration history is reconciled.

## Current Risks / Open Questions

- V15 physical fixture APK contains `AppForgeExpoComponentFactory` and its probe strings, but runtime still reaches `APPLICATION_READY` / Hermes PASS and crashes before any ComponentFactory Activity or Activity lifecycle marker appears. Binary-manifest `strings` was inconclusive, and the general AppForge Terminal does not expose the device-build SDK/APK mount for `aapt2`.
- V16 makes the runtime self-proving: the custom ComponentFactory records `instantiateApplication` before `Application.onCreate()` into an in-memory buffer that is flushed to the public report, and the Java uncaught handler is checked/re-armed at `APPLICATION_READY`.
- Expo native builds also show a repeatable intermittent pattern: one build may pass while the next fails in `expo-modules-core` PCH with Ubuntu clang 18.1.3 exit 139. Treat this as a separate native-build stability bug from the launch crash.
- Expo remains experimental until the installed fixture renders and stays open.
- SDK XML/platform warnings are secondary unless they become build-blocking.

## Safety Boundaries

- Stop at any failed mandatory local, CI, device, security, migration, release, or production gate.
- Preserve `.appforge/`, all `appforge-*backup-*` directories, and physical acceptance evidence.
- Never use reset/clean/checkout to discard local work.
- Keep `main` untouched during this acceptance branch.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
- [[Standing_Delivery_Authorization]]
