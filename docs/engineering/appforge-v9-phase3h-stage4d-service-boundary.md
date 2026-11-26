# AppForge Studio V9 — Phase 3H Stage 4D narrow test service boundary

**Status: OFFLINE TEST-ONLY**, no network server, separate trusted process,
external witness, protected key store, production anti-rollback or Android integration.

Stage 4C exercises genuine signed Phase 3G verification before a fixture-signed
Stage 4B permit. Stage 4D wraps that offline sequence in a deliberately narrow
caller API: `snapshot(device)`, `prepare(device,evidence,root_metadata,
release_metadata,old_state,now)`, `finalize(device,token)`, `abort(device,token)`.
The caller cannot supply a signing callback, root key or witness store per
request. Trusted test inputs are pinned at construction. The wrapper does **not**
inherit Stage 4A's unsafe test-only `prepare_model(authorize=...)` callback.

The underlying Python object remains discoverable with introspection. Nothing
here constitutes a security boundary against an attacker in the same process,
a deployed independent verifying service, or an authenticated remote API.
Stage 4A SQLite can still be rolled back and the authorizer signing fixture
is held by the test harness; all tests use temporary Ed25519 keys. The external
service must independently run Phase 3G on signed metadata, reject client
claims, provision device identities and verifier keys out of band, authenticate
transport, serialize durable witness transitions with audited operations, and
isolate witness persistence and signing material in distinct trust domains.

Thirty tests cover real Phase 3G signed bootstrap/update/rotation, bad root or
release signatures, denylisting per-call authority/root injection, enrollment,
retry/replay, immutable output copies, token misuse, no direct unsafe inherited
API, error propagation, and closed distribution gates. All earlier V9 tests and
Worker tests must pass without skips or resource warnings.

`PROCESS_ISOLATION=NOT_IMPLEMENTED`
`EXTERNAL_WITNESS=NOT_DEPLOYED`
`REAL_TRUSTED_VERIFIER=NOT_CONFIGURED`
`PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`
`R2_UPLOAD=NO`
`PUBLIC_DISTRIBUTION=DISABLED`
`ANDROID_CLIENT_ENABLED=NO`
`ALL_ENGINES_READY=false`
