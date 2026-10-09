---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-09
last_verified: 2026-10-09
confidence: high
tags:
  - project
  - overview
related:
  - "[[Index]]"
  - "[[System_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt"
  - "cloudflare/control-plane/wrangler.example.toml"
---

# Project Overview

AppForge Studio is an Android application studio. The Kotlin/Jetpack Compose application supports project creation, import, inspection, builds and artifact handling. Normal project compilation is device-local: `BuildApiClient` delegates build creation to `DeviceBuildEngine`, which uses `DeviceBuildRuntimeV3`. `DEFAULT_BUILD_SERVICE_URL` is `device://local`. Build execution and persisted local artifacts remain on the device.

The current HTTPS control plane is a separate Cloudflare Worker under `cloudflare/control-plane`, with D1 persistence where applicable. Debug/Release HTTPS control-plane routing is distinct from local build routing; server-verified account, entitlement and authorization responsibilities remain active.

The former Node.js/Express remote Build Service is retired. Its `build-service/` tree, PostgreSQL migrations, Redis queues, remote `worker.js` / `source-worker.js`, quotas and Node/SQL/Docker build topology are historical infrastructure, not current project-build architecture.

## Boundaries

- Live GitHub, Play Console, and production health remain unknown until authenticated checks are run. Railway is retired from the active project-build architecture and is not a current provider whose live status needs to be inferred.
- This wiki does not replace the Android product’s local AI or Unified Agent features.
