# AppForge Studio V9 — Phase 3H Stage 4A

## Transactional witness store: isolated SQLite reference model

**STATUS: OFFLINE TEST-ONLY. NOT an independent, authenticated, remotely deployed witness and NOT production anti-rollback.**

Stage 1 modeled an in-memory prepare/stage/swap/finalize recovery transaction. Stage 2 verified signed witness receipts. Stage 3 exercised POSIX file writes and simulated interruption. Stage 4A models the witness's **database transaction and concurrency behavior**, without claiming that a database in the client's storage domain establishes a trusted high-water floor.

### Scope and wire contract

- `quality/tools/appforge_phase3h_stage4_witness_store_v9.py` provides an opt-in `TestWitnessStore` using Python's standard-library SQLite with `journal_mode=DELETE`, `synchronous=FULL`, `BEGIN IMMEDIATE`, and separate database connections for operations.
- The database file is stored only within an isolated caller-supplied `0700` directory under test. It must be `0600`, owned by the calling UID and non-symlinked on entry. SQLite/hardware power-loss properties are **not** certified by this test.
- Each device identity is a fixed 64-character lowercase hexadecimal identifier; operations target exact device rows using SQL parameters. A successful `prepare_model` writes one pending token and increments generation. `finalize` or `abort` clears that token in a second transaction; only a matching token succeeds. `snapshot` reads pending and committed records for recovery analysis.
- The model rejects invalid checkpoint structure, release rollback, root skip, stale expected committed state, conflicting concurrent prepare and stale finalize/abort. Cross-device rows remain distinct. Unexpected or noncanonical stored JSON fails closed.
- The caller-supplied `authorize` **callback is a TEST STUB, not a trust boundary**. This module does not verify Phase 3G signatures, remotely authenticate a caller, or verify an authoritative release policy. A malicious caller could return true. Therefore this module cannot be put behind a public API or connected to the installed Android app.
- An explicit test demonstrates that restoring an older local SQLite snapshot **successfully rolls back its apparent committed version**. This is a deliberately reproduced vulnerability of same-domain storage. It is not a successful rollback defense. A distinct, independently authenticated witness must prevent such a restore.

### Threat model and Stage 4B requirements

1. An actual witness must hold authoritative state outside the device's rollbackable storage. An app-local SQLite DB, Keystore-protected encryption key, checksum or sibling file does not establish this property.
2. A real service must authenticate the caller/device, bind an immutable client trust domain, and independently validate the signed Phase 3G transition (or cryptographically authenticated approval by a trusted verifier) **before** persisting a prepare intent. Do not accept a boolean, unsigned callback, arbitrary hash, or client self-report as authorization.
3. Persist a strictly monotonic witness generation/epoch with a transactionally consistent intent; provide compare-and-swap and idempotent, challenge-bound signed receipts. Define finalization after client-side durable state swap and stable recovery after connection loss.
4. Service signing keys, release root trust, and device key provisioning must be independent; define key rotation, revocation, backup-restoration, disaster recovery and rollback-resistant database recovery, not merely a single SQLite file.
5. Require durable audit logs, access controls, rate limits, replay protection, trustworthy time, per-device concurrency tests, database outage testing, compromised-client cases, concurrent multi-device policy, and a separate security review.
6. Offline client policy defaults to **no trust advancement** without a current independently authenticated witness. Real Android deployment and on-device hardware capability tests require separate explicit approval.

### Test targets

This Stage 4A module adds **41 isolated tests**. The acceptance target against the Stage 3 pinned commit is **307 Python security tests + 8 Node Worker tests = 315**. There is no GitHub Actions auto-trigger from a feature branch push. Stage 4A installer makes no commit and no push.

**Safeguards:** `MAIN_MODIFIED=NO`, `APK_MODIFIED=NO`, `GITHUB_COMMIT=NO`, `GITHUB_PUSH=NO`, `ACTIONS_DISPATCH=NO`, `PRODUCTION_ROOT_KEY=NOT_PRESENT`, `EXTERNAL_WITNESS=NOT_DEPLOYED`, `PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`, `ANDROID_CLIENT_ENABLED=NO`, `R2_UPLOAD=NO`, `PUBLIC_DISTRIBUTION=DISABLED`, `ALL_ENGINES_READY=false`.
