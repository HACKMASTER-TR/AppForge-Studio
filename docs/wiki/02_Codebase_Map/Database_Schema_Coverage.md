---
type: database
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-09
last_verified: 2026-10-09
confidence: high
tags:
  - database
  - migrations
related:
  - "[[Database_Map]]"
  - "[[Worker_And_Artifact_Flow]]"
source_files:
  - "cloudflare/control-plane/wrangler.example.toml"
  - "cloudflare/control-plane/src/index.mjs"
  - "cloudflare/control-plane/migrations/0001_accountless_control_plane.sql"
  - "cloudflare/control-plane/migrations/0005_pro_lifecycle.sql"
---

# Database Schema Coverage

The former PostgreSQL remote Build Service schema under `build-service/sql/` and its `src/db.js` access layer are historical/retired. They are not current schema authority, and their tables must not be assumed to exist in D1.

Current control-plane migration evidence is under `cloudflare/control-plane/migrations`. The active Cloudflare Worker uses the D1 `DB` binding documented in `cloudflare/control-plane/wrangler.example.toml`. This HTTPS control-plane persistence is separate from device-local compilation and local artifacts.

Migration files prove repository schema intent, not live production state. They do not prove that an authenticated database has been migrated, contains a record, or matches production configuration. Review the relevant migration sequence, consuming Worker source and regression tests when establishing schema behavior.
