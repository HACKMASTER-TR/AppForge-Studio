---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - config
  - environment
related:
  - "[[Deployment_And_CI]]"
source_files:
  - "android-app/app/build.gradle.kts"
---

# Config and Environment Map

Backend configuration is centralized in `build-service/src/config.js`. It reads database, Redis, storage, mail, JWT, TOTP, build quota, worker, Gradle, toolchain, source-isolation, autoscale, Google Play, Sentry, and billing-product settings from environment variables.

Android build configuration reads optional Firebase, OAuth client identifiers, and signing variables. Release keystore and secret values must remain outside source control. This wiki records configuration categories only; it never records real values.

When adding configuration, update an example or documented name only if it is safe, then validate the consuming source and deployment path.
