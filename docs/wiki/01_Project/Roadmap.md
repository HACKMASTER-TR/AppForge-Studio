---
type: roadmap
status: active
project: AppForge Studio
created: 2026-09-11
updated: 2026-09-11
last_verified: 2026-09-11
confidence: medium
tags:
  - appforge
  - second-brain
related:
  - "[[Index]]"
source_files:
  - "README.md"
  - "build-service/package.json"
---

# Roadmap

## Near Term

1. Reduce oversized Android UI/controller responsibilities.
2. Keep Terminal regressions protected with focused tests.
3. Improve build latency using measured cache and incremental-build behavior.
4. Make build/release profiles and failure explanations easier to understand.
5. Standardize external connections and credential boundaries.
6. Add release-health and production observability where missing.

## Architecture Reference Direction

Useful external patterns to evaluate, not copy blindly:

- VS Code: modular IDE/workbench boundaries.
- Expo EAS: build and release experience.
- Turborepo: dependency-aware caching.
- n8n: connector/credential modularity.
- Coder/code-server: persistent isolated workspaces.
- Sentry: release health and observability.

Any adoption must fit current AppForge source and tests.
