---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
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
Gate validates retained policy and feature contracts. Windows Portable Host has
its own Windows CI path.

Google Play delivery is separate and must be explicitly authorized.

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
