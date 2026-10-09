---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-02
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - release-integration
  - windows-portable
  - play-production
  - fail-closed
  - persistence
related:
  - "[[Windows_Output_Artifact_Flow_V2]]"
  - "[[Windows_Publisher_Authorization]]"
  - "[[Current_Status]]"
  - "[[Open_Questions]]"
source_files:
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/windows-portable-host.yml"
  - "quality/tests/appforge_play_distribution_contract.test.js"
  - "quality/tests/windows_portable_persistence_contract.test.js"
  - "windows-host/main.cjs"
  - "windows-host/scripts/append-smoke-payload.cjs"
---

# Release Integration V1

<!-- APPFORGE_RELEASE_INTEGRATION_V1 -->

## Purpose

Release Integration V1 starts from the closed Windows Output Artifact Flow V2
commit `b9993dd45eb4a1452a0acf66eccd2658af49fd9a`.

It closes two code gaps found by the Release Gap Audit without merging the
original draft pull requests or mutating protected `main`.

## Integrated Code Gaps

### Play Production fail-closed

PR #62 source commit:

`4b0ed321151f2e33986bb2740aff1c75f663f28c`

The release integration branch contains the Play Production fail-closed
contract:

- manual workflow dispatch cannot select Production;
- Production publishing requires the strict AppForge release-tag path;
- release tag must resolve to protected `main`;
- release tag version and Android version metadata must match;
- uploader consumes only the resolved fail-closed track.

This closes the Release Gap Audit code blocker
`PLAY_FAIL_CLOSED_PR62_NOT_IN_V2`.

It does not grant Play Production access and does not publish a release.

### Windows Portable persistence

PR #63 source commits:

- `ce5b40e5143aeebda0e4522f2878a396cc96dc8d`
- `868299f46e1f19de00649da16ecefe773c95d177`

Direct cherry-pick conflicted with the newer V2 Portable architecture, so the
feature was reconciled semantically instead of choosing whole-file
`ours`/`theirs`.

Preserved V2 contracts:

- central `storage.cjs` persistent profile architecture;
- stable appId-based runtime identity;
- single-instance behavior;
- existing LocalStorage relaunch contract.

Added PR #63 contracts:

- privileged stable `appforge://app/...` local origin;
- persistent `sessionData`;
- LocalStorage persistence;
- IndexedDB persistence;
- EXE relocation persistence;
- disposable runtime cleanup;
- periodic Chromium `flushStorageData()` durability;
- forced-termination LocalStorage recovery;
- forced-termination IndexedDB recovery.

Targeted release-integration regression set passed 25/25 before this
documentation update.

## Native Windows Physical Acceptance

PR #61 evidence records both of these as physically accepted:

- Native CMake build on a physical Android device: PASS
- produced PE32+ x64 EXE launched on real Windows x64: PASS

Therefore Native Windows x64 execution itself is no longer a pending gate.

Native remains EXPERIMENTAL overall because publisher-signing and final release
acceptance are separate gates.

## Remaining Release Gates

Later historical acceptance records exist for Portable persistence/relaunch/
relocation/forced-termination recovery, real Authenticode end-to-end execution,
cryptographic publisher verification, and both negative signing scenarios.
See [[Windows_Publisher_Authorization#Acceptance scope matrix]] for evidence
and scope. These historical passes do not establish physical acceptance of the
current checkout; any new acceptance claim for a newer source must be separately
bound to its exact source and artifact evidence.

Still pending:

- production custom-domain publisher endpoint decision;
- Play Production access approval;
- final release integration into protected `main`.

## Explicit Boundaries

This integration does not:

- merge PR #54, #60, #61, #62, #63 or #64;
- mutate protected `main`;
- publish to Play Production;
- enable the production publisher endpoint;
- perform a D1 write or migration;
- deploy Cloudflare;
- modify `appforge-failover`.

## Production Readiness

`PRODUCTION_READY=NO`

The two audited code gaps are closed on the release integration branch, but
infrastructure, external-access and final-main-integration gates remain open.
Historical physical acceptance does not transfer to the current checkout.
