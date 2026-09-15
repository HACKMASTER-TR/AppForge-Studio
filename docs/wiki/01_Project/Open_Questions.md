---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
confidence: high
tags:
  - open-questions
related:
  - "[[Current_Status]]"
source_files:
  - "build-service/src/fastSigningKey.js"
  - "build-service/tests/fast_signing_key.test.js"
  - ".appforge/runtime-blockers.json"
---

# Open Questions

1. There is no source-verified replacement for the removed privacy or account-deletion product pages. Their historical content is intentionally excluded from the wiki; any new product or legal requirement needs its own evidence and approval.
2. Is the `fast_signing_key.test.js` byte-length failure a stale fixture, an intentional key change, or a source defect? It was not caused by dependency installation.
3. Is BUG-7 still active after the latest APK is installed and manually tested on a physical device?
4. Should the tracked APK and historical `.bak` files be retained, archived outside Git, or removed in a separately approved cleanup?
