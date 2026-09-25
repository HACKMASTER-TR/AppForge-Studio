---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-19
updated: 2026-09-23
last_verified: 2026-09-23
confidence: high
tags:
  - device-build
  - runtime-v3
  - multi-platform
related:
  - "[[System_Architecture]]"
  - "[[Build_And_Worker_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableHostStore.kt"
  - ".github/workflows/windows-portable-host.yml"
  - "windows-host/payload.cjs"
  - "windows-host/main.cjs"
  - "windows-host/package.json"
  - "quality/tests/offline_build_pack_v1_contract.test.js"
  - "android-app/app/src/main/assets/device-build/prepare-offline-pack.sh"
  - "android-app/app/src/main/assets/device-build/build-node.sh"
  - "quality/tests/node_web_npm_cache_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildRuntimeV3.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildCapabilities.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/assets/device-build/FastActivity.java"
  - "android-app/app/src/main/assets/device-build/AppForgeLocalAssets.java"
  - "quality/tests/node_web_https_assets_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/io/AppIconProcessor.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceProjectIcon.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPeIconPatcher.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/OwnerArtifactReferenceStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/OwnerFilesPanel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/WorkspaceFileService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/WorkspaceFilesPanel.kt"
  - "quality/tests/owner_exe_single_copy_contract.test.js"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "quality/tests/device_build_runtime_v3_contract.test.js"
  - "quality/tests/device_web_manifest_xml_declaration_contract.test.js"
  - "quality/tests/device_build_offline_gradle_contract.test.js"
  - "quality/tests/device_build_gradle_helper_runtime.test.js"
  - "quality/tests/device_build_capability_matrix_contract.test.js"
  - "quality/tests/device_build_aapt2_arm64_contract.test.js"
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

## Historical artifact re-save after process restart

`DeviceBuildEngine.jobs` is process memory and is empty after APK update or
process restart. `BuildApiClient.createDownloadTicket` first retains the live
artifact path, then resolves an exact successful `ProjectLibrary` history ID
under `files/device-build/artifacts/local-*/` for APK, AAB and Windows EXE.
The history must advertise that output; the resolved canonical build directory
must match the requested ID, contain exactly one nonempty file of the correct
extension, and match the persisted build number when available. No project-name
search, owner-vault duplicate, public Downloads copy, network fallback or
cross-account lookup is permitted. A missing or ambiguous artifact fails closed.
CI compilation and physical save-after-restart acceptance are required.

## Owner EXE single-copy projection

Device-local Windows EXE artifacts remain canonical under
`files/device-build/artifacts/local-*/`. Owner visibility does not require a
second private byte copy. `OwnerArtifactReferenceStore` keeps owner-gated
metadata under `no_backup` and `OwnerFilesPanel` projects valid references into
`AppForge Dosyaları/APK`. A reference resolves only to a direct local build EXE;
symlink/hardlink assumptions are not used. Existing owner-vault EXE copies are
adopted only after size and SHA-256 equality, an atomic reference write and a
successful reference re-read. Removing the projected entry removes only its
reference; the canonical build artifact is preserved. Public
`Downloads/AppForgeStudio` export remains unchanged. Physical storage acceptance
remains required after source/test acceptance.

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

The generic base host is now an Offline Pack component.

The host CI passed, its artifact SHA-256 is pinned in Android, Android created
an EXE without network access, and the Android-generated deterministic smoke
EXE was executed successfully on a physical Windows 11 machine.

Those results accepted the generic host and payload packager.

The normal Studio project-build route was accepted on 2026-09-22: a real
`webview-static` project produced its EXE through `DeviceBuildEngine` on
Android, opened on a physical Windows 11 machine, and its JavaScript acceptance
action returned `JAVASCRIPT_OK`. Windows Portable EXE is therefore accepted for
`webview-static` and `node-web`. Per-device readiness still requires the exact
pinned generic host.


### Windows Host Android pack integration

The verified generic Windows Portable Host is distributed as a prerelease
asset and is pinned by immutable transport metadata in Android:

