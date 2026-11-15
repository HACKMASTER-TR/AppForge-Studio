---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-19
updated: 2026-11-15
last_verified: 2026-10-01
confidence: high
tags:
  - device-build
  - runtime-v3
  - multi-platform
related:
  - "[[System_Architecture]]"
  - "[[Build_And_Worker_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/RuntimeStorageBudget.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/RuntimeGenerationStore.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/RuntimeFileClone.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/AndroidArtifactPublication.kt"
  - "android-app/app/build.gradle.kts"
  - "quality/tests/t21_android_publication_runtime.test.js"
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
  - "android-app/app/src/main/java/com/appforge/studio/BuildRuntimeState.kt"
  - "android-app/app/src/main/java/com/appforge/studio/BuildProgressService.kt"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
  - "quality/tests/device_build_cancel_reader_contract.test.js"
  - "quality/tests/device_build_active_state_restore_contract.test.js"
  - "quality/tests/builder_source_engine_refresh_contract.test.js"
  - "quality/tests/builder_project_switch_build_state_contract.test.js"
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

Project builds use a dedicated AppForge-managed device-build runtime.
The persistent Terminal Linux workspace is a separate developer environment and
must never be reused, deleted or repaired as the project-build rootfs.

A runtime revision change may replace only the dedicated build runtime.

## Proven device-local engines

Current accepted families include:

- static WebView HTML/CSS/JavaScript;
- npm-built static web projects including React/Vite class projects;
- native Android Java/Kotlin;
- Python/Chaquopy;
- scoped Expo SDK54 / React Native 0.81 APK+AAB acceptance.

Unvalidated future engine families remain non-READY.

## Artifact architecture

First-class output identities include:

- APK;
- AAB;
- Windows Portable EXE;
- Windows Native EXE where its distinct engine is selected.

Portable and Native must remain distinct throughout build request, history,
download ticket and filename recovery.

## Windows

Portable packaging uses the verified generic Windows host and AppForge payload
format. Persistent browser data is appId-scoped outside disposable extraction
state.

Release Integration V1 adds stable `appforge://` origin, IndexedDB persistence,
EXE relocation persistence and crash-durability contracts while retaining the
V2 persistent-profile and single-instance architecture.

Native Windows cross-compilation has produced a PE32+ x64 executable that
launched on real Windows x64. Publisher signing remains a separate release gate.

## Offline build boundary

Offline Build Pack remains modular and isolated from Terminal Linux. Toolchain
readiness must be deterministic and acceptance-gated. Missing offline
dependencies fail closed rather than silently using a remote Worker.

Normal project compilation remains device-local.

## Lifecycle and artifacts

Active build identity survives relevant Android lifecycle restoration.
Cancellation must not crash AppForge. Historical artifact recovery resolves
only the exact successful build/output identity and fails closed on missing or
ambiguous artifacts.

## Open gates

