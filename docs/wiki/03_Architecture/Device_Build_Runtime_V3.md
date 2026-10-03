---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-19
updated: 2026-10-01
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

- Portable physical Windows persistence/relaunch/relocation/crash retest.
- Authenticode release-signing acceptance.
- final release integration.

Full historical architecture and acceptance chronology:
[[archive/Device_Build_Runtime_V3_History_2026-10-02]].
