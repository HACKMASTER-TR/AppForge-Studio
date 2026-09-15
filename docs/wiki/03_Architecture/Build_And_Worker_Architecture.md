---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - builds
  - workers
related:
  - "[[Build_Service_Map]]"
  - "[[Deployment_And_CI]]"
source_files:
  - "build-service/src/buildEngine.js"
  - "build-service/src/jobQueue.js"
  - "build-service/src/sourceBuildIsolation.js"
  - "build-service/worker.js"
  - "build-service/source-worker.js"
  - "build-service/windows-worker.js"
  - "build-service/unity-worker.js"
---

# Build and Worker Architecture

`buildEngine.js` resolves and executes build paths. The repository has engines for generated/static web, Android Gradle, Python, Flutter, React Native, C++, .NET Android/MAUI, Unity, Node remote backend, PHP remote backend, and Windows output.

`jobQueue.js` records queue state, worker heartbeats, capability claims, cancellation, retry/requeue behavior, and queue metrics. Source builds have explicit isolation capability checks. Do not broaden worker capabilities or relax isolation based only on a local successful build.

Toolchain, Gradle profile, cache, and artifact handling are separate concerns. Changes spanning engines, queueing, or artifacts normally require targeted contracts plus the broader backend suite.
