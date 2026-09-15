---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - deployment
  - ci
related:
  - "[[Test_And_CI_Map]]"
source_files:
  - "build-service/Dockerfile"
  - "build-service/docker-compose.yml"
  - ".github/workflows/appforge-stability-gate.yml"
  - ".github/workflows/production-automation.yml"
  - ".github/workflows/worker-autoscale.yml"
---

# Deployment and CI

Docker Compose defines development services for PostgreSQL, Redis, MinIO, Mailpit, the API, workers, and a video-downloader. The default Dockerfile packages the API; specialized worker images are built by separate workflow paths.

GitHub Actions contains Android debug/release, stability, conversion, worker-image, source-worker-image, Windows-worker-image, production automation, cleanup, and autoscaling workflows. Railway-related URLs and production names in workflow files are configuration evidence, not live-health evidence.

The old repository brain was removed from stability policy. The remaining stability gate checks diff integrity, forbidden staged outputs, and branch policy. Wiki health remains advisory.
