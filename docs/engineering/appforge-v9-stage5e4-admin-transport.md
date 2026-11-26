# AppForge Studio V9 — Stage 5E.4 source-only admin HTTP transport contract

**Status: SOURCE-ONLY, NOT DEPLOYED, NOT WIRED, NOT AUTHORIZED FOR PRODUCTION.**

- `stage5e4-admin-gateway.mjs` exports a factory, **not an active `fetch` route**. Construction fails without a private Stage 5D service, transactional durable storage, independent device-proof `authenticate` verifier, trusted `rateLimit` enforcement and a pinned HTTPS `expectedOrigin`. No public route, CORS, or OAuth signer.
- `POST /v9/private/admin/session/begin`: exact empty JSON body and independently verified fresh device request proof. A server-generated 180s-or-less nonce and session ID are privately bound to the verified request-key fingerprint; no email, local owner flag or client JWT claim is authority.
- `POST /v9/private/admin/device/decide`: same device identity proven **again** by a *new* signed request and proof ID; the private binding and request replay state are consumed atomically before calling Stage 5D's real Google RS256/JWKS/audience/allowlisted-sub/nonce verifier and Stage 5B signed permit. The server response contains only device ID and resulting status; ID token is never returned or logged.
- `V9AdminProofWire.kt` defines canonical P-256 signed bytes: UTF-8 `APPFORGE-V9-STAGE5E4-HTTP\0` + `POST\n` + exact expected HTTPS origin + `\n` + absolute path + `\n` + lowercase SHA256 hex of the **exact UTF-8 JSON body bytes** + `\n` + unique 32-lower-hex `proofId` + `\n` + decimal epoch millisecond `issuedAt` + `\n`. Signature format: DER ECDSA over SHA-256, base64url without padding. Clock tolerance: ±30 seconds. A proof ID MUST be unique for every request and persistently replay-protected server-side.
- `V9AdminHttpTransport.kt` is an **uninstantiated** Android adapter for the existing Stage 5E.2 `V9AdminSessionSource` and `V9AdminDecisionSink`. It requires an explicit trusted HTTPS origin and independently supplied Android Keystore request signer. It does not implement a signer, grant owner permissions, cache tokens, register a route, or alter `GoogleAdminIdentityClient`.

## Explicit release blockers

1. No registered/verified **administrator device P-256 public-key store** and no production Keystore proof signer; untrusted `X-AppForge-*` headers MUST NEVER confer identity by themselves. Trusted `authenticate` MUST verify the signature over the complete canonical request, pinned key, proof ID, clock, and method/path/body. Device bootstrap and revocation must be separately authorized.
2. No deployed trusted session gateway, independent per-principal/IP durable rate limiter, audit, CSRF/native threat-model review, signed key custody/rotation and rollback-resistant evidence. Do not attach `createV9PrivateAdminGateway` to the existing staging Witness or public service.
3. No genuine Google account login or operator-verified allowlisted `sub`. Production private Ed25519 signer remains unconfigured. A build succeeding cannot authorize real devices.
4. No Android Activity wiring or physical Android session acceptance. Existing Android admin login remains unchanged.
5. Replay state in this prototype is not pruned; before deployment implement authenticated, bounded cleanup/TTL and account-level abuse controls in a separate implementation.

`S5E4_SOURCE_ONLY=YES` `CLOUDFLARE_DEPLOY=NO` `API_LIVE=NO` `GOOGLE_LOGIN=NOT_VERIFIED` `APK_BUILD=NO` `PRODUCTION_READINESS=BLOCKED`
