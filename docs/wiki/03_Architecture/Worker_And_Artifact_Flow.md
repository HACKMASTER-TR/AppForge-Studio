---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-17
last_verified: 2026-09-17
confidence: high
tags:
  - build
  - workers
  - artifacts
related:
  - "[[Build_And_Worker_Architecture]]"
  - "[[Database_Schema_Coverage]]"
  - "[[Backend_API_Domains]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeV2.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/BuildArtifactModel.kt"
  - "build-service/src/workerRuntime.js"
  - "build-service/src/jobQueue.js"
  - "build-service/src/workspaceBuild.js"
  - "build-service/src/downloadTickets.js"
  - "build-service/src/storage.js"
  - "build-service/src/buildEngine.js"
---

# Worker and Artifact Flow

Build work is represented in the backend queue and claimed by workers whose capabilities satisfy the job requirement. `jobQueue.js` contains worker registration, claim, heartbeat, retry/requeue, cancellation, and queue-statistics paths. The code distinguishes capability-constrained jobs, including source-build isolation handling.

`workerRuntime.js` coordinates worker execution. Workspace build, build-engine, storage, and download-ticket modules cover adjacent submission and artifact-delivery responsibilities. Download tickets mediate artifact access; do not replace their behavior with raw storage-path assumptions.

Queue positions, worker availability, artifacts, and remote worker health are runtime facts. This map documents code responsibilities only, not a claim that any worker is currently available.

## Android Successful Builds delivery

The Android Successful Builds surface is driven first by successful build
history and structured output availability rather than by scanning downloaded
APK files. APK, Android AAB, and Windows Portable EXE are represented as
explicit artifact types.

A successful artifact is visible before it is downloaded. Saving creates a
user-visible scoped-storage entry under `Downloads/AppForgeStudio`. Existing
files under the legacy `Downloads/AppForge Studio` path remain readable for
backward compatibility.

Artifact download continues through Build Service download tickets. Sharing,
APK installation, and Android MediaStore trash operations act on the locally
saved artifact, while the remote successful build remains available for a
later re-download.
