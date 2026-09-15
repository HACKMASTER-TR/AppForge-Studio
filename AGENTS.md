# AppForge Studio

## Project safety

- Source code, configuration, migrations, tests, CI, and observed runtime behavior are authoritative.
- Never record, print, or commit credentials, tokens, passwords, signing keys, or real environment values.
- Treat `main` as stable-only. Do not weaken or delete tests to obtain a pass.
- Builds and tests are evidence, not proof of device or production acceptance.
- Commit, push, merge, deploy, release, and publishing require an explicit user request. `scripts/appforge autopilot` is the project's explicit full-delivery command when the user asks for that complete flow.

## FULL AUTOPILOT / FAIL-STOP

`APPFORGE_ADMIN_SESSION=1 ./scripts/appforge autopilot` is the one-shot command for an explicitly requested full delivery chain. It must stop at the first mandatory failure; a failed CI check or active runtime blocker forbids later delivery stages.

<!-- SECOND_BRAIN_RULES_START -->

## Project Memory / Wiki Rules

- `docs/wiki` is the project memory. Write it in English unless the user requests another language.
- For meaningful or ambiguous work, read `AGENTS.md`, `docs/wiki/Hot_Context.md`, `docs/wiki/Index.md`, only 2-5 relevant wiki pages, then the related source/config/test files.
- The wiki is a map, not proof. If it conflicts with source, configuration, migrations, tests, CI, or runtime evidence, trust the authoritative evidence and update the wiki.
- Keep `source_files` focused, project-relative, and existing. Follow relevant imports, schemas, configs, middleware, hooks, tests, and adjacent modules when a task needs more evidence.
- Add memory only when it preserves a durable architecture, decision, integration, status, or reusable problem-resolution lesson. Put uncertainty in `01_Project/Open_Questions.md`.
- Do not update the wiki for minor visual, copy-only, formatting-only, or low-impact changes.
- Preserve ADRs and significant bug/problem records; supersede or archive them instead of deleting them. Large pruning, moving, or deletion requires user confirmation.
- Never store secrets in the wiki. Do not create IDE-specific instruction files unless the user asks.
- `08_Prompts` is only for reusable procedures. Wiki checks are advisory and never pre-commit or pre-push gates.
- After meaningful source changes, use `node scripts/wiki-audit.mjs --changed-source-check` when a wiki drift review is useful.

Detailed procedures: `docs/wiki/08_Prompts/Agent_Rules.md`, `New_Session_Start_Prompt.md`, `Documentation_Update_Prompt.md`, `Wiki_Maintenance_Prompt.md`, `Problem_Resolution_Prompt.md`, and `IDE_Agent_Usage.md`.

<!-- SECOND_BRAIN_RULES_END -->
