---
type: feature
status: active
project: AppForge Studio
created: 2026-10-17
updated: 2026-10-17
last_verified: 2026-10-17
confidence: medium
source_files:
  - "android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java"
  - "android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeSelection.java"
  - "android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/HtmlBinaryManifest.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabProtoManifest.kt"
  - "quality/tests/fast_html_v2_runtime.test.js"
---

# Fast HTML V2

Isolated implementation based on T21 `a4bbd3cae2a6bcc28d4f24303f145fce8dfab972`.
This page records implementation boundaries, not release or physical acceptance.
See `docs/reports/fast-html-v2-full-capabilities-20261017.md` for executed evidence
and the complete remaining-capability matrix.

The CI shell adds foreground, user-mediated SAF file selection (up to 32 files),
text splash, runtime window orientation/fullscreen, write-only clipboard, bounded
haptics and immediate plain-text local notifications. Native actions reuse the
T21 main-frame document capability: origin, token, gesture and foreground checks
remain mandatory. Remote native access still requires its separate opt-in.
File selection is independently gated and does not grant remote native access.
Stale picker results are drained before another picker can start; only content
URIs from user selection are returned, without persistent or write grants.

APK binary XML and AAB protobuf XML prune the fixed shell permission inventory to
exact selected declarations (INTERNET, ACCESS_NETWORK_STATE, VIBRATE,
POST_NOTIFICATIONS, CAMERA, RECORD_AUDIO, ACCESS_COARSE_LOCATION, WAKE_LOCK, NFC
and the existing 12 allowlisted additional declarations). No arbitrary permissions
or components are synthesized. Additional declarations do not create native APIs.
Network-state selection enables a foreground validated-connectivity observer and
the gated AppForgeFastNative.networkState() API. Trusted documents receive
appforge-connectivity events. URL content warns when offline and reloads its
configured HTTPS origin on restoration; stale callbacks are rejected. Monitoring
failures report unknown state rather than inventing connectivity. This observer
does not initialize absent third-party SDKs.
Notification permission requests report PERMISSION_REQUESTED, not SENT. SENT
means NotificationManager dispatch; actual visibility remains OS-controlled.
Background notification scheduling and push remain blocked. Packaged local audio
uses the separate private MediaPlayer/MediaSession mediaPlayback foreground service
with mandatory notification consent, focus/lifecycle cleanup and bounded preparation.
AppForgeMedia controls it explicitly; DOM audio is not automatically transferred.

Static media/document assets retain the existing size/count/path limits. Media
is foreground-only and pauses on Activity pause. PDF/UTF-8 CSV export and the
read-only expiring share provider retain T21 protections and origin namespaces.
The Build confirmation step performs read-only source/template/toolchain checks
on an I/O dispatcher before the user starts the build, then rechecks at use.

Local static Android APK/AAB/BOTH combinations use the same runtime configuration.
AAB/BOTH retain pinned SDK 26/37 and existing signing restrictions. URL wrapping
remains APK-only and does not cache remote services for offline operation.
Windows/mixed outputs retain their own engines. HTTPS APK camera/microphone and
coarse geolocation requests require exact origin, recent tap, explicit per-request
native consent and actual Android permission. Local camera/microphone retain the normal route to preserve established storage
origins. Local one-shot location uses native explicit consent and actual COARSE/FINE
grants, a fresh provider fix and request correlation without changing file origin.
GPS may work offline; watch/background location is not implemented. Pending requests cancel on
pause/navigation and stale system results cannot complete another request.
Static bounded PNG icons replace the pinned compiled drawable in both outputs;
APK paths resolve through the resource table despite AAPT2 path shortening.
Deep-link filters are rewritten or pruned in both manifests; validated intent data
is a document event, never an automatic WebView navigation. Foreground screen/CPU
wake protection releases on pause; the partial CPU lock has a 10-minute cap.
NFC reads one bounded tag ID in foreground, with timeout and stale-result guards.
QR/barcode uses bundled ZXing 3.5.3 and a foreground camera surface; no external
scanner, model download or Google Play Services is needed. Decoded text is an
event, never an automatically opened URL. Hardware acceptance remains separate.
Category auto/none/game and primary colors rewrite actual compiled manifest and
resource slots in both formats, including the resource package namespace. Mixed
content uses COMPATIBILITY_MODE when selected and NEVER_ALLOW otherwise.
Play Billing 9.1.0 is compiled into the shell. It queries real offers and launches
foreground purchases; COMPLETE requires receipt-bound HTTPS backend verification
and successful SDK acknowledgement/consumption. Offline state closes the SDK
connection and warns; network availability alone is never purchase success or SDK
readiness. SDK manifest components are validated, rewritten per package and pruned
when OFF. Products, Play distribution/account and a real verification backend are
external prerequisites. Raw purchase tokens are not exposed to JavaScript/logs.
AdMob, UMP and Firebase SDK integrations remain blocked. No automatic cloud build
fallback was added. Physical and live Play acceptance remain NOT_RUN.

## Modular routing revision

See [[Modular_Offline_V3]] for the central support matrix, explicit normal-engine
selection, precise source findings and selected-module installation boundaries.
This revision does not establish physical Android or live external-service acceptance.
