# AppForge Studio V9 — Cloudflare Workers/Durable Objects witness workbench

**Status: IMPLEMENTED SOURCE + LOCAL TESTS; NOT DEPLOYED; NOT PRODUCTION READY.**

## What this implements

- JavaScript port of Phase 3E/3F canonical metadata, Ed25519 SPKI signatures, signed root rotation, release threshold, expiration, SHA-256 evidence pin, old checkpoint sequence/root floor and disabled distribution flags.
- A SQLite-backed Cloudflare Durable Object **KV** transaction model with independent per-device checkpoints, one pending intent, monotonic generation, one-use signed release metadata per device, CAS conflict protection, commit/abort and crash/restart resilience.
- Worker export with **always-503 HTTP**; no published routes, no R2 binding, no uploaded objects, no production signer and no Android integration.
- Staging-only example Wrangler config with SQLite migration and disabled mode; no command deploys this code.
- Node 22 test coverage using real ephemeral Ed25519 signing keys and in-memory transaction-compatible test storage. These are not Cloudflare live Durable Object storage tests. The test evidence pin is a fixture; actual Worker uses the fixed Phase 3D pin.
- A separate non-deploying GitHub security test workflow targeting the V9 development branch.

## Trust and deployment constraints

A Cloudflare Durable Object persists across object restarts, but Cloudflare PITR and an account operator with write privileges can restore data from an earlier point. Thus DO state by itself is **not** an independently anchored, non-rollbackable high-water mark against administrators. See Cloudflare's SQLite Storage API and PITR documentation.

**No public enrollment or device authentication.** RPC access exists within Workers that explicitly bind the namespace; it is not a user-facing API. Do not expose an RPC bridge without first implementing per-device authenticated challenge-response, revocation, rate limiting, admin audit and fail-closed controls. No private signing key or KMS/HSM is configured; no signed fresh witness receipt is issued, and the Android client must not rely on the unsigned return values. Root metadata in this version retains Phase3E's strict `r2_upload_allowed=false`, `public_distribution_allowed=false`, `android_client_enabled=false` policy.

The `V9StagingWitness` Durable Object class operates only if an operator explicitly sets `V9_STAGING_WITNESS_MODE=OFFLINE_TEST_ONLY` and independently provisions the bootstrap root DER and pin. The example config ships with mode `DISABLED`, `workers_dev=false`, no routes and no R2 binding. **It must remain undeployed until separate permissions and security review.**

The current Python Stage 4D is not replaced by this JavaScript module and does not yet prove cross-language equivalence on all V9 fixtures. The existing 428 offline tests and this new Node suite must both pass in an isolated clone before committing.

## Explicitly unfinished release acceptance

1. True production trust boundary and separate verifier authorization process; verifier has to be secured end-to-end and audited against the Python reference.
2. Immutable, independent external non-rollbackable checkpoint or acceptable formally scoped operator threat model plus restore/failover drill.
3. Secure Android enrollment and fresh Ed25519 receipt verification in an isolated debug client; physical 2-device acceptance.
4. KMS/HSM-backed signer or equivalent non-exportable key protections, key rotation/revocation, authenticated service interfaces, monitoring and independent review.
5. Real Cloudflare staging acceptance with live test account and then a separately authorized production release. No Play Production or GitHub main changes without separate user approval.

`V9_PRODUCTION_READINESS=BLOCKED`
`PUBLIC_WITNESS_API=DISABLED`
`R2_UPLOAD=NO`
`ANDROID_CLIENT_ENABLED=NO`
`ALL_ENGINES_READY=false`
