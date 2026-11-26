# AppForge Studio V9 — Phase 3H Stage 4B: offline signed authorization model

**Status:** development/test-only. No server, network endpoint, live credential,
production root key, durable independent witness, Android client, R2 or release.

## Goal

Exercise a signed authorization boundary in front of the Phase 3H Stage 4A
SQLite transactional witness model. Stage 4A's `prepare_model(... authorize=)`
callback is deliberately not a trust boundary and remains unsuitable for
untrusted clients. Stage 4B introduces an **opt-in, offline-only wrapper**
that verifies genuine Ed25519 signatures on tightly scoped authorizations.
It does **not** turn the underlying SQLite store into an authoritative witness.

## Authorization wire contract (test only)

- Canonical JSON envelope with exactly `keyid`, `payload`, `sig_b64`.
- Signature message is domain-separated with
  `APPFORGE-V9-PHASE3H-STAGE4B-TEST-AUTHORIZATION\x00`.
- Public authorizer key is provisioned **outside** the signed message and
  checked against an externally provided SHA-256 SPKI pin. The payload cannot
  authorize its own signing key.
- Signed payload binds `device_id`, one-use `permit_id`, exact `prior` and
  `next` trust checkpoints, current pinned Phase 3F evidence hash, `issued_at`,
  `expires_at`, a fixed role/schema and explicit test-only source label.
- Maximum validity is 90 seconds, with a trusted-clock dependency and a
  maximum 10-second issued-at future tolerance.
- Before changes, verified authorization must pass monotonically increasing
  release/root policy and independent test-enrollment allowlist checks.

## Atomicity and replay

`OfflineTestAuthorizedWitness.prepare_signed_test()` uses Stage 4A's SQLite
`BEGIN IMMEDIATE` transaction to atomically compare the committed checkpoint,
require no outstanding pending intent, consume an unrepeatable permit ID in
`used_test_permits` and create an intent. Failed transactions roll back both
changes. A successfully used permit remains consumed after abort, finalize and
process reopen. A separate random transaction token is created for settle.

## Explicit UNSOLVED trust problems

1. The `source = PHASE3G_VERIFIED_BY_EXTERNAL_TEST_AUTHORIZER` field is only
   an **assertion signed by a test key**. This prototype does not actually
   attest or independently perform Phase 3G verification. Production must
   independently verify signed metadata or authenticate a genuinely trusted
   verifier's decision before persisting an advancement.
2. The authorizer key is temporary test material; the model does not provision,
   protect, rotate or revoke keys or establish device identity securely.
3. The inherited Stage 4A `prepare_model()` test-only callback remains callable.
   This module MUST NOT be deployed as a remotely reachable service or be
   exposed to an untrusted client.
4. A snapshot restore of the entire SQLite database rolls back both the
   checkpoint and the consumed-permit table. The tests demonstrate this.
   There is no independent durable high-water trust anchor or anti-rollback.
5. There is no network, remote trusted server, TLS/mTLS, process isolation,
   multi-device provisioning or trustworthy clock infrastructure. Local
   compare-and-swap tests do not constitute production consensus.

## Acceptance

- 43 test cases use temporary OpenSSL-generated Ed25519 keys.
- Positive bootstrap/advance/rotation and expected signed checkpoint binding.
- Refuse rogue key, wrong signature/domain, payload tampering, duplicate keys,
  malformed/noncanonical input, replay, wrong device, wrong checkpoint,
  invalid evidence hash, old/future/oversized authorization and unsigned data.
- Concurrent same-permit update has one winner; a failed transaction does not
  consume a permit. Abort/finalize/reopen retain consumed ID.
- Explicitly test local database rollback and the inherited Stage 4A bypass
  to prevent a misleading security claim.
- Existing V9 tests and the Worker offline gate must stay green; CI must
  list the new Stage 4B test file explicitly.

`PHASE3H_STAGE4B_AUTHORIZATION=OFFLINE_TEST_ONLY`
`REAL_TRUSTED_VERIFIER=NOT_CONFIGURED`
`EXTERNAL_WITNESS=NOT_DEPLOYED`
`PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`
`PRODUCTION_ROOT_KEY=NOT_PRESENT`
`R2_UPLOAD=NO`
`PUBLIC_DISTRIBUTION=DISABLED`
`ANDROID_CLIENT_ENABLED=NO`
`ALL_ENGINES_READY=false`
