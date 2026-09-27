---
type: prompt
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-27
last_verified: 2026-09-27
confidence: high
tags:
  - new-session
related:
  - "[[Agent_Rules]]"
source_files:
  - "AGENTS.md"
---

# New Session Start Prompt

Read `AGENTS.md`, `Hot_Context.md`, `Index.md`, and `Standing_Delivery_Authorization.md`. Identify the task domain, read only the relevant 2-5 additional pages, then inspect the cited source/config/test files. Summarize verified understanding before editing. If the wiki conflicts with evidence, trust evidence and update the wiki only if durable memory changed. Respect any active standing delivery authorization instead of repeatedly requesting the same stage-by-stage approval, while preserving all fail-stop gates.
