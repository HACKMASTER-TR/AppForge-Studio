---
type: database
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - database
  - migrations
related:
  - "[[Database_Map]]"
  - "[[Worker_And_Artifact_Flow]]"
source_files:
---

# Database Schema Coverage

`build-service/sql/` contains the numbered PostgreSQL migration history, while `src/db.js` is the service database access entry point. The migration set covers foundational users/projects/builds, teams/workers, security/storage, workspace/build control, entitlement/integrity, quotas, devices, and client hardening.

Use the relevant migration sequence rather than only `001_init.sql` when establishing schema behavior. A migration is evidence of repository schema history; it does not prove that an authenticated database has been migrated, contains a record, or has matching production configuration.

Schema changes require review of the consuming backend module and regression tests. Keep table-level details in source or task-specific records unless they provide durable architectural value.
