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
