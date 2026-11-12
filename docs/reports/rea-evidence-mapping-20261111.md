# REA research mapping — 2026-11-11

Input: user-supplied `AppForge_Studio_REA_Entegrasyon_Notu.md`, dated 11 November
2026, concerning HTML to APK Builder 1.34 (48). AppForge comparison base is
`a9c1739b025a939b120888220d827a82f1538467`; the implementation already delivered
on this work branch is `3cbbb73b19d367bd81fe24d03b8b2ec708a01374`.

The input reports static REA/JADX findings and separately identifies store claims.
The reference XAPK, REA evidence payloads and device were not supplied in this
turn. Evidence IDs below are provenance identifiers from the note, not artifacts
independently fetched or reverified by this session. No reference code or embedded
resources were copied into AppForge.

## Comparison and scope

| Research item | AppForge implementation / evidence | Remaining boundary |
| --- | --- | --- |
| URL writer records mode/address and returns without index.html | Existing delivery uses shared fast runtime configuration and no local site assets for HTTPS AAB/BOTH; production Kotlin packager and bundletool checks passed. | Actual installed child app redirects, disconnection/reconnection and same-device normal/fast timings remain NOT_RUN. A URL app needs its server at first launch. |
| AAB manifest network permissions and selected keystore | Existing policy-controlled permissions, proto manifest, resources and upload-key certificate verification are retained. Internet is present; network-state permission follows AppForge's selected capability. | The reference's permission list is not a reason to enable unselected AppForge capabilities. Real Play upload acceptance was not tested. |
| PDF source, private seekable file and built-in reader | Existing delivery has a distinct PDF technology, streamed private import, embedded document/hash, normal Android wrapper, background PdfRenderer, bounded bitmaps, zoom/page selection and per-document last-page storage. | Actual normal-engine PDF app production on Android, rotation, reopen, offline launch and AAB-derived installed reader acceptance remain NOT_RUN. |
| Reference PDF writer bounds normal files to 100 MiB | AppForge intentionally uses its own 32 MiB / 1,000-page budget and adds parser checks plus document identity validation. | Do not raise limits to match the reference without AppForge resource measurements. The reported reference writer excerpt does not establish parser validation. |
| Scanned PDF acceptance | Follow-up host fixture contains two raster-only pages, no selectable text, real image XObjects and independently rendered black pixels. Production PdfProjectSource accepts it. A matching Android parser/rendering test has been added. | Host rendering uses PDFBox. It is not an Android PdfRenderer or reader UI result; actual device execution is NOT_RUN. |
| PDF/CSV plus PNG/JPEG/TXT Blob/Data URI exports | Existing delivery extends the same origin/token/gesture/chunk/size gates and expiring content provider in both AppForge runtimes. Actual host bytes/SAF-boundary tests passed. | Real Android SAF, chooser and recipient consumption remain device acceptance items. |
| Template / manifest / resources / DEX / assets / package / signature stages | Existing delivery measures directly controlled stages with a fixed vocabulary and sanitized PASS/FAIL events. | AppForge's fast path does not perform a separate DEX namespace rewrite. Do not invent a DEX timing for an operation not performed. Normal Gradle signing is included in its measured subprocess, not fabricated as a separate duration. |
| Native AndroidAPI inventory | Existing bounded AppForge deep links, foreground notifications, location, clipboard and other selected APIs were audited separately. | Native printing, reverse geocoding, repeated alarms and dedicated sensors are candidates, not implemented features merely because the reference has method signatures. |
| Project backup ZIP and Android Studio source ZIP | Both existing AppForge flows were identified in the prior audit. | ZIP export is not evidence that every native bridge, PDF reader or monetization configuration builds identically after Android Studio import. |
| PDF bookmarks, thumbnails, night mode and editor | The implemented AppForge PDF reader is intentionally the basic read-only source-to-app path. | Preview/library UX, editing, bookmarks, thumbnails and night mode are separate future work; no automatic scope expansion. |
| Ads, subscriptions, AI credits and provider | Existing AppForge Billing and Studio purchase rules retain their own gates. | Store text, model fields and worker names are not live purchase/credit/provider verification. No ads/credit product change or production access is authorized by this comparison. |

Reference APK SDK levels, class counts, package sizes and a consistent reference
APK signature are observations about that artifact. They neither set AppForge's
SDK budget nor prove how all generated children are signed. AppForge keeps its
own minimum/target 26/37 policy and independent package/certificate checks.
The reference's missing-source placeholder behavior is not adopted: AppForge
continues to reject invalid local HTML entries and invalid PDF identity.

## Reported REA provenance

| Claim | User-provided evidence identifier |
| --- | --- |
| APK producer | `ev_3f32db8a87cd32a9937bed3db59ae0501d1a85812d572c28e5fa4a54cec24a07` |
| AAB producer | `ev_20a388dd1e10ab1fe47540aae28969aa75aaf635fecf1b12f5eb5367104c9bf2` |
| Source-specific asset writer | `ev_18dde0433b209f1c75a039d64781c3accf57e00b62203947b590f584635d805c` |
| PDF reader | `ev_678f451f5313593b2c54b215f054d036b23a294f3ee3381407154f99c683c132` |
| PDF launch method | `ev_8b82f10e6fec9ea9978a2576ec60241e8b27118477f9b622647f5b2c18d08bae` |
| Compiled AndroidAPI | `ev_d4d9452637e6937dc8cf1c58a710852122846a127c0afa704b90401d02554ca9` |

## Follow-up validation

- `python3 quality/tools/pdf_source_host_smoke.py --parser-jars "$VERIFIED_PDFBOX_CLASSPATH"`: PASS, including the new independently checked raster-only scan fixture and the existing valid/multipage/encrypted/corrupt/oversized/symlink/hash-tamper cases.
- `gradle -p android-app :app:assembleDebugAndroidTest --no-daemon --max-workers=2`: PASS (`BUILD SUCCESSFUL in 25s`, 163 actionable tasks). This compiles/packages the added Android test; it does not execute it.
- `PdfProjectSourceInstrumentedTest.imageOnlyScannedDocumentCanBeParsedAndRendered`: device execution NOT_RUN; no attached device/emulator available in the existing environment.
- Snapshot generation/check, wiki audit and secret scan are recorded with this follow-up.

The previous 685-unit / 58-focused / bundletool PASS results belong to the prior
implementation run; they are not relabeled as a new full run for this test/docs
change. Likewise, the seven reproduced base Node failures and the GitHub 403
publication blocker are not erased by a static research note.
