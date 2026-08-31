# Shared Gradle Dependency Cache

v1.3 sets:

```env
GRADLE_CACHE_ROOT=./gradle-cache
```

Workers launch Gradle with:

`GRADLE_USER_HOME=<GRADLE_CACHE_ROOT>`

For multiple workers on the same host or shared persistent volume, this reduces repeated dependency downloads.

Docker Compose mounts:
`appforge_gradle_cache:/gradle-cache`

For geographically separate workers, prefer independent local Gradle caches or a purpose-built artifact proxy rather than a high-latency network filesystem.
