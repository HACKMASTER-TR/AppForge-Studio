---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-22
last_verified: 2026-09-22
confidence: high
tags:
  - hot-context
  - device-build
  - runtime-v3
related:
  - "[[Index]]"
  - "[[Device_Build_Runtime_V3]]"
  - "[[Pro_Code_Lifecycle_Staging]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  - "quality/tests/device_build_runtime_v3_contract.test.js"
  - ".github/workflows/pro-staging-live-audit.yml"
  - "cloudflare/control-plane/src/pro_redemption.mjs"
  - "cloudflare/control-plane/tests/pro_reactivation_postcommit_contract.test.mjs"
---

# Hot Context

## Current Focus

- Pro staging; remote Build Service retired.
- Finish Clean Device Build Runtime V3 and real Android APK/AAB validation.
- Keep Terminal Linux separate from project-build Linux.
- Preserve the current Studio Home and UI V2 design direction.

## Must Know

- Source, tests, CI and observed runtime behavior override wiki claims.
- Normal project builds are device-local and use a disposable build rootfs.
- Railway, Render and Supabase are not AppForge project-build infrastructure.
- GitHub is repository/CI infrastructure; Google Play/Cloud are distribution and billing infrastructure.
- Windows generic-host CI, Android device packaging, physical Windows 11 smoke execution, normal Studio web-project EXE generation, Windows execution, and JavaScript runtime acceptance all passed. The offline pack may report Windows Portable EXE ready when the pinned host is installed.

## Recent Important Changes

- D1 staging schema 0002–0005 was applied manually and validated; `d1_migrations` is absent, so do not run migration apply until ledger reconciliation.
- Existing DB binding and Google configuration are preserved.
- Staging Worker deployment is live; curl HTTP Matrix and Live Audit passed health, D1 reachability and the read-only ownership route.
- Android Kotlin CI and physical-device initial activation passed.
- Restart auto-verification passed.
- Admin revoke removed Pro and the active refresh path failed closed.
- Keystore recovery preserved installation identity without restoring a revoked entitlement.
- Reactivation committed successfully in D1 but returned a false-negative HTTP 409.
- Root cause is the reactivation response path trusting exact D1 `meta.changes` values after a trigger-backed batch.
- Current source fix keeps the atomic `receiptGuard` and post-verifies the committed activation code, active grant and redemption receipt.
- The corrected staging Worker was deployed; same-device direct reactivation, restart verification, offline fail-closed and online recovery passed.
- An isolated APK passed controlled interruption, ownership recovery and post-recovery restart checks. Live staging challenge replay passed: reused signed status challenge returned HTTP 409 and fresh verification passed. A second physical Android device passed initial activation and automatic verification after restart. An isolated `.prodeath` debug APK passed the device-observed process-termination, fail-closed restart, unchanged-key recovery, fresh HTTPS verification and subsequent automatic-verification sequence. Independent PID/log evidence was not collected; unrelated crash scenarios remain untested.

## Current Risks / Open Questions

- Physical-device AAB generation succeeded, but the first public-save acceptance exposed a local `file://` ticket being sent to Android `DownloadManager`, which only accepts HTTP/HTTPS. Source now routes AAB through the same MediaStore/SAF streaming path used for local artifacts; physical save re-acceptance is pending.
- Never touch `main`, Play Production, `appforge-failover` or migrations during this staging sequence.
- A signed device challenge authenticates the installation; HTTPS status has no separate application-level response signature.
- Open-app revocation refresh is best-effort; protected server routes must enforce active grants independently.
- Legacy Play entitlement is separate from admin-issued Pro.
- APK/AAB real-device acceptance remains open. Windows Portable EXE acceptance is complete for the READY web engines (`webview-static` and `node-web`); the reserved standalone `windows-web` engine remains PLANNED.

## Read Next

- [[Pro_Code_Lifecycle_Staging]]
- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
