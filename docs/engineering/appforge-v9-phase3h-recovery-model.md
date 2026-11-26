# AppForge Studio V9 — Phase 3H Stage 1: anti-rollback recovery protocol model

**Status:** offline test-only **state machine**, not a deployed persistent checkpoint, signed witness, secure enclave or Android code.

## Context

Phase 3G performs real Ed25519 signature verification first and returns an in-memory candidate trust state. Phase 3F enforces root and release monotonicity. Phase 3H needs a second, independently trusted component to record accepted high-water state across process restarts and local data rollback. An adjacent file containing a SHA-256 digest is **not** independent authentication.

## Transaction order (simulated)

1. Obtain authenticated committed checkpoint from witness. If unreachable: fail closed.
2. Validate current canonical local state against witness digest/version/sequence. Missing, truncated, replaced or rolled back state: fail closed.
3. Run Phase 3G with a single signed metadata snapshot and the independent checkpoint, producing a candidate. Any failure must leave current state and witness unchanged.
4. Ask witness to **prepare** a compare-and-swap intent (old digest, new digest, root version, release sequence). Witness must authenticate the caller, attest verification authorization and persist the intent durably in any future real implementation.
5. Stage and durably synchronize candidate bytes; atomically swap local state and synchronize the parent directory (NOT implemented in this model).
6. Ask witness to **finalize** the intent. Only then is the high-water state externally committed.
7. Recovery checks witness: pending + old bytes -> abort intent; pending + candidate bytes -> finalize; unknown/ambiguous bytes -> stop. Never bootstrap over an existing state or trust a self-reported local hash.

## Model limitations

- `InMemoryWitness` and `InMemoryDisk` simulate all state in RAM. They cannot survive real process death, app reinstall, Android backup restoration, or external attacker rollback. They make **no production security guarantees**.
- `apply_verified` accepts a callback standing in for Phase 3G. The model alone does not prove that a caller supplied a real signature-verified candidate. 34 model tests validate ordering and recovery; 5 additional signed-bridge tests call the real Phase 3G OpenSSL/Ed25519 evaluator, while the existing 27 Phase 3G tests remain unchanged.
- A future witness needs signed/authenticated replies, device identity binding, replay-resistant compare-and-swap, availability and timeouts, authoritative server-side receipt of cryptographic verification evidence, and a recovery/revocation policy.
- Atomic local writes alone do not protect against state deletion or old snapshot restore. The model does not write any files and does not claim otherwise.
- Do not assume generic Android Keystore monotonic counters; capability depends on device and hardware.
- Offline mode must be explicitly designed. Initial safe option: block any trust advancement without witness; retain verified current state read-only only if its authenticated checkpoint was independently established.

## Acceptance / attack matrix

| Test | Expected |
| --- | --- |
| First verified bootstrap, later update, exact replay | Commit, commit, idempotent |
| Bad signature / wrong checkpoint / witness unavailable | BLOCKED; no advancement |
| Lower sequence, root rollback, root skip, same-version equivocation | BLOCKED |
| Root rotation with changed key and higher version/sequence | Eligible only after signed Phase 3G approval |
| Crash after prepare or stage | Keep previous state, clear pending intent |
| Crash after swap | Finalize matching pending witness intent |
| Crash after witness finalize | Stable new state |
| Missing file, corrupted bytes, unauthenticated witness, conflicting pending | BLOCKED |
| R2/public/Android enable flags | BLOCKED |

## Next gate (Stage 2, not implemented)

Specify authenticated witness API and signed receipts, real filesystem barriers with fault injection, multi-device identity and recovery, independent checkpoint storage, deployable security threat model, and offline policy. Perform hardware-based physical Android acceptance separately **without changing the installed APK** until explicit approval.

## Safety switches

`PRODUCTION_ROOT_KEY=NOT_PRESENT`
`PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`
`EXTERNAL_WITNESS=SIMULATED_NOT_DEPLOYED`
`R2_UPLOAD=NO`
`PUBLIC_DISTRIBUTION=DISABLED`
`ANDROID_CLIENT_ENABLED=NO`
`ALL_ENGINES_READY=false`
