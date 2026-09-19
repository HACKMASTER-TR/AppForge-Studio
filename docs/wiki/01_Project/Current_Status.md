---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
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
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
  - "build-service/tests/device_only_build_ui_contract.test.js"
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

## 2026-09-19 device-only Android CI compile correction

- Android Debug CI exposed an accidental structural deletion in
  `MainActivity.kt` during retired Railway callback cleanup.
- The known-good activity/app-shell prefix was restored from the immediate
  parent revision, then Railway authorization state and callbacks were removed
  surgically without removing lifecycle, navigation, URI persistence or
  `AppForgeApp`.
- `DeviceBuildEngine` Kotlin visibility and sequence-log collection compile
  errors were corrected.
- Local `file://` artifact handling in `DownloadedApkFolder.kt` now constructs
  `java.io.File` explicitly.
- A fresh Android Debug CI run remains the authoritative compile acceptance
  before real-device APK/AAB acceptance.

## 2026-09-19 real-device device-build acceptance follow-up

- AppForge Studio 5.0.29 installed and opened successfully on the physical Android device.
- The new StudioHomeV2 was judged too minimal; the next UI iteration must restore a richer device-first dashboard without restoring remote build infrastructure.
- FIKSTUR TAKIP entered the device-local build path and reported `Build cihaz üzerinde çalışacak`, then failed at 0%.
- The failure UI claimed that live logs were available but no log renderer remained after the device-only cutover.
- The current correction restores sanitized local device-build logs, highlights the first critical error, removes stale Worker/Autoscale wording from the active Builder surface, and aligns the admin stress-test concurrency with the two-thread local device engine.
- APK/AAB physical-device acceptance remains pending until the newly exposed log identifies and the project fixes the actual local build failure.
