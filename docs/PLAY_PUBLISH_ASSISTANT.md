# Play Console Publishing Assistant

v1.0 adds release draft records.

Create:

`POST /api/publish-drafts`

```json
{
  "buildId": "uuid",
  "track": "internal",
  "releaseName": "1.0.0",
  "releaseNotes": {
    "tr-TR": "İlk sürüm",
    "en-US": "Initial release"
  }
}
```

This v1.0 assistant stores and prepares release metadata but does not automatically publish an app to Google Play.

The reason is intentional: publishing requires Play Console access, app-specific credentials, release-track choices, policy compliance, and explicit ownership/authorization.
