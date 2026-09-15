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
- `./scripts/appforge autopilot` is the explicit authorization for commit → push → PR → CI → merge → main CI → deploy → release → publish for that run; no repeated per-stage approval is required.


## Second Brain V3 Final

- `.secondbrain/` is the only active project brain.
- Before meaningful changes run `./scripts/brain all "task"` or at minimum impact + risk + tests.
- Use `./scripts/brain start "task"` before large work and `./scripts/brain finish "task"` after validation.
- Source/config/tests/runtime are authoritative.
- Live state must be verified; never infer GitHub/Railway health.
- Never store secrets, tokens, passwords or credential values in memory.
- `READY`, `REVIEW_REQUIRED`, `BLOCKED`, `ACCEPT`, `REFACTOR_FIRST`, and `REJECT` are advisory engineering decisions.
- Full delivery actions are allowed only through the fail-stop `./scripts/appforge autopilot` command or a separately explicit manual request.


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
- Invoking `./scripts/appforge autopilot` counts as the explicit request for the complete delivery chain; any mandatory FAIL or runtime blocker stops the chain immediately.

Canonical policy:
`.secondbrain/STABILITY_POLICY.md`

Machine-readable rules:
`.secondbrain/REGRESSION_RULES.json`

## FULL AUTOPILOT / FAIL-STOP

- Canonical delivery command: `APPFORGE_ADMIN_SESSION=1 ./scripts/appforge autopilot`.
- `submit` is an alias of FULL AUTOPILOT.
- One invocation authorizes commit, push, PR creation/update, required PR CI, merge, post-merge main CI, applicable production deployment, GitHub Release, and Google Play publishing.
- Never ask for a second approval between those stages.
- Any failed mandatory gate stops immediately; later stages must not run.
- A PR CI failure forbids merge.
- A main CI failure forbids deploy/release/publish.
- An active `.appforge/runtime-blockers.json` entry forbids deploy/release/publish after main validation.
- Failure output must identify stage, classification, failed check/workflow, and a focused failing-log excerpt when available.
- Termux Android/JVM unit execution is `DEFERRED / CI REQUIRED`; it must never be reported as a local Android unit-test PASS.
- External platform review/wait states are `EXTERNAL_PENDING`, not fabricated success.
- Existing HARD STABILITY GATE, fail-0, secret scanning, forbidden-file rules, and no-test-weakening rules remain mandatory.


## GITHUB CLEANUP / POST-MERGE

- Full Autopilot runs conservative GitHub cleanup after merge + required main CI and before deploy/release/publish.
- Never delete an open PR branch or PR record; never rewrite/force/reset `main`.
- Preserve recent successful main evidence, latest failure, current validated main SHA, rollback assets and `AppForgeStudio-latest.apk`.
- Cleanup failure is fail-stop for later distribution and never rolls back already merged code.
