---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-11
updated: 2026-09-11
last_verified: 2026-09-11
confidence: high
tags:
  - appforge
  - second-brain
related:
  - "[[Index]]"
source_files:
  - "build-service/package.json"
  - "android-app/build.gradle.kts"
---

# Dependency Map

## Build service

Verified dependency families include:

- Express
- PostgreSQL (`pg`)
- Redis
- AWS S3 SDK
- Sentry
- JWT/auth helpers
- Google APIs
- upload/archive utilities

## Android

Android dependencies are defined by Gradle. Inspect the app-level Gradle file before documenting exact library versions.

## Change Rule

New dependencies require a clear owner, reason, security review where applicable, and removal plan if experimental.
