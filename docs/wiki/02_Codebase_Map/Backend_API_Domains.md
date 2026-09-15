---
type: api
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
  - "[[Build_Service_Map]]"
  - "[[Worker_And_Artifact_Flow]]"
  - "[[Security_And_Entitlements]]"
source_files:
  - "build-service/bootstrap.js"
  - "build-service/server.js"
  - "build-service/src/clientHardening.js"
  - "build-service/src/v5Studio.js"
  - "build-service/tests"
---

# Backend API Domains

`bootstrap.js` installs the client-hardening router before loading the Express composition root in `server.js`. The server route surface includes health/readiness, administration, account and device flows, teams, project workspace/revisions, builds/artifacts, security attestation, Play/entitlement/quota, publish drafts, purchases, and worker operations.

The V5 Studio module owns a separate workspace-oriented backend surface. For a route change, identify the route definition, its imported module, relevant SQL migration, and contract test before documenting or altering a contract.

This page intentionally does not enumerate response shapes or authorization rules. Those details must be read from the route and tests because they are high-change behavior rather than durable index facts.
