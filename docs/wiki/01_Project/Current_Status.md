---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-17
last_verified: 2026-09-17
confidence: high
tags:
  - status
  - validation
related:
  - "[[Hot_Context]]"
  - "[[Bug_Index]]"
source_files:
  - "build-service/tests/appforge_terminal_viewport_stability_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - ".appforge/runtime-blockers.json"
  - "build-service/package.json"
  - "build-service/tests/appforge_terminal_integration.test.js"
  - "build-service/src/fastSigningKey.js"
  - "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "android-app/app/src/test/java/com/appforge/studio/UpdateGatePlayVisibilityTest.kt"
---

# Current Status

## Verified repository state

- The checked-out revision is `591a65b`; no live deployment state was inspected.
- The old `.secondbrain` and legacy `docs` content were intentionally deleted before this wiki bootstrap.
- Legacy brain references in scripts, CI policy, Android navigation, owner sync, and local-AI context were removed during this bootstrap.

## Validation evidence

- A pre-refocus `npm test` run in `build-service` reported 683 passes and 37 failures in this local environment; most failures could not load `adm-zip` because `build-service/node_modules` was absent.
- `appforge_terminal_integration.test.js` previously read a deleted legacy privacy document. It now verifies only the Android connection, encryption, and configuration sources and passed 7/7 direct tests on 2026-09-15.
- `fast_signing_key.test.js` had an independent byte-length assertion failure (expected 384, actual 438) in the pre-refocus run.
- Android Gradle tests were not run: no wrapper or local Android toolchain configuration was present.

- The terminal native-viewport touch-scroll regression now has a source fix and a targeted contract test. The targeted test passes 2/2 and `git diff --check` is clean on the current work branch. On-device scroll/fling acceptance passed on 2026-09-16.

- AppForge Terminal's Ubuntu/proot runtime exposed host Java/Gradle tools that can crash natively with exit 139. Autopilot now detects the Android-hosted/proot environment before starting Java or Gradle and defers the authoritative Android JVM suite to `android-debug.yml`. Ordinary Linux hosts retain the Gradle-version compatibility guard.

- Android/proot `safe_stage()` now uses the verified `git add --all` index-write path after forbidden-file validation; ordinary hosts keep explicit pathspec staging.

## Shipping state

`BUG-7` device acceptance passed on 2026-09-16. Scroll/fling, Copy -> Write persistence, leaving and returning to Terminal, workspace-selection persistence, keyboard open/close smoothness, shortcut placement, restart, `pwd`, and `echo APPFORGE_OK` were confirmed on-device. There is no active BUG-7 runtime blocker.

## 2026-09-17 Android correction package

- Normal-user update prompts now require a newer version to be visible from
  Google Play Core for the current account/device; CI/GitHub-only versions
  are not sufficient.
- Successful APK cards include Android 11+ MediaStore `Çöpe taşı`.
- Terminal `+ Oturum` moves persisted creation off the UI dispatcher and
  selects the new session before PTY startup. Device acceptance remains
  pending for this new multi-session correction.
