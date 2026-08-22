# Build Cache

v1.3 computes a SHA-256 cache key from:

- sanitized build config
- project ZIP hash
- keystore hash
- icon hash
- Firebase config hash

Signing passwords are excluded from the serialized config, while the keystore file hash still participates in the key.

Environment:

```env
BUILD_CACHE_ENABLED=true
BUILD_CACHE_TTL_HOURS=24
```

On cache HIT:
- no Gradle worker is used
- a new successful build row is created
- output references are reused
- `cacheHit=true`

On MISS:
- the job goes to the normal worker queue
- successful outputs are stored in `appforge_build_cache`