- release tag: `windows-host-v1-a8c5323`
- expected byte length: `375025483`
- SHA-256:
  `699e5e13a157b9e436f8c19d0d6bca6264b510a71939a741d756078148d56c03`

The host is stored under AppForge's `noBackupFilesDir` modular build-pack
namespace, not inside the Terminal Linux workspace and not inside the
disposable Device Build Runtime rootfs.

Downloads use a `.part` file and HTTP Range resume where supported. A server
which ignores Range and returns HTTP 200 causes a clean full-file rewrite
instead of appending incompatible data.

The host is promoted to its final filename only after exact byte-length and
SHA-256 validation. A separate marker records the verified revision,
digest and length.

A verified host installation is not equivalent to Windows EXE readiness.
The UI may report `HOST KURULDU`, but `windowsExeReady` stays false until
Android creates a project-specific EXE locally and that artifact passes the
real Windows acceptance gate.


### Android-local Windows EXE packager

After the generic Windows host has been downloaded and SHA-256 verified,
Android can create a project-specific Portable EXE without Wine,
electron-builder or a remote AppForge Worker.

The device packager copies the verified host, creates a bounded local
`project.zip`, appends the existing AppForge manifest/payload contract and
verifies the resulting MZ/payload/footer structure before promoting the
`.part` file.

Project packaging limits remain aligned with the Windows runtime contract:
at most 10000 local files, 500 MB site content and a 512 MB AppForge payload.

The Offline Pack screen provides a deterministic device-generated acceptance
EXE containing `APPFORGE_WINDOWS_DEVICE_SMOKE_OK`. The file must still be
saved and executed on a real Windows machine before Windows output can move
from PLANNED to READY.


### Normal Studio project EXE integration

The accepted generic host/payload mechanism is wired into the normal
`DeviceBuildEngine` artifact path for `webview-static` and `node-web`.

For `node-web`, the npm-built static output is produced once and reused by the
Android wrapper and Windows packager when those outputs are requested. The EXE
is exposed through the existing `BuildApiClient` status and Storage Access
Framework save flow. Normal project source is not uploaded to a remote Windows
Worker.

Native Android/Kotlin/Java and Python Android engines do not advertise
`WINDOWS_EXE`. The legacy `windows-web` capability alias remains PLANNED while
the normal web engines own the accepted output.

The complete offline-pack `windowsExeReady` flag remains false until a real
normal project is built on Android and that resulting EXE is executed on
Windows.

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


### Public artifact save policy

User-visible build outputs use `Downloads/AppForgeStudio` as the canonical
public location. APK already followed this rule. AAB and Windows EXE now do the
same on Android 10+ even during an active owner/admin session. The private
`AppForge Dosyaları/APK` copy is additional only and never replaces the public
copy. APK+AAB and APK+AAB+EXE combinations inherit the same per-artifact rule.

## Web APK Manifest declaration fix

The Web wrapper writes AndroidManifest.xml through DeviceBuildEngine.webManifest.
The generated XML declaration must begin at byte zero even when multiline
permission entries prevent trimIndent from removing the leading indentation.
The manifest builder therefore applies trimStart after trimIndent. This is a
device-local APK packaging correction, not a remote build service restoration.

## Unified Agent historical artifact resolution

Unified Agent's checksum-validated session history is distinct from normal
`ProjectLibrary` build history. `BuildApiClient` can reissue a local ticket
only from a successful session's exact `local-...` ID, explicit advertised
output kind, canonical build directory and matching build number. The saved
artifact remains in private app files and is copied to public Downloads only
when explicitly requested. Never scan arbitrary project names or owner vaults.

## Selected app icon embedding (device build)

User-selected prepared PNG is embedded into the disposable Web/Node,
Python or native Android project's drawable resources and manifests before
Gradle executes; the original imported project stays unchanged. The selected
PNG must not silently fall back to a default icon. For normal Windows Portable
EXE packaging only, the verified host is first copied to the per-project
`.part` file. Existing RT_ICON and RT_GROUP_ICON slots in that copy are updated
without moving the PE sections, changing the host cache or shifting the large
NSIS overlay and AppForge footer. Missing/undersized slots fail closed rather
than advertising an EXE with the wrong icon. CI compilation, physical Android
icon acceptance and Windows Explorer/file launch acceptance remain separate.

