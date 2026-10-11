---
type: api
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - backend
  - api
  - cloudflare
  - device-build
related:
  - "[[Build_And_Worker_Architecture]]"
  - "[[Security_And_Entitlements]]"
  - "[[Database_Map]]"
  - "[[Deployment_And_CI]]"
source_files:
  - "cloudflare/control-plane/src/index.mjs"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/src/pro_redemption.mjs"
  - "android-app/app/src/main/java/com/appforge/studio/net/AppForgeAccountClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "quality/tests/control_plane_https_separation_contract.test.js"
---

# Backend API Domains

## Current boundary

Normal AppForge project compilation is device-local.

`BuildApiClient` preserves the Android UI/build contract but normal project
builds execute through `DeviceBuildEngine`. It is not an HTTP client for a
remote project-build queue.

The active repository-managed HTTPS server surface is the Cloudflare control
plane under `cloudflare/control-plane/`.

Its responsibilities include server-verified administrator identity,
administrator-issued Pro lifecycle, installation ownership/challenge flows,
entitlement/status operations, audit evidence and Windows publisher signing
authorization.

The control plane is security infrastructure. It is not a remote build Worker
and must not be treated as a replacement project-build backend.

## Account compatibility surface

`AppForgeAccountClient` remains an active Android client surface for named
account/auth operations and must not be removed merely because normal Studio
usage is accountless.

Its presence does not make the retired Express Build Service a current
project-build architecture.

## Source-of-truth rule

For current server behavior, inspect:

- `cloudflare/control-plane/src/`
- migrations `0001` through `0005`
- Cloudflare control-plane tests
- the Android client using the route
- deployment and physical-acceptance evidence where relevant

Do not infer current routes from the retired Build Service documentation.

Do not infer live deployment state merely from repository source.
