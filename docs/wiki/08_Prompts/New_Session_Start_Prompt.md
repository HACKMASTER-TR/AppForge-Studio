---
type: prompt
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-08
last_verified: 2026-10-08
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

Read [[ChatGPT_Codex_Development_Standard]] before preparing the Codex handoff. Check existing Codex login status; reuse valid authentication. Establish exact target branch/base SHA, worktree HEAD and cleanliness, scope and protected boundaries. Recover the ChatGPT → Codex CLI → independent gate → human authorization handoff from that policy. Report only independently evidenced stages as PASS.
