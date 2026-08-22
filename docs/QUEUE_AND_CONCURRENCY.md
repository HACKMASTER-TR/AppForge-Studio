# Build Queue and Concurrency

Environment:

```bash
BUILD_CONCURRENCY=2
MAX_QUEUE_SIZE=100
RATE_LIMIT_PER_HOUR=30
```

The queue:
- accepts build jobs
- limits concurrent Gradle builds
- rejects new jobs when full
- stores build state in PostgreSQL
- keeps user build histories isolated

This prevents many Gradle builds from exhausting CPU/RAM at the same time.
