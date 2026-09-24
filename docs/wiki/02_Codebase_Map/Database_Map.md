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
  - postgres
related:
  - "[[Build_Service_Map]]"
  - "[[Security_And_Entitlements]]"
source_files:
---

# Database Map

The service uses PostgreSQL through `src/db.js` and runs 25 numbered SQL migrations. Core tables cover users, API tokens, projects, builds, templates, localizations, publish jobs, teams, job queue/events, workers, auth tokens, build cache/logs/download tickets/idempotency keys, project files/revisions, entitlements, Play purchases, device binding, quota reservations, and client hardening ownership.

Migrations are authoritative for schema history. Do not infer a live database schema or data state from migration files; inspect the authenticated target when a task needs that fact.

Quota and entitlement tables enforce user, project, build, and product rules. Schema changes need corresponding backend behavior and regression tests.
