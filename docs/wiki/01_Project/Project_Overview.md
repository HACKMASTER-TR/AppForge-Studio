---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - project
  - overview
related:
  - "[[Index]]"
  - "[[System_Architecture]]"
source_files:
  - "README.md"
  - "android-app/settings.gradle.kts"
  - "build-service/package.json"
---

# Project Overview

AppForge Studio is an Android application studio backed by a Node.js build service. The Android client lets users create, import, inspect, test, and publish application projects. The build service persists projects, queues builds, produces artifacts, and exposes account, team, billing, worker, and administrative APIs.

The repository contains a Kotlin/Jetpack Compose Android app, a JavaScript/Express backend, PostgreSQL migrations, Redis- and storage-aware build infrastructure, Docker development topology, GitHub Actions, and specialized workers. Supported build paths include generated web wrappers and several imported-source engines; each engine has distinct toolchain and isolation requirements.

This is a long-lived, multi-domain repository. The wiki therefore uses Full mode, but only pages justified by current evidence are created.

## Boundaries

- Live GitHub, Railway, Play Console, and production health are unknown until authenticated checks are run.
- This wiki does not replace the Android product’s local AI or Unified Agent features.
