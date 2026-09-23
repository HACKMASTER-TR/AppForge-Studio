---
type: bug
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-22
last_verified: 2026-09-22
confidence: high
tags:
  - bugs
  - validation
related:
  - "[[Current_Status]]"
source_files:
  - ".appforge/runtime-blockers.json"
  - "quality/tests/appforge_terminal_viewport_stability_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt"
  - "quality/tests/appforge_terminal_mirror_lifecycle_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  - ".github/workflows/android-play-release.yml"
  - "scripts/appforge"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "quality/tests/appforge_terminal_persistent_viewport_workspace_contract.test.js"
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

- Immutable Source Worker Android toolchain gap — an Expo/React Native build
  requested NDK 27.1.12297006 while the image only contained NDK
  28.2.13676358. Gradle attempted runtime SDK installation, but the hardened
  `/opt/android-sdk` is intentionally read-only. Source Worker now uses a
  declared compatibility matrix verified during image build and read-only
  runtime smoke. Missing SDK components are reported as Worker toolchain
  failures instead of generic Gradle/user validation errors.


- Source Worker SDK package-list newline escaping — the compatibility-matrix
  Docker layer accidentally emitted literal `\\n` delimiters. `xargs` therefore
  passed the whole Android SDK package list to `sdkmanager` as one invalid
  package name such as `platform-toolsnplatforms;android-34...`. The generator
  now emits real newline separators and a regression contract protects this
  Docker build boundary.


- Device-local Windows EXE Gradle compile collision — normal-project EXE
  integration used the same local name `outputs` for both requested
  `DeviceArtifactKind` values and discovered APK/AAB files inside
  `buildGradleProject`. Kotlin rejected the conflicting declarations and
  produced secondary type-inference errors. The two concepts are now named
  `requestedArtifacts` and `artifactFiles`, and a scoped regression contract
  protects the compile boundary.


- Owner artifact visibility inconsistency — APK already wrote a public
  `Downloads/AppForgeStudio` copy plus an optional private owner copy, while
  AAB and Windows EXE short-circuited to the private owner vault. On Android
  10+ AAB and EXE now publish the public copy as well, so owner/admin builds
  remain visible in the normal Files application. Combined outputs inherit the
  same per-artifact rule.

- Device-local AAB public save rejected `file://` artifact URI — normal
  device builds return local artifact tickets. The AAB non-owner path still
  used Android `DownloadManager.Request`, which rejects non-HTTP(S) URIs with
  `Can only download HTTP/HTTPS URIs`. AAB export now uses the existing
  MediaStore `downloadArtifactToDownloads` stream on Android 10+ and SAF on
  Android 8/9, matching the device-local EXE transport behavior. Physical
  public-save re-acceptance remains required.

- Terminal accountless verified-owner crash — empty session email reached `TerminalWorkspaceResolver.accountScope` and threw on the Compose UI thread before Terminal opened. Verified owners without a normal account now use a separate stable workspace namespace; blank-email legacy workspace migration is disabled, and expired owner access returns a recoverable screen. Terminal Linux, Pro state and build assets are not reset. Source/test/CI/device acceptance must be reported separately.

- Successful Builds re-save after process restart — the UI retained persisted
  `ProjectLibrary` build history while `BuildApiClient.createDownloadTicket`
  required an in-memory `DeviceBuildEngine.jobs` entry. After an APK update,
  the EXE canonical file and public Share remained available but `Kaydet`
  reported `EXE çıktısı hazır değil.` The ticket now falls back only to the
  exact successful saved build's canonical artifact directory and output kind;
  missing or ambiguous files fail closed. Device re-save after restart remains
  a separate physical acceptance gate.

## 2026-09-23 retired backend cleanup

Legacy remote Build Service source and backend-only test cases retired with
explicit mapping to preserved device, Terminal, Pro, Windows and CI tests
under `quality/tests`. Existing historical bug records are retained.
