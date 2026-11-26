# HTML/HTTPS APK generation and PDF export

The quick route packages the CI-built `html-apk-template` APK/AAB shell. It does
not download or execute SDK/JDK/Gradle/Node on the user's device. Normal static
and npm-built projects use the generated `FastActivity` Gradle wrapper. Both
routes now include the same `AppForgeDocuments` and `AppForgeLocalAssets` Java
helpers, with package names adjusted for their respective shells. A regression
requires their implementations to stay identical.

## Document contract

The project ZIP and imported source files are never changed. Each route retains
its pre-T19 storage namespace: normal node-web/microphone wrappers use
`https://appassets.androidplatform.net/assets/site/…`; plain normal HTML and
Quick HTML retain their file namespace. Quick HTML retains exactly
`file:///android_asset/site/…` and its existing entry path/URI builder. There is
no storage migration, clearing or profile relocation. Keeping the original URL
preserves access to existing localStorage and IndexedDB namespaces.

Normal HTTPS asset responses insert the document shim before application
scripts, preserving HTML bytes and doctype. Both file-based paths inject after page
completion, because Android asset file URLs cannot receive an exact HTTPS-origin
message port. Startup feature detection of document sharing is therefore not
guaranteed in either file-based path. Quick HTML’s sole annotated interface accepts only a bounded
message and a private per-navigation capability. It schedules validation on the
UI thread and exposes no file reading or general device API. Quick HTML native file access,
file-to-file and universal access remain disabled; Android packaged assets remain
available. The normal wrapper retains its original file-access configuration. The normal wrapper retains its separate synchronous compatibility
dispatcher guarded by capability, trusted origin and native gesture.

The shim intercepts generated PDF Blob/data anchor clicks, including scripted
clicks and the anchor's `download` filename. It also supplies:

- `AppForgeDocuments.save(blob, filename)`;
- `AppForgeDocuments.download(blobOrDataUrl, filename)`;
- `AppForgeDocuments.share(blob, filename)`;
- `navigator.share({files: [pdfFile]})` and file-aware `navigator.canShare` when
  both the native bridge and sharing are enabled.

These document methods return promises. Normal-wrapper legacy native operations
retain synchronous return strings through the capability-guarded dispatcher.
Text sharing still sends text; PDF sharing sends a PDF stream. Camera chooser, geolocation, microphone, deep-link and QR
implementations remain in the normal wrapper.

PDF export accepts only `application/pdf`, the `%PDF-` signature, and at most
16 MiB. Each canonical base64 chunk decodes to at most 24 KiB, has an exact
expected offset, and cannot exceed the declared total. Transfers expire after
two minutes; filenames lose traversal separators, controls and bidi overrides.
A recent native touch is required, and one document request can be active at a
time. The signature check is an identification check, not full PDF validation.

Both save and share use `ACTION_CREATE_DOCUMENT` on Android 8–16. The user first
selects where to persist the PDF. The wrapper writes the exact transferred bytes
on a worker thread, flushes and closes the stream, then reports `saved`. SAF
supplies the destination and commit-on-close behavior; MediaStore permissions
and pending rows are unnecessary for this route. A share then launches
`ACTION_SEND` with `application/pdf`, `EXTRA_STREAM`, ClipData and a read-only
URI grant. Its success result is `chooser-launched`, never recipient delivery.
Document-picker cancellation, write failure and chooser-launch failure have
separate outcomes. Sharing therefore includes a document destination picker.
No provider declaration, file URI sharing, gallery permission, broad storage
permission or unrestricted local file reading is added.

Backgrounding revokes the touch authorization and cancels an unfinished chunk
transfer. The system document picker may retain the completed transfer while
it is open. Navigation/destruction closes the message port, clears buffers,
and invalidates worker completion so it cannot open a stale share chooser.
A completed write waits for resume before launching a share chooser or reporting
completion. Rotation cancels rather than silently resuming an export. A cancelled or failed
provider write can leave a partial document at the user-selected destination;
there is no success response for that write.

## Trust and configuration

A random handshake value binds the JavaScript closure to a port delivered by
`WebView.postWebMessage` to the main frame and an exact HTTPS target origin.
Cross-origin iframes do not receive the port or the handshake. Main-page URL
checks alone are not caller authentication. Navigation invalidates old ports and rotates capabilities.
Same-origin application scripts retain the application's authority.

