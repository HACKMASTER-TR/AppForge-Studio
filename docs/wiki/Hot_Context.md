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
- Expo SDK 54 / React Native 0.81 remains `EXPERIMENTAL`; `READY_OUTPUTS=ZERO` until an AppForge-built fixture installs and stays open.
- `main` remains untouched.

## Must Know

- Source, tests, CI, and observed device behavior override wiki claims.
- GitHub remains repository/CI infrastructure; retired remote build backends stay retired.
- D1 migrations remain blocked until migration history is reconciled.
- Standing fail-stop authorization is recorded in [[Standing_Delivery_Authorization]].

## Recent Important Changes

- V11.1 physically proved Hermes Java/native packaging while host Hermes AOT stays skipped and AppForge embeds the production Expo bundle.
- V16 physically proves `AppForgeExpoComponentFactory` is active: Application instantiation BEFORE/AFTER is captured, `APPLICATION_READY` is reached, Hermes class/native load PASS, and the AppForge uncaught handler is still active at READY.
- V17 physical evidence shows the sampled process remains alive past 3 seconds with both AppForge uncaught handlers intact, yet no Activity factory/lifecycle marker appears. Main Looper messages `164` and repeated `131` are observed, but no ActivityThread `EXECUTE_TRANSACTION=159`.
- V18 records installed launcher resolution, enabled/exported Activity state, process importance, lifecycle count, and whether ActivityThread `EXECUTE_TRANSACTION=159` ever appears.

## Current Risks / Open Questions

- Earlier runs record Android exit reason `4`, but the sampled V17 run does not capture a current Java crash; it stays alive while no Activity launch transaction appears. V18 must distinguish launcher-resolution failure from a later crash.
- Expo native compilation is intermittently unstable: identical builds can alternate PASS/FAIL in `expo-modules-core` PCH with Ubuntu clang 18.1.3 exit `139`.
- Returning through the active-build notification can cancel the build; active build identity/session must survive background-to-foreground return.
- Build progress is not authoritative yet. App UI and notification must share one real `DeviceBuildEngine` progress source and reach 100 only on success.
- Build numbers must start at `AF-0000001000`, increment persistently, and never be reused.
- Expo remains experimental until physical launch acceptance passes.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
- [[Standing_Delivery_Authorization]]
