# AppForge Studio V9 Stage 5C — Google OIDC administrator decision authority (offline)

**SOURCE + OFFLINE CRYPTO TESTS ONLY.** No HTTP gateway, live Google login, production
OAuth configuration, Cloudflare deployment, active Worker, Android APK, production
signer, R2 upload, public release or Play Production modifications.

## Trust model

- `admin-oidc.mjs` validates a **cryptographically signed** Google `RS256` ID token.
  Signing keys must come only from the hardcoded Google HTTPS JWKS address
  `https://www.googleapis.com/oauth2/v3/certs`; never use caller-provided `jku`,
  `jwk`, `x5u`, a decoded user claim, arbitrary HTTPS URL or an unsigned JWT as trust.
- Reject wrong `iss`, `aud`, `azp`, `sub`, `nonce`, `iat`, `exp`, `nbf` or algorithm.
  Configure exactly the intended Google OAuth Web Client ID and an independently
  managed allowlist of **Google `sub` identifiers**, not email addresses. Email,
  `email_verified`, `isAdmin` and Google profile pictures do not authorize devices.
  Key rotation is fail-closed if fresh trusted Google JWKS cannot be retrieved.
- `admin-authority.mjs` generates a **server-owned, single-use** 180-second OIDC
  session nonce in a private Durable Object and binds the Google ID token to it.
  This is a new protocol; the existing Android admin login generates a local nonce
  and is **not yet wired** to the Stage 5C nonce protocol.
- After successful OIDC signature and sub-allowlist checks, consume the nonce
  transactionally before signing. Obtain device state only via private Stage 5B
  registry `snapshot`, never accept requestId/revision/adminId from the caller.
  Sign a Stage 5B 60-second decision permit with a separate **nonexportable**
  Ed25519 private CryptoKey supplied by a trusted internal issuer. A dedicated
  production signer, key provisioning, secret management, rotation and audit are
  **not implemented**. All test signing keys are ephemeral fixtures.
- The Stage 5B registry independently verifies the pinned Ed25519 public key,
  exact device/request/revision, spent decision marker and atomic transition.
  The current Stage 5C uses that unchanged protocol.

## Required before any live admin operation

1. Privileged internal gateway with explicit request/session binding, CSRF/origin
   enforcement and operator rate limits; do not expose these classes via public RPC.
2. Real Google OAuth client ID, Google keys retrieved over trusted HTTPS and safe
   JWKS refresh behavior; operator-verified Google `sub` allowlist.
3. Internal signing key created and stored in a separate trust domain with
   narrow privileges, audit trails and revocation/rotation procedures.
4. Live isolated Durable Object persistence and restart validation for admin
   nonce, Stage 5B registry, approved/revoked device, cross-service failures.
5. Separately audited rollback resistance against a Cloudflare administrator
   restoring or deleting DO state; production receipt trust requires this.
6. Real Android debug endpoint integration and on-device physical acceptance only
   after separate approval. Production main/APK/Play remain unchanged.

Security note: The verifier accepts a trusted JWKS object supplied **only by
trusted internal wiring**. Test fixtures supply synthetic RSA public keys. A
caller-provided JWKS or a caller-defined trusted nonce would destroy the trust
boundary. Production HTTP wiring must never allow those as request fields.

`V9_STAGE5C=SOURCE_ONLY`
`V9_OIDC_REAL_PROVIDER_SESSION=NOT_VERIFIED`
`V9_ADMIN_SIGNER_PRODUCTION=NOT_CONFIGURED`
`CLOUDFLARE_DEPLOYED=NO`
`PUBLIC_WITNESS_API=DISABLED`
`ANDROID_APK_BUILT=NO`
`PRODUCTION_READINESS=BLOCKED`
