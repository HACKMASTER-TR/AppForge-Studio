---
type: architecture
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
  - ".github/workflows/android-debug.yml"
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/production-automation.yml"
  - ".github/workflows/worker-image.yml"
  - ".github/workflows/source-worker-image.yml"
  - ".github/workflows/windows-worker-image.yml"
---

# Deployment Notes

GitHub Actions is a major automation surface.

Verified workflow categories include:

- Android debug validation
- Android Play release
- normal worker image
- source worker image
- Windows worker image
- production automation
- worker autoscaling
- conversion smoke checks

Before changing deployment behavior, inspect the exact workflow and the matching Dockerfile/service code. Avoid assuming all workflows share the same environment or credentials.
