# Full HTML wrapper repair: discovery and acceptance matrix

## Identity and scope

Inspected on 2026-10-16 in `/workspace`, clean feature branch
`fix/fast-html-full-acceptance-20261016`, HEAD/base supplied for this task
`075200ebf08decb4265ceb19b67e67b4d0426231`. The merge-base with the local
`main` reference is `591a65b104e867c5d0c072660314504e7f80041f`;
this is separate from the supplied implementation base. No main edits,
commit, push, installation, release, project ZIP changes or migrations are
authorized. This document records discovery, not an implemented repair.

## Findings before implementation

| Requirement | Observed implementation | Acceptance needed |
|---|---|---|
| Direct PDF share, both wrappers | Both `AppForgeDocuments.java` copies enter `ACTION_CREATE_DOCUMENT` on finish, irrespective of mode; `ACTION_SEND` occurs only after SAF output finishes | Share produces a chooser directly; no save picker, public write, arbitrary input URI or file URI |
| PDF save | SAF destination must use `content`; worker writes, flushes and closes before reporting success; cancellation is separate | Retain save picker and exercise failed/slow/closed output streams, navigation and lifecycle cancellation |
| Temporary shared bytes | No private share provider exists in the inspected template manifest | Nonexported, grantable provider with per-package authority; bounded app-owned cache, read-only open, opaque expiring tokens, safe display names and delayed cleanup |
| Generated normal wrapper | `DeviceBuildEngine` copies Documents and LocalAssets Java into the generated app and emits its manifest | Copy provider source and declare exact generated package authority in the same generated project |
| Quick APK manifest | `HtmlBinaryManifest` rewrites package/label/version/SDK string pool and primitives | Validate and rewrite exactly one provider authority while preserving fixed Java class names and provider security attributes; reject malformed or colliding template identities |
| Quick AAB manifest | `FastHtmlAabProtoManifest` pins the compiled manifest digest and recursively rewrites only manifest/SDK/application/activity identity | Deterministically regenerate provider-bearing template; retain strict pinning; validate authority rewriting and compare two generated package identities |
| CSV | JS and native bridge accept only PDF; anchors support generated PDF MIME only | Explicit PDF/CSV allowlist, bounded strict UTF-8 validation, CSV quoting and exact byte tests; separate CSV file/name/MIME for save/share |
| Real File/blob/data | Shipped JS handles real Blob/File, chunked bytes and generated PDF URLs; native accepts no source URI reads | Preserve actual bytes and deny file/content/network URLs as document inputs |
| HTTP downloads | Separate DownloadManager path; public Downloads on API 29+, app-specific Downloads on older Android | Retain legacy behavior without making document Share enqueue a download |
| Quick settings | APK config serializes only mode/url/entry/download/native/share/remote opt-ins; Tiny activity hardcodes JS and DOM storage | Implement supported settings or deterministically route to normal engine with a reason before packaging |
| Quick AAB parity | Unsigned packager emits only mode/url/entry | Same supported settings as APK, or explicit fail-closed AAB/BOTH rejection; no artifact success on partial output |
| Origins and records | Quick uses original `file:///android_asset/site/`; normal selects HTTPS only for node assets or microphone | Keep both existing namespaces; no storage-clearing or business-data migration |
| Security tests | Existing Java endpoint doubles exercise tokens, replay, size, origins, lifecycle and SAF; SDK CI compilation is separately configured | Extend native execution to direct Share/provider expiry/package isolation/CSV and permission toggles; endpoint doubles do not prove Android URI grant enforcement |
| Physical acceptance | No current-task child APK/device execution | Studio CI APK is not Kayseri child APK acceptance; signature-checked manual installation and Android 8–16 recipient/upgrade tests remain separate |

## Wizard 1–9 routing truth table

These are required classifications for a repaired route, compared with inspected
behavior. `QUICK_IMPLEMENTED` must mean the option reaches actual Tiny activity
behavior, not merely a JSON key. `NORMAL_ENGINE_REQUIRED` must include a visible
reason and normal prerequisites. `EXTERNAL_CONFIGURATION_REQUIRED` is not a
claim that a generated integration exists. `UNSUPPORTED_BLOCKED` must stop before
artifact success. This audit does not change routing or claim the table is enforced.

