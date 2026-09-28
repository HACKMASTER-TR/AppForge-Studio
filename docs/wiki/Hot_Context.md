---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-28
last_verified: 2026-09-28
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
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/AppForgeApplication.kt"
  - "android-app/app/src/main/java/com/appforge/studio/AppForgeBuildNumbers.kt"
  - "android-app/app/src/main/assets/device-build/build-expo.sh"
---
# Hot Context

## Current Focus

- Device Build Runtime V3 is physically accepted for React/Vite, native Android Java/Kotlin, Python/Chaquopy, and scoped Expo SDK 54 / React Native 0.81.
- Expo V20.3 passed 4/4 consecutive physical APK+AAB builds plus final direct-launch UI acceptance. V21 promotes only this Expo family to READY; standalone React Native remains EXPERIMENTAL.
- V21.7 duplicate artifact filename ordering is physically accepted. Current work is V22 deterministic Android APK/AAB artifact selection; `main` and Play Production remain untouched.

## Must Know

- Source, tests, CI, and physical-device evidence override wiki claims.
- Remote build backends remain retired; project builds stay device-local.
- D1 migrations remain blocked pending migration-history reconciliation.
- Standing fail-stop authorization is in [[Standing_Delivery_Authorization]].

## Recent Important Changes

- BUG-A closed in V19 with duplicate-safe `ExpoModulesPackage()` registration.
- BUG-B closed in V20.3 by disabling only `expo-modules-core` PCH in the disposable Expo workspace; clang/PCH exit 139 did not recur in 4/4 acceptance.
- V21 added shared event-driven progress, non-crashing cancel flow, and persistent Build No values beginning at `AF-0000001000`.
- V21 physical acceptance passed progress synchronization, cancel stability, success-only 100%, Build No sequence/persistence, and active-build survival.
- V21.1 and V21.2 still failed BUG-C physically: notification return could show `Hazır / %0`; notification dismissal/foreground handoff also remained incorrect.
- V21.3 physical retest failed BUG-C: notification still arrived about 10 seconds late, dismissal failed, and Builder eventually returned to `Hazır / %0` while tracking was lost.
- V21.4 physical retest passed prompt notification display and compact progress, but tapping the notification caused MainActivity to be redirected/finished by the application update gate; build survival and background reappearance therefore failed.
- V21.5 physical retest passed notification tap return, same-build continuation, dismissal, background reappearance, and final-result retention; BUG-C is closed.
- V21.6 physically passed soft Builder percentage/bar movement while preserving V21.5 notification and build lifecycle behavior.
- V21.7 physically passed repeated APK/AAB/EXE saves; duplicate suffixes remain before `.apk`, `.aab`, and `.exe`, and existing files stay untouched.

## V21.7 Physical Acceptance

- Repeated APK, AAB and EXE saves passed with `(1)`, `(2)` inserted before the extension.
- Existing artifacts remained untouched; V21.7 source, tests, Android Debug CI and physical acceptance are closed.

## Current Risks / Open Questions

- V22 replaces project-wide `lastModified()` APK/AAB discovery with `:app` variant-scoped Gradle output selection; source/CI and focused device re-acceptance are required before closure.
- Expo READY remains limited to SDK54/RN0.81; broader Expo and standalone React Native stay outside the accepted surface.
- D1 migration history remains unresolved.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
- [[Standing_Delivery_Authorization]]
