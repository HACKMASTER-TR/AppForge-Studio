---
type: decision
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-18
last_verified: 2026-09-18
confidence: high
tags:
  - decisions
related:
  - "[[System_Architecture]]"
source_files:
  - "android-app/app/build.gradle.kts"
  - "build-service/package.json"
  - "build-service/source-worker-toolchain.json"
  - "build-service/src/sourceToolchainRegistry.js"
  - "build-service/src/projectToolchainInspector.js"
  - "build-service/src/sourceBuildIsolation.js"
  - "build-service/src/jobQueue.js"
  - "build-service/worker.js"
  - "build-service/tests/source_toolchain_router.test.js"
---

# Decision Index

No historical ADR was retained as authoritative evidence during the old documentation cleanup. Do not reconstruct historical motivations from code alone.

Create ADRs here only for future decisions with a verified context, decision, alternatives, consequences, and source/test evidence. Existing architecture is mapped in [[System_Architecture]] rather than presented as inferred decision history.

## 2026-09-18 — Universal Toolchain Router v1

### Context

The dedicated Source Worker has an immutable/read-only Android SDK. A project
can explicitly require a platform, Build Tools, NDK, CMake, Gradle, JDK, or AGP
combination that is not present in the production image. Allowing Gradle to
find this only after a job is claimed produces a late generic build failure and
cannot repair the image at runtime.

### Decision

Use one versioned `source-worker-toolchain.json` registry as the compatibility
contract for Source Worker image installation, startup attestation, project
preflight, and queue capability generation. Inspect untrusted ZIP metadata
without executing project code. For Android Gradle / React Native / Expo v1,
reject explicitly unsupported combinations before enqueue with
`SOURCE_TOOLCHAIN_UNSUPPORTED`; otherwise emit versioned capabilities and keep
`source-isolation-dedicated` mandatory. Worker startup advertises only
registry entries that are actually installed.

### Alternatives considered

- Install missing SDK/NDK components during each build: rejected because the
  production SDK is intentionally read-only and Source Worker runs non-root.
- Put every historical toolchain into one ever-growing image: rejected because
  it increases image size and weakens capability-aware routing.
- Route only by a generic `gradle` capability: rejected because incompatible
  projects can reach the wrong Worker and fail after queue claim.

### Consequences

The registry becomes an explicit deployment contract and must be updated with
image contents. Queue SQL remains unchanged (`required_capabilities <@
worker.capabilities`), so future specialized Source Worker images can coexist.
Projects with missing optional metadata preserve existing defaults; explicit
unknown versions fail closed before Gradle.

### Evidence

`source_toolchain_router.test.js` covers NexBrain-like Expo routing, unknown
NDK/API/Build Tools/CMake, incompatible Gradle/JDK/AGP, missing metadata, and
legacy-engine pass-through. Source Worker matrix/runtime contracts verify
registry installation and automatic capability registration.
