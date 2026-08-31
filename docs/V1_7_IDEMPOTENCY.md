# Build Idempotency

Build creation can now include:

`Idempotency-Key: <stable-request-id>`

Supported on:
- multipart `POST /api/builds`
- workspace `POST /api/projects/:id/builds`

The request is fingerprinted using the existing build cache key.

If the same user repeats the same idempotency key with the same fingerprint:
- no second build is created
- the original build ID is returned

If the same key is reused for different content/config:
- HTTP 409 is returned

Default retention:
`IDEMPOTENCY_TTL_HOURS=24`
