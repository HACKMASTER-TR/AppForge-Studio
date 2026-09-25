---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-25
last_verified: 2026-09-25
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

## Must Know

- Source, tests, CI and observed runtime behavior override wiki claims.
- Acceptance applies to the exact tested fixtures and prepared offline caches; arbitrary dependency sets are not automatically accepted.
- Railway, Render and Supabase are not normal AppForge project-build infrastructure.
- GitHub remains repository and CI infrastructure.

## Current Risks / Open Questions

- Acceptance covers the exact tested fixtures and prepared offline caches; unprepared dependency versions can still fail offline.
- PR #56 remains Draft and no production delivery step has started.

## Safety Boundaries

- Do not merge PR #56 without explicit approval.
- Do not start Play Production.
- Do not deploy Cloudflare or apply D1 migrations during this sequence.
- Do not modify the verified Windows Host.
- Preserve .appforge backups and device acceptance evidence.

## Read Next

- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
- [[Bug_Index]]
