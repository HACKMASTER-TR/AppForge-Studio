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

1. Run the Authenticode-aware Windows Host payload parser on Windows CI.
2. Capture the exact new Windows Host SHA-256 and byte size, then update the
   Android pinned host only from that accepted artifact.
3. Build the exact Android Debug APK and repeat signed Portable execution on
   real Windows, requiring the embedded AppForge project to load successfully.
4. Complete non-admin and missing-provider/certificate fail-closed physical
   signing acceptance.
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

Authenticode cryptographic execution, embedded signer presence, Code Signing
EKU, DigiCert RFC3161 timestamp and signed-artifact preservation are physically
proven.

End-to-end Portable acceptance remains open because Authenticode places its PE
Certificate Table after the existing AppForge overlay. The old Windows Host
treated physical EOF as the AppForge payload end and therefore failed to locate
the project footer after signing.

The local patch is Certificate-Table aware and adds Android post-sign payload
validation. Windows CI plus a new exact-host physical rebuild remain required
before the end-to-end gate can close.

## Standing boundaries

- Normal project compilation remains device-local.
- Production publisher endpoint remains disabled.
- Play Production remains untouched unless separately authorized.
- `PRODUCTION_READY=NO`.
