---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-08
last_verified: 2026-10-08
confidence: high
tags:
  - deployment
  - ci
  - cloudflare
related:
  - "[[Test_And_CI_Map]]"
  - "[[System_Architecture]]"
  - "[[Windows_Publisher_Authorization]]"
source_files:
  - ".github/workflows/android-debug.yml"
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/appforge-stability-gate.yml"
  - ".github/workflows/pro-kotlin-feature.yml"
  - ".github/workflows/pro-cloudflare-auth-preflight.yml"
  - ".github/workflows/pro-cloudflare-dry-run.yml"
  - ".github/workflows/pro-cloudflare-staging-deploy.yml"
  - ".github/workflows/windows-portable-host.yml"
  - "scripts/appforge-stability-gate"
---
# Deployment and CI

## Product build architecture

Normal user project compilation is device-local.

Railway, Render, remote Build Service workers, remote Android workers and
autoscaling are retired from the active project-build architecture.

GitHub remains the source and CI authority.

Android Debug validates Android application compilation. AppForge Stability
Gate validates retained policy, feature contracts and offline Cloudflare
control-plane behavioral regressions. Windows Portable Host has
its own Windows CI path.

Google Play delivery is separate and must be explicitly authorized.

## Global backend regression coverage (F17)

On every pull request and push to `main`, the existing `backend-regression`
job retains `npm --prefix quality test` and also runs:

- `node --test cloudflare/control-plane/tests/*.test.mjs`
- `python3 cloudflare/control-plane/tests/pro_lifecycle_sqlite.py`

The first command includes BEHAVIORAL authorization, privilege boundaries,
replay protection, AI gating/provider fallback and malformed-request tests,
plus STATIC_CONTRACT source/schema assertions. Route/module INTEGRATION uses
local injected D1, Google and provider fixtures; it does not prove live services.
The SQLITE/D1_LOCAL harness applies repository migrations only to in-memory
SQLite and checks lifecycle rollback, history and replay constraints. It does
not test remote D1 or apply remote migrations.

Both steps must pass for the existing global stability summary to succeed.
The workflow wiring is CI_ONLY evidence. Neither these commands nor static
contracts establish PHYSICAL_ACCEPTANCE, live deployment or Production readiness.
No production credentials, deploys, signing or Play operations are required.
Mutable action references (F18) remain outside this change.

## Cloudflare control plane

The Cloudflare Worker is security/control-plane infrastructure, not a remote
project-build worker.

Staging deployment uses a protected sequence:

1. read-only Cloudflare token/binding preflight;
2. Wrangler bundle dry-run;
3. exact source review;
4. explicit marker-only deploy commit;
5. controlled Worker deployment;
6. post-deploy health and route checks.

The controlled deployment workflow preserves existing Worker variables and D1
binding and does not automatically apply migrations.

## Publisher authorization deployment — 2026-10-02

The publisher authorization source was first validated without deployment.

Cloudflare-only source commit
`63d32e68e4f97454b0bde79ddde5dcd87e49252e` was then fast-forwarded to the
control-plane feature branch.

Marker-only commit
`7736c3564210727ce75ea13a50124c11a23d36da` triggered the existing controlled
staging workflow.

Run `36971590024` passed:

- explicit deploy request guard;
- Cloudflare credential/binding preflight;
- Wrangler dry-run;
- Worker deployment;
- staging health;
- D1 reachability;
- existing Pro route checks.

`MIGRATIONS_APPLY=NOT_STARTED` and `DATABASE_WRITES=NONE` were recorded by the
deployment smoke test.

A later physical acceptance intentionally wrote only the publisher grant audit
events required by the feature.

## Protection boundaries

`main`, Play Production and `appforge-failover` are not staging deployment
targets.

Feature CI, staging deployment and physical acceptance do not imply permission
to merge a draft PR or publish to Play.

A production custom-domain change requires its own review and acceptance.
