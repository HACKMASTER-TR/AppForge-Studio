---
type: bug
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-17
last_verified: 2026-09-17
confidence: high
tags:
  - bugs
  - validation
related:
  - "[[Current_Status]]"
source_files:
  - ".appforge/runtime-blockers.json"
  - "build-service/tests/fast_signing_key.test.js"
  - "build-service/tests/appforge_terminal_integration.test.js"
  - "build-service/tests/appforge_terminal_viewport_stability_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt"
  - "build-service/tests/appforge_terminal_mirror_lifecycle_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt"
  - "build-service/src/clientHardening.js"
  - "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  - ".github/workflows/android-play-release.yml"
  - "scripts/appforge"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "build-service/tests/appforge_terminal_persistent_viewport_workspace_contract.test.js"
  - "build-service/src/reactNativeBuildEngine.js"
  - "build-service/tests/react_native_build_error_excerpt.test.js"
---

# Bug Index

- [[Legacy_Brain_Removal_And_Validation_State]] — device-runtime validation history and cleanup-related validation state.

- Terminal native viewport gesture regression — the native Termux `TerminalView` must receive normal one-finger drag/fling input; the Compose transform detector is fallback-only. A source contract test protects this ownership. On-device verification passed on 2026-09-16.

- Terminal mirror lifecycle regression — copy-mode exit or leaving/re-entering the Terminal screen could recreate the native Termux viewport without restoring visible history, while restart could leave stale mirror scrollback. Source now replays after TerminalView attachment and resets AppForge/native buffers together. On-device verification passed on 2026-09-16.

- Unified Agent artifact MediaStore API guard — `MediaStore.Downloads.EXTERNAL_CONTENT_URI` is API 29+ while AppForge keeps minSdk 26. The Q+ exporter is explicitly API-gated; API 26–28 continue through the existing legacy export path. Release lint must remain green without raising minSdk or baselining this error.

- Android Play rollout/version-policy mismatch — backend update floors must not assume that a build visible to one Play track/account is globally available. Unconfigured version policy now fails open, stale cached FORCED state cannot hard-lock offline startup, and a forced client update is relaxed when Play cannot actually deliver the required minimum to that account. Official versioned GitHub releases publish to the production track; manual Play workflow runs retain the configurable test/internal path.

Document only significant, reusable debugging knowledge. Do not add one-off visual or formatting defects.

- Terminal new-session responsiveness regression — `+ Oturum` previously
  performed persisted session creation from the UI coroutine and did not
  select the new tab until PTY startup returned. New-session persistence now
  runs on IO, the tab becomes active before startup, and failed starts clean
  up their incomplete session.

- Successful APK trash action gap — downloaded AppForge APK cards now keep
  Install/Share and add Android 11+ MediaStore trash semantics without
  silently converting the action into permanent deletion on older Android.

- Terminal multi-session native viewport rebinding regression — creating a
  second PTY could select the new AppForge session while Compose reused the
  old AndroidView. The new Termux mirror controller therefore never received
  `createView()` / `ensureRegistered()`, producing a black terminal viewport.
  The native AndroidView is now keyed by controller/session identity.

- Legacy Android unknown-version update lockout — Android builds predating
  the explicit `X-AppForge-Version-Code` contract arrive as `legacy_android`
  without a trustworthy version code. They must not be interpreted as
  version zero. The backend compares the configured public-Play legacy grace
  version against the minimum supported version. Once the real production
  minimum advances beyond that grace version, legacy clients are forced to
  update normally.

- React Native/Expo Gradle root-cause truncation — source-build command
  failures previously exposed only the final 16,000 characters of Gradle
  output. With `--stacktrace`, this could discard the real `FAILURE`,
  `* What went wrong:`, `Execution failed for task`, or `Caused by:` block
  and leave only Gradle internal stack frames for classification. Failure
  excerpts now preserve the root-cause block while retaining a bounded tail
  sample. The focused contract protects short-output behavior, legacy
  tail-only fallback when no marker exists, root-cause preservation, and
  Gradle FAILURE-block priority.
