---
type: integration
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
  - "build-service/package.json"
  - "build-service/.env.example"
---

# External Services

## Verified from repository metadata

- AWS S3 SDK is a build-service dependency.
- Sentry is a build-service dependency.
- Redis and PostgreSQL clients are build-service dependencies.
- Google APIs are a build-service dependency.
- README documents GitHub and Railway connection support in AppForge Terminal.

## Rules

- Never document real credentials.
- Keep connector permissions minimal.
- Treat credentials, OAuth/token exchange, SSH, and artifact access as security-sensitive.
- Update this page only after verifying the current source/config path.
