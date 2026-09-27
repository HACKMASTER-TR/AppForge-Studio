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
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
  - "android-app/app/src/main/assets/device-build/python-template/app/src/main/java/com/appforge/pythonruntime/MainActivity.kt"
  - "quality/tests/device_build_cancel_reader_contract.test.js"
  - "quality/tests/device_build_active_state_restore_contract.test.js"
  - "quality/tests/python_template_system_bars_contract.test.js"
---
# Hot Context

## Current Focus

- Device Build Runtime V3 is physically accepted for React/Vite, native Android Java/Kotlin, and Python/Chaquopy.
- Normal project compilation is device-local and separate from Terminal Linux.
- Expo SDK 54 / React Native 0.81 remains experimental with zero accepted outputs.

## Recent Important Changes

- The Prefab execution shim passed physical execution: generated `prefab_command` now has a shebang, Java direct execution returns `0`, and `/bin/sh` execution returns `0`.
- The build progressed to the next native boundary and exposed ARM64-host CMake incompatibility: SDK CMake `3.22.1` is rejected as `bad machine`.
- Current experiment maps AGP's expected CMake/Ninja paths to verified ARM64 Ubuntu host binaries.


## Expo SDK 54 Physical Evidence — 2026-09-27

- Expo SDK 54 / React Native 0.81 remains EXPERIMENTAL with zero accepted outputs.
- Runtime AGP is `8.11.0`; NDK `27.1.12297006` is present.
- The Prefab direct-exec blocker is physically cleared: `prefab_command` is now `#!/bin/sh`, Java start PASS/RC `0`, shell probe PASS/RC `0`.
- The next failure is `[CXX1429]`: `/opt/appforge-device/android-sdk/cmake/3.22.1/bin/cmake` is rejected as `bad machine` on the ARM64 device host.
- The current fix uses ARM64 Ubuntu CMake/Ninja through AGP's expected SDK paths and adds toolchain readiness/smoke gates.
- A fresh physical Expo build is required. If CMake passes, the NDK host-tool boundary may become the next acceptance check.


## Must Know

- Source, tests, CI, and observed runtime behavior override wiki claims.
- Acceptance applies only to tested fixtures and prepared caches.
- Railway, Render, and Supabase are not normal AppForge project-build infrastructure.
- GitHub remains repository and CI infrastructure.
- Standing fail-stop delivery authorization is recorded in [[Standing_Delivery_Authorization]].

## Current Risks / Open Questions

- ARM64 CMake/Ninja compatibility has not yet passed physical Expo acceptance.
- Official Android host tooling may expose another architecture boundary inside NDK host executables after CMake starts.
- SDK XML/platform-layout warnings remain secondary until native configuration advances past host-tool execution.


## Safety Boundaries

- Never continue past a failed mandatory local, CI, device, security, migration, release, or production gate.
- D1 migrations remain blocked until migration history is reconciled.
- Preserve `.appforge/` backups and physical acceptance evidence.
- Do not mark Expo READY until APK build and installed runtime acceptance both pass.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
- [[Standing_Delivery_Authorization]]
