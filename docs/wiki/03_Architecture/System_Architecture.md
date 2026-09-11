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
  - "README.md"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "build-service/package.json"
  - "build-service/docker-compose.yml"
  - ".github/workflows/production-automation.yml"
---

# System Architecture

AppForge Studio is a multi-part development platform.

## Client layer

The Android application provides the Studio experience, local project interaction, Terminal, Git/SSH tooling, preview/build navigation, and local AI features.

## Build/service layer

The Node.js build service separates API/server responsibilities from worker responsibilities. Dedicated normal/source/Windows worker concepts are represented by Dockerfiles and automation.

## Automation layer

GitHub Actions builds Android artifacts, prepares worker images, performs smoke checks, supports production automation, and manages scaling workflows.

## Design Rule

Do not couple unrelated Studio, Terminal, build, connection, and release responsibilities into one state owner. Prefer explicit module boundaries and contracts.