Remote documents receive no shim/port unless `remoteBridgeAllowed` is explicitly
enabled. The configured and current URLs must have the same normalized HTTPS
scheme/host/port, without user information. There is no wildcard target origin.
Remote injection occurs after page completion; early remote startup feature
detection is not guaranteed. Quick HTML file messages require the private
capability injected only into the trusted main frame, an active matching file
page, and foreground state. Subframes have interface visibility but receive no
capability. Wrong capabilities, old navigation tokens, replayed request IDs,
concurrent starts and untrusted paths/origins are rejected. No wildcard message
port target or arbitrary local-file read is used. Same-origin application scripts
retain application authority; this is not isolation from hostile application code.

`DeviceBuildEngine` now serializes the runtime's real `shareBridge`,
`clipboardBridge`, `vibrationBridge`, `nativeBridgeAllowRemote`, `qrScanner`,
`versionName` and `hasCustomIcon` keys. `watermark` is not a ProjectDraft field;
its existing runtime default remains false rather than inventing a project flag.
Quick packaging serializes the narrow document/download opt-ins. It does not
add clipboard, vibration, QR or other general device APIs to the tiny shell.

Ordinary HTTP/HTTPS downloads continue through DownloadManager with scoped
cookies, user agent, MIME and safe filenames. Android 10+ uses public Downloads;
Android 8/9 uses app-specific Downloads without storage permissions. Native
HTTP export requires a trusted page, configured downloads and a recent touch.
Enqueuing is reported as enqueuing, not persistence.

## Verification and remaining acceptance

Run on Ubuntu:

```sh
node --test quality/tests/html_document_bridge_runtime.test.js quality/tests/web_wrapper_config_contract.test.js
javac --release 8 -Xlint:-options -cp "$ANDROID_JAR" -d "$WRAPPER_CLASSES" \
  android-app/app/src/main/assets/device-build/*.java \
  android-app/html-apk-template/src/main/java/com/appforge/htmlstub/*.java
```

The executable tests run the shipped JavaScript with actual Blob/File/data URL
bytes and run the production Java transfer/SAF/intent code against host Android
endpoint doubles. The doubles are not an emulator or an Android SAF provider.
Android Debug CI now explicitly compiles both wrappers against its Android 37
SDK and runs the focused regressions before Studio assembly. Release and Play
Production delivery controls are unchanged; CI has not been triggered here.

Local Android SDK compilation against API 36 passed during implementation.
Physical Android 8–16 acceptance, actual share recipients, cloud SAF providers,
rotation/background timing and the reported Kayseri CNC Market flow still need
independent device validation. Studio assembly alone is not child-APK evidence.
Do not infer release readiness from host tests or compilation.

## Local task evidence (2026-10-15 repair)

Base and unchanged HEAD: `44614aa3c77925934b1eaf43ffafdf2dcbfe9fba`, isolated
branch `fix/html-document-bridge-20261015`, uncommitted patch. The previous
1062/1063 result identified a real Quick HTML storage-origin regression. The
repair restores the original file URL rather than replacing that expectation.
The legacy no-interface assertion is replaced with narrow-interface checks after
adding executable negative contracts for the production document transport.
The normal wrapper also retains the original `(nodeWebAssets || microphone)`
HTTPS-selection policy rather than forcing existing file-based apps onto HTTPS.
All other original assertions remain.

The host harness executes Quick HTML creation, verifies its original entry URL
and storage/security settings, and exercises production Java document validation,
SAF bytes and share intents. The JavaScript harness executes both HTTPS-port and
file-capability transports. These establish compatibility by retaining namespace,
not physical localStorage/IndexedDB upgrade acceptance. No user records were
opened, changed or migrated. Physical update/relaunch verification of existing
service records and actual Android SAF/share behavior remains NOT_RUN.

Ubuntu focused verification: 32/32 tests PASS across document runtime,
wrapper configuration, legacy fast HTML recovery and HTTPS asset contracts.
Both complete generated wrapper source sets compile against the official Android
API 36 SDK jar with Java 8 syntax (deprecation notes only). The known legacy
failure was reproduced before repair and now passes. The full Node suite was
not rerun in this repair; the earlier 1062/1063 result is historical evidence.

Snapshot regeneration is deferred to the caller. No hosted CI, installation,
commit, push, merge, publication or release is authorized or performed.
