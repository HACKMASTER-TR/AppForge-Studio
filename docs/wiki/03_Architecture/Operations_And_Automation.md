---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - operations
  - automation
  - ci
related:
  - "[[Deployment_And_CI]]"
  - "[[Test_And_CI_Map]]"
  - "[[Current_Status]]"
source_files:
  - "build-service/docker-compose.yml"
  - ".github/workflows/appforge-stability-gate.yml"
  - ".github/workflows/android-debug.yml"
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/production-automation.yml"
  - ".github/workflows/source-worker-image.yml"
  - ".github/workflows/windows-worker-image.yml"
  - "scripts/appforge"
  - "scripts/appforge-stability-gate"
---

# Operations and Automation

The local Compose definition names PostgreSQL, Redis, MinIO, Mailpit, API, worker, and video-downloader services. It is local configuration evidence, not evidence that an environment is deployed or healthy.

The workflow directory contains Android debug and Play release, stability gate, conversion smoke, production automation, cleanup, autoscaling, and worker-image workflows. Shell scripts provide project automation and stability behavior. Wiki health scripts remain advisory and are not installed as a Git hook or required workflow gate.

Read workflow triggers, environment requirements, and the called script before changing delivery behavior. Do not infer remote deployment success, secret availability, or production state from names in configuration.
