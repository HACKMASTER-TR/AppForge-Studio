---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - android
  - compose
related:
  - "[[System_Architecture]]"
  - "[[Feature_Overview]]"
source_files:
  - "android-app/app/src/main/AndroidManifest.xml"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/build.gradle.kts"
---

# Android App Map

The Android app is Kotlin with Jetpack Compose. `UpdateGateActivity` is the launcher; `MainActivity` owns a large screen-state router and much of the creation, build, preview, production, account, template, and local-AI UI. It is approximately 25,000 lines and is a high-impact change surface.

Major packages:

- `ai/`: local assistant, Unified Agent, project generation, recovery, and safe prompt/context routines.
- `terminal/`: local and Linux PTY, workspace files, Git, SSH, OAuth connections, editor, and terminal UI.
- `io/`: project persistence, import/export, icons, keystore handling, source analysis, and templates.
- `security/`: account storage, device identity, billing, signature verification, and owner policy.
- `net/` and `build/`: Build Service clients.

The manifest declares networking, notifications, biometric, foreground-service, wake-lock, and package-install permissions. Verify every permission-dependent feature against the manifest and the related Kotlin implementation.

## APK sharing

Downloaded APK artifacts can be shared from both the build result screen
and the Successful APKs screen. The build screen shares its verified
installer-cache APK through the existing `FileProvider`; the Successful
APKs screen shares the MediaStore `content://` URI directly. Both flows
use `ACTION_SEND`, the Android APK MIME type, and temporary read grants.
No additional broad storage permission is required.
