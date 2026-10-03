---
type: log
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-03
last_verified: 2026-10-03
confidence: high
tags:
  - log
related:
  - "[[Index]]"
source_files: []
---

# Project Log

## 2026-09-15 — maintenance | Legacy repository brain replaced

- Summary: Removed the legacy `.secondbrain` implementation and created a source-verified wiki foundation.
- Updated wiki pages: [[Index]], [[Hot_Context]], [[Current_Status]], [[System_Architecture]]
- Related source files: `AGENTS.md`, `scripts/appforge-stability-gate`, `android-app/app/src/main/java/com/appforge/studio/MainActivity.kt`
- Related problem: [[Legacy_Brain_Removal_And_Validation_State]]

## 2026-09-15 — maintenance | Source-verified coverage completed

- Summary: Added the Coverage Registry and source maps for terminal tools, local AI, account/security, backend API domains, workers/artifacts, database schema coverage, and operations.
- Validation: Wiki structural audit, secret scan, and prune report are Green. The direct terminal integration contract passed 7/7 after its deleted-document dependency was removed.
- Boundaries: Legacy documentation was not restored or used as wiki evidence. Full backend and Android acceptance remains subject to provisioned dependencies and device/toolchain availability.

## 2026-09-15 — maintenance | Legacy docs root cleanup verified

- Removed legacy Markdown/backups from `docs/`; durable wiki memory remains in
  `docs/wiki/`. Legal privacy/delete pages were preserved and cleanup CI passed.

## 2026-09-21 — validation | Pro staging Live Audit accepted

- Summary: Replaced the staging Live Audit HTTP transport
  from Python `urllib` to curl after the HTTP Matrix proved
  GitHub Runner access across three request profiles.
- Validation: Live Audit run 3 passed staging health, D1
  reachability and the GET-only ownership route. Existing
  DB binding and Google configuration names were preserved.
- Boundaries: No database write, migration apply, new Worker
  deployment, `main` change, Play Production change or
  failover change was part of the audit.
- Remaining gates: `d1_migrations` reconciliation and
  real-device Pro lifecycle acceptance.

## 2026-10-02 — validation | Server-verified publisher authorization accepted

- Summary: Windows publisher signing now requires a short-lived one-time
  control-plane grant bound to the exact unsigned EXE SHA-256, Build ID,
  purpose and random request nonce.
- Source validation: implementation commit `53895ff` passed full quality and
  Android Debug CI.
- Staging: source commit `63d32e6` was deployed by marker-only commit
  `7736c35`; controlled staging workflow run `36971590024` passed.
- Physical acceptance: debug acceptance commit `54d564e` and Android Debug run
  `36974764595` produced the physical test APK.
- Result: Google admin verification PASS, grant issue PASS, artifact mutation
  BLOCKED, valid consume PASS, replay BLOCKED.
- Boundaries: no new D1 migration, no `main` change, no Play Production change
  and no draft PR merge.
- Remaining: real Windows Authenticode physical end-to-end acceptance.

## 2026-10-02 — maintenance | Stale backend documentation reconciled

- Summary: Reclassified the former Express/Worker Build Service as historical,
  rewrote the active backend map around device-local project builds and the
  Cloudflare control plane, and replaced the stale PostgreSQL database map
  with the reconciled D1 0001–0005 schema.
- BUG-7: the legacy-brain validation page is now resolved; it no longer claims
  an active device blocker.
- Navigation: Wiki task routing no longer sends current build work to retired
  Express/queue documentation.
- Preserved: `docs/delete-account.html`, active `AppForgeAccountClient`,
  `BuildApiClient`, Unified Agent resume behavior and Cloudflare control plane.
- Boundaries: no application route deletion, D1 write/migration, deployment,
  `main` change or Play Production action.

## 2026-10-02 — maintenance | Retired provider active claims removed

- Confirmed by source audit that Railway, Render and remote autoscale runtime
  providers are absent from production source.
- Rewrote README text that still advertised Railway authorization and
  remote-Worker performance behavior as active.
- README now reflects device-local normal builds and Pro Ömür Boyu only.
- Project Overview no longer treats Railway as a live provider with unknown
  status.
- Current Status now marks the removed AdminAccountsScreen as historical.
- Negative retirement contracts, historical decisions and
  `.appforge/fail-inventory` evidence remain preserved.
