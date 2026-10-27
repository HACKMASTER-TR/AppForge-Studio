# AppForge Studio V9 — Phase 3G: signed metadata + anti-rollback integration

**Scope:** test-only, offline, fail-closed verification; NO production rollout.

## Trust boundary

1. Bootstrap root Ed25519 SPKI DER and SHA-256 key pin must be supplied through an independently trusted channel, not through release metadata.
2. Subsequent transitions require the complete previous canonical state AND an independently authenticated SHA-256 checkpoint. A sibling checkpoint file on the same writable filesystem does not satisfy this requirement.
3. The Phase 3D evidence digest, snapshot and disabled-distribution flags are invariant. A synthetic evidence fixture in a unit test is NOT evidence that the real pinned 650,832,480 bytes were rechecked.
4. ROOT and RELEASE canonical JSON are read **once each** and validated against a single verification clock. Verified in-memory snapshots, not newly read file paths, flow into the Phase 3F policy evaluator.
5. Ed25519 signatures must verify before the candidate transition is computed. The root rotation must be endorsed by both prior and replacement root keys. Release signatures meet the signer threshold and bind the root version, release sequence and Phase 3D evidence.
6. Release sequence must increase, except byte-identical replay. Root version cannot skip or decrease. A root metadata change requires permitted dual-signed key rotation AND newer release sequence.
7. On ANY exception, produce no candidate state. No local state file is written or replaced and no rollback floor is advanced. State rollback resistance after process restart is NOT implemented.
8. R2, public distribution, Android integration, APK changes, production key generation and changes to `main` are forbidden.

## Adversarial matrix

| Scenario | Required outcome |
| --- | --- |
| Valid signed bootstrap | Candidate root v1, sequence 1 |
| Authenticated strictly higher release | Candidate higher sequence |
| Exact byte replay of root and release | Idempotent |
| Lower sequence / root rollback / root version skip | BLOCKED |
| Same sequence but different signed metadata | BLOCKED |
| Altered root/release without new valid signatures | BLOCKED |
| Expired/future metadata | BLOCKED |
| Dual-signed valid root rotation | Candidate newer root version and release sequence |
| Missing old-root authorization / new-root possession signature | BLOCKED |
| Changed root metadata without rotation | BLOCKED |
| Release signer below threshold / untrusted signer | BLOCKED |
| Truncated, missing, malformed or duplicate-key state | BLOCKED |
| Wrong/missing independently trusted state checkpoint | BLOCKED |
| Modified Phase 3D evidence / snapshot | BLOCKED |
| Signed metadata tries to enable R2/public/Android | BLOCKED |
| Failed update with existing state file | Existing file unchanged |
| Metadata swapped between two reads | N/A: single canonical snapshot per input |

Tests create ephemeral local **test** keys using OpenSSL and destroy them in temporary directories. Test-only monkey-patching permits synthetic Phase 3D evidence without changing production pinned constants; production API has no digest-override parameter.

## Local QA

```sh
python3 -B -m unittest discover -s quality/tests -p 'test_v9_*.py' -v
node --test cloudflare/package-distribution/worker.test.mjs
```

**Remaining production blockers:** independent persistent high-water checkpoint / witness; secure first-run root provisioning; crash-safe checkpoint commitment; explicit offline policy; Android threat-model/Keystore capabilities; real independently approved signed releases; and subsequent physical device acceptance.

**Release gates:** `PRODUCTION_ROOT_KEY=NOT_PRESENT`; `PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`; `R2_UPLOAD=NO`; `PUBLIC_DISTRIBUTION=DISABLED`; `ANDROID_CLIENT_ENABLED=NO`; `ALL_ENGINES_READY=false`.
