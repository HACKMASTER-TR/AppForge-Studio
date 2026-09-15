---
type: prompt
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - wiki-maintenance
related:
  - "[[Agent_Rules]]"
source_files:
  - "scripts/wiki-audit.mjs"
  - "scripts/wiki-secret-scan.py"
  - "scripts/wiki-prune-report.py"
---

# Wiki Maintenance Prompt

Run `node scripts/wiki-audit.mjs`, `python scripts/wiki-secret-scan.py`, and `python scripts/wiki-prune-report.py` after important wiki updates or when memory seems stale. Use `--changed-source-check` after meaningful source changes when drift review is useful. These are advisory health checks, not release gates. Preserve ADRs and significant bug history; ask before large cleanup.
