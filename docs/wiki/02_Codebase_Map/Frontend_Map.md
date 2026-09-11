---
type: codebase
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
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
---

# Frontend Map

The verified primary client surface is the Android application.

`MainActivity.kt` currently imports and coordinates many Android, Compose, WebView, download, network, and platform APIs. Treat changes there as potentially high-coupling work and prefer extracting feature-specific state and UI responsibilities.

The Terminal UI/runtime is under the Android `terminal` package. Input, PTY, scrollback, bracketed paste, keyboard/IME behavior, and session state are regression-sensitive.

When touching other client surfaces such as web or desktop, verify their current source entry points first and update this page only after source confirmation.
