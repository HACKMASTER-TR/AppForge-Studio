# AppForge Studio V9 — Phase 3H Stage 4C: verified transition -> test witness authorization

**OFFLINE TEST-ONLY.** This stage binds a genuine Phase 3G Ed25519 root/release verification result to the existing Stage 4B one-time signed test authorization. It does **not** deploy a verifier service, harden local SQLite against rollback or grant Android/R2/public distribution permissions.

## Security boundary

1. Obtain existing trusted checkpoint from Stage 4A's local SQLite test witness. This is **not independent hardware or remote trust**; the local DB may be rolled back. Bootstrap is only allowed when generation is zero; a previously aborted attempt blocks implicit rebootstrap.
2. On update, compare the canonical local previous state and its SHA-256 against the witnessed checkpoint. A root key supplied by the caller cannot override a committed checkpoint.
3. Call `appforge_phase3g_integrated_v9.evaluate()` itself with the pinned evidence/root/release metadata and existing checkpoint. Never accept an arbitrary candidate or an unchecked `source=verified` claim in place of Phase 3G.
4. Derive a byte-exact SHA-256 and root/release tuple from the returned canonical candidate. Do not issue an authorization for a same-release replay.
5. Only after successful verification, request a **fixture-signed test authorization**. Copy the candidate fields before handing them to the signing callback so mutation does not alter the recorded next-state tuple. Recheck the witness snapshot and let Stage 4B enforce the one-use permit and SQLite transaction.
6. Return the candidate and pending transaction token to the **offline test**. Stage 4B still requires separate finalize or abort.

## Important limitations and remaining attacks

- The signer callback is supplied by the test harness. This is **not a trusted service identity**; Stage 4C provides no production attestation or independent evidence of Phase 3G execution.
- Stage 4A's `prepare_model(authorize=...)` bypass remains callable in this development API. It must never be exposed as a server endpoint. A separately hardened service API is required.
- The test process can call both verifier and local SQLite witness. Local database snapshot restore can roll back checkpoints and one-use permit history.
- The trust root and witness enrollment remain independent inputs in the test. There is no secure device enrollment, trusted execution, multi-process isolation, network authentication, revocation, durable remote witness, rate limit, deployment or Android key lifecycle.
- An ordinary authenticated signed **claim** cannot replace actual verification. Stage 4C ensures the local bridge runs Phase 3G; it does not prove to an external service that such evaluation occurred.
- Process/file adapter tests do not constitute physical power-failure/flash hardware testing.

## Gate

Run all `test_v9_*.py` Python security tests and `cloudflare/package-distribution/worker.test.mjs`; no skipped tests, resource warnings or failed cases. CI workflow is changed only to list the new Stage 4C tests. Stage 4C may be committed only to the named V9 development branch after exact-diff review. The initial Termux package must never commit, push, dispatch Actions or modify Android APK.

`PRODUCTION_ROOT_KEY=NOT_PRESENT`
`EXTERNAL_WITNESS=NOT_DEPLOYED`
`REAL_TRUSTED_VERIFIER=NOT_CONFIGURED`
`PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`
`R2_UPLOAD=NO`
`PUBLIC_DISTRIBUTION=DISABLED`
`ANDROID_CLIENT_ENABLED=NO`
`ALL_ENGINES_READY=false`
