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
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt"
  - "windows-host/payload.cjs"
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
- Full Quality is `826/826 PASS`.
- Tulpar Windows Portable persistence/relaunch/relocation/crash physical retest
  is PASS.
- Authenticode crypto, Code Signing EKU, RFC3161 timestamp, final signed
  artifact preservation and embedded Portable project runtime are physically
  PASS.
- `REAL_AUTHENTICODE_END_TO_END=PASS`.
- Protected `main` remains untouched.

## Must Know

- Normal project compilation remains device-local.
- D1 migration ledger reconciliation is complete for migrations 0001 through
  0005.
- Portable and Native EXE are distinct artifact identities.
- Portable post-sign flow must not execute the Native-only x86-64 validator.
- Signed Portable payload lookup must resolve logical EOF from the PE Security
  Directory rather than assume the physical file end is the AppForge footer.
- Native Android CMake and real Windows x64 launch are physically PASS.
- Production publisher endpoint remains DISABLED.
- `PRODUCTION_READY=NO`.

## Recent Important Changes

- Portable localStorage and IndexedDB survived physical relaunch, EXE
  relocation and forced termination with sequence `1 → 2 → 3 → 4 → 5`.
- Physical publisher signing consumed the server grant, produced a valid
  debug-only self-signed Authenticode signature and verified the DigiCert
  RFC3161 timestamp.
- The Native-only validator regression is fixed and the next signed artifact
  was preserved.
- That preserved EXE proved a second runtime regression: Authenticode's
  Certificate Table moved physical EOF beyond the AppForge footer.
- Windows Host CI run `37143822118` passed; Android pins exact staging host
  `windows-host-v1-c7e4b2a`.
- Exact-head Android Debug run `37144740820` passed.
- Physical build `AF-0000001057` rendered the project on Tulpar without the
  former payload-signature error; launch counter reached `2`.
- `AF-0000001058` physically proved missing-provider/certificate fail-closed;
  the final EXE was deleted.
- `AF-0000001059` physically proved non-admin signing denial with
  `Owner access denied.`; the final EXE was deleted.
- Both publisher-signing negative physical gates are PASS.

## Current Risks / Open Questions

- Production publisher endpoint: DISABLED pending review.
- Play Production access and final protected-main integration: PENDING.

## Read Next

- [[Current_Status]]
- [[Release_Integration_V1]]
- [[Windows_Output_Artifact_Flow_V2]]
- [[Windows_Publisher_Authorization]]
- [[Open_Questions]]
