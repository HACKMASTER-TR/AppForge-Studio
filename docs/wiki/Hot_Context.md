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

- Physical proof confirmed actual AGP `8.11.0`: Java direct execution of the generated `prefab_command` fails, while explicit `/bin/sh` execution succeeds.
- The current local change applies a narrowly scoped Prefab execution shim and requires fresh physical acceptance.

## Expo SDK 54 Physical Evidence — 2026-09-27

- Expo reaches `:expo-modules-core:configureCMakeDebug[arm64-v8a]`.
- NDK `27.1.12297006` is present.
- The generated `prefab_command` is mode `700`, readable, executable, root-owned, plain ASCII text, and has no shebang.
- Moving native module intermediates from `/workspace` to rootfs-native storage did not fix execution.
- JDK `FORK` launch mode did not fix execution.
- The attempted source-level AGP 8.7.3 pin did not control the actual runtime AGP and is removed.
- Physical proof on commit `0ecf2c7` reported actual AGP `8.11.0`.
- Exact `prefab_command` first line begins with the AppForge JDK Java executable.
- Java `ProcessBuilder` direct execution of that exact file fails with `java.io.IOException`, `error=13`.
- Explicit `/bin/sh prefab_command` succeeds with exit code `0`.
- Therefore the current blocker is the shebang-less direct-exec boundary under the AppForge PRoot/JVM environment, not file permissions or Prefab command contents.
- Current local experiment adds a narrowly scoped pre-exec shim for the generated AGP Prefab command. Physical re-test is required before this fix is accepted.

## Must Know

- Source, tests, CI, and observed runtime behavior override wiki claims.
- Acceptance applies only to tested fixtures and prepared caches.
- Railway, Render, and Supabase are not normal AppForge project-build infrastructure.
- GitHub remains repository and CI infrastructure.
- Standing fail-stop delivery authorization is recorded in [[Standing_Delivery_Authorization]].

## Current Risks / Open Questions

- Prefab execution shim has not yet passed physical Expo APK acceptance.
- SDK XML/platform-layout warnings remain secondary until the direct-exec blocker is cleared.

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
