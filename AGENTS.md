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


## Second Brain V3 Final

- `.secondbrain/` is the only active project brain.
- Before meaningful changes run `./scripts/brain all "task"` or at minimum impact + risk + tests.
- Use `./scripts/brain start "task"` before large work and `./scripts/brain finish "task"` after validation.
- Source/config/tests/runtime are authoritative.
- Live state must be verified; never infer GitHub/Railway health.
- Never store secrets, tokens, passwords or credential values in memory.
- `READY`, `REVIEW_REQUIRED`, `BLOCKED`, `ACCEPT`, `REFACTOR_FIRST`, and `REJECT` are advisory engineering decisions.
- Do not commit, push, deploy or publish unless explicitly requested.


## HARD STABILITY GATE V1

This rule is mandatory for every meaningful AppForge change.

- main is stable-only.
- Existing working behavior is a protected contract.
- A new fix or feature may not regress another working feature.
- Before change: evaluate impact, risk and affected tests.
- After change: run stability/regression validation.
- Compile success alone is not runtime proof.
- Skipping/removing/weaking tests to get PASS is forbidden.
- Any mandatory FAIL blocks progression.
- Generated binaries/backups must not be staged.
- Artifacts must be traceable to an exact commit SHA.
- Source/config/tests/CI/runtime remain authoritative.
- Commit/push/merge/deploy/publish require explicit user request.

Canonical policy:
`.secondbrain/STABILITY_POLICY.md`

Machine-readable rules:
`.secondbrain/REGRESSION_RULES.json`
