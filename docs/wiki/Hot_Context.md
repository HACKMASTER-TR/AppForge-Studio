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

- Device Build Runtime V3 physical offline acceptance is complete for the current React/Vite, native Android Java, native Android Kotlin and Python/Chaquopy fixtures.
- Normal project compilation remains device-local and separate from Terminal Linux.
- PR #56 remains Draft until an explicit merge decision.

## Verified 2026-09-25

- React/Vite node-web: offline APK+AAB and runtime launch passed.
- Native Java: offline APK+AAB and APPFORGE_NATIVE_JAVA_PASS.
- Native Kotlin: offline APK+AAB and APPFORGE_NATIVE_KOTLIN_PASS.
- Python/Chaquopy: offline APK+AAB, Chaquopy 17.0.0, Python 3.12 and APPFORGE_PYTHON_CHAQUOPY_PASS.
- Python system-bar safe-area correction passed physical re-test.
- Device build cancellation no longer crashes AppForge.
- Active-build restoration no longer falls back to Hazır / 0.
- Project switching and source-engine refresh passed the current acceptance sequence.

## Recent Important Changes

- Physical offline acceptance is complete for the current React/Vite, native Java, native Kotlin and Python/Chaquopy fixtures.
- Python generated runtime safe-area handling, safe build cancellation and active-build lifecycle restoration passed device re-test.

## Expo SDK 54 Experimental Acceptance — 2026-09-27

- Expo SDK 54 / React Native 0.81 remains EXPERIMENTAL with zero accepted outputs.
- Commit `ccad35f` passed Android Debug CI and produced a verified AppForge Studio APK.
- Physical Expo build still failed in `:expo-modules-core:configureCMakeDebug[arm64-v8a]`.
- The generated `prefab_command` was mode 700, readable and executable, owned by root, and had no shebang.
- Moving native intermediates to rootfs-native storage did not remove the failure.
- The scoped JDK `FORK` launch-mechanism experiment also did not remove the failure and is superseded.
- Commit `a4ea644` also failed physically with the same `prefab_command` error=13 result; the AGP 8.7.3 compatibility pin did not produce physical acceptance.\n- The current proof build records the AGP version actually loaded by Gradle and compares Java ProcessBuilder execution of the exact generated `prefab_command` with explicit `/bin/sh` interpretation.\n- No further permission, AGP-version, or shebang workaround is considered verified until this execution-boundary probe is observed on-device.\n- Physical-device APK build and runtime acceptance remain mandatory before Expo can become READY.

## Must Know

- Source, tests, CI and observed runtime behavior override wiki claims.
- Acceptance applies to the exact tested fixtures and prepared offline caches; arbitrary dependency sets are not automatically accepted.
- Railway, Render and Supabase are not normal AppForge project-build infrastructure.
- GitHub remains repository and CI infrastructure.

## Current Risks / Open Questions

- Acceptance covers the exact tested fixtures and prepared offline caches; unprepared dependency versions can still fail offline.
- PR #56 remains Draft and no production delivery step has started.

## Safety Boundaries

- A standing owner authorization for the full fail-stop delivery chain is recorded in [[Standing_Delivery_Authorization]].
- Do not continue past a failed mandatory local, CI, physical-device, security, migration, release, or production gate.
- D1 migration execution remains technically blocked until migration history is reconciled and verified.
- Do not modify the verified Windows Host without a technically justified change.
- Preserve `.appforge/` backups and device acceptance evidence.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
