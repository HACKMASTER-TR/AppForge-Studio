# v1.5 Android Generator Hardening

## AGP 9 built-in Kotlin

Generated projects no longer apply `org.jetbrains.kotlin.android`.

AGP 9 provides built-in Kotlin support, so generated projects use:
- `com.android.application` 9.1.2
- Java 17 compileOptions
- built-in Kotlin

The obsolete `android.kotlinOptions {}` block has also been removed.

## Local WebView origin

Local HTML is loaded from:

`https://appassets.androidplatform.net/assets/site/index.html`

using AndroidX WebKit `WebViewAssetLoader`.

Generated WebViews disable:
- `allowFileAccess`
- `allowContentAccess`

Local navigation is restricted to the appassets origin. Other HTTP/HTTPS links open in the external browser.

## Remote Native Bridge

For URL-mode apps, JS-to-native bridge access is now disabled unless `allowRemote=true` is explicitly selected.

Use this only for a HTTPS site you fully control.
