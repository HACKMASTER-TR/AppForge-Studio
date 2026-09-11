---
type: status
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
  - "build-service/package.json"
  - "android-app/build.gradle.kts"
---

# Project Overview

AppForge Studio is an AI-assisted application studio and build platform. The repository combines an Android Studio-like product experience with project scaffolding, build/release tooling, an embedded project-aware terminal, Git/SSH capabilities, and local AI assistance.

The Android application is a primary client surface. The build service provides remote build/API/worker responsibilities and supports multiple worker roles. GitHub Actions cover Android debug/release, worker images, autoscaling, conversion smoke checks, and production automation.

## Users

Primary users are developers or builders who want to create, inspect, build, test, and publish applications from AppForge without assembling the entire toolchain manually.

## Long-Term Direction

Keep the product broad, but make each major capability modular, testable, observable, and independently maintainable.
