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
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ProPurchasesActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
  - "build-service/tests/device_build_toolchain_scope_contract.test.js"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
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

## 2026-09-18 — Source Worker Reliability v1.1

### Context

A production Expo/React Native build reached the Source Worker cgroup memory
limit and stopped producing CMake/Ninja output while the Node Worker heartbeat
continued. The next compatible build remained queued because production had
one Source Worker replica. Existing autoscaling intentionally excluded
`source-isolation-dedicated`, and React Native/Expo child processes had only a
long total timeout that killed the direct launcher instead of the full process
tree.

### Decision

Treat build progress, Worker presence, and Worker capacity as separate health
signals. Supervise long-running source subprocesses as process groups, recover
silent or memory-critical process trees with the existing bounded retry path,
and stop claiming new work above the cgroup memory high-water mark. Scale the
dedicated Source Worker as its own capability-isolated Railway pool and
dispatch that autoscaler immediately when source work queues. When compatible
capacity is being recovered, report an explicit recovery state instead of a
historical queue ETA.

### Consequences

A single stuck source build no longer has to monopolize the only compatible
slot until a 20-minute hard timeout. Recovery remains bounded by
`maxJobAttempts`; genuine user-code failures are still non-retryable. Source
autoscaling does not weaken isolation or allow Source Workers to claim normal
Android jobs. The runtime needs finite cgroup memory limits for the memory
guard; when they are unavailable, progress/timeout supervision still applies.

### Evidence

Production Source Worker metrics on 2026-09-18 reached approximately 8/8 GiB
with CPU near idle while CMake/Ninja output stopped. Repository evidence showed
Source Workers excluded from the existing autoscaler, Worker heartbeat
independent of process progress, direct-child `SIGKILL` in multiple source
engines, and a React Native/Expo 20-minute hard timeout without a stall
watchdog.

- 2026-09-19: AppForge normal build execution moved from remote Worker/queue infrastructure to an on-device PRoot/Linux build engine. Remote build upload/polling is no longer part of the normal Android build path. Shipping acceptance requires real-device APK/AAB validation.

## 2026-09-19 — Device Build Toolchain Scope v1

### Context

Real-device FIKSTUR TAKIP acceptance reached the device-local Linux build engine but failed before project Gradle execution. Sanitized logs showed Ubuntu dependency resolution failing while installing npm. The device installer provisioned Node.js/npm and Python for every source type even when an Android Gradle or static WebView build did not require them.

### Decision

Device build preparation uses the verified Ubuntu base environment rather than the complete Terminal development profile. The local toolchain installer receives the selected source-build engine and provisions optional language packages only when required: Node.js/npm for `node-web`, Python tooling for `python-android`, while Android/JDK tooling remains the common device-build base.

### Alternatives considered

- Keep one universal toolchain for every build: rejected because an unrelated Node/npm package failure can block Android projects before Gradle starts.
- Remove Node and Python support from device builds: rejected because those source engines remain valid local build targets.
- Fall back to a remote Worker when local package installation fails: rejected because normal user builds are intentionally device-only.

### Consequences

Android Gradle and static WebView builds no longer depend on npm package availability. Node and Python projects still receive their required toolchains. Failures are isolated closer to the source engine that actually requires the package. Real-device APK/AAB acceptance remains mandatory.

### Evidence

`device_build_toolchain_scope_contract.test.js` verifies that `DeviceBuildEngine` does not require the full Terminal development profile, passes the source engine to the installer, and prevents unconditional npm installation. Existing device-only contracts also pass.

### 2026-09-19 — Device builds use a pinned JDK archive

Device-local Android builds must not depend on Ubuntu OpenJDK package post-install/configuration behavior. AppForge provisions a checksum-pinned Temurin JDK 17 archive and passes its JAVA_HOME explicitly to Gradle. Ubuntu APT remains responsible for ordinary base utilities, while Java is treated as part of AppForge's deterministic device-build toolchain.

