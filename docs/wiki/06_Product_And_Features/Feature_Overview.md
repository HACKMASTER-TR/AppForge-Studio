---
type: feature
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-09
last_verified: 2026-10-09
confidence: high
tags:
  - features
  - product
related:
  - "[[Android_App_Map]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectImporter.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectBackupManager.kt"
  - "cloudflare/control-plane/src/index.mjs"
---

# Feature Overview

Android feature areas include project creation, source import/conversion, templates, previews, build history/artifacts, project backup/library, account and Pro flows, keystore management, local AI, Unified Agent, and developer terminal tools. Individual behavior must be verified in the matching source and tests.

Normal compilation and local artifact flow belong to the device-local `DeviceBuildEngine` / `DeviceBuildRuntimeV3`, accessed through `BuildApiClient`. Android UI and local project modules such as `ProjectLibrary`, `ProjectImporter` and `ProjectBackupManager` own the supported local project lifecycle.

Separate HTTPS control-plane services handle server-verified functions, including account and entitlement checks. The Cloudflare Worker/D1 control plane is not the project compiler. The former remote Build Service is retired; its queue, quota and workspace services must not be attributed to a current remote build backend.
