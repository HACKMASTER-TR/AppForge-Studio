---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - build
  - artifacts
  - device-build
related:
  - "[[Build_And_Worker_Architecture]]"
  - "[[Android_App_Map]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"
---

# Device Build and Artifact Flow

The active Android build path no longer submits jobs to remote Workers.

A project is copied into an AppForge-owned device workspace, the appropriate
local toolchain is selected, and the build executes through the rootless
Linux runtime.

Generated APK/AAB files are retained locally. `ProjectLibrary` stores build
history while the Successful Builds screen publishes or shares local
artifacts through Android storage APIs.

`BuildApiClient` currently preserves the pre-existing UI-facing contract but
resolves build state and download tickets from local device artifacts.

Remote queue position, Worker capacity and server artifact tickets are not
part of the current normal build flow.
