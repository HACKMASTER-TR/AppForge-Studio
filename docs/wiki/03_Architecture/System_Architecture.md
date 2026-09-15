---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - architecture
  - system
related:
  - "[[Project_Overview]]"
  - "[[Build_And_Worker_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "build-service/server.js"
  - "build-service/src/workerRuntime.js"
  - "build-service/docker-compose.yml"
---

# System Architecture

The Android client calls the Build Service for account, project, build, artifact, entitlement, and administrative operations. The Build Service stores durable state in PostgreSQL, uses Redis for coordination/caching, and uses local or S3-compatible object storage for input/output artifacts.

Build submission is preflighted, persisted, queued, and claimed by capability-aware workers. Worker variants handle standard, source, Windows, and Unity paths. Artifact delivery is mediated by tickets and storage keys rather than exposing raw internal paths.

The local Android app also owns device-local project files, keystore data, terminal workspaces, and local AI state. Those local concerns must not be mistaken for server-authoritative state.
