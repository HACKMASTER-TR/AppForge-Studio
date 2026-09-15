---
type: integration
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - integrations
related:
  - "[[Security_And_Entitlements]]"
source_files:
  - "build-service/package.json"
  - "build-service/src/storage.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/ExternalConnectionsClient.kt"
---

# Integration Index

Source evidence identifies PostgreSQL, Redis, S3-compatible storage/MinIO, Google APIs and Play services, Firebase Cloud Messaging, Sentry, SMTP/Mailjet/SendGrid, GitHub, Railway, and Android billing/integrity dependencies.

Integration configuration and connection health are intentionally not inferred from dependency declarations. Use source/configuration to understand the contract and authenticated live checks to determine current external status.