### Physical icon visual regression (2026-09-24)

The first selected-icon implementation passed source tests, but on-device
acceptance found its prepared Android PNG double-padded content (640/1024),
and high-detail photos failed in the fixed-size RT_ICON slots of the pinned
Windows Portable Host. Prepared Android content now fills 960/1024. The
project-copy PE patcher first tries full-resolution PNG and DIB, then bounded
color quantization/detail reduction if the existing slot is smaller. It still
fails closed if the selected artwork cannot be embedded; the pinned Host
and its NSIS overlay/footer are not resized or rewritten. Windows Explorer
icon appearance AND actual Windows executable startup still require physical
acceptance. Device CI alone is not acceptance.

## Shared selected-icon master (real-device visual follow-up)

Selecting a NEW icon prepares one 1024 px square master under prepared-icons.
The entire source graphic is contained at full available width without
resizing its aspect ratio or synthesizing a coloured frame. For opaque wide
images, the uncovered square bars match the source corner background rather
than the unrelated primary UI colour; transparent sources keep the explicitly
selected background. The same saved iconUri is consumed by APK/AAB and the
project-copy Windows PE icon patcher. Pre-existing prepared-icons PNG files
are not silently rewritten; reselect the ORIGINAL source when validating.

A landscape design cannot simultaneously occupy the complete square AND
remain uncropped/undistorted. Real Android launcher and Windows Explorer
large/small-icon screenshots, plus Windows EXE execution, are separate
acceptance gates. The verified generic Windows Host is immutable.

## node-web local HTTPS module loading (2026-09-24)

A physical React/Vite APK packaged its JS and CSS correctly
but displayed a blank WebView when loaded from file://.

The node-web Android wrapper now opts in to framework-only,
offline, same-origin HTTPS asset interception through
appassets.androidplatform.net/assets/site/.

Explicit MIME types and path traversal rejection are enforced.
webview-static keeps its original file loading path.

Native bridge and geolocation trust recognize the exact local
origin when this node-web flag is enabled.

No new AndroidX dependency or Windows Host revision is added.

The earlier npm ENOENT remains a separate intermittent issue.
No npm cache is purged by this patch.

Static tests, CI and physical APK/AAB re-acceptance are required.


### Persistent node-web npm cache (2026-09-25)

Physical React/Vite builds repeatedly exposed npm ENOENT
rename failures under the default `/root/.npm/_cacache/tmp`.

node-web now uses the AppForge-owned persistent cache
`/opt/appforge-device/npm-cache-v1` for both online cache
priming and offline builds.

An online build may perform one bounded retry only when the
failure is the observed cacache temporary ENOENT pattern.
That retry removes only the cache temporary directory; cached
package content and the legacy `/root/.npm` tree are preserved.

Offline builds never gain a network fallback from this recovery
path. Project dependencies must already exist in the persistent
cache.


### node-web native optional dependencies (2026-09-25)

Physical React/Vite acceptance showed that npm cache recovery
could succeed while Vite still failed because the platform-specific
`@esbuild/linux-arm64` package was absent.

node-web now explicitly includes optional dependencies during both
online and offline npm installs. Install scripts remain disabled.

After installation, AppForge validates an available esbuild binary
and Rollup runtime before starting the project build. This catches
missing platform-native optional packages before Vite execution.

Online cache priming and later offline installation continue to use
the same persistent AppForge npm cache.


## 2026-09-25 node-web physical offline acceptance

React/Vite node-web acceptance passed on a physical Android
device after the local HTTPS WebView, persistent npm cache and
native optional-dependency corrections.

A fresh React + Vite test project was built with Wi-Fi and
mobile data disabled. Device-local APK and AAB generation
completed, the generated APK launched successfully, and the
runtime marker `APPFORGE_REACT_VITE_JS_PASS` was observed.

The same fixture also passed online before the offline run.

This closes physical APK/AAB offline acceptance for the
current React/Vite node-web fixture. Arbitrary imported npm
projects remain dependent on their exact package versions being
available in the AppForge persistent npm cache.
