---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
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
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalTerminalEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxTerminalJobService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/WorkspaceFileService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/GitWorkspaceService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/SshTerminalClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/ExternalConnectionsClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TerminalCommandPolicy.kt"
  - "build-service/tests/appforge_terminal_integration.test.js"
---

# Terminal and Developer Tools

The Android `terminal/` package owns terminal UI, local and Linux runtime adapters, workspace files, Git, SSH, external connection flows, editor-related tools, command policy, and terminal state. It is a local-app surface; the terminal contract test asserts that the Build Service does not expose `/api/terminal` or `/api/shell` routes.

`LocalTerminalEngine` launches the Android shell path used by the local terminal. Linux runtime, PTY-session, workspace, Git, SSH, and connection modules are separate change surfaces; inspect the named module and its tests rather than inferring behavior from the screen name.

GitHub and Railway connection flows use provider HTTPS endpoints in `ExternalConnectionsClient`. Connection and pending-authorization persistence are handled by `SecureAccountStore`; see [[Account_And_Security_Map]] for the storage boundary. The wiki makes no claim about live provider authorization, user data, or legal disclosures.

Command restrictions belong to `TerminalCommandPolicy`. Device keyboard and terminal rendering acceptance remains subject to the active runtime blocker in [[Legacy_Brain_Removal_And_Validation_State]].
