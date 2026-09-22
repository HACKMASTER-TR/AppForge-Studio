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
  - "android-app/app/src/main/java/com/appforge/studio/security/GoogleAdminIdentityClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/src/index.mjs"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/tests/google_oidc_admin.test.mjs"
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

## 2026-09-20 — Admin privileges require verified Google identity

### Context

Accountless Android retirement left `OwnerAccessPolicy` depending on the old local session, hiding Terminal and Admin despite a successful Phase 5 Pro/device build. OAuth clients alone confer no administrator rights.

### Decision

Use Google RS256 OIDC signature/issuer/audience/expiry verification in Cloudflare and a server-provisioned active D1 allow-list keyed by SHA-256 of Google `sub`. No email, local cached owner flag, obsolete bearer, device ID or Play purchase grants Admin. Stage the server verifier first; keep Admin operations and Android Terminal access closed until Credential Manager and server-backed owner policy pass device acceptance.

### Alternatives and consequences

Unconditionally granting local owner or matching email was rejected as a privilege escalation. Missing JWKS/config/D1 fails closed. A control-plane outage does not stop normal device builds. Production Admin/Terminal acceptance and provisioning remain pending.

## 2026-09-19 — Android admin isolation from normal user accounts (staging)

**Context:** Normal user email/password login was retired before the Terminal
owner guard was changed. The home still showed `GİRİŞ YAP` and even the former
owner could no longer reach Terminal or Second Brain.

**Decision:** Keep normal AppForge use accountless. Require a user-initiated
Google Credential Manager sign-in for admin, server-verified signed ID token
with nonce, and an explicitly provisioned D1 hash of Google `sub`. Only a
short-lived in-process token can satisfy `OwnerAccessPolicy`; neither an
AppForge email, old session, Play entitlement nor device identifier grants
owner privileges. Keep the owner vault filesystem location unchanged. Do not
restore obsolete account-management features.

**Consequences:** Existing encrypted data is not cleared or migrated; previous
Terminal workspace selection must be tested on-device. Staging deployment and
real D1 admin provisioning are required before access is operational. Production
Cloudflare/Play changes remain separate, explicitly gated steps.

**Evidence:** Android owner policy, GoogleAdminIdentityClient, AdminOpsScreen,
StudioHomeV2, Cloudflare google_oidc and Worker tests.


## 2026-09-22 — Modular One-Click Offline Build Pack

### Context

Embedding every Android, Node, Python and future Windows build tool
directly into the main AppForge APK would make ordinary application
updates excessively large.

### Decision

Keep the main APK comparatively small and install a versioned,
AppForge-managed offline build pack from inside the application.
The user sees one installation action while the implementation keeps
Android, Node, Python and future Windows EXE capabilities modular.

The pack lives only inside the dedicated Device Build Runtime
boundary. It does not reuse or mutate the persistent Terminal Linux
workspace.

Portable EXE remains a declared target but cannot become READY until
the Android-hosted packager and real Windows execution acceptance
both pass.

### Consequences

Normal AppForge APK updates do not require re-downloading the whole
offline toolchain. Pack revisions can update only affected modules.
Physical-device offline APK/AAB acceptance and future Windows EXE
acceptance remain mandatory release gates.


## 2026-09-22 — Android SDK License Requires Explicit Consent

The Offline Build Pack must never manufacture or silently mark an
Android SDK license as accepted.

The application presents a dedicated consent step before the first
Android SDK runtime installation. Only after explicit acceptance may
the device runtime register the required Android SDK package/license.
The acceptance state is versioned in AppForge preferences and the
runtime keeps its own verified license marker.

License refusal or missing license metadata is fail-closed and cannot
produce an Android offline readiness marker.


## 2026-09-22 — Device Python Runtime Aligned to 3.12

The Android device-build Python engine uses Python 3.12 end-to-end.
The Ubuntu 24.04 runtime's Python interpreter and Chaquopy application
Python version must have identical major/minor versions.

The device template, generated device project and toolchain readiness
checks therefore pin `/usr/bin/python3.12` and Chaquopy Python 3.12.
A mismatch is a fail-closed toolchain condition.


## 2026-09-22 — Windows Portable EXE Uses a Generic Verified Host

### Context

Running Electron Builder, Wine and the complete Windows packaging toolchain
for every project on an Android phone would make offline EXE generation large,
slow and fragile.

### Decision

Build a generic x64 AppForge Portable Host on a Windows CI runner. The host
contains the Windows Electron runtime but no user project.

After that base host has been verified and SHA-256 pinned, Android may create
a project-specific EXE by copying the host and appending the existing AppForge
reversible manifest/project payload.

Normal project source remains on-device. GitHub CI creates only the generic
host and never receives the user's project as part of the normal packaging
flow.

### Acceptance

The generic host must prove on Windows that the outer portable executable
remains runnable after an AppForge payload is appended and that the host can
read and load that payload.

Windows remains PLANNED until Android offline packaging and a real Windows
machine acceptance test both pass.


## 2026-09-22 — Windows Host Pack Uses Pinned Resumable Distribution

### Decision

The generic Windows Portable Host is installed as a separate AppForge-managed
offline-pack module rather than embedding the 375 MB host in every Android APK.

Android pins the exact prerelease asset URL, byte length and SHA-256. Download
state uses a resumable `.part` file. The final host is exposed to later
packaging only after cryptographic verification.

The host module lives under `noBackupFilesDir` and remains physically separate
from both the persistent Terminal Linux workspace and the disposable Device
Build Runtime rootfs.

Installing the host does not mark Windows EXE output READY. Device-local
project packaging and actual Windows execution remain independent acceptance
gates.


## 2026-09-22 — Android Packages Windows EXE by Payload Append

### Decision

Android does not run Wine or electron-builder for each Windows build.

After the generic Windows host is cryptographically verified, the device
copies that immutable host and appends the AppForge reversible manifest and
project ZIP payload directly.

The project therefore remains on the user's device and the packaging step can
run without an internet connection.

### Acceptance

A deterministic device-generated smoke EXE is provided as the first physical
acceptance artifact. Windows output remains gated until that Android-generated
EXE opens successfully on an actual Windows machine and displays the expected
AppForge smoke content.


## 2026-09-22 — Normal Web Engines Own Device-Local Windows EXE Output

### Decision

The accepted Windows generic host is not exposed through a second remote build
route. `webview-static` and `node-web` produce `WINDOWS_EXE` as another
device-local artifact in the existing Studio build job.

For npm web projects, AppForge runs the web build once and reuses the resulting
static site for Android and Windows packaging.

`BuildApiClient` exposes the EXE like the existing local APK/AAB artifacts,
while Studio continues to save large EXE files through Android Storage Access
Framework.

Native Android and Python engines do not advertise Windows output. The complete
offline-pack READY flag remains gated until a real normal-project EXE passes
Android-to-Windows acceptance.


## 2026-09-22 — Windows EXE Accepted and Public Artifact Copies Are Canonical

### Decision

Windows Portable EXE is accepted for the device-local `webview-static` and
`node-web` engines after generic-host CI, Android offline packaging, physical
Windows 11 smoke, normal Studio project execution and JavaScript runtime
acceptance passed.

The public `Downloads/AppForgeStudio` copy is canonical for APK, AAB and EXE.
An active owner/admin session may retain an additional private copy under
`AppForge Dosyaları/APK`, but private storage must not replace public output.
The reserved standalone `windows-web` engine remains PLANNED.
