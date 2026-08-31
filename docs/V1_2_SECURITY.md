# v1.2 Account Security

## Email verification
- Verification tokens are random.
- Only SHA-256 token hashes are stored.
- Tokens expire and become single-use.
- `REQUIRE_VERIFIED_EMAIL_FOR_BUILD=true` can enforce verified email before builds.

## Password reset
- Reset responses do not reveal whether an account exists.
- Reset token hashes are stored instead of raw tokens.
- Tokens are single-use and expire.

## TOTP 2FA
- Uses RFC-compatible TOTP.
- The secret is encrypted with AES-256-GCM before storage.
- Prefer a separate `TOTP_ENCRYPTION_KEY` in production.
- Login first checks the password and then issues a short-lived 2FA challenge.

## Team API tokens
Team owners/admins can create tokens restricted to a team:
`POST /api/teams/:id/api-tokens`

The raw token is shown only at creation time.
