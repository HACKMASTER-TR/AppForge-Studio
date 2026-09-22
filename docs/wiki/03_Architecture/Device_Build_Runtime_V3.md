---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-19
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - device-build
  - runtime-v3
  - multi-platform
related:
  - "[[System_Architecture]]"
  - "[[Build_And_Worker_Architecture]]"
source_files:
  - "build-service/tests/windows_portable_host_v1_contract.test.js"
  - ".github/workflows/windows-portable-host.yml"
  - "windows-host/payload.cjs"
  - "windows-host/main.cjs"
  - "windows-host/package.json"
  - "build-service/tests/offline_build_pack_v1_contract.test.js"
  - "android-app/app/src/main/assets/device-build/prepare-offline-pack.sh"
  - "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "build-service/tests/device_build_runtime_v3_contract.test.js"
  - "build-service/tests/device_build_offline_gradle_contract.test.js"
  - "build-service/tests/device_build_gradle_helper_runtime.test.js"
  - "build-service/tests/device_build_capability_matrix_contract.test.js"
  - "build-service/tests/device_build_aapt2_arm64_contract.test.js"
---

# Device Build Runtime V3

## Isolation boundary

Project builds use a dedicated, versioned and disposable Ubuntu rootfs under
the device-build namespace.

The AppForge Terminal Linux environment is a separate persistent developer
workspace and is never reused as a project-build rootfs.

A Device Build Runtime revision mismatch may delete and reinstall only the
dedicated build runtime. It must never purge or repair Terminal files.

## Current proven device engines

- Static HTML/CSS/JavaScript WebView
- npm-built static Web projects such as React, Vue, Svelte and Vite
- Native Android Gradle Kotlin/Java
- Python Android through Chaquopy

These remain APK/AAB device-local targets.

## Output architecture

The common artifact model covers:

- Android APK
- Android AAB
- Windows Portable EXE

A project may eventually request APK, AAB, EXE, APK+AAB or all compatible
outputs. An output must not be exposed as ready until its device-local packager
has passed CI and physical-device/platform acceptance.

## Windows and conversion target

AppForge retains the existing AppForge-managed APK -> Windows EXE and
EXE -> Android APK conversion contracts.

The long-term requirement is a local Windows Portable EXE packager. Normal user
source must not be uploaded to a Railway, Render or remote AppForge Worker as a
fallback.

Where technically valid, one shared source/project model should produce both
Android and Windows artifacts. Native platform-only APIs must be reported by
preflight instead of being silently removed.

## Engine roadmap

The V3 capability registry reserves explicit engine families for:

- Android NDK / C / C++
- React Native
- Expo
- Flutter / Dart
- .NET Android
- .NET MAUI
- Windows Web portable EXE
- Unity

Unvalidated engines remain EXPERIMENTAL, PLANNED or
EXTERNAL_TOOL_REQUIRED. They must never be presented as working merely because
their detector exists.

Unity specifically requires an officially supported build host/editor path;
the current Android-hosted Linux runtime must not pretend to provide one.

## UI requirement

The previously over-minimal StudioHomeV2 layout was rejected. Future V3 build
UI should remain clear but restore useful project/build status, output targets,
capability information and richer dashboard affordances without returning to an
unmanageable single screen.

## Offline Gradle shell regression

The installer defines one ensure-gradle helper before the
toolchain readiness shortcut. A separate runtime regression
test executes its extracted shell body in disposable test
directories with curl blocked, covering installed Gradle,
verified cache, missing cache and invalid cache checksum.

These checks do not constitute physical Android APK/AAB
acceptance or prove that project dependencies are cached.

## Offline Gradle preflight

The device build engine passes offline state to the
version-specific Gradle installer.

A verified cached distribution can be reused without
network access. An unavailable distribution fails with
APPFORGE_OFFLINE_GRADLE_MISSING instead of downloading.

The ensure-gradle helper is refreshed before the existing
toolchain readiness shortcut, preserving already installed
V3 toolchains and their readiness marker.

This is a source-level implementation. Real Android offline
APK/AAB acceptance remains pending.

## Real-device API 37.0 platform layout correction

Physical-device acceptance exposed an Android SDK platform
layout mismatch. AGP 9.1.1 requested `platforms;android-37.0`,
while Device Build Runtime V4 had materialized the pinned
platform under `platforms/android-37`.

AGP therefore treated API 37.0 as missing and attempted an
SDK package installation, which reached the Android license
gate instead of using the already packaged platform.

The device installer now materializes the pinned platform at
`platforms/android-37.0`. The readiness marker advances from
V4 to V5 so an existing device runtime is re-provisioned on
the next build.

