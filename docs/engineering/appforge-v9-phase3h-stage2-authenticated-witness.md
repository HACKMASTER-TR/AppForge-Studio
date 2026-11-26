# AppForge Studio V9 — Phase 3H Stage 2: signed witness receipt (offline verifier)

**Scope: test-only verifier and wire-format acceptance, no server, no persistence, no Android.**

## Trust and crypto contract

- An independently provisioned Ed25519 SubjectPublicKeyInfo DER file and SHA-256 pin identify the witness. A key contained in a receipt must never establish its own trust.
- A response consists of a **canonical JSON envelope** with exactly `payload`, `keyid`, `sig_b64`. Its signature covers the fixed domain `APPFORGE-V9-PHASE3H-WITNESS-RECEIPT\x00` followed by canonical payload bytes. Domain separation prevents reusing release or root signatures.
- The signed payload binds an externally provisioned, pseudonymous 64-character hex **device ID**, a fresh unpredictable 128-bit **challenge**, witness epoch/generation, committed checkpoint, optional pending compare-and-swap intent and expiry. A response belongs to one request and device.
- The verifier rejects duplicate JSON fields, noncanonical wire encoding, wrong key, bad signature, missing/extra fields, boolean numeric impostors, lower witnessed generation, same-generation conflicting state, lower committed release floors, and invalid pending monotonicity.
- Receipt validity is at most 120 seconds; signed `issued_at` may be at most 10 seconds ahead of a **trusted** verification clock. Receipt acceptance depends on clock integrity and on challenges being generated securely per request.
- A `ReceiptVerifier` tracks used challenges and high-water state **in RAM only**; its local cache is not an independent persistent checkpoint. Witness epoch replacement needs separate trusted re-provisioning, never an arbitrary receipt.

## Not yet implemented

The simulator does not provide an authenticated server, caller authorization, durable witness log, transaction consensus, evidence that the witness itself verified the Phase 3G signed metadata, a device identity provisioning workflow, revocation, multi-device trust-domain semantics, trustworthy time, crash-safe file operations, Android Keystore integration, or any production rollback guarantee.

The future authoritative witness service **must independently validate** the signed transition, or cryptographically authenticate a separate trusted verifier's decision. A client signature and signed snapshot alone cannot authorize arbitrary high-water advancement.

## Fail-closed acceptance

Inspect 37 test cases including genuine temporary OpenSSL Ed25519 signature validation, challenge replay, device binding, expired/future receipts, key substitution, signed malicious pending intents, generation equivocation and transition between prepared/finalized stages. Existing Phase 3G and Phase 3H Stage 1 tests are preserved. A new GitHub workflow test pattern is required but does not trigger a CI run merely by pushing to the feature branch.

`PRODUCTION_ROOT_KEY=NOT_PRESENT`
`PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`
`EXTERNAL_WITNESS=SIMULATED_NOT_DEPLOYED`
`SIGNED_WITNESS_RECEIPTS=OFFLINE_TEST_ONLY`
`R2_UPLOAD=NO`
`PUBLIC_DISTRIBUTION=DISABLED`
`ANDROID_CLIENT_ENABLED=NO`
`ALL_ENGINES_READY=false`
