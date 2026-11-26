# AppForge Studio V9 — Stage 5E.5 device-bound administrator P-256 request proofs

**Status: SOURCE ONLY / NOT WIRED / NO LIVE KEY REGISTRATION / NO APK / NO DEPLOY.**

The six files in this stage add proof verification and an Android Keystore reader, not an administrator login or public route. All Stage 5E.4 sources remain unchanged.

## Trust boundaries

1. An Android application MUST have an independently and securely provisioned `AndroidKeyStore` P-256 private key with a trusted alias of the shape `appforge-v9-admin-proof-...`. Its *public* SPKI (DER) fingerprint is SHA-256 lowercase hex (`principalId`). A generated key or an app-supplied SPKI **does not itself authorize** administrator access. Key generation, device attestation, authorized enrollment, audit and revocation require a separate approved workflow. No self-registration mechanism is provided here.
2. `V9AdminKeystoreRequestSigner.kt` **reads an already-provisioned** `AndroidKeyStore` private key, rejects keys exposing `encoded`, and signs the exact `V9AdminProofWire.canonical()` bytes via `SHA256withECDSA`. `V9AdminP256SigningCore.kt` provides the key validation, random 128-bit per-request `proofId`, monotonic no-reuse expectation, and signature formatting. It does not save ID tokens, grant owner status, connect to app navigation, or automatically generate/install/delete a key. Hardware-backed availability and user-authentication requirements are not proven by these source-only tests.
3. `createV9RegisteredAdminProofVerifier` requires independently trusted private Durable Object `storage`. It obtains `s5e5:admin-device:<principalId>` **from trusted server state**, requiring `status=active`, `purpose=admin-operator`, exact `principalId`, a nonexpired `expiresAt`, a pinned P-256 SPKI DER and a SHA-256 fingerprint match. It does not trust headers containing public keys or a claimed `isAdmin`, Google email, or token claims.
4. The server validates a P-256/SHA256 ECDSA signature on the exact HTTP method, pinned HTTPS origin, allowed path, SHA-256 of raw request body, `proofId`, and millisecond issuance timestamp (±30 seconds). Android `Signature.getInstance("SHA256withECDSA")` produces DER; WebCrypto requires P1363 raw `r||s`; the verifier strictly parses DER to 64-byte raw. Malformed and noncanonical encodings are rejected.
5. Stage 5E.4 `createV9PrivateAdminGateway` must independently enforce replay prevention using atomic **durable** `storage.transactionSync()` and a truly trustworthy rate limiter on each request; an identity verifier **alone is insufficient** for anti-replay. The gateway's `sessionId` binding is checked again at the decision endpoint; a revoked key cannot complete a pending session. Stage 5C still independently enforces real Google RS256 `aud`/`iss`/`sub`/nonce and one-use decision signing.
6. A separate privileged provisioning service must populate and revoke the admin-device registry with out-of-band authorization, key attestation as required, least-privilege role governance, audit, bounded replay-state TTL/GC, rollback resistance and per-principal/IP rate limits. This Stage supplies **no write route**, no key enrollment API, no public Worker `fetch` entrypoint or CORS policy, and no remote deployment. A fake in-memory registry in the offline tests is not proof of a secure production provisioning process.

## Test scope

- Node.js: 27 new Stage 5E.5 tests verify P-256 signatures against pinned registered SPKI, DER parsing, tampering, role/status/revocation, key substitution, freshness, gateway consumption and replay. The 70 existing Stage 5A–5D tests and 20 Stage 5E.4 tests must remain green.
- Kotlin/JVM: 20 new ephemeral-key cryptography tests including ECDSA verification, signing-byte vector equivalence with JavaScript, nonce uniqueness, wrong-key/body, P-384 rejection. The existing 18 Stage 5E.4 Kotlin tests must remain green. These JVM tests do **not** run against hardware Android Keystore; a real Android SDK compile and physical device tests remain required.
- Nothing is installed, pushed, deployed, signed for release, or shipped as an APK.

## Release blockers

`REAL_GOOGLE_USER_LOGIN=NOT_VERIFIED` · `AUTHORIZED_ADMIN_DEVICE_ENROLLMENT=NOT_IMPLEMENTED` · `HARDWARE_KEY_ATTESTATION=NOT_VERIFIED` · `REAL_ANDROID_SDK_COMPILE=NOT_VERIFIED` · `CLOUDFLARE_DEPLOY=NO` · `PUBLIC_ADMIN_ENDPOINT=DISABLED` · `PRODUCTION_SIGNER=NOT_CONFIGURED` · `PRODUCTION_READINESS=BLOCKED`.
