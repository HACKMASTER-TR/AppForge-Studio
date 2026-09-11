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
  - "README.md"
---

# Folder Structure

## `android-app/`

Android client application and Gradle configuration.

## `build-service/`

Node.js API/build service, worker roles, Dockerfiles, test suites, and build templates/runtime assets.

## `.github/workflows/`

Android build/release, worker image, autoscaling, production automation, and smoke workflows.

## `docs/`

Repository documentation. `/docs/wiki` is the Second Brain memory vault.

## `examples/`

Example project/material area. Verify exact use before changing.

## Generated / non-source artifacts

Root APKs, logs, backup files, and build outputs should not be treated as architecture documentation.
