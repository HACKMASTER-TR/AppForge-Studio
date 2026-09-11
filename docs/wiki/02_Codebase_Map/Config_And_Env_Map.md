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
  - "build-service/.env.example"
  - "build-service/docker-compose.yml"
  - "android-app/gradle.properties"
  - "android-app/settings.gradle.kts"
---

# Config and Environment Map

## Build service

`build-service/.env.example` is the safe starting point for environment-variable names. Never copy real values into the wiki.

`build-service/docker-compose.yml` defines local multi-service wiring.

## Android

`android-app/gradle.properties`, `settings.gradle.kts`, and Gradle build files define the Android toolchain and project setup.

## Rule

Environment names may be documented; real credentials, signing material, tokens, passwords, API keys, or production secrets must never be written to `/docs/wiki`.
