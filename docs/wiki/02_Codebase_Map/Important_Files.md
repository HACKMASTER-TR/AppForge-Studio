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
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "android-app/build.gradle.kts"
  - "build-service/package.json"
  - "build-service/docker-compose.yml"
  - "build-service/Dockerfile.worker"
  - "build-service/Dockerfile.source-worker"
  - ".github/workflows/android-debug.yml"
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/worker-autoscale.yml"
---

# Important Files

| Area | File | Why it matters |
|---|---|---|
| Product overview | `README.md` | High-level feature and runtime intent |
| Android app | `android-app/app/src/main/java/com/appforge/studio/MainActivity.kt` | Major Android surface and navigation/UI responsibilities |
| Terminal | `.../terminal/AnsiTerminalBuffer.kt` | Terminal buffer/scrollback behavior |
| Terminal | `.../terminal/LocalPtyTerminalPanel.kt` | PTY/input/paste behavior |
| Android build | `android-app/build.gradle.kts` | Root Android build configuration |
| Build service | `build-service/package.json` | Service commands and core dependencies |
| Local infra | `build-service/docker-compose.yml` | Multi-service local topology |
| Worker | `build-service/Dockerfile.worker` | Normal worker image |
| Source worker | `build-service/Dockerfile.source-worker` | Source-build worker image |
| CI | `.github/workflows/android-debug.yml` | Android build validation |
| Release | `.github/workflows/android-play-release.yml` | Play release path |
| Scale | `.github/workflows/worker-autoscale.yml` | Worker scaling automation |
