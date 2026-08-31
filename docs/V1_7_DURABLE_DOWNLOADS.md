# Durable Download Tickets

Local output downloads no longer rely on an in-memory `Map`.

Tickets are stored in PostgreSQL:
`appforge_download_tickets`

Properties:
- random 256-bit token
- only SHA-256 token hash stored
- one-time use
- expiry
- build ID
- APK/AAB kind
- creating user

This means API restarts no longer invalidate every local download ticket.

S3-compatible outputs continue to use short-lived presigned URLs.
