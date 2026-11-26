# AppForge V9 Stage 5B — private device enrollment and revocation model

**Status: SOURCE + OFFLINE SECURITY TESTS ONLY. NOT deployed.** This stage does not create an HTTP API, modify a Cloudflare Worker, generate a production signer, enable distribution, or build an Android APK.

## Device proof and registration state

1. A candidate Android client independently generates a V9-specific Android Keystore P-256 key (Stage 5A). `SHA-256(SPKI DER)` is the pseudonymous 64-hex device ID. `Settings.Secure.ANDROID_ID` is not a V9 authenticator.
2. Private enrollment service issues a 90-second server-generated challenge bound to a valid P-256 SPKI. The signer must sign `APPFORGE-V9-DEVICE-PROOF\0` with `operation=enroll`. Stage 5A verification and strict DER checks are reused, not replaced.
3. Verified proof creates **pending** state only, using a transactionally spent challenge. A device **cannot approve itself**. At most one request wins a race; key fingerprints with any existing pending/active/rejected/revoked state cannot silently reenroll.
4. An **independent, trusted admin authorization service (NOT built yet)** must verify an administrator's identity and authorization, then issue a short-lived Ed25519 **decision permit**. The registry verifies this permit using a trusted, out-of-band pinned public SPKI DER and SHA-256. No public key or `isAdmin` field supplied in a request is authority. Fixture-signed permits in tests are **NOT** real administrator sessions.
5. `approve`: pending rev 0 → active rev 1, and writes `v9-auth:registered:<device>` in a single Durable Object `transactionSync`. Stage 5A challenges can then be issued.
6. `reject`: pending rev 0 → rejected rev 1 (terminal). `revoke`: active rev 1 → revoked rev 2, also updates Stage 5A registration record inside the same transaction. Revoked or previously issued challenges are blocked by Stage 5A's registration rechecks.

## Admin permit contract

Signed detached message is `APPFORGE-V9-ADMIN-DEVICE-DECISION\0` followed by canonical sorted-key JSON and LF. Payload exactly `schema`, `action`, `deviceId`, `requestId`, `expectedRevision`, `decisionId`, `adminId`, `issuedAt`, `expiresAt`. `adminId` is an opaque, pre-authorized administrator identifier **attested only by the external permit issuer**. The source model itself does **not** perform OIDC validation. Issued-at can be at most 10 seconds in the future, max lifetime 120 seconds. The decision ID is persisted as spent; prior state must match signed `requestId` and exact revision. All transitions and spent marker writes are atomic.

## Trust boundaries and blockers

- V9 enrollment registry is a private **model/helper**. It has no `fetch` endpoint, service binding, deployed Worker, real administrator signing service, Google OIDC integration, key rotation or auditable operator interface. A separate private service must validate identity and issue narrowly scoped permits. Never use GitHub/Cloudflare API tokens as the admin permit key.
- Enrollment initiation lacks public rate limiting, abuse prevention, human device ownership checks, or device attestation. Do NOT expose request methods publicly until independently authenticated and rate-limited. Android KeyStore P-256 is proof of key possession, not inherent proof of device trust or Play Integrity.
- Synthetic fixture keys are test-only. Test-memory `kv` is not proof of physical persistence. Stage 5B needs **separate real Cloudflare Durable Object acceptance** after source review; the Stage 5A staging Witness remains disabled.
- A Cloudflare account administrator able to restore Durable Object snapshots might roll back state; independent high-water state is not yet implemented. Receipt signing, production signer custody, device enrollment operator UI, actual Android runtime integration, revocation propagation, and trusted off-Cloudflare restore resistance remain open.
- The current implementation stores request metadata in a private DO and must be subject to retention, data protection and operational audit policy before real users are enrolled.

## Acceptance

Run `node --test --test-reporter=tap cloudflare/v9-witness/device-auth/{protocol.test.mjs,registry.test.mjs}` as separate file invocations, verify all existing V9 suites with zero skips, and ensure GitHub Actions only performs offline tests. Keep `main` and Android APK unchanged; push only to the pinned V9 development branch after exact-file diff review.

`V9_STAGE5B=SOURCE_ONLY`
`REAL_ADMIN_OIDC_ISSUER=NOT_IMPLEMENTED`
`CLOUDFLARE_DEPLOYED=NO`
`PUBLIC_WITNESS_API=DISABLED`
`ANDROID_APK_BUILT=NO`
`WITNESS_RECEIPT_SIGNER=NOT_CONFIGURED`
`PRODUCTION_READINESS=BLOCKED`
