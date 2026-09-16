---
type: bug
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-16
last_verified: 2026-09-16
confidence: high
tags:
  - bugs
  - validation
related:
  - "[[Current_Status]]"
source_files:
  - ".appforge/runtime-blockers.json"
  - "build-service/tests/fast_signing_key.test.js"
  - "build-service/tests/appforge_terminal_integration.test.js"
  - "build-service/tests/appforge_terminal_viewport_stability_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/TermuxTerminalCoreAdapter.kt"
  - "build-service/tests/appforge_terminal_mirror_lifecycle_contract.test.js"
---

# Bug Index

- [[Legacy_Brain_Removal_And_Validation_State]] — device-runtime validation history and cleanup-related validation state.

- Terminal native viewport gesture regression — the native Termux `TerminalView` must receive normal one-finger drag/fling input; the Compose transform detector is fallback-only. A source contract test protects this ownership. On-device verification passed on 2026-09-16.

- Terminal mirror lifecycle regression — copy-mode exit or leaving/re-entering the Terminal screen could recreate the native Termux viewport without restoring visible history, while restart could leave stale mirror scrollback. Source now replays after TerminalView attachment and resets AppForge/native buffers together. On-device verification passed on 2026-09-16.

Document only significant, reusable debugging knowledge. Do not add one-off visual or formatting defects.