| Step / ProjectDraft fields | Source-to-runtime trace and observed gap | Required classification / prerequisites |
|---|---|---|
| 1 identity: appName, packageName, versionName, versionCode | SourceStep/validateDraft → fast binary/proto identity writers or normal Gradle generation | QUICK_IMPLEMENTED for validated identity; provider authority must follow package without changing app ID |
| 1 source: sourceMode, sourceLabel, sourceUri, importedFolder, startPage, sourceTechnology, sourceTechnologyLabel, sourceBuildEngine, sourceBuildReady, webUrl | SourceStep → source analysis/effectiveSourceEngine → quick static asset collector or normal engine; labels/URIs are source metadata, not native features | QUICK_IMPLEMENTED only eligible static HTML/HTTPS; NORMAL_ENGINE_REQUIRED for other supported engines; invalid/unready sources UNSUPPORTED_BLOCKED |
| 1 autoVersionCode | UI/build preparation owns version allocation; not a Tiny activity capability | Verify allocated version before classifying QUICK_IMPLEMENTED; must not silently rewrite installed identity |
| 2 camera, microphone, location | Quick policy rejects; normal manifest/config → FastActivity runtime request/consent and trusted-origin checks | NORMAL_ENGINE_REQUIRED; manifest alone never grants WebView capture; ON/OFF, denial, origin and selected-resource behavior must execute |
| 2 notifications, firebaseMessagingEnabled | Quick notifications rejects; normal declares notification permission and has a notification setting; Firebase auto-injection is rejected by validateCapabilities | NORMAL_ENGINE_REQUIRED for permission selection; FCM integration UNSUPPORTED_BLOCKED, even with external Firebase config |
| 2 networkState, wakeLock, nfc, additionalPermissions | Quick excludes wake/NFC/additional but does not serialize networkState; normal manifest emits declared permissions and aliases | NORMAL_ENGINE_REQUIRED for declarations; runtime APIs not supplied by a declaration remain UNSUPPORTED_BLOCKED; additional allowlist validation retained |
| 3 fileUpload | No Tiny WebChromeClient file chooser; normal config → onShowFileChooser checks fileUpload | NORMAL_ENGINE_REQUIRED when requested; OFF must deny picker |
| 3 downloads | Quick APK config → Documents/DownloadListener; AAB config omits it; normal config → Documents/DownloadManager | APK QUICK_IMPLEMENTED for current PDF save/HTTP behavior; AAB parity blocked; direct Share is not implemented |
| 3 offlineCache | Normal config → FastActivity cache mode; quick config omits | NORMAL_ENGINE_REQUIRED until Tiny implements tested cache semantics |
| 3 fullscreen | Normal config → window/insets behavior; quick omits | NORMAL_ENGINE_REQUIRED |
| 3 webJavaScriptEnabled, webDomStorageEnabled | Normal config → WebSettings; Tiny hardcodes true | NORMAL_ENGINE_REQUIRED for differing values until quick config/runtime supports toggles; JS OFF must not expose functioning native shims |
| 3 webZoomEnabled, webWideViewPortEnabled, webOverviewModeEnabled, webMediaAutoplayEnabled, webMixedContentAllowed | Normal serializes all seven WebView flags and FastActivity applies them; quick omits | NORMAL_ENGINE_REQUIRED until explicitly implemented; preserve security validation and target SDK |
| 4 iconUri, iconName | Quick policy rejects custom icon; normal DeviceProjectIcon integration | NORMAL_ENGINE_REQUIRED; actual decodable icon prerequisite; iconName is metadata |
| 4 orientation | Normal generated manifest emits selected orientation; quick manifest has none | NORMAL_ENGINE_REQUIRED for nondefault orientation until quick implements it |
| 4 primaryColor, backgroundColor, statusBarColor, navigationBarColor | Normal config explicitly carries background/status/navigation; primaryColor is not serialized by that builder | NORMAL_ENGINE_REQUIRED for supported colors; primaryColor end-to-end behavior remains unproven and must be blocked or explicitly implemented |
| 4 splashEnabled, splashText | Normal config → FastActivity splash; quick omits | NORMAL_ENGINE_REQUIRED until tested quick implementation exists |
| 4 appCategory | No consumption found in inspected build Java/Kotlin | UNSUPPORTED_BLOCKED for any promised generated category behavior until manifest integration is verified |
| 5 javascriptBridge, remoteBridgeAllowed, shareBridge | APK config → document capability; native/share opt-ins gate sharing; HTTPS opt-in gates remote bridge; AAB omits config | Narrow APK document path QUICK_IMPLEMENTED but direct PDF Share repair remains pending; general native behavior requires normal engine; AAB parity blocked |
| 5 clipboardBridge, vibrationBridge | Normal serializes → compat dispatch; Tiny does not implement these APIs | NORMAL_ENGINE_REQUIRED; haptics also need correct VIBRATE declaration (not present in inspected webManifest permission builder); do not claim haptic acceptance |
| 5 qrScanner, mediaPlayerBridge | validateCapabilities rejects both before route selection; normal QR uses optional reflected FastQrRuntime that is not guaranteed present | UNSUPPORTED_BLOCKED for automatic injection; merely routing to normal cannot make them work |
| 6 admobEnabled, admobAppId, admobBannerUnitId, admobInterstitialUnitId, admobRewardedUnitId, umpConsentEnabled | UI validates App ID; validateCapabilities rejects AdMob auto-injection | UNSUPPORTED_BLOCKED; external AdMob/consent configuration alone is insufficient |
| 6 billingEnabled, billingProductIds, billingSubscriptionIds, consumableProductIds, removeAdsProductId, purchaseVerificationUrl | UI requires product IDs and HTTPS verifier; engine rejects automatic Billing integration | UNSUPPORTED_BLOCKED; EXTERNAL_CONFIGURATION_REQUIRED for Play products/server verification does not override engine block |
| 6 firebaseAnalyticsEnabled, firebaseCrashlyticsEnabled, firebaseMessagingEnabled, firebaseConfigUri, firebaseConfigName | UI requires selected config; validateCapabilities rejects Firebase auto-injection | UNSUPPORTED_BLOCKED; EXTERNAL_CONFIGURATION_REQUIRED for Firebase project/config remains additional, not sufficient |
| 7 deepLinkEnabled, deepLinkScheme, deepLinkHost, deepLinkPathPrefix | Shared validator → quick exclusion → normal manifest filter/config → FastActivity handling | NORMAL_ENGINE_REQUIRED; unsafe schemes/host/path denied; verified HTTPS App Links additionally EXTERNAL_CONFIGURATION_REQUIRED |
| 8 signingMode, keystoreUri, keystoreName, keyAlias, storePassword, keyPassword | APK quick requires debug and uses existing persistent alias; AAB permits managed debug or selected upload key; normal signing configuration separate | QUICK_IMPLEMENTED only supported explicit key modes; custom APK NORMAL_ENGINE_REQUIRED; key/password/certificate prerequisites checked without logging values |
| 9 minSdk, targetSdk | APK rewrites SDK primitives; AAB insists 26/37; normal Gradle config | QUICK_IMPLEMENTED within actual supported SDK shape; preserve target 37; unsupported AAB SDK selection must reject or use normal explicitly |
| 9 buildOutput | Shared output policy routes Windows/mixed outputs normal; AAB/BOTH use local AAB packager and signer | APK QUICK_IMPLEMENTED within truthful supported set; AAB/BOTH blocked pending template/config parity; Windows NORMAL_ENGINE_REQUIRED with its distinct signing/runtime prerequisites |
| 9 buildServiceUrl, buildApiKey | Device-local build/control-plane separation; not WebView/native runtime flags | Preserve device-local route and entitlement checks; never serialize credentials into child runtime config |

