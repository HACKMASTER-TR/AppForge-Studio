---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-17
last_verified: 2026-09-17
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
  - "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
  - "android-app/app/build.gradle.kts"
  - "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "android-app/app/src/test/java/com/appforge/studio/UpdateGatePlayVisibilityTest.kt"
  - "build-service/tests/android_system_back_navigation_contract.test.js"
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

## Play-visible update policy

Normal-user update visibility is reconciled with Google Play Core. Backend
version values may express maintenance/minimum policy, but CI, GitHub APK,
or unreleased version numbers do not become user-visible updates unless
Google Play actually offers a newer version to that account/device.

## Successful APK trash

The Successful APKs surface keeps Install and Share and also exposes
`Çöpe taşı`. On Android 11+ AppForge marks its MediaStore APK as trashed
instead of performing an irreversible delete. Trashed MediaStore entries
are excluded from the Successful APK query.

## Android system back navigation

`MainActivity` owns the application-level Android system-back policy.
Child AppForge screens must consume system back and route to their
AppForge parent/return destination instead of falling through to Activity
exit. Home requires an explicit Yes/No exit confirmation. Nested
Successful Builds navigation in `StudioHomeV2` consumes system back before
the Home exit handler.

## Global Android back-stack policy

`MainActivity` maintains a bounded real `AppScreen` history for Android
system-back navigation. This history observes all actual screen transitions,
including routes that historically assigned `screen = AppScreen.X` directly,
so system back does not depend only on workspace-return metadata.

Home owns the explicit Yes/No application-exit confirmation. Terminal keeps
its separately verified local tab-back behavior. Normal AppForge routes use
a late route-level BackHandler so system back returns to the immediately
previous AppForge screen. Returning to Builder also restores the captured
Builder step. Reaching Home clears stale navigation history.
