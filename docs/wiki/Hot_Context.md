---
type: context
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
  - "README.md"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/AnsiTerminalBuffer.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - "build-service/package.json"
---

# Hot Context

## Current Focus

Keep AppForge Studio reliable while reducing architectural coupling. Major work should preserve the working Android Studio, AppForge Terminal, build-service, Git/SSH/Connections, and release flows before adding new breadth.

## Must Know

- AppForge Studio V5 combines app scaffolding, Android/Windows/Web publishing metadata, build tooling, project-aware terminal features, Git/SSH, and local AI assistance.
- The embedded Terminal has multi-session behavior, file/Git/SSH tools, large scrollback/paste requirements, and regression-sensitive IME behavior.
- Build infrastructure includes API/worker/source-worker roles and multiple GitHub Actions workflows.
- Source code and runtime behavior override wiki memory when they conflict.
- Do not treat a PROOT/architecture toolchain limitation as a source regression without source-level evidence.
- Keep generated binaries, logs, backups, and temporary artifacts out of durable project memory.

## Recent Important Changes

- A previous custom `.appforge-brain` implementation was replaced by this `/docs/wiki` Second Brain system.
- The new memory system follows the uploaded Second Brain Setup v1.3.0 model: AGENTS entry point, routed wiki, advisory audit scripts, secret scan, prune report, and source-file verification.

## Current Risks / Open Questions

- Large Android/Compose surfaces can become tightly coupled; prefer feature/module boundaries.
- Build performance and worker caching need measurement before optimization claims.
- External connection/auth behavior must be verified from current source before changing credential handling.
- Exact web/desktop implementation entry points should be verified when those areas are touched.

## Read Next

- [[Current_Status]]
- [[System_Architecture]]
- [[Important_Files]]
- [[Recurring_Problems]]
- [[Integration_Index]]
