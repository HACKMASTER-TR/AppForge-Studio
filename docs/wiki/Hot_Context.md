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

- Native Android Kotlin offline APK/AAB acceptance; Java/node-web passed; Project Switch V2 and source-engine refresh await device re-test.
- Pro staging; remote Build Service retired.
- Finish Clean Device Build Runtime V3 and real Android APK/AAB validation.
- Keep Terminal Linux separate from project-build Linux.
- Preserve the current Studio Home and UI V2 design direction.

## Must Know

- Source, tests, CI and observed runtime behavior override wiki claims.
- Normal project builds are device-local and use a disposable build rootfs.
- Railway, Render and Supabase are not AppForge project-build infrastructure.
- GitHub is repository/CI infrastructure; Google Play/Cloud are distribution and billing infrastructure.
- Windows host CI, Android packaging, Windows 11 smoke, Studio web-project EXE and JavaScript runtime passed. Offline EXE readiness requires the pinned host.

## Recent Important Changes

- PR #55 merged to main at 15c608f18a3bc9fd50620d561e6b4049b4dd8b8f; main Stability and Android Debug CI passed.
- React/Vite node-web passed online and fresh-project fully offline APK/AAB device acceptance; local HTTPS fixed the blank WebView.

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
- Isolated APK: interruption, ownership recovery and restart passed. Live challenge replay returned 409; fresh verification passed. Second Android device activated and auto-verified after restart. Isolated `.prodeath` APK: observed process termination, fail-closed restart, unchanged-key recovery, fresh HTTPS verification and auto-verification passed. Independent PID/log evidence absent; unrelated crashes untested.

## Current Risks / Open Questions

- Device AAB generated; public save failed because `DownloadManager` rejects local `file://` tickets. Source now streams local AAB through MediaStore/SAF; physical save re-acceptance pending.
- Never touch `main`, Play Production, `appforge-failover` or migrations during this staging sequence.
- A signed device challenge authenticates the installation; HTTPS status has no separate application-level response signature.
- Open-app revocation refresh is best-effort; protected server routes must enforce active grants independently.
- Legacy Play entitlement is separate from admin-issued Pro.
- Native Android/Python APK/AAB device acceptance remains open; node-web React/Vite offline acceptance passed. Windows web EXE acceptance is complete.

## Read Next

- [[Pro_Code_Lifecycle_Staging]]
- [[Device_Build_Runtime_V3]]
- [[Current_Status]]
