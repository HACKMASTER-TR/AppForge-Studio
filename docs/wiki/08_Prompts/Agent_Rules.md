---
type: prompt
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
---

# Agent Rules

## Memory Quality Gate

Before writing to the wiki, ask whether the information is durable, useful in a future session, and verifiable. If not, do not add it.

## Token Budget Rule

Read `AGENTS.md`, `Hot_Context.md`, `Index.md`, then only 2-5 relevant pages. Do not scan the whole wiki by default.

## Source of Truth

Source code, config, tests, migrations, and runtime behavior win every conflict. Wiki is memory and routing.

## Wiki-Source Verifiability Rule

Implementation pages must use focused project-relative `source_files`. Follow imports/tests/config only when verification requires it.

## Wiki Drift Check

After meaningful source changes, review whether referenced wiki memory became stale. `--changed-source-check` is advisory.

## Append-Only Safety Rule

Do not silently erase durable ADRs, significant bug history, or problem-resolution memory. Supersede or archive.

## Problem Resolution Memory Rule

Document solved problems only when the root cause/fix/verification will save future debugging time.

## ADR Lifecycle Rule

Keep accepted decisions. Mark outdated decisions superseded and link the replacement.

## Wiki Mode Upgrade Rule

Start with the smallest useful structure. Add pages only when project complexity justifies them.

## 08_Prompts Size Control Rule

This folder contains reusable procedures only, not project facts or task notes.

## Session Wrap-Up Check

At the end of meaningful work, update durable memory only if architecture, status, decisions, recurring problems, integrations, or roadmap knowledge changed.
