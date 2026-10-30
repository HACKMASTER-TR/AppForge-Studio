# V9 Stage 5E.2 — Isolated Android server-nonce administrator bridge

State: **LOCAL SOURCE ONLY / NOT WIRED / NO APK / NO DEPLOY**.

- `V9AdminSessionBridge.kt`: pure Kotlin orchestrator, consumes only `V9AdminNonceWire.ServerChallenge` from a trusted session adapter; passes the exact server nonce to Google token source; checks expiration both before and after interactive sign-in; sends sessionId/deviceId/action/token once to a trusted internal decision adapter. No token caching or local owner grant.
- `V9AdminCredentialProvider.kt`: standalone Android Credential Manager adapter using `.setNonce(serverNonce)` from the bridge. Not used by `GoogleAdminIdentityClient` or app navigation. No Android build was requested.
- `V9AdminSessionBridgeOfflineTest.kt`: pure JVM contract checks. Compile `V9AdminNonceWire.kt`, bridge and fixture with `kotlinc` when available. Do not claim that the Android-specific adapter has compiled without an Android Gradle CI run.
- This bridge is **not** an HTTP client. Neither challenge-source transport nor decision-source transport exists here. Future integration requires authenticated origin-bound HTTPS, CSRF and session binding, server-side Google signature/audience/sub/nonce verification, rate limits, transport security, key custody, audit and real Android device acceptance.
- The server MUST use `V9AdminNonceStore.begin()` and `V9GoogleAdminDecisionAuthority.authorizeAndSign()` internally, not grant device authority based on client-provided token claims or local return value. One-use server nonce remains authoritative.
- No real Google account, ID token, issuer signing key, private endpoint, production route or public RPC exists in this offline patch. `main`, V10, Witness, APK, R2, Play Production remain unchanged.
