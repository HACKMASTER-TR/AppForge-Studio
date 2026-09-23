---
type: problem
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - legacy-cleanup
  - runtime-blocker
related:
  - "[[Bug_Index]]"
  - "[[Current_Status]]"
source_files:
  - ".appforge/runtime-blockers.json"
---

# Legacy Brain Removal and Validation State

## Severity

High for release readiness; the active device blocker is shipping-blocking.

## Verified facts

- BUG-7 requires a fresh APK device retest of terminal keyboard animation, shortcut placement, command I/O, and logcat stability.
- Initial removal of the legacy `docs` directory exposed a terminal contract test that read `docs/privacy.html`. The test was refocused on direct Android connection, encryption, and configuration evidence; its direct run passed 7/7 on 2026-09-15.
- A backend suite run also exposed missing `adm-zip` dependencies and an independent FAST debug-keystore length mismatch.

## Prevention

- Keep project-memory changes separate from product pages and runtime artifacts; do not restore deleted legacy documentation as wiki evidence.
- Do not report a full regression pass without provisioned dependencies and required device validation.
- Revisit this page after the legal-page decision, a provisioned backend run, and on-device BUG-7 verification.
