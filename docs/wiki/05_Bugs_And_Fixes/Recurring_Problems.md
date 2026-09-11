---
type: problem
status: active
project: AppForge Studio
created: 2026-09-11
updated: 2026-09-11
last_verified: 2026-09-11
confidence: high
tags:
  - appforge
  - second-brain
related:
  - "[[Index]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt"
---

# Recurring Problems

## Terminal input / paste regressions

The embedded terminal has historically required care around:

- bracketed paste mode
- large paste chunking
- IME/Backspace behavior
- replay/double-execution protection
- Unicode boundaries
- large scrollback responsiveness

When touching input dispatch, PTY writes, buffer limits, or terminal session state, inspect existing regression tests and add coverage before changing established behavior.

## Environment limitation vs source failure

Android builds may be impossible in constrained local/PROOT environments because of native SDK/NDK/toolchain support. Do not classify that alone as a source regression. Use supported CI or another valid Android build environment to confirm source-level failures.
