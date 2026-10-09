---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-09
last_verified: 2026-10-09
confidence: high
tags:
  - config
  - environment
related:
  - "[[Deployment_And_CI]]"
source_files:
  - "android-app/app/build.gradle.kts"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "cloudflare/control-plane/wrangler.example.toml"
  - ".github/workflows/pro-cloudflare-staging-deploy.yml"
---

# Config and Environment Map

Configuration is distributed by active subsystem:

- Android Gradle/product configuration lives in `android-app/app/build.gradle.kts`, including Debug/Release HTTPS control-plane routing and signing configuration.
- Device-local build/runtime/toolchain configuration belongs to `DeviceBuildEngine`, `DeviceBuildRuntimeV3` and their toolchain modules. `DEFAULT_BUILD_SERVICE_URL` is `device://local`, distinct from HTTPS routing.
- Cloudflare control-plane Wrangler bindings, vars and secret configuration are documented by `cloudflare/control-plane/wrangler.example.toml`; the Worker uses D1 where applicable.
- Deployment workflow configuration lives in `.github/workflows`, with each workflow governing its own deployment path.

The former remote Build Service is retired. Its `build-service/src/config.js` is historical, not a current centralized backend configuration authority.

Release keystores and secret values must remain outside source control. This map records configuration categories only, never real values. Validate configuration against its consuming source and deployment path; repository configuration does not establish live production health.
