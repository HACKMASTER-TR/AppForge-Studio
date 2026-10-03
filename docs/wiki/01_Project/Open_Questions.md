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

1. Complete non-admin and missing-provider/certificate fail-closed physical
   signing acceptance.
2. Review production custom-domain enablement for publisher authorization.
3. Wait for Play Production access approval before any Production release.
4. Reconcile the release-integration branch into protected `main` only after
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
- Windows Host CI run `37143822118` passed with the Authenticode-aware payload
  reader and exact staging host pin.
- Exact-head Android Debug run `37144740820` passed.
- Physical build `AF-0000001057` rendered its embedded project on Tulpar after
  signing, without the former payload-signature error; launch counter reached
  `2`.
- `REAL_AUTHENTICODE_END_TO_END=PASS`.

## Real Windows Authenticode boundary

Authenticode cryptographic execution, embedded signer presence, Code Signing
EKU, DigiCert RFC3161 timestamp, signed-artifact preservation and the embedded
AppForge project runtime are physically proven.

The Certificate-Table-aware Windows Host and Android post-sign validation are
accepted. Physical build `AF-0000001057` loaded the project successfully on
Tulpar with no payload-signature error.

`REAL_AUTHENTICODE_END_TO_END=PASS`.

## Standing boundaries

- Normal project compilation remains device-local.
- Production publisher endpoint remains disabled.
- Play Production remains untouched unless separately authorized.
- `PRODUCTION_READY=NO`.
