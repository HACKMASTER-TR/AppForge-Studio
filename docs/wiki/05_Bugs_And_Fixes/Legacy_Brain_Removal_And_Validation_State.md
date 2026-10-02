---
type: problem
status: archived
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - legacy-cleanup
  - resolved
related:
  - "[[Bug_Index]]"
  - "[[Current_Status]]"
source_files:
  - "quality/tests/appforge_terminal_owner_only_visibility_contract.test.js"
  - "quality/tests/appforge_terminal_persistent_viewport_workspace_contract.test.js"
  - "quality/tests/retired_backend_absence.test.js"
---

# Legacy Brain Removal and Validation State

## Resolution

The legacy project-memory cleanup is no longer an active release blocker.

The earlier BUG-7 terminal/device validation requirement was completed through
the later terminal acceptance work. Current quality contracts explicitly record
that BUG-7 has no remaining active runtime blocker.

This page is retained as historical debugging and migration context.

## Historical facts

The original legacy-brain cleanup exposed tests that still depended on deleted
documentation and older backend assumptions.

Those dependencies were progressively moved to current source and contract
evidence rather than restoring stale documentation.

The former remote Build Service was subsequently retired as normal project
builds moved to the device-local architecture.

## Current rule

Do not restore deleted legacy project-memory files merely to satisfy an old
test.

When an old test or document conflicts with current architecture:

1. verify the active source path;
2. verify the current contract test;
3. update or retire the stale documentation/test;
4. keep historical context clearly marked as historical.

Current open blockers belong in [[Open_Questions]] and [[Current_Status]], not
in this resolved page.