## Required executable acceptance cases

1. Execute production Java for both packages: one PDF Share launches SEND with
   MIME/stream/ClipData/read grant and never calls SAF or DownloadManager; save
   alone opens SAF and returns success only after output close.
2. Execute provider: read-only bytes/name/MIME, rejected write modes, traversal,
   wrong authority, unknown/expired tokens, oversized payloads, cleanup after
   chooser delay, process restart and foreign-package isolation. Verify actual
   compiled APK and AAB manifest authority/security attributes for two app IDs.
3. Execute JS with real File/Blob: binary PDF, CSV UTF-8/quoting, data/blob anchors,
   wrong MIME/signature/encoding/size, simultaneous/replayed operations, forged
   tokens, subframe handshake, remote origin opt-in and insecure input schemes.
4. Execute cancellation/navigation/background cases, including slow cache and
   SAF writes; report chooser launch rather than claiming recipient delivery or
   a completed share when Android offers no completion evidence.
5. Parameterize every wizard setting and output alias; require consumed values,
   deterministic normal routing with reason, or explicit unsupported rejection.
   Preserve PRO gates, existing signing aliases and both storage-origin tests.

## Evidence status

Focused baseline command:
`node --test quality/tests/html_document_bridge_runtime.test.js quality/tests/fast_html_advanced_routing_runtime.test.js quality/tests/web_wrapper_config_contract.test.js`
passed **25/25**. The baseline harness explicitly asserts a save picker for
`ready("share")`; this is evidence of the old behavior, not direct-share
acceptance. Tests were not weakened or changed.

