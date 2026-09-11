---
type: api
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
  - "build-service/package.json"
  - "build-service/docker-compose.yml"
  - "build-service/.env.example"
---

# Backend / API Map

The `build-service` package is a Node.js module with distinct commands for:

- API/server
- normal worker
- Unity worker
- source worker

Dependencies include Express, PostgreSQL, Redis, AWS S3 tooling, JWT, upload handling, mail, OTP/QR support, Google APIs, and Sentry.

Use this page as routing only. Before changing an endpoint, auth flow, queue behavior, or persistence contract, inspect the current server/worker source and tests.
