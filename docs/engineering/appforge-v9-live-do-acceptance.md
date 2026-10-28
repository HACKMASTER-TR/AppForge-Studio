# AppForge V9 — Live Durable Object disposable probe

**Scope: a one-off test, NOT a public Witness or production release.**

- New, distinct Worker `appforge-v9-do-live-probe-20261028`, `workers_dev=false`, `preview_urls=false`, no route or service binding to the existing V9 Worker.
- Uses the same `WitnessLedger` JS module with a **synthetic, ephemeral** Ed25519 root/release pair and locally generated evidence hash. This does **not** assert acceptance of the pinned production Phase3D evidence or production key custody.
- Cron every minute while test runs; worker has no public endpoint. Logs are observed with `wrangler tail` in one GitHub Actions job.
- Creates real SQLite DO record with two concurrent attempts to `prepare`, exactly one accepted and finalized, checks generation 2 and signed release monotonicity.
- Calls `ctx.abort()` on the test DO to induce a real restart; next Cron checks new isolate identity, durable persisted checkpoint and denial of signed-release replay.
- Probe Worker is **deleted after execution** even on test failure, if the deployment command succeeded. Cleanup status must be inspected in logs; a failed cleanup needs a separate explicit manual review.
- Existing `appforge-v9-witness-isolated-staging` is only checked for presence; no calls, changes, routes, metadata signing, release flags or user operations are applied to it.
- GitHub dev branch only. `main`, Play Production, APK unchanged.
- Time-bound attempt; Cloudflare Cron propagation can delay triggering. Timeout/failed tail is **not** acceptance.
- Passing this test proves Cloudflare DO substrate operates for the staging ledger algorithm. It does **not** prove operator-rollback resistance, Android authentication, production signer or live end-to-end distribution.

`V9_PRODUCTION_READINESS=BLOCKED`
