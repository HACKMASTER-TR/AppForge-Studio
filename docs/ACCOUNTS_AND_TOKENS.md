# Accounts and API Tokens

## Register

`POST /api/auth/register`

```json
{
  "email": "user@example.com",
  "password": "strong-password",
  "displayName": "User"
}
```

## Login

`POST /api/auth/login`

## JWT

Use:

`Authorization: Bearer <jwt>`

## Build API tokens

Create:

`POST /api/auth/api-tokens`

The raw API token is returned only at creation time.

Use it as:

`X-AppForge-Key: afs_...`

API tokens are stored as SHA-256 hashes in PostgreSQL.
Passwords are stored using bcrypt.
