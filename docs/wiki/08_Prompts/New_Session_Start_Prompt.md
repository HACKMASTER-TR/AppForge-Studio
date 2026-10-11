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

Read [[Current_State_Handoff]] early, then validate/check its canonical `01_Project/current_state.json` using `python3 scripts/current-state.py validate` and `check`. Use `show` to recover live Git branch/HEAD separately from recorded evidence. Resolve mismatch and dirty changes before using historical PASS; authenticate remote/physical evidence independently. UNKNOWN, NOT_RUN and NOT_APPLICABLE are distinct from PASS.

Read `AGENTS.md`, `Hot_Context.md`, `Index.md`, and `Standing_Delivery_Authorization.md`. Identify the task domain, read only the relevant 2-5 additional pages, then inspect the cited source/config/test files. Summarize verified understanding before editing. If the wiki conflicts with evidence, trust evidence and update the wiki only if durable memory changed. Respect any active standing delivery authorization instead of repeatedly requesting the same stage-by-stage approval, while preserving all fail-stop gates.

Read [[ChatGPT_Codex_Development_Standard]] before preparing the Codex handoff. Check existing Codex login status; reuse valid authentication. Establish exact target branch/base SHA, worktree HEAD and cleanliness, scope and protected boundaries. Recover the ChatGPT → Codex CLI → independent gate → human authorization handoff from that policy. Report only independently evidenced stages as PASS.