### 2026-09-19 — Persistent device rootfs cleanup is engine-aware

Device-local builds may reuse a persistent Ubuntu rootfs, but package repair must remain scoped to the selected source engine. Non-Node builds may remove only incomplete or broken legacy Node/npm package states; healthy Node installations are preserved, while `node-web` builds retain their required Node toolchain.

## 2026-09-19 — Clean Device Build Runtime V3

### Context

Repeated real-device failures were caused by package state inherited from a
persistent Linux environment: first unconditional Node/npm provisioning, then
OpenJDK dpkg configuration, then stale Node package state. Repairing packages
one failure at a time allowed unrelated historical package state to influence a
new Android project build.

### Decision

Project builds use a dedicated, versioned and disposable build rootfs which is
physically separate from the AppForge Terminal rootfs. A runtime revision
mismatch rebuilds only this build runtime. Terminal files and package state are
outside the cleanup boundary.

A central capability matrix records engine and output readiness. APK, AAB and
Windows Portable EXE are first-class artifact kinds, but only platform-tested
outputs may be marked READY.

### Consequences

Old Terminal package state cannot block new project builds. Toolchain upgrades
can intentionally invalidate the build runtime without destroying developer
workspaces. Future Flutter, React Native, NDK, .NET and Windows engines can use
separate capability/toolchain layers without turning every build into a
universal environment.

Unity remains explicitly external-tool-required until a supported device build
host exists.

Remote Worker fallback remains forbidden for normal user project builds.

## 2026-09-19 — AppForge UI V2 Design System

### Context

Studio screens had accumulated separate dark palettes and normal build
failures exposed implementation-oriented runtime, Worker and toolchain
wording.

### Decision

Use `AppForgeTheme` as the shared Material 3 color/shape authority. The
product language is deep navy with cyan primary actions, violet accents and
rounded elevated surfaces. Terminal and Excel Tools keep specialized layouts
but share the same palette. Standalone update and Pro purchase activities
also use the shared theme.

Normal build UI presents status, progress, actionable error summaries and
artifacts first. Sanitized local logs remain available behind an explicit
technical-details control instead of dominating the default failure screen.

### Consequences

Visual changes can be coordinated centrally without altering build routing,
owner authorization, Terminal behavior or entitlement logic. The retired
five-build stress UI remains absent.

### Evidence

`studio_home_modern_ui_contract.test.js`,
`device_only_build_ui_contract.test.js`, Android Debug CI and real-device UI
acceptance.

## 2026-09-19 — Accountless, single lifetime Pro product

### Decision

AppForge's new paid offering consists of one Google Play non-consumable
one-time Pro purchase, proposed product ID `appforge_pro_lifetime`. No normal
AppForge login, recurring subscription or quota add-on sales. Verify a
purchase server-side against Google Play and reconcile voids/refunds before
granting rights; restore via Google Play Billing. Admin uses independent
verified Google identity. Free build execution stays device-local.

### Consequences

The earlier monthly/add-on and Stage 2 email/password model is superseded,
not silently deleted from historical project records. Android's legacy UI,
Google Play Console products and purchase-validation implementation require
separate verification before production cutover.

## 2026-09-19 — One accountless lifetime purchase

### Context

The Android app previously exposed email/password accounts, monthly SUBS and
10/25/50 add-on offers even though the new Cloudflare Worker has no account
system and no live Play verification.

### Decision

Normal users need no AppForge account. Use one proposed non-consumable Google
Play item (`appforge_pro_lifetime`) with server-verified receipts and restore.
Retire monthly and add-on purchase UI; do not treat local Billing callbacks,
headers, device IDs or a cached account as proof of payment/admin identity.

### Consequences and evidence

The UI remains fail-closed while the real verification API is unavailable.
Play product/credentials, acknowledgement/refunds, verified Google admin OIDC,
whole-repo CI and physical-device testing are independent release blockers.
`cloudflare/control-plane/tests/worker.test.mjs` protects server staging;
Android single-product source contracts accompany this change.
