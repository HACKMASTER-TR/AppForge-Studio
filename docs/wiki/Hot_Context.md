---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-09
last_verified: 2026-10-09
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

<!-- APPFORGE_HOT_CONTEXT_CURRENT_RECOVERY -->
## Current Focus

Recover repository-tracked workstreams through [[01_Project/Current_State_Handoff]].
`current_state.json` is the canonical cross-workstream claim registry;
Current_State_Handoff is its generated readable derivative. Git and exact external
evidence remain authoritative. Draft/open PR does not mean merged; hosted CI does
not mean physical acceptance. An evidence-record/meta commit is distinct from the
canonical implementation SHA; later checkouts do not inherit acceptance.

- Admin AI: [[02_Codebase_Map/Local_AI_And_Agent_Map]]. Implementation exists;
  Android/physical acceptance and constrained native Windows/publisher-signing
  execution remain separately evidenced.
- Google Play: [[06_Product_And_Features/Google_Play_Platform_V1]]. Newer isolated
  staging / Integrity / Internal-track work exists beyond the historical
  checkpoint. Play Production remains a separate protected gate; partial
  evidence does not establish Production acceptance.

## Historical checkpoint — 2026-10-03

October 3 release-integration evidence is historical, not current-checkout PASS:
Release Integration V1, Full Quality `826/826 PASS`, Tulpar Portable persistence,
relaunch, relocation and crash retest PASS; Authenticode crypto, Code Signing EKU,
RFC3161 timestamp, signed-artifact preservation and embedded runtime physically
PASS; `REAL_AUTHENTICODE_END_TO_END=PASS`. Protected `main` was untouched.

## Must Know

At the historical checkpoint:

- Normal project compilation remains device-local.
- D1 migration ledger reconciliation is complete for migrations 0001–0005.
- Portable and Native EXE identities were distinct; Portable must avoid the
  Native-only x86-64 validator and resolve logical EOF through PE Security Directory.
- Native Android CMake and Windows x64 launch were physically PASS.
- Production publisher endpoint was DISABLED; `PRODUCTION_READY=NO`.

## Recent Important Changes

Historical October 3 references:

- Portable storage survived relaunch/relocation/termination, sequence `1 → 2 → 3 → 4 → 5`.
- Debug-only self-signed Authenticode consumed the server grant; DigiCert RFC3161
  verified. Validator and Certificate Table/footer regressions were resolved.
- Windows Host CI `37143822118`, host `windows-host-v1-c7e4b2a`; Android Debug `37144740820`.
- Tulpar `AF-0000001057` rendered successfully, counter `2`.
- `AF-0000001058` missing-provider/certificate and `AF-0000001059` non-admin denial
  proved fail-closed; final EXEs were deleted. Both negative physical gates PASS.

## Current Risks / Open Questions

- Publisher authority: [[Windows_Publisher_Authorization]].
- Play Production: [[06_Product_And_Features/Google_Play_Platform_V1]].
- Delivery requires scoped evidence and authorization: [[01_Project/Current_State_Handoff]],
  [[ChatGPT_Codex_Development_Standard]].

## Read Next

- [[Current_Status]]
- [[Release_Integration_V1]]
- [[Windows_Output_Artifact_Flow_V2]]
- [[Windows_Publisher_Authorization]]
- [[Open_Questions]]
