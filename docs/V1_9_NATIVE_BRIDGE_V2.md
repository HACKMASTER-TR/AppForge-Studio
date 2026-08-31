# v1.9 — Secure Native Bridge v2

Generated Android apps no longer expose native methods with:

`WebView.addJavascriptInterface(...)`

The bridge now uses AndroidX WebKit:

- `WebViewCompat.addWebMessageListener`
- exact `allowedOriginRules`
- `WebViewCompat.addDocumentStartJavaScript`

## Origin policy

Local projects:

`https://appassets.androidplatform.net`

Remote URL projects:

Only the exact HTTPS origin derived from the configured URL is allowed.

The server-side config already requires HTTPS in URL mode.

## Main-frame only

Native messages are ignored when they come from a subframe.

## Payload limits

Native messages:
- maximum 64 KB overall

Specific inputs:
- share title: 400 chars
- share text: 40,000 chars
- clipboard copy: 40,000 chars
- vibration: 1–1000 ms
- product ID: 400 chars
- offer token: 8,192 chars
- scanner text event: 8,192 chars

## Clipboard privacy

The old `readClipboard()` bridge method has been removed.

Generated pages can request copying text to the clipboard, but native clipboard contents are not exposed back to page JavaScript.

## Compatibility shim

Generated pages still receive a `window.AppForge` object.

The shim forwards supported actions over `AppForgeNative.postMessage(...)`, so existing calls such as:

```js
AppForge.share("Başlık", "Metin")
AppForge.copy("Metin")
AppForge.vibrate(100)
AppForge.purchase("premium_unlock")
```

continue to use the familiar API surface.

`platform()` and `appVersion()` remain local synchronous helper methods.

## Fail closed

If the installed WebView does not support the required WebMessage feature, the bridge is not downgraded to `addJavascriptInterface`.

Instead, an `appforge-bridge-error` event is dispatched.
