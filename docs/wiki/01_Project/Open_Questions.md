---
type: status
status: draft
project: AppForge Studio
created: 2026-09-11
updated: 2026-09-11
last_verified: 2026-09-11
confidence: low
tags:
  - appforge
  - second-brain
related:
  - "[[Index]]"
source_files: []
---

# Open Questions

- What are the current canonical entry points for the web and desktop clients, and which are production-supported today?
- Which database tables/schemas are considered stable contracts for the build service?
- What is the current production topology for API, normal worker, source worker, and Windows worker?
- Which external connections are production-ready versus experimental?
- What build-cache layers are safe to share across users or workers without cross-tenant leakage?
- Which release-health metrics are currently collected in production?
- Which Android screens/components should be split first when reducing `MainActivity` responsibilities?

Resolve these from current source/config/runtime evidence before turning them into high-confidence wiki facts.
