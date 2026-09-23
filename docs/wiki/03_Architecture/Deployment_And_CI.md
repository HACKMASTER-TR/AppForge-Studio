---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-20
last_verified: 2026-09-20
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
  - ".github/workflows/pro-kotlin-feature.yml"
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


## Feature-branch Pro Kotlin validation

`pro-kotlin-feature.yml` runs only on the dedicated
Pro control-plane feature branch. It is a compile
check, not an APK release or device acceptance.

For API 37, SDK Manager uses the package
`platforms;android-37.0`, with `android.jar` inside
`platforms/android-37.0`. The Android Gradle
configuration retains `compileSdk = 37`.

The initial CI run failed during Android SDK setup
because it requested `platforms;android-37`.
Kotlin compilation was skipped. A later successful
run is required before recording Kotlin compile PASS.

## 2026-09-23 retirement

The legacy Node backend test entrypoint was replaced by
`npm --prefix quality test`; `appforge-stability-gate.yml` runs the retained
device/Pro/Windows/Terminal contracts with Node 22 and does not build or
deploy any remote worker. Android Debug remains the compilation gate.
