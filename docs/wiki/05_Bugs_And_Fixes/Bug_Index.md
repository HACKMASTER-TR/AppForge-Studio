---
type: bug
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-25
last_verified: 2026-09-25
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
  - "quality/tests/appforge_unified_agent_local_artifact_save_contract.test.js"
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

- Unified Agent local APK/AAB/EXE buttons rejected device `file://` tickets:
  after device build succeeded, all three buttons passed the local ticket to an
  HTTPS-only DownloadManager path. Save local bytes to public MediaStore
  Downloads/AppForgeStudio, verify the copied byte count, publish only after
  success and roll back incomplete copies. Keep HTTPS downloads distinct.
  Physical-device re-acceptance remains required after Android CI.

- Unified Agent historical artifact lookup after app update — its durable
  session contains the successful build ID but `ProjectLibrary` does not contain
  that agent-owned build. After process restart local jobs are empty; exact
  session/build/output-kind fallback is needed without searching other builds.
  Device acceptance must check saved outputs without rebuilding.

- Google admin restore after APK update — owner memory is intentionally
  process-only and stored ID token expires within an hour. On startup, only
  an unexpired encrypted candidate may be revalidated with HTTPS admin status;
  network failure must not grant access or erase valid encrypted candidate.
  Expired credentials require an explicit fresh Google sign-in.

- Selected icon not embedded in device-local outputs — prepared icon URI was
  persisted but not injected into generated Android resources/manifest or
  project-specific Windows PE icons. New source integration fails closed for
  selected icons; no generic Host mutation. Physical APK launcher and Windows
  Explorer/portable launch acceptance remain pending.

- Selected icon visual acceptance regression — the initially successful
  source-level icon test did not exercise actual launcher/Explorer appearance.
  AppIconProcessor applied a 640/1024 inset to content that Android launchers
  already shrink, and detailed photos exceeded the unchanged NSIS Host's
  RT_ICON slot capacities. Android content sizing and project-copy bounded
  PE PNG encoding are corrected in source. Physical APK icon and Windows
  Explorer/launch re-acceptance remain open; never count contract tests alone.

- Custom-icon visual size after source-level green tests — physical Android
  and Windows screenshots showed the old 960/1024 inset plus a cyan/white
  frame around a wide logo. Source-only contract PASS does not establish
  visual acceptance. The selected opaque image now creates a full-width
  aspect-preserving square master with background sampled from its own
  corners. Previously prepared icon files stay unchanged; reselect the
  original artwork for device acceptance. Never claim that a wide design
  can fill a square without crop or distortion. Windows host remains pinned.


- Builder stale build state after project switch — physical re-test
  showed the first project-key-only reset was insufficient. After a
  completed/failed build, opening another project could overwrite the
  status with `Proje yüklendi` while retaining the old build ID,
  progress and timer. That stale state was then interpreted as active,
  exposing old progress and `DERLEMEYİ İPTAL ET` until process restart.
  Explicit project create/open/load actions now reset inactive transient
  build state immediately. Active builds block project replacement.
  Saved history and canonical artifacts are preserved, and output state
  requires a non-null matching `buildProjectKey`.


- Builder stale source-engine metadata after project restore/switch —
  a saved draft could still declare `node-web` even when the currently
  imported project tree was detected as another technology. The build
  then entered npm preparation and failed with `package.json bulunamadı`.
  Local Builder builds now refresh source technology and build engine
  from the actual imported folder immediately before build start.


- Device build cancel reader-thread crash — physical-device logcat on
  2026-09-25 confirmed `FATAL EXCEPTION: AppForgeLinux-device-...-android-build`
  with `InterruptedIOException: read interrupted by close() on another thread`
  at `LinuxShellEngine.kt`. Cancel/timeout teardown now marks pipe closure as
  expected before destroying the process. Expected close-time reader I/O is
  contained; unexpected reader I/O is propagated back to normal build error
  handling instead of escaping an unmanaged thread. Physical cancel
  re-acceptance remains required.

- Active device build UI reset to Ready / 0 — `DeviceBuildEngine` jobs are
  process-level, while Builder runtime state and `buildBusy` were only Compose
  `remember` state. Activity/UI recreation could therefore show `Hazır • %0`
  although the real local build continued. The foreground tracker now persists
  the active build identity/project key/start time, Builder rehydrates from the
  real engine snapshot before rendering, and lifecycle-restored polling resumes
  until a terminal state. Physical lifecycle re-acceptance remains required.