Physical-device APK/AAB acceptance remains required.

## One-click Offline Build Pack

AppForge uses a small application APK plus an optional one-click
offline build pack instead of embedding multi-gigabyte toolchains
inside every APK update.

The pack reuses the dedicated Device Build Runtime V3 boundary and
must never modify the persistent AppForge Terminal Linux workspace.

The first implementation prepares the currently enabled Android
device engines:

- Android SDK / Build Tools / JDK / Gradle and AGP cache
- Node.js and npm toolchain
- Python and Chaquopy Android cache

Node project dependencies remain version-specific and must be proven
present before an arbitrary imported npm project can be claimed as
offline-ready.

Windows Portable EXE is part of the target pack architecture but
remains PLANNED until Android-hosted packaging and execution on an
actual Windows machine pass acceptance. The UI must not report the
complete pack as READY before that gate passes.

The target user experience is one action in Settings. Internally the
pack remains modular so later runtime updates can replace only changed
components instead of redownloading the complete payload.


### Offline pack failure diagnostics

Offline pack prewarm Gradle commands intentionally use concise
`--console=plain` output. On failure the runtime emits the Gradle
`FAILURE:` section rather than flooding the user-visible error with
internal Gradle stack frames. The pack remains fail-closed and no
component readiness marker is written after a failed prewarm.


### Android SDK license gate

The one-click Offline Build Pack must not silently accept the Google
Android SDK license.

Before the first Android toolchain preparation, the AppForge UI shows
an explicit Android SDK license confirmation flow and lets the user
open the current terms. Only an explicit confirmation permits the
runtime to invoke the pinned Android command-line tools for API 37.0
license/package registration.

The command-line tools artifact is SHA-256 pinned. The existing pinned
Android 37.0 r02 platform payload remains the deterministic source for
the platform files. A failed or unaccepted license must remain
fail-closed and no Android offline-pack readiness marker may be
written.


### Python / Chaquopy device runtime

The Device Build Runtime V3 Python Android engine uses Python 3.12.

Chaquopy 17 requires the `buildPython` interpreter major/minor version
to match the Python version selected for the Android application.
The pinned Ubuntu 24.04 device runtime provides Python 3.12, therefore
both the device template and generated Python Gradle project use:

- Chaquopy Python version `3.12`
- build interpreter `/usr/bin/python3.12`

The toolchain readiness check fails closed if that interpreter is
missing or reports a different major/minor version.

This change applies to the device-local Python engine. It does not
silently rewrite unrelated legacy/cloud Python templates.


## Windows Portable Host V1

The first device-local Windows EXE architecture does not run Wine or
electron-builder for every project on Android.

A generic x64 Windows Portable Host is built separately on a Windows CI host.
The generic host contains Electron and the AppForge Windows runtime but no
user project.

At project-package time, Android will copy the verified generic host and append
the existing AppForge reversible payload format:

- `AFEXEP01`
- manifest length
- AppForge manifest
- local site/project ZIP when required
- payload length
- `APPFORGE-EXE-V1!`

Electron Builder's portable runtime exposes the original outer portable
executable path. The host reads the appended payload from that outer file,
validates bounds and paths, extracts local content into a temporary isolated
directory and starts the project without a remote AppForge build Worker.

The Windows CI gate must prove that an actual generated portable EXE still
starts after an AppForge payload is appended and that the runtime can read
that payload through the portable-executable path.

The base host is not yet an Offline Pack component and Windows remains
`PLANNED` until:

1. the Windows host CI passes,
2. the artifact SHA-256 is pinned in Android,
3. Android can package a real project into a `.exe` without network access,
4. that generated EXE passes acceptance on an actual Windows machine.

## Acceptance

Shipping requires:

1. Node/contracts fail 0.
2. Android Debug CI success.
3. Clean first-install runtime test.
4. Runtime-revision replacement test.
5. Real APK and AAB build on Android.
6. Per-engine device acceptance.
7. Windows EXE acceptance on an actual Windows machine before EXE becomes READY.

## ARM64 AAPT2 compatibility correction

The first real V3 device build reached the SDK toolchain smoke test but failed
before project Gradle execution because AAPT2 could not resolve libdl.so.

The correction replaces the ARM64 native Android SDK tools with checksum-pinned
Linux-glibc ARM64 executables from the fixed Build-Tools 36.0.0 release.
The official x86_64 tools remain selected for x86_64 hosts.

AAPT2 version execution and Android SDK 37 platform resource parsing are
required before the new toolchain readiness marker is written.

This source correction requires Android CI and physical-device APK/AAB
acceptance. Static tests alone do not prove native execution.
