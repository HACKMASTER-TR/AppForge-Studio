# AppForge V9 Stage 5D — disposable private Cloudflare integration acceptance

**Single isolated test deployment, synthetic Google RSA keys and administrator Ed25519 issuer. NOT production.**

## Scope delivered
- Separate `V9PrivateDeviceAdminService` boundary reusing unchanged Stage 5A P-256 proof, Stage 5B enrollment/revocation and Stage 5C Google RS256 ID-token verification and signed one-time admin permit.
- No public fetch, registration write, signer method, JWKS injection by untrusted caller, or self-approval. Server generates unique enrollment, device and administrator challenges.
- Standalone `V9AdminNonceWire.kt` validates Stage 5C server sessions for future Android integration; **existing Android Google login remains unchanged**, and it cannot be used as the Stage 5D protocol without wiring.
- Synthetic end-to-end test performs signed device ownership, pending registration, Google JWT+nonce decision, approval, authenticated action challenge, spent-proof replay rejection, signed revocation and post-revocation denial.
- Separate, disposable Cloudflare Worker with cron, SQLite Durable Object and forced isolate restart checks persisted revoked status and denylist, then removes the disposable Worker via API. No Worker HTTP route, preview URL, workers.dev, R2 or Android artifact.

## Fail-closed release blockers
- **Real Google account login NOT VERIFIED.** Requires genuine configured OAuth Web Client ID, operator-confirmed `sub` allowlist, actual user consent and trusted JWKS retrieval; fixture JWTs do not prove an operator is authenticated. Do not supply Google credentials or real ID tokens in logs.
- **Production administrator signing key NOT CONFIGURED.** The live probe generates ephemeral in-memory keys; real key custody needs isolated signer, least privilege, audit, rotation and recovery.
- **No public Android gateway and no Android physical tests.** HTTPS/CSRF/rate limits, device attestation and session binding must be implemented and reviewed independently. Existing Android login nonce comes from device and is incompatible with Stage 5C server nonce; `V9AdminNonceWire.kt` only supplies validation.
- **No independent high-water trust against Cloudflare account admin rollback**. A Cloudflare admin able to roll back or delete DO state is outside this test's guarantee. Cloudflare cron/script removal may not prove erasure of synthetic DO storage.
- No change to original `appforge-v9-witness-isolated-staging`, `main`, Production or installed APK. Never enable release, signer or public endpoint based solely on this smoke pass.

## CI acceptance
- Prior 17+27+24 device tests remain green, plus Stage 5D synthetic integration tests.
- Exact nine-file push commit scope & parent `be0bc65e8781eee668dacf20e5160f8958452464`. **Push itself never deploys Stage 5D.** The live job is skipped on push, and requires a separate manual `workflow_dispatch` with `confirm_disposable=RUN_PRIVATE_STAGE5D_LIVE` on the same V9 SHA. Manual dispatch against a non-default branch depends on GitHub API/CLI workflow availability; if unavailable, live acceptance stays blocked rather than modifying main.
- GitHub offline device-auth workflow checks **106 exact Node TAP successes and 440 Python V9 successes, no failures or skips**, before a live run may be authorized. Passing the source test suite alone does not authorize live activity.
- Disposable script name includes GitHub run ID and attempt to avoid preexisting-name collisions. Worker cleanup is armed **before** deploy, so a partially successful upload is still investigated and removed. Deletion requires a successful Cloudflare API response and subsequent read proving the target absent.
- Original staging Witness source hash is pinned before the disposable deployment and compared again afterward. A hash mismatch blocks acceptance; unrelated Cloudflare configuration is not independently proven by this pin.
- Require `STAGE5D_LIVE_ACCEPTANCE=PASS`, `STAGE5D_DISPOSABLE_WORKER_DELETED=PASS` and `STAGE5D_ORIGINAL_WITNESS_CONTENT_UNCHANGED=PASS` for accepted smoke. A missing permission or API error is BLOCKED, never PASS.

`REAL_GOOGLE_USER_SESSION=NOT_VERIFIED`  
`PRODUCTION_SIGNER=NOT_CONFIGURED`  
`PUBLIC_WITNESS_API=DISABLED`  
`ANDROID_APK_BUILD=NO`  
`PRODUCTION_READINESS=BLOCKED`
