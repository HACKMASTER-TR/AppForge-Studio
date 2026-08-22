# Team Invitation E-mail

When SMTP is configured, v1.3 automatically sends team invites by e-mail.

The invite still uses:
- a random token
- SHA-256 token hash in PostgreSQL
- expiration
- one-time acceptance
- target e-mail matching

If SMTP is not configured, the raw invite token is returned so a development environment can still test the flow.
