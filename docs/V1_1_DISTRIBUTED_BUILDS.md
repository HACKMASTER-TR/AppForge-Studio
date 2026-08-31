# AppForge v1.1 — Distributed Builds

v1.1 moves build scheduling from an in-memory queue to PostgreSQL.

## Why

An in-memory queue disappears when the API restarts and cannot be shared by multiple machines.

v1.1 uses `appforge_build_jobs` with:

- `FOR UPDATE SKIP LOCKED`
- worker ownership
- heartbeat
- stale worker recovery
- retry count
- retry delay
- priority
- persistent queue state

## Simple mode

```env
RUN_INLINE_WORKER=true
BUILD_CONCURRENCY=2
```

The API process also executes builds.

## Distributed mode

API:

```env
RUN_INLINE_WORKER=false
```

Worker:

```bash
npm run worker
```

Multiple worker processes can run against the same PostgreSQL database.

## Shared input/output

For separate hosts, `SHARED_INPUT_ROOT` and `OUTPUT_ROOT` must point to storage visible to all build workers.

A network filesystem, persistent volume, or a future object-storage backend can provide that shared storage.
