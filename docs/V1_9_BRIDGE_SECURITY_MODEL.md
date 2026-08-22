# Native Bridge Security Model

v1.9 applies multiple independent controls:

1. Native Bridge disabled by default for remote URL mode.
2. Remote bridge requires explicit opt-in.
3. URL mode itself requires HTTPS.
4. WebMessage listener is restricted to one exact origin.
5. Only main-frame messages are accepted.
6. Messages are JSON commands from a fixed allowlist.
7. Input lengths are capped.
8. Unknown actions are rejected.
9. Clipboard reading is unavailable.
10. No fallback to `addJavascriptInterface`.

The allowed-origin filtering is enforced by AndroidX WebKit before bridge messages are delivered to the app, and the generated listener performs an additional source-origin equality check.
