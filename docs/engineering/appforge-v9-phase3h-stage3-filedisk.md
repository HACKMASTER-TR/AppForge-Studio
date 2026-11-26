# AppForge Studio V9 — Phase 3H Stage 3: POSIX crash-injection file adapter

**Status:** isolated development-only file adapter; **not** a deployed persistent anti-rollback implementation. The signed witness in Stage 2 has no deployed service, and Stage 1 witness is still simulated entirely in RAM.

## Scope

- `FileDisk` operates on a caller-created, owner-controlled `0700` POSIX directory, using a pinned directory file descriptor, `O_NOFOLLOW`, `O_EXCL`, private `0600` state files, and hardlink rejection.
- A canonical Phase 3F candidate is staged exclusively, file data synchronized with `fsync`, then the parent directory synchronized. Swap uses `os.replace` within the same directory and synchronizes the directory again.
- The Stage 1 recovery model can be connected to the file adapter. On process-level interruption, recovery against the **same surviving simulated witness** may abort a pending update or finalize a swapped candidate.
- Tests exercise stage, swap, file loss, malformed state, bad mode, symlinks, hardlinks, missing witness, mismatched checkpoint, simulated interruption at four protocol steps, and process termination immediately after stage/swap.

## Explicit limitations

1. No truly external authoritative witness, no durable witness data, no real multi-process lock, no adversarial concurrent writer protection and no production signing keys. A same-directory checksum is never sufficient to establish a trust floor.
2. This code is **not** used by the Android application and must never be pointed at production state. The local file adapter cannot stop rollback by a storage attacker and cannot provide a universal guarantee under actual device power failure or filesystem/hardware reordering.
3. The process-exit tests use controlled exit after the storage calls return; they are not equivalent to injecting power loss during individual writes, `fsync`, or rename. More exhaustive fault injection, subprocess concurrency, and per-device platform testing remain necessary.
4. Stage 2 signed receipt validation does not prove the remote witness authorized or durably recorded any transition. A real witness must independently verify Phase 3G evidence or authenticate a trusted verifier's authorization.
5. All existing Phase 3E, 3F, 3G, 3H Stage 1 and Stage 2 tests must keep passing; GitHub Actions and `main` are not modified outside adding the Stage 3 test pattern to the existing debug workflow gate.

## Tests

```sh
python3 -B -m unittest discover -s quality/tests -p 'test_v9_phase3h_filedisk.py' -v
python3 -B -m unittest discover -s quality/tests -p 'test_v9_*.py' -v
node --test cloudflare/package-distribution/worker.test.mjs
```

## Release gates

`PRODUCTION_ROOT_KEY=NOT_PRESENT`
`PERSISTENT_ROLLBACK_STATE=NOT_IMPLEMENTED`
`EXTERNAL_WITNESS=SIMULATED_NOT_DEPLOYED`
`R2_UPLOAD=NO`
`PUBLIC_DISTRIBUTION=DISABLED`
`ANDROID_CLIENT_ENABLED=NO`
`ALL_ENGINES_READY=false`