- No control-plane source, D1 state, deployment, `main`, or Play Production
  change was made.

## 2026-10-02 — maintenance | Final repository hygiene gate

- Removed five source files with no reachable production path:
  `UniversalLanguageSupport.kt`,
  `AppForgeAgentIntelligentBlueprintProvider.kt`,
  `AppForgeAgentProductionCoordinator.kt`,
  `AppForgeAgentSecondBrain.kt`, and
  `ExcelToolsHistoryStore.kt`.
- The removed V9 intelligent-provider / Second Brain pair was a test-only
  unreachable cluster. Active Second Brain V2 integration remains separate.
- Removed tracked `AppForgeStudio-latest.apk`; Android Debug continues to
  generate it as a transient CI artifact and the owner Terminal command
  resolves the successful exact-HEAD Actions artifact.
- Added permanent repository hygiene rules and a reusable hygiene guard.
- Hard Stability Gate and local pre-push now enforce repository hygiene and
  deterministic Second Brain snapshot freshness.
- `.appforge` backups and fail-inventory evidence remain preserved.
- No D1 write, Cloudflare deployment, Play Production action, `main` mutation
  or PR merge was performed.

## 2026-10-02 — feature | Windows EXE mode UX V1

- Builder now presents Windows Portable EXE and Windows Native EXE as two
  explicit Windows output engines instead of a generic Windows EXE label.
- Portable EXE remains READY for compatible Web/Node/Universal projects and
  uses the AppForge Generic Host.
- Native EXE remains EXPERIMENTAL for C/C++/CMake sources and uses the
  device-local CMake + MinGW-w64 Windows x64 PE path.
- Build result status, download action and build history identify the selected
  Windows EXE engine.
- Build history now persists `buildOutput` with a backwards-compatible empty
  default for older records.
- No engine readiness promotion was performed.
- PR #63 portable physical acceptance remains pending for the Tulpar test.
- No merge, Play Production action, D1 write or Cloudflare deployment was
  performed.

## 2026-10-02 — feature | Windows Output Artifact Flow V2

<!-- APPFORGE_WINDOWS_OUTPUT_ARTIFACT_FLOW_V2_LOG -->

- Separated Windows Portable and Windows Native artifact identities beyond the
  V1 UI layer into ticketing, persisted recovery, filename generation and
  public Downloads classification.
- Portable uses ticket `exe`; Native uses ticket `native-exe`.
- Added explicit `*_windows-portable.exe` and `*_windows-native.exe`
  identities.
- Legacy generic EXE remains Portable.
- Legacy Unified Agent Native lookup fails closed instead of inferring Native
  from old EXE history.
- Added permanent artifact-flow regression coverage: 8/8 PASS.
- Updated stale history tests to be whitespace-tolerant without weakening exact
  Build ID and fail-closed recovery contracts.
- Full Quality completed 814/814 PASS.
- Boundaries: no PR merge, protected-main mutation, Play Production action, D1
  write/migration, Cloudflare deployment or failover change.
- Remaining external gates: Portable/Native physical Windows acceptance and
  physical Authenticode acceptance.

## 2026-10-02 — release integration | PR #62 + PR #63

<!-- APPFORGE_RELEASE_INTEGRATION_V1_LOG -->

- Started from closed Windows Output Artifact Flow V2.
- Integrated PR #62 Play Production fail-closed commit without merging PR #62.
- PR #63 direct cherry-pick conflicted with newer V2 Windows host architecture.
- Aborted the conflict cleanly.
- Reconciled PR #63 semantically while preserving V2 `storage.cjs` persistent
  profile and single-instance behavior.
- Added stable `appforge://` origin, IndexedDB persistence, relocation
  persistence and forced-termination storage durability contracts.
- targeted release-integration contracts: 25/25 PASS.
- Corrected Second Brain truth: Native real Windows x64 launch is PASS based on
  PR #61 physical acceptance evidence.
- Remaining gates are physical Authenticode, Portable physical retest,
  production publisher endpoint review, Play Production access and final main
  integration.
- No PR merge, protected-main mutation or Production publish occurred.

## 2026-10-03 — validation | Portable Authenticode

- Crypto/RFC3161 and artifact preservation: PASS.
- Authenticode Certificate-Table payload lookup regression patched.
- Targeted `14/14`, Full Quality `826/826 PASS`.
- Windows Host CI and physical runtime retest remain.
