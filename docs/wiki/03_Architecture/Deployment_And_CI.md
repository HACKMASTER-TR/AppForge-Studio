---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - deployment
  - ci
related:
  - "[[Test_And_CI_Map]]"
  - "[[System_Architecture]]"
source_files:
  - ".github/workflows/android-debug.yml"
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/appforge-stability-gate.yml"
  - ".github/workflows/cleanup-old-runs.yml"
  - "scripts/appforge"
  - "scripts/appforge-stability-gate"
---

# Deployment and CI

AppForge Studio no longer deploys a remote Build Service or Worker pool for
normal application builds.

The active distribution path is:

1. AppForge Studio builds user projects on the Android device.
2. GitHub remains the source repository and CI authority.
3. `android-debug.yml` validates Android application changes.
4. `android-play-release.yml` handles Google Play delivery when explicitly
   requested and eligible.
5. Google Play / Google Cloud remain external distribution and billing
   infrastructure.

Railway, Render, remote Android Workers, Source Workers, Windows Workers,
autoscaling, and production build-service deployment workflows are retired
from the active architecture.

CI success is not a substitute for device acceptance. Device-build changes
must be tested on a real supported Android device before shipping.
