# AppForge Studio V9 — Stage 5D-R3: Offline Cron Diagnostic Design

Source guard: `feat/v9-global-package-distribution-20261027` @ `b0dc17c71f6f23fa81c4d1413137053386fd5aa5`.

## Why R3

The prior live run (40901385819) did not prove that two real scheduled invocations occurred. Analytics returned no rows; that **does not prove** no Cron executions. The cleanup integrity gate hashed raw Cloudflare multipart response bytes and erroneously treated representation differences as code drift. R2 later proved two independently fetched canonical multipart hashes match, and pinned Witness version/ETag match. R2 GitHub secret access and read-only Workers/Analytics tests passed (40918819094).

## R3 offline-only payload

- New, strictly separate private diagnostic Worker under `cloudflare/v9-witness/stage5d-r3-probe/`.
- Imports the **unchanged R1 Stage 5D fixture**; does not create production authorization or real Google login.
- Logs `R3_SCHEDULED_RECEIVED`, phase-specific progress, `INITIAL`, restart request, then only after a subsequent successful DO restart/persistence check emits `V9_STAGE5D_R3_LIVE=PASS`.
- Logs controlled failure stage without printing raw errors, stack traces, token or raw response body. Cloudflare itself may separately record an uncaught exception, so error logs still need restricted access.
- Does not expose public Worker route: `workers_dev=false`, `preview_urls=false`, and default fetch handler returns 503.
- Unit tests cover mode rejection, initial phase, successful restart, missing restart signal, state and persistence errors.
- The offline package contains **no live GitHub workflow**, **no auto deploy**, and **no production integration**.

## Live acceptance gates (not yet satisfied)

1. Exact V9 source commit and file-scope check; 106+ existing Node/440 Python suites plus R3 tests, no skips.
2. `wrangler deploy --dry-run` of R3 standalone config. No live changes at this gate.
3. Read-only Cloudflare account auth, Worker list, Witness deployed-version and ETag verification; fail on any existing R1/R3 disposable name.
4. Separate, expressly authorized one-shot GitHub workflow for **disposable R3 Worker only**. No public route or production secret, no change to original Witness.
5. After deployment, read back GET `/accounts/{account_id}/workers/scripts/{worker}/schedules` and require **exactly** `* * * * *`; start tail before waiting. Allow adequate propagation and show evidence of both scheduled passes. Cloudflare says Cron changes can take up to 15 minutes to propagate, and historical dashboard Cron Events can take up to 30 minutes to appear.
6. Test fails closed unless marker `V9_STAGE5D_R3_LIVE=PASS` plus `R3_RESTART_PERSISTENCE=PASS` are both observed from real scheduled invocations; no synthetic tail marker insertion.
7. Strict cleanup trap must delete only the newly deployed disposable Worker, validate 404, then verify original Witness **pinned version/ETag and canonical multipart contents**, not raw multipart bytes. Historical code identity cannot be proved solely by one later ETag; collect baseline before any mutation.
8. Show PASS only if live test, cleanup, and original-Witness integrity all pass. Otherwise `NOT_VERIFIED` and retain safe audit evidence.

## Explicit exclusions

No changes to `main`, V10, Play Production, existing Witness Worker, credentials, signing key custody, Android APK, or real Google OIDC. R3 fixture acceptance alone must never be described as production readiness.

## References

- https://developers.cloudflare.com/workers/configuration/cron-triggers/
- https://developers.cloudflare.com/workers/runtime-apis/handlers/scheduled/
- https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/schedules/
- https://developers.cloudflare.com/analytics/graphql-api/tutorials/querying-workers-metrics/
