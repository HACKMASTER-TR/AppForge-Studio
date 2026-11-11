---
type: feature
status: draft
project: AppForge Studio
created: 2026-11-11
updated: 2026-11-11
last_verified: 2026-11-11
confidence: medium
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabUnsignedPackager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/PdfProjectSource.kt"
  - "android-app/app/src/main/assets/device-build/AppForgePdfReader.java"
  - "android-app/app/src/main/assets/device-build/AppForgeDocuments.java"
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildStageTimings.kt"
  - "quality/tests/html_document_bridge_runtime.test.js"
  - "quality/tools/pdf_source_host_smoke.py"
  - "android-app/app/src/androidTest/java/com/appforge/studio/io/PdfProjectSourceInstrumentedTest.kt"
---

# HTML and PDF integration

Development work based on `a9c1739b025a939b120888220d827a82f1538467`.
See [[Fast_HTML_V2]] for existing native capabilities. This page records code
boundaries, not Play publication or device acceptance.

## URL bundles

HTTPS URL projects can use the same fast runtime configuration for APK and AAB,
including combined outputs and normalized Android output aliases. URL bundles
contain no synthetic HTML entry or site assets. URL syntax, credentials, ports,
SDK 26/37, template integrity, manifest/resources, signing and artifact validation
remain enforced. HTTP APK compatibility is preserved; HTTP AAB requests use the
normal engine. Local HTML still requires its real entry and bounded safe assets.
Custom upload keys retain the existing certificate agreement checks for BOTH.

## PDF projects

The source picker accepts PDF MIME or filenames, copies at most 32 MiB, validates
using Android PdfRenderer, rejects password-protected/corrupt documents and limits
pages to 1–1000. An explicit marker binds the embedded PDF to its SHA-256.
An internal HTML fallback supports the existing project layout; it is not used by
the native PDF reader. PDF is a separate detected technology and uses the normal
Android wrapper, never the fast static packager or Windows exporter.

The wrapper validates both original and copied document identity and includes a
native reader with no network/JS/external-viewer dependency. The reader checks its
packaged identity, renders one bounded bitmap at a time on a worker, offers page
selection and bounded zoom, and persists the last successfully rendered page by
document hash. It uses project icon/version/orientation/window colors/fullscreen
and the zoom setting. WebView-native APIs do not apply to this reader. PDF editing,
password entry, text search/selection and annotations are not implemented.

Temporary documents are private, bounded and removed on close; stale private
reader cache files expire. Bitmap, page, renderer, descriptor and worker lifetimes
are closed on destruction. Process-death and offline first-launch behavior still
require actual Android device acceptance.

## Document export

Both runtime templates share PDF/CSV/PNG/JPEG/TXT save/share support. The existing
main-frame origin/token, foreground, recent user tap, project opt-in, generation,
chunk ordering, 16 MiB transport and read-only expiring provider gates remain.
TXT requires valid UTF-8 without binary controls; CSV additionally validates
quoting. Images require matching signatures and decoder MIME, bounded dimensions
and at most four million pixels before full decoding. Supplied conflicting or
unknown filename extensions fail closed. SAF cancellation and write read-back
verification remain separate from share chooser launch. Chooser launch does not
prove a recipient consumed the document.

## Build events

Fixed-vocabulary stage events report monotonic elapsed milliseconds and PASS/FAIL
for template, manifest, resources, assets, packaging, signing and verification
where those operations are directly controlled. They never include exception
messages, URLs or signing material. Normal Gradle packaging includes its own
signing work; no separate signing duration is fabricated for that subprocess.
Telemetry failures do not alter artifact or signing outcomes.

## API catalog audit

The local `document-export` template provides five real generated formats, project
opt-ins and device test steps. It uses existing capability-gated runtime APIs.

| Candidate | Existing evidence and remaining boundary |
| --- | --- |
| Printing | No native PrintManager adapter in the shipped wrappers; browser print is not advertised as native support. |
| Deep links | Existing bounded scheme/host/path receiver and manifest rewriting; Android/app-link domain verification and physical delivery still need device evidence. |
| Reverse geocoding | Coordinates are implemented; no native Geocoder bridge. Network/geocoding service and privacy behavior would be separate work. |
| Repeating notifications | Existing immediate foreground native notifications; no scheduler/repeating background delivery. |
| Sensors | Existing browser DeviceMotion/DeviceOrientation example; hardware/WebView support varies, no dedicated native sensor bridge. |
| Android Studio source ZIP | Existing `ProjectBackupManager.exportAllAndroidProjectsToUri` and `writeAndroidProject`; preserving configuration does not prove native bridge/SDK or new PDF reader parity in that exporter. |
| PDF editing | New reader is read-only; editing/annotation absent. |
| Ads/subscriptions | Fast HTML has real Billing 9.1.0 with receipt-bound HTTPS backend requirements. AdMob/UMP and normal-engine Billing injection remain blocked. Studio lifetime purchase is separate; retired monthly/add-on routes must not be revived implicitly. |
| AI credits | No active AI-credit flow established by this audit. The accountless control-plane quota routes return verification-not-configured; retired monthly/add-on endpoints return 410. No live service/store acceptance is claimed. |

## Verification boundaries

`quality/tools/pdf_source_host_smoke.py` executes production source validation
with PDFBox at the Android parser boundary: host evidence only. Instrumented
source tests cover the real Android parser, including a test-only encrypted
fixture; they require an attached device/emulator. The compiled packager smoke
executes real Kotlin packagers/signers with network/process execution denied and
can retain signed test artifacts and run bundletool. Neither harness is a device
installation test. Detailed commands/results are in the delivery report.

## User-supplied REA research follow-up

The 11 November reference note distinguishes static REA findings, store claims and
unperformed device acceptance. See the [evidence mapping](../../reports/rea-evidence-mapping-20261111.md)
for reported evidence IDs and the AppForge comparison. These IDs were supplied by
the user; their underlying payloads were not independently fetched here.

A new host test verifies an actual two-page raster-only PDF fixture with image
resources, no selectable text and nonblank independently rendered pixels before
running production source validation. The Android counterpart parses and renders
both pages and checks pixels; compiling it is not device acceptance. Existing
32 MiB/1,000-page AppForge budgets remain unchanged. Reference PDF editing,
bookmarks, thumbnails, native API methods, ads and AI credit descriptions do not
automatically become implemented AppForge features or authorize product changes.
