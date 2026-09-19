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
  - "build-service/tests/studio_home_modern_ui_contract.test.js"
  - "build-service/tests/retired_five_build_contract.test.js"
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

## Builder Android system-back behavior

Builder steps 1 through 10 live inside the same `AppScreen.BUILDER` route,
so route history alone cannot represent wizard-step navigation.

Android system back therefore consumes Builder steps first:
`step > 1` moves to `step - 1`. Only Builder step 1 may leave the Builder
through the normal AppScreen back history. This matches the Builder's
visible Geri button behavior and prevents system back from jumping directly
from step 2+ to Home.

## Modern Studio Home

`StudioHomeV2` is the primary project dashboard. The previously rejected
over-minimal layout must not return.

The Home surface now keeps a clearer visual hierarchy with:

- a branded project-production hero and live project/build counts,
- quick project creation,
- AI and Unified Agent entry points,
- successful-build access,
- conversion and import actions,
- recent project cards,
- project-management tools,
- owner-only Terminal/Admin controls.

Normal account and owner authorization rules remain unchanged.

## Retired five-build stress surface

The temporary five-build stress/test surface was removed from the production
Builder UI together with its dedicated tester allow-list and batch UI state.
Normal project build execution remains available through `UYGULAMAYI DERLE`.

Admin Ops remains protected by `OwnerAccessPolicy`; removing the stress surface
does not broaden administrative access.

## Studio Home composition boundary

`StudioHomeV2.kt` remains a compact navigation/state coordinator and must stay
below the existing size guard. Modern visual primitives live in
`StudioHomeDashboard.kt`.

This preserves the simplified-home architectural boundary without returning to
the rejected sparse design. Terminal remains visible only inside the active
owner guard. The retired five-build stress surface must not return.

## AppForge UI V2

`AppForgeTheme` in `AppForgeUiTokens.kt` is the shared visual authority for
normal Android surfaces: deep navy background, cyan primary actions, violet
accents and rounded elevated cards.

Studio Home keeps the approved richer dashboard hierarchy. Internal labels
such as runtime revision, Worker capacity and toolchain preflight names are
not primary product copy. Build failures show an actionable summary first;
sanitized raw logs remain available through an explicit technical-details
control.

Terminal and Excel Tools keep domain-specific layouts while sharing the same
palette. Standalone update and Pro purchase activities also use the shared
theme.
