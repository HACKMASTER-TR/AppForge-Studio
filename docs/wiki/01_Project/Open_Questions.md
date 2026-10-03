---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-03
last_verified: 2026-10-03
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

1. Repeat the real Windows Authenticode Portable EXE build with the
   post-sign Native-only validation fix and preserve the final signed artifact.
2. Verify that final signed Portable EXE on real Windows, including embedded
   signature, expected test publisher identity and executable launch.
3. Complete non-admin and missing-provider/certificate fail-closed physical
   signing acceptance.
4. Decide when PR #61, PR #63 and PR #64 are eligible for merge. They remain
   draft/open and no merge is implied by physical or staging acceptance.
5. Review production custom-domain enablement for publisher authorization.
6. Wait for Play Production access approval before any Production release.
7. Reconcile the release-integration branch into protected `main` only after
   the remaining release gates are closed.

## Resolved since earlier checkpoints

- D1 migrations 0001 through 0005 are reconciled.
- Google administrator OIDC and D1 allow-list verification are physically
  accepted against staging.
- Server-verified publisher grant issue/consume/replay protection is physically
  accepted.
- Windows Portable persistence/relaunch/relocation/forced-termination recovery
  physical retest is PASS.
- Physical Authenticode cryptographic signing is PASS.
- DigiCert RFC3161 timestamp verification is PASS.
- Debug-only self-signed publisher signature verification is PASS.
- The Portable post-sign Native x86-64 validator regression is patched and
  covered by regression tests.

## Real Windows Authenticode boundary

Real Windows Authenticode cryptographic execution is now physically proven,
but the complete final-artifact gate is not closed yet because the successful
signed Portable artifact was deleted by the subsequently discovered post-sign
Native-only validation bug.

The patched APK must therefore repeat the physical build and preserve the final
signed EXE before Authenticode end-to-end acceptance can be closed.

## Standing boundaries

- Normal project compilation remains device-local.
- Production publisher endpoint remains disabled.
- Play Production remains untouched unless separately authorized.
- `PRODUCTION_READY=NO`.