No Android SDK jar, AAPT2, sdkmanager, prebuilt template APK or AAB was found by
the workspace file inventory. Available tools report Java 21 and Gradle 9.6.0;
CI uses Gradle 9.3.1. Workspace-local template generation was attempted for
`:html-apk-template:assembleRelease :html-apk-template:bundleRelease`:

- Offline: **FAIL**, Android Gradle Plugin 9.1.1 unavailable in the new local
  Gradle cache (43 seconds).
- Online retry: dependencies resolved, then **FAIL**, configuration of `:app`
  reported `SDK location not found` (1 minute 9 seconds). No SDK was provisioned
  and no replacement template or compiled manifest was produced.
- Android SDK javac: **NOT_RUN**, no configured/local SDK jar. Java endpoint-double
  compilation/execution passed in the focused suite; it is not SDK compilation.
- Full baseline Node quality: **PASS, 1066/1066**, zero failures/skips.
- Deterministic snapshot `--check`: **PASS**, existing snapshot remains fresh.
  This engineering document is outside the generator's selected evidence
  inventory; no snapshot content was changed.
- Local Stability Gate: **PASS, fail 0**; `git diff --check`: **PASS**.

The coupled provider/template repair is **BLOCKED / NOT_IMPLEMENTED**, not PASS.
Stopping before runtime edits preserves the original behavior and tests without
introducing a provider whose compiled identity/isolation has not been verified.
The next implementation environment needs SDK 37 (including the 37.0 platform
layout), the CI-equivalent Gradle/toolchain, deterministic APK/AAB template
regeneration, and executable manifest/provider package-isolation checks. Then
the entire batch and its new acceptance tests still need implementation.

Neither historical 1066/1066 nor the supplied hosted CI run is acceptance for a
new provider/configuration/template change.

Runtime implementation, new acceptance regressions, SDK compilation,
deterministically regenerated provider-bearing pins, hosted CI and physical
child APK acceptance are **NOT_ESTABLISHED** by this document. Do not publish a
partial APK or label this repair complete.

## Resume implementation — 2026-10-16 (supersedes the earlier NOT_IMPLEMENTED conclusion)

The preceding discovery and failed SDK attempts are preserved as historical
execution evidence. This resume implements source changes on the same isolated
branch/base; it does not install an SDK, retry Gradle, build/install an APK,
commit, push, merge or publish. The original user ZIP and business data are
untouched. Current modified-file evidence is `git status --short`; HEAD remains
`075200ebf08decb4265ceb19b67e67b4d0426231` with uncommitted implementation.

### Implemented document contract

Both shipped Java bridges now branch before SAF. Share writes only app cache
on a worker, constructs ACTION_SEND with EXTRA_STREAM, ClipData and a read
URI grant, and launches the Android chooser. It never calls SAF or DownloadManager.
The response is `chooser-launched`, which does not claim recipient delivery or
report chooser cancellation that Android does not expose through this API.
A missing recipient/failed chooser produces an error, without a fallback save.

Both providers use the actual installed package plus `.appforge.documents`.
They are nonexported and grantable; filenames are display metadata, never
filesystem input. Only opaque 128-bit UUID keys with PDF/CSV extensions resolve
within their private cache directory. Writes, inserts, updates, deletes, wrong
authorities, query/fragment arguments, traversal, unknown keys, expired files
and invalid sizes are denied. PDFs retain the existing signature check (this is
not a full PDF parser or malware scanner). CSV accepts strictly decoded UTF-8,
rejects disallowed controls and malformed CSV quoting, and preserves exact
validated bytes. MIME is limited to application/pdf, text/csv and application/csv;
CSV Share and provider responses use text/csv. `AppForgeDocuments.csv(rows)`
creates quoted UTF-8 fields with doubled quote escapes and CRLF rows. It is an
opt-in helper; existing page File objects and ZIP/JS sources are not rewritten.

