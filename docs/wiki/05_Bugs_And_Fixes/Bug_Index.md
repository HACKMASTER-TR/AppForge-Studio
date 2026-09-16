---
type: bug
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
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
---

# Bug Index

- [[Legacy_Brain_Removal_And_Validation_State]] — active device-runtime blocker, current local test failures, and cleanup-related validation state.

- Terminal native viewport gesture regression — the native Termux `TerminalView` must receive normal one-finger drag/fling input; the Compose transform detector is fallback-only. A source contract test protects this ownership. Device verification remains required.

Document only significant, reusable debugging knowledge. Do not add one-off visual or formatting defects.