Later historical physical acceptance is recorded for Portable persistence,
relaunch, relocation and forced-termination recovery, Authenticode end-to-end
execution, cryptographic verification and negative signing scenarios. See
[[Windows_Publisher_Authorization#Acceptance scope matrix]] for their scope and
evidence. Current-head physical acceptance is not inferred from those records.

Production public publisher trust is not established by debug self-signed
verification. The production custom-domain publisher endpoint decision, Play
Production access and final protected-main release integration remain pending
and separate from historical physical acceptance. Any new acceptance claim for
a newer source requires separate exact source/artifact binding.

Full historical architecture and acceptance chronology:
[[archive/Device_Build_Runtime_V3_History_2026-10-02]].

## T21 wizard readiness boundary

`WizardReadinessEvaluator` in `WebviewProjectSettings.kt` collects settings
blockers by wizard field and step before UI build start, offline requirements
and engine execution. Its classification is settings evidence only; filesystem,
template integrity, toolchain and signer checks remain mandatory at use.
Default wizard bridges/upload/network/splash still require the normal engine.
Unsupported category/primary-color injection and automatic provider integrations
remain explicit early blockers with selections preserved. Fast URL AAB and
non-pinned AAB SDK selections use the normal engine. Declaration-only permissions
do not supply native APIs. See `docs/reports/t21-unified-development.md` for the
inventory and scoped execution evidence; no current physical acceptance is inferred.

T21 continuation distinguishes settings eligibility from experimental status:
explicit experimental readyOutputs (currently Windows Native EXE) remain selectable
with a warning; PLANNED/external-only engines and output mismatches fail closed.
Toolchain/source/signer availability remains checked at execution. Wizard warnings
and per-step issue links occupy a separate bounded scroll viewport above step
content; this source structure does not establish Compose/device visual acceptance.
Provider resolver JVM tests additionally cover expiry, future timestamps, path and
symlink escape with stubbed Android boundaries; OS grants remain a physical gate.

### T21 routing explanation boundary

`FastHtmlApkPolicy.normalRouteReasons` is scoped to the same static HTML/HTTPS
source family and Android-only output boundary as fast eligibility. It returns
no Tiny-versus-normal reasons for Node, Expo, native engines, non-HTML local
sources, Windows-only or mixed Android/Windows targets. The wizard reports
engine/host prerequisites separately as nonblocking external-configuration
information; pure settings eligibility never attests installed tools or keys.
Experimental Native EXE remains selectable only for its declared outputs and
retains its nonproduction disclosure. The evaluator also mirrors the engine's
Expo source-readiness and global unsupported-injection guards before build.

### T21 normal Android publication and selected-source evidence

Normal Gradle APK/AAB collection uses `AndroidArtifactPublication`: select every
requested output before copying, bound canonical paths, validate format/size and
signatures, copy into a private job pending directory, compare bytes by SHA-256,
then finalize each file with explicit rollback on failure/cancellation. Per-file
renames are not a multi-file atomic filesystem operation. APK/AAB download and
snapshot visibility requires final job success; a later engine failure clears
Android fields/files. Windows artifact identity and its signing lifecycle remain
separate. Recovery removes interrupted directories for the same job identity;
this is not a startup sweep or cross-process recovery database.

Normal APK verification checks ApkVerifier and Android package identity; AAB reads
all signed JAR payloads and checks certificate consistency, custom selected normal
keystore identity, and APK/AAB signer parity when both are requested. Normal AAB
proto manifest package/permission parity still requires Android acceptance evidence.

Gradle preserves the three exact compiled artifact/manifest integrity pins. A
separate `html-template-source-inputs.txt` records a versioned, sorted path/byte
fingerprint of template main sources and selected repository Gradle inputs,
captured before template preBuild and checked again before embedding metadata.
It excludes environment, secrets, local properties and generated outputs: it is
neither a complete source attestation nor an artifact digest. JVM tests exercise
the fingerprint function, not Gradle/AGP task execution. Android build and physical
acceptance remain NOT_RUN for this remediation.

## V537 storage candidate: bounded admission and optional reflink

The storage candidate is based on `1acce4b9ea703abc7bd1dbb858d9dfe33042b186`;
it does not attest a physical update or authorize production delivery.
`RuntimeStorageBudget` is admission policy, independent of installed readiness.
Any retained failed stage/quarantine blocks another generation clone. Unknown
copies remain untouched. Obsolete previous generations have a 256 MiB / 16-entry
admission ceiling; required receipt references and a verified rollback are kept.
A new transaction reserves a full independent root copy plus 256 MiB, even if
reflink is supported. Module expansion reservations are explicit upper limits:
Node 2 GiB; Rust/Python 4 GiB; Linux/Windows Native 8 GiB; NDK 12 GiB;
Android Core/Expo 16 GiB; Portable 1 GiB. They are not measured package sizes.
The installer checks expansion and remaining free space during execution. A denied
installation does not invalidate receipts or disable existing builds/files/cache.

`RuntimeFileClone` probes FICLONE on the actual destination filesystem using two
files and writes in both directions. JNI failure or unsupported reflink uses a
real independent copy. No active/staged hardlinks are created. Allocation reports
use lstat blocks when available, report unknown otherwise, and never interpret
shared extent counts as exclusive reclaimable bytes. Native support alone does
not prove reflink support on Android.

`RuntimeMutationLock` holds an OS file lock across generation preparation through
commit/recovery/close, root activation, and initial rootfs installation. Leases
can cross coroutine threads; initial activation borrows its installer's lease.
Process death releases the lock but leaves existing journals and protected files.
An interrupted initial extraction blocks another extraction until reviewed.
Existing schema-2 recovery stays fail closed. Unknown historical journals and
ownership failures are preserved, not migrated by filename pattern.

Cleanup previews bind the active tree digest, exact candidate names/digests,
logical byte count and cleanup scope. Execution recomputes the same plan under
both the mutation lease and filesystem lock. Changed or unowned trees are not
removed. At least one verified rollback and all receipt references are retained.
Different historical quarantines are intentionally not eligible. An interrupted
partial cleanup may require manual inspection; it is never blindly swept.

Verified transport shares existing signed R2/native CAS locations without changing
pinned input catalogs. Cache validation precedes network policy/acquisition; cache
hits report zero network and do not assert READY. SHA-256 remains mandatory even
when expected size is available. npm/Gradle lock/offline verification remains with
its existing package manager. No installer recipe, signed metadata, witness,
anti-rollback, GPG, Ed25519, TLS or production verifier is weakened.

The dedicated update workflow preserves the old v534 reference and separately
pins the real v536 artifact. It requires v537, the isolated3 package and original
certificate before upload, adds immutable source/APK/pin-catalog evidence, and
requires disposable emulator storage/update tests before publishing a candidate.
Physical acceptance and actual hosted execution remain separate evidence gates.
