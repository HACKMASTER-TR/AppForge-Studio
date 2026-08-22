# Server-Authoritative Pro Entitlements

Database:
`appforge_pro_entitlements`

Entitlement status is no longer written by the Android Settings screen.

Endpoints:
- `GET /api/pro/status`
- `POST /api/pro/activate`
- `POST /api/admin/pro/grant`
- `POST /api/admin/pro/revoke`

`/api/pro/activate` requires:
- authenticated AppForge account
- valid short-lived Integrity session when strict mode is enabled
- Google Play purchase token verified server-side for `STUDIO_PRO_PRODUCT_ID`

Raw purchase tokens are not stored in the entitlement table; only SHA-256 is stored.

Administrative grant/revoke endpoints require an AppForge admin account.
