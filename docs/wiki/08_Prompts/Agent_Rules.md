---
type: prompt
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-27
last_verified: 2026-09-27
confidence: high
tags:
  - agent-rules
related:
  - "[[Index]]"
source_files:
  - "AGENTS.md"
---

# Agent Rules

## Memory Hierarchy

1. `AGENTS.md`
2. `Hot_Context.md`
3. `Index.md`
4. relevant 2-5 pages
5. related source/config/test files

## Strict Rules

- Stay within the project root and do not store secrets.
- Source is authoritative; the wiki is routing and durable memory, not proof.
- Use focused, existing project-relative `source_files` and follow relevant dependencies only when needed.
- Keep prompts reusable and do not create IDE-specific files by default.
- Preserve ADRs and significant problem history. Supersede or archive; do not silently delete.
- Commit, push, deploy, release, merge, and publishing require explicit user authorization. A currently active standing authorization recorded in [[Standing_Delivery_Authorization]] counts as that explicit authorization within its documented scope until the owner revokes or replaces it.
- Standing authorization never bypasses fail-stop tests, CI, physical acceptance, security checks, migration preconditions, or production gates.

## Memory Quality and Token Budget

Write only durable knowledge that reduces repeated investigation, preserves a decision, or prevents a recurring failure. Put weak evidence in [[Open_Questions]]. Do not delete useful facts to save tokens; reduce duplication and unnecessary reading instead.

## Wiki Drift and Problem Resolution

After meaningful source changes, decide whether durable memory changed and use the changed-source audit when useful. Record significant solved problems with symptom, cause, fix, verification, prevention, and source files. Large pruning needs user confirmation.

## Decision and Evidence Records

Create an ADR only for a future decision with verified context, alternatives, decision, consequences, and source/test evidence. Do not reconstruct historical intent from implementation alone. A bug or problem record must state its evidence, impact, verification state, and prevention; unresolved or weakly evidenced claims belong in [[Open_Questions]].
