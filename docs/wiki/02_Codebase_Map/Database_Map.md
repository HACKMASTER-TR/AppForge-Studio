---
type: database
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - database
  - cloudflare
  - d1
related:
  - "[[Security_And_Entitlements]]"
  - "[[Backend_API_Domains]]"
  - "[[Pro_Code_Lifecycle_Staging]]"
source_files:
  - "cloudflare/control-plane/migrations/0001_accountless_control_plane.sql"
  - "cloudflare/control-plane/migrations/0002_admin_pro_codes.sql"
  - "cloudflare/control-plane/migrations/0003_pro_grants.sql"
  - "cloudflare/control-plane/migrations/0004_pro_grant_revocation.sql"
  - "cloudflare/control-plane/migrations/0005_pro_lifecycle.sql"
  - "cloudflare/control-plane/src/index.mjs"
---

# Database Map

## Current repository-managed database

The current server-side database represented in the active repository is the
Cloudflare D1 control-plane database.

The reconciled migration ledger is exactly:

1. `0001_accountless_control_plane.sql`
2. `0002_admin_pro_codes.sql`
3. `0003_pro_grants.sql`
4. `0004_pro_grant_revocation.sql`
5. `0005_pro_lifecycle.sql`

Do not add, reorder or apply a migration merely to make documentation match
an environment.

## Main schema areas

The active D1 migrations define or extend:

- `installations`
- `play_purchase_records`
- `admin_identities`
- `quota_events`
- `audit_events`
- `pro_activation_codes`
- `pro_admin_grants`
- `pro_installation_keys`
- `installation_challenges`
- `pro_admin_grant_history`
- `pro_redemption_receipts`

The 0004 migration adds administrator revocation attribution to the current
grant table.

The 0005 migration adds challenge-consumption replay protection, historical
grant archival and redemption receipts used as a transactional guard.

## Authority boundaries

A database row, installation ID or locally cached value alone is not proof of
administrator or Pro authority.

Administrator access requires verified Google identity plus an active
`admin_identities` record.

Administrator-issued Pro requires the server lifecycle and device ownership
proof defined by the control-plane contract.

Windows publisher authorization reuses `audit_events`; it introduced no new
D1 migration.

## Historical PostgreSQL warning

The former Build Service PostgreSQL schema and its historical numbered
migrations are retired and are not the active database map.

See [[Build_Service_Map]] for historical context.

Live D1 state must be verified against the authorized target when a task
depends on deployed data. Repository migrations alone do not prove live state.
