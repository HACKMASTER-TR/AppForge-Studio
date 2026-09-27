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
  - "android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/AppForgeBuildNumbers.kt"
  - "android-app/app/src/main/assets/device-build/build-expo.sh"
---
# Hot Context

## Current Focus

- Device Build Runtime V3 is physically accepted for React/Vite, native Android Java/Kotlin, Python/Chaquopy, and the scoped Expo SDK 54 / React Native 0.81 Android path.
- Expo V20.3 produced 4/4 consecutive physical APK+AAB builds; the final APK installed, launched directly, rendered `APPFORGE_EXPO54_DEVICE_PASS`, and remained stable.
- V21 promotes only that proven Expo SDK54/RN0.81 family to READY APK+AAB. Standalone React Native remains EXPERIMENTAL with zero accepted outputs.
- `main` remains untouched.

## Must Know

- Source, tests, CI, and observed device behavior override wiki claims.
- GitHub remains repository/CI infrastructure; retired remote build backends stay retired.
- D1 migrations remain blocked until migration history is reconciled.
- Standing fail-stop authorization is recorded in [[Standing_Delivery_Authorization]].

## Recent Important Changes

- BUG-A is closed: V19 duplicate-safe `ExpoModulesPackage()` registration removed the `globalThis.expo.EventEmitter` runtime crash; Hermes, JS, Activity lifecycle and UI render physically passed.
- BUG-B is closed: V20.3 disables only `expo-modules-core` PCH in the disposable workspace; clang/PCH exit 139 did not recur across 4/4 consecutive physical builds.
- V21 makes active-build notification return navigation/rebind-only, uses one shared engine progress rule for UI + notification, and adds event-driven build milestones.
- V21 replaces epoch Build No values with a synchronously persisted sequence beginning at `AF-0000001000`.
- V21 CI compile follow-up replaces six stale Builder `backendProgress` stage-label references with the shared `safeProgress` value; the first V21 CI run failed only on those unresolved references.
- The next V21 CI run compiled successfully and reached all 259 Android/JVM unit tests; one stale Unified Agent test still expected accepted Expo SDK54/RN0.81 to report `buildReady=false`. This follow-up aligns that unit test and removes the obsolete experimental bypass from the preparer.
- V21 physical acceptance: progress synchronization, cancel stability, success-only 100%, and persistent Build No sequence passed. BUG-C still showed a terminal-notification UI flash (`Hazır / %0`) before a second notification tap restored `Başarılı / %100`. V21.1 seeds the terminal notification Build ID synchronously into `BuildRuntimeState` before Builder step 10 renders.

## Physical Re-acceptance Still Required

- BUG-C: background an active build, tap its notification, verify the same Build ID continues and is not cancelled.
- BUG-D: verify Builder and notification show the same percentage, long stages hold steady, failure/cancel keep the last real stage, and only success reaches 100.
- FEATURE-E: verify new builds show `AF-0000001000`, `AF-0000001001`, then after app close/reopen `AF-0000001002`.

## Current Risks / Open Questions

- BUG-C notification return, BUG-D progress synchronization and persistent Build No still require the combined V21 physical device acceptance after the new APK is installed.
- Expo READY is intentionally scoped to Expo SDK 54 / React Native 0.81; broader Expo versions and standalone React Native remain outside the accepted surface.
- D1 migration work remains blocked until migration history is reconciled.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
- [[Standing_Delivery_Authorization]]
