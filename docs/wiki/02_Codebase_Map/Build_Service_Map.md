---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - backend
  - api
related:
  - "[[Build_And_Worker_Architecture]]"
  - "[[Database_Map]]"
source_files:
---

# Build Service Map

`build-service` is an ESM Node.js service. `bootstrap.js` installs the client-hardening router before loading `server.js`; `server.js` is the Express API composition root and is approximately 5,600 lines.

API domains include health/readiness, administration, registration/login/2FA/device transfer/account deletion, teams and permissions, project workspace and revisions, uploads, builds and artifacts, security attestation, Play entitlement activation, quota add-ons, publish drafts, purchases, and workers.

Core modules are grouped by concern in `src/`: auth, PostgreSQL access, queue/worker runtime, build engines, storage, cache, quotas, Play verification, client hardening, teams, workspace persistence, and observability. Read the relevant route plus its imported module and contract test before altering behavior.

## Retired on 2026-09-23

This page is a historical description only. The Express/SQL/Redis/Worker
Build Service was removed from the active repository after the local device
build cutover. Do not treat the earlier route inventory as live capability.
The `quality` suite and `android-app` runtime are current sources.
