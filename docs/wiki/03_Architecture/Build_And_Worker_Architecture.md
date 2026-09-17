---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-18
last_verified: 2026-09-18
confidence: high
tags:
  - builds
  - workers
related:
  - "[[Build_Service_Map]]"
  - "[[Deployment_And_CI]]"
source_files:
  - "build-service/src/buildEngine.js"
  - "build-service/src/reactNativeBuildEngine.js"
  - "build-service/tests/react_native_build_error_excerpt.test.js"
  - "build-service/tests/source_worker_toolchain_matrix_contract.test.js"
  - "build-service/src/problemExplainer.js"
  - "build-service/src/buildErrorClassifier.js"
  - "build-service/scripts/source-worker-runtime-smoke.sh"
  - "build-service/Dockerfile.source-worker"
  - "build-service/scripts/source-worker-toolchain-doctor.js"
  - "build-service/source-worker-toolchain.json"
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

## React Native / Expo failure evidence

React Native and Expo source-build command failures are summarized by
`reactNativeBuildEngine.js`. Gradle failures must preserve the meaningful
root-cause block (`FAILURE: Build failed with an exception.`,
`* What went wrong:`, `Execution failed for task`, or `Caused by:`) rather
than exposing only the end of a long `--stacktrace` log.

The excerpt remains bounded and may include a tail sample for surrounding
context. If no recognized root marker exists, the legacy bounded tail
fallback remains valid. This behavior is protected by
`react_native_build_error_excerpt.test.js`.

## Immutable Source Worker toolchain matrix

The dedicated Source Worker keeps the Android SDK immutable at runtime.
Required Android platforms, Build Tools, NDK, CMake, and trusted Gradle
versions are therefore declared in `build-service/source-worker-toolchain.json`
and installed while the image is built.

The image build and the non-root/read-only runtime smoke both verify this
matrix. A missing component must fail image CI before a user project reaches
production. Runtime Gradle builds must not rely on installing SDK components
into `/opt/android-sdk`.

Missing immutable SDK components are classified as Worker toolchain failures,
not user-code or generic Gradle failures.
