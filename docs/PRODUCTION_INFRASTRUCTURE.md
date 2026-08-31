# AppForge Production Infrastructure

AppForge Build Service uses PostgreSQL as the authoritative durable build queue and database. Redis is an acceleration and coordination layer: distributed rate limits, metadata cache, and worker wake-up signals. A Redis outage therefore does not delete queued builds; workers can continue polling PostgreSQL.

## Production components

- PostgreSQL: users, projects, build jobs, events, durable queue, authoritative build-cache rows.
- Redis 7.2+ / 8.x: distributed rate limiting, build-cache front cache, low-latency worker wake-up signals.
- S3-compatible object storage: project uploads and APK/AAB/EXE build outputs. AWS S3, Cloudflare R2, MinIO and compatible providers are supported.
- SMTP: verification, password reset and team invite email delivery.
- Sentry: Node/Express/worker error reporting and tracing. Request bodies and sensitive authorization/cookie/API-key headers are redacted before transmission.

## Railway production variables

Set the same `DATABASE_URL`, `REDIS_URL`, storage variables and required flags on every API/worker service that needs them. Never commit these values to Git.

```env
REDIS_URL=redis://...
REDIS_REQUIRED=true
REDIS_PREFIX=appforge
REDIS_CONNECT_TIMEOUT_MS=3000

STORAGE_DRIVER=s3
S3_ENDPOINT=https://<ACCOUNT_ID>.r2.cloudflarestorage.com
S3_REGION=auto
S3_BUCKET=appforge-production
S3_ACCESS_KEY_ID=...
S3_SECRET_ACCESS_KEY=...
S3_FORCE_PATH_STYLE=true

SMTP_HOST=...
SMTP_PORT=587
SMTP_SECURE=false
SMTP_USER=...
SMTP_PASS=...
SMTP_REQUIRED=true
EMAIL_FROM=AppForge <no-reply@your-domain.example>

SENTRY_DSN=...
SENTRY_ENVIRONMENT=production
SENTRY_TRACES_SAMPLE_RATE=0.1
# Optional; Railway commit SHA is used automatically when available.
SENTRY_RELEASE=
```

For AWS S3, leave `S3_ENDPOINT` empty and set the real AWS region. For Cloudflare R2, use the account-specific R2 S3 endpoint and `S3_REGION=auto`.

## Dedicated Source Worker routing

Production source builds use a reserved Source Worker. The Source Worker is
intentionally not an extra normal Android build slot: a worker that advertises
`source-isolation-dedicated` may only claim jobs that explicitly require that
capability. Normal APK/AAB jobs continue to run on the normal Android Worker pool.

The API service is responsible for tagging eligible untrusted `LOCAL` source builds.
Set this routing policy on the production API service:

```env
SOURCE_BUILD_ISOLATION_MODE=dedicated
SOURCE_BUILD_REQUIRE_ISOLATION=true
SOURCE_BUILD_ISOLATION_CAPABILITY=source-isolation-dedicated
```

The dedicated Source Worker image already provides safe defaults for the same
isolation mode/capability. If the hosting platform overrides worker variables,
preserve the dedicated capability:

```env
SOURCE_BUILD_ISOLATION_MODE=dedicated
SOURCE_BUILD_REQUIRE_ISOLATION=true
SOURCE_BUILD_ISOLATION_CAPABILITY=source-isolation-dedicated
WORKER_CAPABILITIES=android-api-37,build-tools-36.0.0,java-17,gradle,source-isolation-dedicated
```

Do not add `source-isolation-dedicated` to normal Android Workers.

The dedicated Source Worker is expected for code-executing `LOCAL` engines such as
`android-gradle`, `node-web`, `flutter`, `react-native-android`, `python-android`,
`android-ndk`, `.NET Android/MAUI`, and `unity-android`. Static `webview-static`
builds remain on the normal Android Worker pool.

Production acceptance:

1. Normal Android APK/AAB builds are claimed by normal Android Workers.
2. An eligible untrusted `LOCAL` source build is tagged with
   `source-isolation-dedicated` when `SOURCE_BUILD_REQUIRE_ISOLATION=true`.
3. Only the dedicated Source Worker claims that tagged build.

If `/health` reports `sourceBuildIsolation.required=false`, the API service is not
tagging source jobs for the reserved Source Worker.

## Health endpoints

- `GET /health`: liveness plus configuration summary, DB status, queue state, Redis state and observability state.
- `GET /ready`: dependency readiness. It checks PostgreSQL, Redis, S3 (when selected), and SMTP (when configured). It returns HTTP 503 when a required dependency is unhealthy.

## Local full-stack smoke

`docker-compose.yml` now provides PostgreSQL, Redis, MinIO, a MinIO bucket initializer and Mailpit. Mailpit UI is exposed on `http://localhost:8025` for local verification emails. The API uses MinIO through the same S3 code path used by production object storage.

```bash
cd build-service
ANDROID_SDK_LICENSE_ACCEPTED=true docker compose up --build
```

Then verify:

```bash
curl -fsS http://localhost:8080/health
curl -fsS http://localhost:8080/ready
```

## Sentry

The Node process starts with `node --import ./instrument.mjs ...`, which initializes Sentry before Express and worker modules are loaded. If `SENTRY_DSN` is empty, Sentry stays disabled without breaking local development.

## Security

- Redis, SMTP, S3 and Sentry credentials belong in the hosting platform's secret/environment variable store.
- Do not put provider secrets in AppForge Studio APKs, generated APKs, Git history, screenshots, support messages or logs.
- S3 objects remain private; AppForge generates short-lived signed URLs for delivery.
- PostgreSQL remains the source of truth for build jobs. Redis is never the sole copy of a build request.
