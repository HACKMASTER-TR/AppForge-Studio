---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-11
updated: 2026-09-11
last_verified: 2026-09-11
confidence: high
tags:
  - appforge
  - second-brain
related:
  - "[[Index]]"
source_files:
  - "README.md"
  - "build-service/Dockerfile.worker"
  - "build-service/Dockerfile.source-worker"
  - ".github/workflows/worker-autoscale.yml"
---

# Caching and Performance

The repository README documents a throughput-oriented Gradle strategy with warm daemons/caches and fallback behavior on constrained workers.

Performance work must be measured.

## Required measurements for meaningful optimization

- build duration P50/P95
- queue latency
- cache hit/miss where available
- worker CPU/RAM pressure
- terminal responsiveness under large output/paste
- regression comparison against a known baseline

## Rule

Do not claim a speedup from code shape alone. Record before/after evidence for major performance changes.
