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
- Expo SDK 54 / React Native 0.81 remains `EXPERIMENTAL`; V19 physical launch/UI acceptance PASS, but `READY_OUTPUTS=ZERO` until intermittent native build BUG-B is stabilized.
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
- V18 direct-launch physically reaches `MainActivity`, ReactInstanceManager, and `EXECUTE_TRANSACTION=159`, then crashes on `mqt_native_modules` with `JavascriptException: Cannot read property 'EventEmitter' of undefined`.
- V19 physical acceptance PASS: `MainActivity` reaches RESUMED, Hermes and JS run, Expo UI renders, and the prior `globalThis.expo.EventEmitter` crash is gone after duplicate-safe `ExpoModulesPackage()` registration.
- V20 targets BUG-B by disabling only `expo-modules-core` CMake PCH commands in the disposable workspace. The first V20 physical attempt failed before npm install because the patch ran too early; V20.1 moves the same scoped PCH patch after dependency installation/version verification.

## Current Risks / Open Questions

- BUG-A Expo runtime launch crash is closed by V19 physical evidence.
- BUG-B remains open until V20 produces at least four consecutive identical physical Expo builds without the prior `expo-modules-core` PCH / clang exit `139` failure. PCH disabling is a scoped stabilization workaround, not accepted as fixed before that run.
- Returning through the active-build notification can cancel the build; active build identity/session must survive background-to-foreground return.
- Build progress is not authoritative yet. App UI and notification must share one real `DeviceBuildEngine` progress source and reach 100 only on success.
- Build numbers must start at `AF-0000001000`, increment persistently, and never be reused.
- Expo remains experimental until physical launch acceptance passes.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
- [[Standing_Delivery_Authorization]]
