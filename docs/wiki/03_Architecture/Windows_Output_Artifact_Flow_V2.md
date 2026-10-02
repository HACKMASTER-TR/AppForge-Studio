---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-02
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - windows
  - artifact-flow
  - device-build
  - portable-exe
  - native-exe
related:
  - "[[Hot_Context]]"
  - "[[Current_Status]]"
  - "[[Device_Build_Runtime_V3]]"
  - "[[Worker_And_Artifact_Flow]]"
  - "[[Windows_Publisher_Authorization]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/BuildArtifactModel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "quality/tests/windows_output_artifact_flow_v2_contract.test.js"
  - "quality/tests/device_build_history_resave_contract.test.js"
---

# Windows Output Artifact Flow V2

<!-- APPFORGE_WINDOWS_OUTPUT_ARTIFACT_FLOW_V2 -->

## Status

Windows Output Artifact Flow V2 is source-complete and Full Quality accepted on
the feature branch `feat/windows-output-artifact-flow-v2-20261002`.

This page records artifact identity and recovery contracts. It does not promote
Windows Native EXE to READY and does not represent physical Windows acceptance,
production publishing, PR merge or protected-main delivery.

## Artifact Identity

Windows Portable EXE and Windows Native EXE are separate artifact identities.

| Artifact | Build output identity | Download ticket | Public filename |
|---|---|---|---|
| Windows Portable EXE | Portable / legacy EXE | `exe` | `*_windows-portable.exe` |
| Windows Native EXE | `native-exe` / `windows-native-exe` | `native-exe` | `*_windows-native.exe` |

Portable remains the backwards-compatible interpretation of historical generic
EXE records.

Native remains a distinct experimental Windows artifact path.

## Download Ticket Contract

`MainActivity` derives the Windows download ticket from persisted `buildOutput`.

- Native output uses `native-exe`.
- Portable output uses `exe`.
- Legacy records without a Native identity continue to use `exe`.
- Three Builder download paths use the same typed ticket helper.

The UI therefore cannot request a Native artifact through the historical
Portable ticket by accident.

## Persisted History Recovery

`BuildApiClient` normalizes artifact ticket identity before resolving a
persisted artifact.

For persisted project builds:

- the exact `local-<id>` build is loaded from `ProjectLibrary`;
- only successful history may resolve an artifact;
- APK, AAB and Windows artifacts remain bound to that exact Build ID;
- Windows artifact recovery compares the requested ticket identity against
  the persisted `buildOutput`;
- a Portable request cannot resolve a Native artifact;
- a Native request cannot resolve a Portable artifact;
- ambiguous file matches fail closed.

## Legacy Unified Agent Boundary

Legacy Unified Agent history predates Native EXE typing.

Therefore:

- historical generic EXE remains Portable;
- a `native-exe` request against legacy Unified Agent history returns no
  artifact;
- Native is never inferred from a historical generic EXE record.

The presence of `native-exe` in the legacy resolver is a rejection guard, not
a Native classification path.

## Public Downloads Classification

Downloaded Windows artifacts are classified before the generic `.exe`
fallback:

1. `*_windows-native.exe` -> Windows Native EXE
2. `*_windows-portable.exe` -> Windows Portable EXE
3. historical generic `.exe` -> Windows Portable EXE

This ordering preserves legacy compatibility while preventing Native files from
being collapsed into the Portable type.

## Filename Contract

`buildArtifactFileName()` derives the Windows suffix from
`BuildArtifactType`.

- Portable: `_windows-portable`
- Native: `_windows-native`
- both retain the `.exe` extension

The full filename is assembled dynamically, so a complete filename literal is
not required in source.

## Verification

Permanent V2 contract:

- `quality/tests/windows_output_artifact_flow_v2_contract.test.js`
- 8/8 PASS

History recovery contract:

- `quality/tests/device_build_history_resave_contract.test.js`
- 2/2 PASS after stale whitespace-sensitive assertions were converted to
  structural assertions.

Full Quality:

- tests: 814
- pass: 814
- fail: 0

Deterministic Second Brain snapshot freshness also passed before this
documentation update and must be regenerated after this page is added.

## Preserved Boundaries

V2 does not:

- merge PR #61, #63 or #64;
- mutate protected `main`;
- publish to Play Production;
- apply a D1 migration or D1 write;
- deploy Cloudflare;
- modify `appforge-failover`;
- promote Windows Native EXE from EXPERIMENTAL to READY.

## Remaining Acceptance

Source and automated contracts are not substitutes for physical Windows
acceptance.

Still pending outside this V2 source gate:

- Portable EXE Tulpar persistence/relaunch/relocation acceptance;
- Native Windows x64 physical execution acceptance;
- physical Authenticode publisher-signing acceptance.
