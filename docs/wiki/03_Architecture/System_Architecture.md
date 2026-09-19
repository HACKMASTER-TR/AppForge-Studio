---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - architecture
  - device-build
related:
  - "[[Project_Overview]]"
  - "[[Build_And_Worker_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/AndroidLinuxRuntimeManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"
---

# System Architecture

AppForge Studio uses a device-first architecture.

User project compilation runs inside the Android application through the
packaged rootless Linux/PRoot runtime. Project source is not uploaded to a
remote AppForge build Worker.

`DeviceBuildEngine` owns local build execution. The existing
`BuildApiClient` name is temporarily retained as an Android UI compatibility
facade, but the normal build path delegates to the device engine instead of
calling `/api/builds`.

Initial device build families are static Web, npm-based Web projects,
Android Gradle Kotlin/Java projects, and Python/Chaquopy projects.

APK and AAB outputs are stored locally. Project metadata and build history
remain device-local through `ProjectLibrary`.

GitHub remains repository/CI infrastructure. Google Play and Google Cloud
remain AppForge Studio distribution, billing and Play-integrity
infrastructure. They are not project-build Workers.

## Clean Device Build Runtime V3

Terminal Linux and project-build Linux are separate trust/lifecycle domains.

The Terminal rootfs is a persistent developer workspace. Project builds use a
versioned build-only rootfs which may be replaced when the runtime revision
changes. Project source is mounted through the build workspace and is not
uploaded to a remote AppForge Worker.

`DeviceBuildCapabilities` is the compatibility registry for source engines and
artifact targets. Unsupported or unvalidated engines fail explicitly.
