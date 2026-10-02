---
type: codebase
status: archived
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - historical
  - retired-backend
  - build-service
related:
  - "[[Build_And_Worker_Architecture]]"
  - "[[Backend_API_Domains]]"
  - "[[Database_Map]]"
source_files:
  - "quality/tests/retired_backend_absence.test.js"
  - "quality/tests/device_only_cutover_contract.test.js"
---

# Retired Build Service Map

## Status

The former Express / PostgreSQL / Redis / remote Worker Build Service is
retired from the active AppForge project-build architecture.

It was removed from the active repository during the device-local build
cutover. This page is retained only so historical links and decisions do not
silently lose context.

Normal Android project builds must not fall back to this retired service.

## Historical architecture

Before retirement, the service used an Express composition root and included
account, project, build, artifact, queue/worker, entitlement, quota and
publishing surfaces backed by PostgreSQL and related infrastructure.

That route and schema inventory is historical evidence only. It is not a
current API contract.

## Current replacements

Project compilation:

`DeviceBuildEngine` and the dedicated device build runtime.

Local artifact access:

`BuildApiClient` plus persisted local build metadata and Android storage APIs.

Security and entitlement control plane:

Cloudflare Worker + D1.

Current architecture details belong in:

- [[Build_And_Worker_Architecture]]
- [[Worker_And_Artifact_Flow]]
- [[Backend_API_Domains]]
- [[Database_Map]]
- [[Deployment_And_CI]]

Do not restore the retired build-service tree or a hidden remote fallback
without a new architecture decision and acceptance cycle.
