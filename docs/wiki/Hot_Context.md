---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-03
last_verified: 2026-10-03
confidence: high
tags:
  - hot-context
  - release-integration
  - device-build
  - windows
related:
  - "[[Index]]"
  - "[[Current_Status]]"
  - "[[Release_Integration_V1]]"
  - "[[Windows_Output_Artifact_Flow_V2]]"
  - "[[Windows_Publisher_Authorization]]"
  - "[[Open_Questions]]"
source_files:
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/windows-portable-host.yml"
  - "windows-host/main.cjs"
  - "quality/tests/appforge_play_distribution_contract.test.js"
  - "quality/tests/windows_portable_persistence_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "quality/tests/windows_publisher_signing_provider_contract.test.js"
---

# Hot Context

## Current Focus

- Release Integration V1 remains the active integration line.
- Full Quality is `825/825 PASS`.
- Tulpar Windows Portable persistence/relaunch/relocation/crash physical retest
  is PASS.
- Authenticode crypto, signature verification and RFC3161 timestamp are
  physically PASS; patched final signed-artifact retest remains pending.
- Protected `main` remains untouched.

## Must Know

- Normal project compilation remains device-local.
- D1 migration ledger reconciliation is complete for migrations 0001 through
  0005.
- Portable and Native EXE are distinct artifact identities.
- Portable post-sign flow must not execute the Native-only x86-64 validator.
- Native Android CMake and real Windows x64 launch are physically PASS.
- Production publisher endpoint remains DISABLED.
- `PRODUCTION_READY=NO`.

## Recent Important Changes

- Portable localStorage and IndexedDB survived physical relaunch, EXE
  relocation and forced termination with sequence `1 → 2 → 3 → 4 → 5`.
- Physical publisher signing consumed the server grant, produced a valid
  debug-only self-signed Authenticode signature and verified the DigiCert
  RFC3161 timestamp.
- A post-sign bug incorrectly applied Native PE validation to Portable output;
  fail-closed deleted the signed artifact.
- The validator is now Native-only; publisher targeted tests are `27/27 PASS`
  and Portable targeted tests are `10/10 PASS`.

## Current Risks / Open Questions

- Repeat signed Portable physical build using the patched APK.
- Verify and launch the preserved signed EXE on real Windows.
- Non-admin and missing-provider fail-closed physical acceptance: PENDING.
- Production publisher endpoint: DISABLED pending review.
- Play Production access and final protected-main integration: PENDING.

## Read Next

- [[Current_Status]]
- [[Release_Integration_V1]]
- [[Windows_Output_Artifact_Flow_V2]]
- [[Windows_Publisher_Authorization]]
- [[Open_Questions]]
