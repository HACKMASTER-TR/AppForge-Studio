# Build Service Security — v0.9

## API key

Set:

```bash
APPFORGE_API_KEY=long-random-secret
```

AppForge Studio sends it as:

`X-AppForge-Key`

Protected:
- POST /api/builds
- GET /api/builds
- GET /api/builds/:id
- GET /outputs/:buildId/:file

The health endpoint stays public.

## Rate limiting

```bash
RATE_LIMIT_PER_HOUR=30
```

Build creation is limited per authenticated owner/IP.

## Output retention

```bash
OUTPUT_RETENTION_HOURS=72
```

A periodic cleanup removes old output directories.

## Build history

`HISTORY_FILE` stores recent server build metadata. It does not store keystore passwords.

## Production recommendations

- Put the service behind HTTPS.
- Use a reverse proxy.
- Use a strong API secret.
- Prefer per-user API keys and a database for multi-user deployments.
- Do not expose service-account credentials or signing passwords.
