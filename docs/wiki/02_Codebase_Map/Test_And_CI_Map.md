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
  - "build-service/package.json"
  - ".github/workflows/appforge-stability-gate.yml"
  - "scripts/appforge-stability-gate"
---

# Test and CI Map

The backend test command is `npm test` in `build-service`, invoking Node’s test runner over `tests/*.test.js`. The repository includes 297 backend test files, plus Android JVM tests and contract tests that inspect Kotlin, scripts, and configuration.

The stability workflow runs policy checks, installs Node dependencies before backend regression, runs Android JVM tests with JDK 21 and Gradle 9.3.1, and performs static smoke checks. Other workflows build Android artifacts, worker images, conversion smoke, production automation, maintenance cleanup, and autoscaling.

Wiki audit scripts are deliberately advisory and are not part of this gate.

## 2026-09-23 safe test cleanup

Eight duplicate assertions were removed while preserving the corresponding
retirement and active-feature guards. The unreferenced Agent Scale V10
simulation module and its ten tests were removed together after a tracked
consumer check. The expected Node regression baseline is 970 tests, subject
to the local full test run and CI. The retired Build Service still has
interconnected Worker, queue, billing and quota entry points; do not remove
these modules or their regression guards without dependency-level retirement.
