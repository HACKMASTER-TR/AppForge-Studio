---
type: context
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-16
last_verified: 2026-09-16
confidence: high
tags:
  - hot-context
related:
  - "[[Index]]"
source_files: []
---

# Hot Context

## Current Focus

- Complete the source-verified wiki coverage registry and subsystem maps.
- Keep the wiki independent from build, release, and pre-push gates.

## Must Know

- Android Kotlin/Compose and the Node/Express build service are separate major surfaces.
- Source, configuration, migrations, tests, CI, and runtime evidence override this wiki.
- The application’s former repository-snapshot feature was removed with the legacy brain; local AI and Unified Agent features remain separate.

## Recent Important Changes

- BUG-7 device acceptance passed on 2026-09-16; the active runtime blocker was cleared after the required Terminal regression checks passed.
- AppForge Terminal is owner-only at the Android UI and route boundary. Free and Pro non-owner accounts do not receive a Terminal entry or Terminal navigation.

- Legacy `.secondbrain` files and its command, snapshot, UI, sync, and hard-gate links were removed on 2026-09-15.
- The old `docs` tree remains deleted; no legacy documentation is a source for this wiki.

## Current Risks / Open Questions

- The local backend suite is not green: dependency provisioning and one keystore assertion need separate resolution.
- Existing tracked APK and backup files remain technical-debt candidates; do not remove them without approval.

## Read Next

- [[Current_Status]]
- [[Open_Questions]]
- [[System_Architecture]]
- [[Bug_Index]]
- [[Agent_Rules]]