The per-file limit is 16 MiB; the live share cache budget is 64 MiB. New opens
expire after one hour. Expired cache files are removed on access or subsequent
export; cleanup is lazy, with no early lifecycle/chooser-return deletion. An
already opened recipient file descriptor may outlive the URI expiry. Android
sandbox/URI-grant enforcement and exact recipient behavior still need device
acceptance. No generic file/content URI input is accepted by the document API.

Save alone uses ACTION_CREATE_DOCUMENT, verifies a content destination, writes,
flushes and closes off the UI thread, then reads back bounded bytes and compares
them before returning `saved`. A write-only or unverifiable provider returns a
clear failure even if it accepted a write. Picker cancellation remains distinct.
Background completions wait for foreground; navigation/cancellation invalidates
stale completions. Share bytes survive navigation and chooser delay until expiry.
Legacy HTTP(S) DownloadManager downloads retain their separate destination policy.

### Enforced wizard truth table after this repair

The field-by-field discovery table above remains the source trace. This table
records the resulting classifications, including limits rather than promises.

| Wizard scope | Enforced classification and behavior |
|---|---|
| 1 identity/version/static source and 9 SDK | QUICK_IMPLEMENTED within existing validated HTML/HTTPS and SDK bounds; both manifest writers preserve Java class identity and rewrite exactly one provider authority; AAB remains local static SDK 26/37 only. Source readiness, source analysis and allocated versions remain owned by existing preparation/preflight. |
| 2 camera/microphone/location/notifications/NFC/wake/additional permissions | NORMAL_ENGINE_REQUIRED; existing normal toolchain/prerequisite gate remains mandatory. Permission selection declares permission; it does not grant runtime consent or supply an NFC/notification native API. Existing media permission code remains origin/resource/consent gated. |
| 2 networkState | NORMAL_ENGINE_REQUIRED when selected, including the wizard default; Tiny does not silently omit the selected manifest permission. |
| 3 fileUpload | NORMAL_ENGINE_REQUIRED when selected, including the default. OFF uses Tiny with no WebChromeClient chooser. Normal ON uses its existing system picker and camera-consent path. No broad gallery permission was added. |
| 3 downloads | QUICK_IMPLEMENTED: SAF save/read-back, generated PDF/CSV anchors and separate public HTTP(S) downloads, with origin/gesture checks. |
| 3 JS/DOM/zoom/wide viewport/overview/autoplay/offline cache | QUICK_IMPLEMENTED with shared APK/AAB serialized booleans and actual Tiny WebSettings calls; cache enabled means normal WebView HTTP cache semantics, not a promise of offline remote-site availability. No storage namespace is cleared or migrated. |
| 3 fullscreen/mixed content | NORMAL_ENGINE_REQUIRED with explicit reason and existing security/preflight checks. Tiny retains NEVER_ALLOW mixed content. |
| 4 icon/orientation/splash | NORMAL_ENGINE_REQUIRED when selected; splash enabled is a default, so ordinary wizard defaults deliberately take normal prerequisites. Custom icon decoding remains a normal prerequisite. |
| 4 background/status/navigation colors | Default values are serialized/applied by Tiny. Custom colors route NORMAL_ENGINE_REQUIRED. Android system edge-to-edge appearance is subject to OS behavior and remains a physical acceptance item. |
| 4 primaryColor/appCategory | UNSUPPORTED_BLOCKED for nondefault primary color or non-auto category; existing default metadata does not claim a new generated feature. Shared preflight rejects them before artifact success. |
| 5 document/native share | QUICK_IMPLEMENTED for the PDF/CSV document surface, with existing native/share switches and explicit remote HTTPS opt-in. No generic native API is invented in Tiny. |
| 5 clipboard/haptics | NORMAL_ENGINE_REQUIRED when selected (also default selections). Normal haptics now declares VIBRATE when enabled with the native bridge; runtime consent/origin/capability restrictions remain. |
| 5 QR/media and 6 AdMob/Billing/Firebase/UMP | UNSUPPORTED_BLOCKED by shared Android wrapper preflight and engine validation. QR/Media3/AdMob/Billing/Firebase auto-injection is absent; UMP cannot be advertised independently. EXTERNAL_CONFIGURATION_REQUIRED for provider projects, consent, Play products, verifier and Firebase files is additional and does not remove the engine block. |
| 7 deep links | NORMAL_ENGINE_REQUIRED with existing scheme/host/path validator. Verified HTTPS App Links also require external domain association; no verification claim is made. |
| 8 signing | Existing managed signing aliases/certificates and custom upload-key checks are preserved. Custom APK uses NORMAL_ENGINE_REQUIRED; eligible AAB/BOTH retains existing explicit upload-key flow. No key values enter child config or this report. |
| 9 APK/AAB/BOTH | QUICK_IMPLEMENTED only for the same narrow supported runtime settings. AAB restrictions (local/static/26–37 and supported key selection) fail closed. BOTH publishes artifact state only after both requested artifacts succeed; existing failure cleanup remains. |
| 9 Windows/mixed outputs | NORMAL_ENGINE_REQUIRED through existing output-aware routing and independent Windows prerequisites. Android repair does not imply Windows or publisher acceptance. |
| Source/signing labels, URIs, control-plane credentials, entitlement | Existing metadata/preparation/PRO controls remain; credentials are not child runtime settings. No migration, alias, package-ID or entitlement mutation was added. |

