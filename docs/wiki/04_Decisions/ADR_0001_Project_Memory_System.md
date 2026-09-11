---
type: decision
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
  - "AGENTS.md"
  - "scripts/wiki-audit.mjs"
---

# ADR 0001 — Project Memory System

## Status

Accepted.

## Decision

Use the uploaded Second Brain Setup v1.3.0 model:

- short root `AGENTS.md`
- `/docs/wiki` as project memory
- `Hot_Context.md` for fast active context
- `Index.md` for routing
- focused `source_files` for verification
- advisory wiki audit, secret scan, and prune report

## Consequences

The previous `.appforge-brain` implementation is removed rather than maintained in parallel.

Source code remains the source of truth. Wiki checks are advisory unless explicitly promoted to gates later.
