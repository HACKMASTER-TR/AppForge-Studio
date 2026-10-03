---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
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
---

# Hot Context

## Current Focus

- Release Integration V1 integrates the audited PR #62 Play fail-closed gap and semantically reconciles PR #63 Portable persistence with V2.
- Full Quality is `824/824 PASS`.
- Windows Output Artifact Flow V2 remains closed at `b9993dd45eb4a1452a0acf66eccd2658af49fd9a`.
- Protected `main` remains untouched.

## Must Know

- Normal project compilation remains device-local.
- D1 migration ledger reconciliation is complete for migrations 0001 through 0005.
- Portable and Native EXE are distinct artifact identities.
- Native Android physical CMake build: PASS.
- Native PE32+ x64 launch on real Windows x64: PASS.
- Server-verified publisher authorization staging acceptance: PASS.
- `PRODUCTION_READY=NO`.

## Recent Important Changes

- PR #62 Play Production fail-closed behavior is integrated without merging the draft PR.
- PR #63 persistence was reconciled while preserving V2 `storage.cjs` and single-instance behavior.
- Portable now carries stable `appforge://` origin, IndexedDB, relocation and crash-durability contracts.
- Second Brain records Native Windows x64 execution as physically accepted.

## Current Risks / Open Questions

- Portable Windows persistence/relaunch/relocation/crash physical retest: PENDING.
- Real Windows Authenticode end-to-end signing: PENDING.
- Signed-publisher and negative fail-closed signing acceptance: PENDING.
- Production publisher endpoint: DISABLED pending review.
- Play Production access: PENDING.
- Final protected-main integration: PENDING.

## Read Next

- [[Release_Integration_V1]]
- [[Current_Status]]
- [[Windows_Output_Artifact_Flow_V2]]
- [[Windows_Publisher_Authorization]]
- [[Open_Questions]]
