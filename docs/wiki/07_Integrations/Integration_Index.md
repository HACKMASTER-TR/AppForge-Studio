---
type: integration
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-09
last_verified: 2026-10-09
confidence: high
tags:
  - integrations
related:
  - "[[Security_And_Entitlements]]"
source_files:
  - "android-app/app/build.gradle.kts"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/ExternalConnectionsClient.kt"
  - "cloudflare/control-plane/wrangler.example.toml"
---

# Integration Index

## Active integrations supported by repository evidence

- Google identity, Google Play services, Play Integrity and Android billing dependencies are declared in `android-app/app/build.gradle.kts`.
- GitHub OAuth/device authorization and API access are implemented by `ExternalConnectionsClient`.
- The separate HTTPS control plane is a Cloudflare Worker with a D1 binding; normal project builds remain device-local.

## Historical / retired Build Service infrastructure

PostgreSQL, Redis and S3-compatible storage/MinIO belonged to the retired remote Build Service. That history does not establish them as active AppForge platform infrastructure. SMTP/Mailjet/SendGrid, Sentry and Firebase Cloud Messaging are not asserted here as current platform integrations without current consuming source/configuration evidence.

## Connection health

Repository source and dependency declarations establish integration contracts, not live connection health. Authenticated checks are required to determine current external or production status.