`FastHtmlApkPolicy.normalRouteReasons` provides deterministic reasons for selected
Tiny-incompatible settings. MainActivity exposes these in preflight and the build
engine logs them; the existing OfflineBuildPackManager normal prerequisite check
continues to run. Default file upload, clipboard, haptics, network-state and
splash selections therefore use normal engine rather than a falsely capable
Quick artifact. Unsupported wrappers are rejected by shared settings validation.

### Template integrity and manifest evidence

`generateFastHtmlIntegrity` depends on the actual AGP-built APK and AAB, hashes
both exact byte streams and the exact compiled AAB manifest, and writes a strict
three-entry metadata asset. Templates and metadata are embedded together in the
signed Studio APK. Runtime verifies the APK/AAB hashes before packaging and the
AAB manifest hash before rewriting; absent, malformed or mismatching pins fail
closed. No digest is invented, guessed, downloaded at runtime, or disabled.
The historical providerless protobuf fixture is now explicitly rejected.

Binary manifest rewriting checks one provider, exact class/authority, exported
false and grantUriPermissions true before rewriting the authority. Protobuf
rewriting checks exact provider identity/security and unique provider/authority.
Normal DeviceBuildEngine copies the provider Java and declares the actual child
package authority. Real AGP template round trips and metadata hashes are mandatory
Gradle unit tests, fed the same built outputs that Studio packages. Missing
outputs do not cause a skip. Existing Android Debug SDK javac wildcard compilation
already includes both new providers; existing unit/build/release/Play gates and
the workflow remain unchanged.

### Current acceptance limits and next independent gates

New executable host regressions execute the actual Java bridges/providers/Tiny
activity against endpoint doubles, real JS Blob/File data, and production Kotlin
policy/binary/protobuf writers. Host manifest fixtures deliberately test parser
branches; they are **not** Android-compiled manifest evidence. Additional Gradle
unit tests require real built templates and exercise actual shared JSON config
and integrity parsing. They have not run locally without the SDK.

Required external stages remain: exact reviewed-source Android Debug CI (SDK
javac, new mandatory real-template/unit tests, Studio build and signature), then
signature-checked child APK installation and Android 8–16 physical save/share,
recipient-grant/expiry/cancellation, custom normal-feature and upgrade/storage
acceptance. A Studio CI APK is not a real Kayseri child APK acceptance record.
No partial APK is produced or delivered by this iteration. No claim of all
features working, hosted CI success or physical acceptance is made.

### Modified file inventory

