# AppForge Studio — Second Brain V3 Final

Version: 3.0.0

Second Brain V3 is the single active project intelligence system.

## Capabilities

- Code + dependency graph
- Impact analysis
- Risk engine: ACCEPT / REFACTOR_FIRST / REJECT
- Security V3
- API contract intelligence
- Database + migration intelligence
- Worker / CI topology
- Test intelligence + regression memory
- Git intelligence
- Live GitHub / Railway / build-service health adapters
- Real performance samples with P50/P95 and regression comparison
- Pre-change / post-change checkpoints
- Task memory
- ADR and bug memory
- Architecture drift detection
- Integration and surface maps
- Complexity/refactor candidates
- Release readiness: READY / REVIEW_REQUIRED / BLOCKED
- Root-cause engine
- Memory confidence metadata
- Safe memory pruning
- Ask / Feature / Bug / Release modes
- Machine-readable JSON reports
- Android Second Brain screen
- AppForge AI Second Brain context bridge
- Incremental file cache
- Self-test / Doctor

## Main commands

```text
./scripts/brain all "task"
./scripts/brain doctor
./scripts/brain selftest
./scripts/brain session
./scripts/brain dependency
./scripts/brain api-contracts
./scripts/brain database
./scripts/brain workers
./scripts/brain tests
./scripts/brain integrations
./scripts/brain surfaces
./scripts/brain complexity
./scripts/brain subsystems
./scripts/brain impact
./scripts/brain risk
./scripts/brain security --full
./scripts/brain live
./scripts/brain performance
./scripts/brain release-check
./scripts/brain ci-plan
./scripts/brain start "task"
./scripts/brain finish "task"
./scripts/brain feature "feature"
./scripts/brain bug "problem"
./scripts/brain ask "question"
./scripts/brain root-cause path/to/log.txt
./scripts/brain perf-record app_build 12345
./scripts/brain test-record PASS "tests passed"
./scripts/brain ui-data
./scripts/brain ai-context
./scripts/brain prune
./scripts/brain prune --apply
```

## Truth rules

Source code, configuration, migrations, tests, CI and runtime behavior remain authoritative.
Secret values are never stored in durable Second Brain memory.
Unknown live or performance state is never invented.
Commit / push / deploy / publish require explicit user request.


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
