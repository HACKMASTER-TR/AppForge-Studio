# S3-Compatible Object Storage

Set:

```env
STORAGE_DRIVER=s3
S3_ENDPOINT=https://...
S3_REGION=us-east-1
S3_BUCKET=appforge
S3_ACCESS_KEY_ID=...
S3_SECRET_ACCESS_KEY=...
S3_FORCE_PATH_STYLE=true
```

Compatible architecture includes AWS S3 and S3-compatible services.

Build inputs are uploaded as objects under:
`inputs/<buildId>/...`

Build outputs are stored under:
`<buildId>/app-release.apk`
or:
`<buildId>/app-release.aab`

Downloads use short-lived presigned URLs.

For local development, keep:
`STORAGE_DRIVER=local`
