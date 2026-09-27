---
type: maintenance
status: active
project: AppForge Studio
created: 2026-09-27
updated: 2026-09-27
last_verified: 2026-09-27
confidence: high
tags:
  - governance
  - delivery
  - owner-authorization
related:
  - "[[Agent_Rules]]"
  - "[[New_Session_Start_Prompt]]"
  - "[[Hot_Context]]"
source_files:
  - "AGENTS.md"
  - "scripts/appforge"
  - ".github/workflows/android-debug.yml"
---

# Standing Delivery Authorization

## Owner authorization

On 2026-09-27 the project owner explicitly authorized the normal AppForge
delivery workflow to continue without asking for the same approval at every
individual stage, until that authorization is revoked or replaced.

The standing scope includes:

- local validation and fail-stop quality gates
- commit and push on the active development branch
- GitHub Android Debug CI
- CI artifact verification
- APK download to the device
- APK size and SHA-256 verification
- opening the Android installer
- copying the complete Termux report to the clipboard
- PR and main merge when all required acceptance gates are satisfied
- release and publishing when all required acceptance gates are satisfied
- Google Play Production when all required release and production gates pass
- Cloudflare deployment when the relevant control-plane gates pass
- D1 migration only when migration history is reconciled and the migration is technically safe

## Fail-stop rule

Standing authorization never overrides a failing technical or acceptance gate.

If a mandatory local test, CI run, physical-device acceptance, security check,
migration precondition, release precondition, or production verification fails,
stop the delivery chain at that stage. Diagnose and repair the failure before
continuing later stages.

A build or CI pass is not a substitute for required physical-device acceptance.

## Termux delivery convention

Prefer one pasteable Termux command for each delivery cycle.

The command should, where applicable:

1. verify branch, HEAD, remote and clean-worktree guards
2. run local tests and safety checks
3. commit and push only the intended files
4. run and wait for the exact CI commit
5. include failed CI logs when CI fails
6. verify the exact CI artifact
7. download the APK after successful CI
8. verify APK byte size and SHA-256
9. open the installer
10. write the full report to `~/appforge-last-output.txt`
11. copy the complete report to the Android clipboard
12. keep the outer Termux session open

## D1 special boundary

The existing AppForge D1 migration history is not yet reconciled with all
manually applied staging migrations. Standing authorization does not make an
unsafe `wrangler d1 migrations apply` acceptable.

Reconcile and verify migration history first, then apply only the intended,
reviewed migration using the normal fail-stop delivery chain.
