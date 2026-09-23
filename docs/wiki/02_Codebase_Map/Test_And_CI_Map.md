---
type: codebase
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-23
last_verified: 2026-09-23
confidence: high
tags:
  - tests
  - ci
related:
  - "[[Deployment_And_CI]]"
  - "[[Bug_Index]]"
source_files:
  - ".github/workflows/appforge-stability-gate.yml"
  - "scripts/appforge-stability-gate"
---

# Test and CI Map

The device contract command is `npm --prefix quality test`, invoking Node’s test runner over `tests/*.test.js`. The repository includes 189 device feature contract files, plus Android JVM tests and contract tests that inspect Kotlin, scripts, and configuration.

The stability workflow runs policy checks, executes device contracts using Node 22, runs Android JVM tests with JDK 21 and Gradle 9.3.1, and performs static smoke checks. Other workflows build Android artifacts and Windows Portable Host or validate Cloudflare Pro.

Wiki audit scripts are deliberately advisory and are not part of this gate.

## 2026-09-23 safe test cleanup

Eight duplicate assertions were removed while preserving the corresponding
retirement and active-feature guards. The unreferenced Agent Scale V10
simulation module and its ten tests were removed together after a tracked
consumer check. The expected Node regression baseline is 970 tests, subject
to the local full test run and CI. The retired Build Service still has
interconnected Worker, queue, billing and quota entry points; do not remove
these modules or their regression guards without dependency-level retirement.

## 2026-09-23 retired Build Service

The disconnected remote Build Service and its Node/SQL/Docker/Worker/queue/monthly
quota code were removed together. Active static contracts were migrated to
`quality/tests` without changing their directory depth or their Android,
Cloudflare, Windows and Terminal target assertions. Retired-backend-only
contracts were removed with their targets. Historical failure records remain.
The authoritative acceptance after this migration is the local Node
contract suite, GitHub Android Debug CI and physical device where relevant.
