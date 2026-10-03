---
type: bug
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-25
last_verified: 2026-09-25
confidence: high
tags:
  - bugs
  - validation
related:
  - "[[Current_Status]]"
source_files:
  - ".appforge/runtime-blockers.json"
  - "quality/tests/appforge_terminal_viewport_stability_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt"
  - "quality/tests/appforge_terminal_mirror_lifecycle_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAgentArtifactClient.kt"
  - "quality/tests/appforge_unified_agent_local_artifact_save_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  - ".github/workflows/android-play-release.yml"
  - "scripts/appforge"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "quality/tests/appforge_terminal_persistent_viewport_workspace_contract.test.js"
---

# Bug Index

The runtime blocker ledger currently has no active blocker.
This page keeps only reusable active/closed regression knowledge; full historical
detail is preserved in [[archive/Bug_Index_History_2026-10-02]].

## Closed / protected regressions

- Terminal native viewport touch ownership and mirror lifecycle regressions:
  physically accepted and contract protected.
- device-build cancellation reader-thread crash: closed and physically
  re-accepted.
- active build UI returning to Ready/0 during lifecycle restoration: closed.
- notification-return build handoff regression: closed physically.
- Python generated-runtime system-bar overlap: closed physically.
- duplicate artifact filename ordering: closed physically.
- Android Gradle artifact selection ambiguity: closed physically.
- Expo SDK54 scoped runtime/build regressions: closed for the accepted family.
- Windows Native CMake scratch and MinGW probe failures: corrected; physical
  Windows x64 executable launch passed.

## Release-sensitive contracts

- historical build re-save must resolve only the exact persisted build/output;
- local artifacts must use the correct public/private Android save path;
- selected project icons must be embedded in project outputs without mutating
  the generic Windows host;
- Portable and Native EXE identities must remain distinct;
- signing failure must remove or withhold the final signed artifact rather than
  silently publishing unsigned output;
- Play Production must remain fail-closed outside its strict release contract.

## Pending physical release checks

These are release gates, not active runtime blockers:

- Portable Windows persistence/relaunch/relocation/crash retest;
- real Authenticode end-to-end signing;
- signed-publisher verification and negative signing tests.

One-off visual defects should not be added here unless they establish reusable
debugging knowledge.
