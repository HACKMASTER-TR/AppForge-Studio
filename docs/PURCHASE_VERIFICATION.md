# Server-side Google Play Purchase Verification

AppForge Build Service v0.8 includes:

`POST /api/verify-purchase`

Request:

```json
{
  "packageName": "com.example.app",
  "productId": "premium",
  "purchaseToken": "...",
  "productType": "inapp"
}
```

For subscriptions use:

```json
{
  "packageName": "com.example.app",
  "productId": "pro_monthly",
  "purchaseToken": "...",
  "productType": "subs"
}
```

Environment:

```bash
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON=/secure/service-account.json
```

The service account must have the necessary Google Play Developer API permissions for the target app.

Important:
- Never ship service-account credentials inside the Android app.
- Keep purchase verification on a trusted server.
- HTTPS should be used in production.
- Entitlements should only be granted after verification succeeds.
