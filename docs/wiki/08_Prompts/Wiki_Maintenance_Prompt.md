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
source_files: []
---

# Wiki Maintenance Prompt

Run:

```bash
node scripts/wiki-audit.mjs
python3 scripts/wiki-secret-scan.py
python3 scripts/wiki-prune-report.py
```

Use `node scripts/wiki-audit.mjs --changed-source-check` after meaningful source changes when drift review is useful.

Health:
- Green: no errors, minor/no warnings
- Yellow: warnings without immediate correctness/security risk
- Red: missing core files, secret risk, broken routing, or source/wiki verification errors

Prune duplication and stale noise, but preserve ADRs and significant problem memory.
