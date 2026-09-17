---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-17
last_verified: 2026-09-17
confidence: high
tags:
  - terminal
  - developer-tools
related:
  - "[[Android_App_Map]]"
  - "[[Account_And_Security_Map]]"
  - "[[Bug_Index]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalWorkspaceScreen.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/OwnerFilesPanel.kt"
  - "build-service/tests/appforge_terminal_back_navigation_contract.test.js"
  - "build-service/tests/appforge_latest_apk_replace_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalTerminalEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalStandaloneDeveloperBootstrap.kt"
  - "android-app/app/src/main/assets/terminal/appforge-apk"
  - "build-service/tests/appforge_apk_current_head_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxTerminalJobService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/WorkspaceFileService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/GitWorkspaceService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/SshTerminalClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/ExternalConnectionsClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalCommandPolicy.kt"
  - "build-service/tests/appforge_terminal_integration.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "build-service/tests/appforge_terminal_persistent_viewport_workspace_contract.test.js"
---

# Terminal and Developer Tools

The Android `terminal/` package owns terminal UI, local and Linux runtime adapters, workspace files, Git, SSH, external connection flows, editor-related tools, command policy, and terminal state. It is a local-app surface; the terminal contract test asserts that the Build Service does not expose `/api/terminal` or `/api/shell` routes.

`LocalTerminalEngine` launches the Android shell path used by the local terminal. Linux runtime, PTY-session, workspace, Git, SSH, and connection modules are separate change surfaces; inspect the named module and its tests rather than inferring behavior from the screen name.

GitHub and Railway connection flows use provider HTTPS endpoints in `ExternalConnectionsClient`. Connection and pending-authorization persistence are handled by `SecureAccountStore`; see [[Account_And_Security_Map]] for the storage boundary. The wiki makes no claim about live provider authorization, user data, or legal disclosures.

In normal terminal mode, the vendored Termux `TerminalView` owns touch drag, scrollback, and fling behavior. Compose transform gestures must not consume one-finger pan input while that native viewport is active. The Compose pinch path remains available only for the fallback renderer.

The native Termux mirror lifecycle is session-aware. When a TerminalView is recreated after copy mode or screen navigation, backlog replay occurs only after the native view has attached to its TerminalSession. A terminal restart clears both the AppForge ANSI buffer and the Termux mirror backlog/scrollback so stale output cannot survive a restart.

Command restrictions belong to `TerminalCommandPolicy`. Device keyboard and terminal rendering acceptance passed on 2026-09-16.


## Owner-only access

AppForge Terminal is an owner-only Android surface. `OwnerAccessPolicy` is the authoritative account check. `StudioHomeV2` does not render the Terminal card for non-owner Free or Pro accounts, and `MainActivity` rejects Terminal navigation, restored Terminal state, and external-authorization routing for non-owner accounts. Terminal source remains packaged in the common APK, but it is not exposed or routable to those accounts.

## New PTY session creation

`+ Oturum` creates its persisted PTY record on `Dispatchers.IO`, selects
the new tab before potentially slow PTY startup, and prevents environment
re-initialization from stealing the active user-selected session. A failed
new-session start cleans up the incomplete registry entry. Device acceptance
is still required for the interactive multi-session behavior.

## PTY native viewport rebinding

Each AppForge PTY owns a persistent Termux mirror controller. When the active
PTY session changes, Compose must key the native `AndroidView` by the current
controller/session identity. This forces `TerminalView` creation and mirror
registration for the newly selected PTY while preserving each session's
persistent TerminalSession and scrollback when it is not visible.

Device acceptance remains required for multi-session switching.

## AppForge APK shortcut provenance

The packaged `appforge-apk` terminal command treats AppForge Studio
specially: it resolves the current local branch and exact HEAD, finds a
successful `android-debug.yml` run for that same HEAD, and downloads that
run's non-expired `AppForgeStudio-debug-*` artifact. If no successful
artifact exists for the exact HEAD, the command stops instead of silently
installing an older AppForge Studio Release APK. Other GitHub projects keep
the generic latest-Release behavior.

## Terminal system back and APK bridge stability

Android system back is consumed locally inside Terminal before leaving the
Terminal route. When a secondary Terminal tab is active, one system-back
action returns to the Terminal tab; only system back from the Terminal tab
returns to the parent AppForge screen. Dangerous-command confirmation is
dismissed before navigation.

`/workspace/AppForgeDownloads` is a permanent Linux-to-Android owner APK
bridge. Owner-file synchronization must not delete the bridge or import
hidden/`.part` publication files. `appforge-apk` recreates the bridge before
atomic publication and exposes only the completed
`AppForgeStudio-latest.apk`. The owner APK vault treats AppForge latest and
commit-named AppForge update APKs as one self-update family so stale update
copies are removed without touching project APK/EXE artifacts.
