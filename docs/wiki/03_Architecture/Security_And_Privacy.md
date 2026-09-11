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
  - "build-service/.env.example"
  - "build-service/package.json"
---

# Security and Privacy

## Verified principles

- The README states that project summaries supplied to local AI exclude passwords and Build API keys.
- External connections and build-service dependencies create credential and authorization boundaries that must remain explicit.
- Environment examples may be documented, but real values must never enter project memory.

## High-risk changes

Treat these as security-sensitive:

- token/credential storage
- SSH host-key behavior
- authentication/authorization changes
- upload/download validation
- multi-user workspace isolation
- build artifact access
- shared caches across users

Require source review and tests before changing these areas.
