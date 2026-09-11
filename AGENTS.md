# AGENTS.md — AppForge Studio Second Brain V2

`.secondbrain/` is the only active project brain.

## Required workflow for meaningful changes

1. Read `.secondbrain/memory/HOT_CONTEXT.md`.
2. Run `./scripts/brain impact`.
3. Run `./scripts/brain risk`.
4. Run `./scripts/brain test-plan`.
5. Run `./scripts/brain plan`.
6. Verify behavior against source/config/tests/runtime.
7. After durable architectural changes run `./scripts/brain sync`.

Or run:

`./scripts/brain all "task-name"`

## Decisions

The risk engine returns:

- ACCEPT
- REFACTOR_FIRST
- REJECT

These are advisory engineering gates, not proof of correctness.

## Safety and integrity

- Source, config, migrations, tests, CI and runtime behavior are authoritative.
- Never store API keys, secrets, passwords, tokens or private credentials.
- Never invent performance or live deployment health.
- Use authenticated live systems to verify current GitHub/Railway/Sentry state.
- Runtime reports are local and not durable project memory.
- Do not commit, merge, push, deploy or publish unless explicitly requested.
