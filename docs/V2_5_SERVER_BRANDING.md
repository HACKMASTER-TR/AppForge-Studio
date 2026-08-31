# AppForge Studio v2.5 — Server Branding + Free Watermark

Brand:
- product name: `AppForge Studio`
- short brand: `AppForge`
- free-project watermark: `Built with AppForge`

## Server-authoritative branding

The Android client does not decide whether the watermark is shown.

Before the cache key is computed, the official Build Service overwrites `config.branding` using the authenticated account's server-side Pro entitlement.

Free account:

```json
{
  "brand": "AppForge",
  "text": "Built with AppForge",
  "showWatermark": true,
  "nativeOverlay": true,
  "serverEnforced": true,
  "entitlement": "free"
}
```

Pro account sets `showWatermark=false`.

Because branding is part of the config before cache hashing, free and Pro outputs cannot accidentally share the same cached APK/AAB.

## Native watermark

For free outputs the watermark is created as an Android `TextView` on top of the WebView.

It is:
- bottom-left
- compact
- semi-transparent
- rounded
- native Android UI, not HTML/CSS
- added after the WebView
- shifted above the bottom AdMob banner when needed

Website CSS/JavaScript therefore cannot simply hide or delete it.

## Important limitation

An APK owner can still reverse engineer or patch their own binary. No client-side watermark can be mathematically impossible to remove.

The protection here is that the official AppForge server always inserts branding into free builds, while Pro removal depends on server-side entitlement.
