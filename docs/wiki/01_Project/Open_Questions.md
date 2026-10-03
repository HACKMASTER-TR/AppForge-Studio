---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-02
last_verified: 2026-10-02
confidence: high
tags:
  - open-questions
related:
  - "[[Current_Status]]"
  - "[[Windows_Publisher_Authorization]]"
source_files: []
---
# Open Questions

## Active engineering gates

1. Complete a real Windows Authenticode end-to-end physical signing test using
   configured publisher material. Server grant acceptance alone does not prove
   the final Windows signature path.

2. Resume the paused Portable EXE physical relaunch/update persistence retest.
   Crash durability is CI accepted, but the latest physical Windows retest is
   intentionally paused.

3. Decide when PR #61, PR #63 and PR #64 are eligible for merge. They remain
   draft/open and no merge is implied by staging acceptance.

4. Review production custom-domain enablement for publisher authorization.
   Staging is accepted; production routing remains intentionally separate.

5. Review and remove genuinely dead legacy routes only in a separately scoped
   cleanup. Do not mix cleanup with accepted feature branches.

6. Replace or redesign the stale runtime `second_brain_snapshot.json`
   generation model. The source-verified Wiki is authoritative until the
   embedded snapshot has its own reproducible generator and semantics.

## Resolved since earlier wiki checkpoints

- D1 migration history for migrations 0001 through 0005 is reconciled.
- Google administrator OIDC and active D1 allow-list verification are live in
  staging and physically accepted.
- Server-verified publisher grant issue/consume/replay protection is physically
  accepted on Android against staging.
- Builder V25.1 re-entry work is closed.

## Standing boundaries

- Normal project builds remain device-local.
- Play Production remains untouched unless separately authorized.
- Do not infer production readiness from staging acceptance.

## Release Integration V1 — remaining gates

<!-- APPFORGE_RELEASE_INTEGRATION_V1_OPEN_GATES -->

The Release Gap Audit code blockers from PR #62 and PR #63 are closed on the
release integration branch. The original draft PRs remain unmerged.

Remaining release questions/gates:

1. Complete the Windows Portable persistence/relaunch/relocation/crash physical
   retest.
2. Complete real Windows Authenticode end-to-end signing.
3. Verify the signed publisher on Windows.
4. Complete non-admin and missing-provider/certificate fail-closed physical
   signing acceptance.
5. Decide whether and when to enable the production publisher authorization
   endpoint.
6. Wait for Play Production access approval before any Production release.
7. Reconcile final release integration into protected `main` only after the
   remaining gates are closed.
