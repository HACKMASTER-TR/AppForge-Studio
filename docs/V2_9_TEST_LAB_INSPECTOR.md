# AppForge Studio v2.9 — Test Lab + Inspector

v2.9 turns Preview and Production Center into a developer-quality inspection workflow.

## Preview Inspector

Tabs:
- Preview
- Console
- Network
- Performance
- Security

### Console
Captures JavaScript console messages with level, source and line number.

### Network
Captures WebView requests:
- HTTP method
- URL

The preview does not proxy or modify the response.

### Performance
Collects browser Navigation Timing / Resource Timing summary:
- DOMContentLoaded
- page load
- transfer bytes
- decoded bytes
- resource count

### Security
Reuses AppForge production checks while previewing.

## Test Lab

Server endpoint:
`GET /api/builds/:id/test-lab`

For successful APK/AAB outputs it analyzes the ZIP structure and reports:
- package artifact size
- uncompressed size
- entry count
- file categories
- largest files
- SHA-256
- server-side security checks

## Build Compare

Endpoint:
`GET /api/builds/compare?left=...&right=...`

Reports:
- config differences
- APK size delta
- AAB size delta
- redacted sensitive fields
- release-note suggestions

## Release Notes

Endpoint:
`GET /api/builds/:id/release-notes`

Compares the build with the previous successful build for the same package.

## PWA Inspector

Production Center detects:
- manifest.webmanifest
- compatible manifest.json
- service worker files
- PWA name
- start_url
- display mode
- theme color
- icon count

## Native Module Center

Production Center surfaces the existing AppForge runtime modules:
- Native Bridge
- camera
- location
- QR/barcode
- share
- clipboard
- vibration

## Storage hardening

v2.9 also fixes two infrastructure issues:
- S3 GetObject materialization prefers streaming instead of buffering the whole object
- local input move falls back from rename to copy/unlink on cross-filesystem EXDEV