- `android-app/app/build.gradle.kts`
- `android-app/app/src/main/assets/device-build/AppForgeDocuments.java`
- `android-app/app/src/main/assets/device-build/AppForgeShareProvider.java`
- `android-app/app/src/main/assets/second_brain_snapshot.json`
- `android-app/app/src/main/java/com/appforge/studio/MainActivity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabProtoManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlAabUnsignedPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPackager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/FastHtmlApkPolicy.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/HtmlBinaryManifest.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/TemplateIntegrity.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/WebviewProjectSettings.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/CompiledHtmlTemplateIdentityTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlAabProtoManifestTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlAabRoutingTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/FastHtmlAabUnsignedPackagerTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/QuickHtmlConfigTest.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/TemplateIntegrityTest.kt`
- `android-app/html-apk-template/src/main/AndroidManifest.xml`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeDocuments.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/AppForgeShareProvider.java`
- `android-app/html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java`
- `docs/engineering/fast-html-full-repair-acceptance-20261016.md`
- `quality/tests/advanced_permission_route_runtime.test.js`
- `quality/tests/fast_html_advanced_routing_runtime.test.js`
- `quality/tests/html_document_bridge_runtime.test.js`
- `quality/tests/html_template_rewrite_runtime.test.js`
- `quality/tests/offline_build_preflight_gate_v1_contract.test.js`
- `quality/tests/web_wrapper_config_contract.test.js`

### Executed resume evidence

- First full run during implementation: **1064/1066**, with the newly required
  VIBRATE declaration missing from an old expected set, plus stale snapshot.
  The permission assertion now also verifies VIBRATE OFF; no permission check
  was removed. Snapshot was regenerated with the canonical generator.
- Expanded focused suite: **29/29 PASS**, including executable Java endpoints,
  JS PDF/CSV, policy/media/permission behavior and both legacy-origin namespaces.
- Separate executable Kotlin manifest writer regressions: **1/1 PASS**, including
  unique generated authorities, insecure provider flags, duplicate providers,
  authority collisions and wrong pins. Repeated after final Boolean guard:
  **1/1 PASS**, no skips.
- Full repaired Node suite: **1069/1069 PASS**, no failures or skips. A final
  repeat is recorded below when complete; the original historical 1066 result
  above is not used as repair acceptance.
- Canonical snapshot generation and freshness check: **PASS**. Wiki drift audit:
  **0 errors, 27 advisory warnings**; wiki pages themselves are outside this
  run's allowed edit paths. Durable repair decisions and unresolved acceptance
  are retained here; snapshot remains a generated repository attestation.
- Stability Gate local, repeated after final source edit: **PASS, fail 0**.
  `git diff --check`: **PASS**.
- Local Android SDK javac and Gradle/unit/assemble/bundle: **NOT_RUN in resume**;
  SDK absent, no installation or Gradle retry. New real-template/config/integrity
  Gradle unit tests and existing Android wrapper SDK javac require isolated CI.
- Hosted exact-source CI, child package installation and physical acceptance:
  **NOT_RUN**, no push/build/install authorization exercised. No release/Play
  gate was changed and no all-working/production-ready conclusion is claimed.

A later full repeat returned **1068/1069**: the executable preflight test
extracts only the failure block and omitted its newly referenced enclosing
`normalRouteReasons` variable. Production defines that variable before the block.
The harness now models it and asserts each reason survives prerequisite failure;
its focused rerun passed **8/8**. This was a regression-harness failure, not a
waived check. The final full rerun follows all source and harness corrections.

**Final frozen-source local result:** `node --test quality/tests/*.test.js`
passed **1070/1070**, fail 0, skipped 0 (81.56 seconds). The preceding corrected
repeat also passed 1069/1069 before adding the explicit normal file-picker
execution test. The new picker/media/policy focused file passed **4/4**: upload
OFF denies, ON opens picker, denied camera consent never inserts capture media,
granted camera capture is selected explicitly, Android 8 falls back to picker,
and picker exceptions clear pending state. QR/media auto-injection remains
blocked, with validator ON/OFF regressions rather than a fictitious QR runtime.

Final Stability Gate: **PASS / fail 0**. Canonical snapshot is regenerated for
all final source/test changes. Modified-file inventory contains **30 files**,
all within the authorized paths. No main edit, staging/commit/push, SDK install,
Gradle retry, artifact installation, project ZIP edit, migration, merge, release
or publishing occurred. Implementation is ready for source review and the
mandatory external CI/device gates; those gates remain **NOT_RUN**, not PASS.
