# T21 unified wizard/build/documents development

## Identity and evidence boundary

Worktree `/workspace`, branch `fix/t21-unified-wizard-fast-html-documents-20261016`, interrupted dirty worktree at HEAD/base `d1d99cca3a78328cda6d1c1df6d7badc463b98ce`. No delivery authorization. Source inspection precedes implementation. Historical 1070/1070 T20 local tests do not certify T21. Owner reports: child build AF-0000001058 on phone, PDF ACTION_SEND chooser passed, earlier PDF save issue resolved. These are historical reports, not observations made in this session; chooser launch does not prove recipient delivery.

## Inventory and classification rules

Q = QUICK_SUPPORTED, N = NORMAL_SUPPORTED, E = REQUIRES_EXTERNAL_CONFIGURATION, B = UNSUPPORTED_BLOCKED. Each row covers default and both toggle states unless explicit states are listed. Q assumes static HTML/HTTPS, Android-only outputs, valid identity/source, and no other N/B selections. N requires the normal local toolchain; manifest-only rows do not imply a native JavaScript API. B selections must persist and be explained before build. Invalid imported values are never silently repaired by preflight. External configuration alone cannot make an unimplemented integration supported.

Authoritative evidence: `android-app/app/src/main/java/com/appforge/studio/model/ProjectDraft.kt` (defaults); `MainActivity.kt` (SourceStep, PermissionsStep, FeaturesStep, AppearanceStep, NativeBridgeStep, MonetizationStep, DeepLinkStep, SigningStep, BuildSettingsStep, BuildRuntimeStep); `io/ProjectLibrary.kt` serializeProject/restore; MainActivity project JSON import; `build/WebviewProjectSettings.kt`; `FastHtmlApkPolicy.kt`; `OfflineBuildPackManager.kt`; `DeviceBuildEngine.kt` validateCapabilities/buildWebWrapper/webManifest; `FastHtmlApkPackager.kt`; `FastHtmlAabUnsignedPackager.kt`; `FastHtmlAabProtoManifest.kt`; `HtmlBinaryManifest.kt`; `TemplateIntegrity.kt`; `app/build.gradle.kts`; normal `assets/device-build/FastActivity.java`; quick `html-apk-template/src/main/java/com/appforge/htmlstub/TinyHtmlActivity.java`; both AppForgeDocuments/AppForgeShareProvider variants. Quality evidence is recorded separately below.

## Wizard 1–10 matrix (discovery, before edits)

| Step | Fields / selectable states (default first) | Class and actual consumers / error location |
|---|---|---|
| 1 Source/identity | appName empty/custom/invalid; packageName com.appforgestudio.myapp/custom/invalid; versionName 1.0.0/custom; versionCode 1/custom/invalid; autoVersionCode false/true | Empty name B, valid identity Q/N, invalid package/version B at 1. Binary/proto manifests and normal Gradle consume identity; auto version assigned by caller. Prior normal version coercion must not hide invalid input. |
| 1 Source | LOCAL/URL; sourceUri/importedFolder/startPage; sourceLabel; sourceTechnology/sourceTechnologyLabel/sourceBuildEngine/sourceBuildReady | Local static Q when actual bounded HTML tree exists; URL valid credential-free HTTPS Q APK; fast URL AAB needs normal fallback. Missing/escaping/symlink/excessive local input B at 1, detailed filesystem checks remain packager responsibility. node-web N; other engines use DeviceBuildCapabilities and their own prerequisites. Labels are metadata, never capability proof. |
| 2 Permissions | fileUpload true/false; camera/microphone/location false/true | Upload ON N chooser, OFF Q. Capture/location ON N FastActivity runtime consent and trusted origin; OFF Q. Camera capture has Android 8 picker fallback. Mic local enables HTTPS asset loader; remote requires HTTPS. Declaration alone never grants consent. |
| 2 Permissions | networkState true/false; wakeLock/nfc/notifications false/true | ON N manifest declarations; notifications additionally requests runtime permission. No NFC JS API, no acquired wake lock, no notification dispatch API, no native network-state bridge. OFF Q. UI wording describes declarations; evaluator must expose this limitation. |
| 2 Permissions | additionalPermissions empty/aliases/canonical/invalid | Empty Q. BLUETOOTH -> SCAN/CONNECT, BIOMETRIC -> USE_BIOMETRIC, CALENDAR -> READ/WRITE_CALENDAR, CONTACTS -> READ/WRITE_CONTACTS, BACKGROUND_LOCATION, EXACT_ALARM, MEDIA_IMAGES, MEDIA_VIDEO, ACTIVITY_RECOGNITION: N declaration only. Canonical/android.permission-prefixed allowlisted names N. Unknown/malformed B at 2. No native APIs or extra runtime consent inferred. |
| 3 Web features | downloads true/false; offlineCache true/false | Both Q, Tiny and normal config consumed. Cache is WebView LOAD_DEFAULT/LOAD_NO_CACHE, not guaranteed complete offline availability. Downloads OFF blocks save/download; share independent. HTTP DownloadManager requires trusted main page and gesture. |
| 3 Web features | webJavaScriptEnabled/webDomStorageEnabled/webZoomEnabled/webWideViewPortEnabled/webOverviewModeEnabled/webMediaAutoplayEnabled true/false | Both Q, exact config -> WebSettings in both children. JS OFF means AppForgeDocuments shim cannot execute. Autoplay is WebSettings policy, not guaranteed codec/media support. |
| 3 Web features | webMixedContentAllowed false/true | OFF Q, ON N security preflight/WebSettings. Does not authorize HTTP top-level static URL or credential-bearing URLs. |
| 4 Appearance | iconUri null/custom/invalid; iconName | Null Q; custom N DeviceProjectIcon.install; unreadable/invalid icon B during resource preparation, UI picker has checks. Name metadata only. |
| 4 Appearance | orientation unspecified/portrait/landscape/invalid; fullscreen false/true | Default/OFF Q; portrait/landscape and fullscreen ON N manifest/window. Invalid orientation B at 4 (previous normal manifest silently normalized). Platform may override orientation/window bars. |
| 4 Appearance | primaryColor #6B7CFF/custom; backgroundColor/statusBarColor/navigationBarColor #07101F/custom/invalid; Koyu/Açık/OLED/custom | Default Q. Primary nondefault B at 4: no primary resource/runtime consumer. Koyu defaults Q subject to splash; Açık/OLED B due to primary. Valid background/status/navigation colors N runtime; invalid B at 4. Android edge-to-edge may override bars, physical acceptance separate. |
| 4 Appearance | appCategory auto/game/none/invalid | auto Q, game/none B at 4 (no manifest injection); unknown B. Existing UI promises auto game inference, unsupported by webManifest; explicit early limitation required, never conflicting isGame/appCategory declarations. |
| 4 Appearance | splashEnabled true/false; splashText empty/custom | ON or nonempty text N normal runtime; OFF+empty Q. Text with OFF preserved, no rendered splash implied. |
| 5 Native bridges | javascriptBridge/shareBridge/clipboardBridge/vibrationBridge true/false; remoteBridgeAllowed false/true | Master/share Q, clipboard/haptics ON N (default N); OFF Q. Native compat methods check per-feature config; master/share control document sharing. Remote OFF no remote document bridge, ON only configured trusted HTTPS main origin. Token/port isolates subframes; gesture and foreground still required. |
| 5 Native bridges | mediaPlayerBridge/qrScanner false/true | OFF Q; ON B at 5: no Media3/scanner implementation. Native scanCode unsupported response does not establish scanner support. |
| 6 Monetization | admobEnabled false/true; admobAppId/banner/interstitial/rewarded IDs empty/valid/invalid; umpConsentEnabled false/true | OFF/inert fields Q. ON B at 6 even with valid IDs; missing/invalid configuration cannot enable absent AdMob/UMP implementation. UMP without AdMob B separately. Never echo IDs/config payloads in diagnostics. |
| 6 Monetization | billingEnabled false/true; product/subscription/consumable IDs/removeAdsProductId/purchaseVerificationUrl empty/valid/invalid | OFF/inert fields Q; ON B at 6, no automatic Billing/verification integration, no invented successful purchase. |
| 6 Firebase | analytics/crashlytics/messaging false/true; firebaseConfigUri/name null/custom/invalid | OFF/inert config Q. Each ON B at 6 even with genuine config; external provider setup necessary but insufficient because injection unsupported. Do not read/log private config to claim support. |
| 7 Deep links | enabled false/true; scheme https/custom/invalid; host empty/valid/invalid; path /custom/invalid | OFF Q, inactive malformed fields preserved. ON valid N manifest + FastActivity onNewIntent filtering; unsafe scheme, malformed host/path B at 7. HTTPS association remains E: assetlinks/server setup not implied by intent filter. |
| 8 Signing | DEBUG/CUSTOM; keystoreUri/name/alias/passwords absent/valid/invalid | DEBUG Q local persistent identity, CUSTOM APK N, CUSTOM local AAB/BOTH quick path with upload-key selection checks. Missing fields E at 8. Actual key readability/password/certificate validity verified in signer, cannot be certified by pure evaluator. Secrets never serialized/diagnosed. |
| 9 Build | minSdk 26/custom; targetSdk 37/custom; invalid range | Valid 26..37 ordered Q APK/N; fast AAB pinned 26/37, other valid SDK values require N. Invalid B at 9. |
| 9 Build | buildOutput apk/aab/both/aliases/Windows/combined/invalid | Android-only Q when template supports exact selections, otherwise N. Windows and combined always N, separate host/signing prerequisites; invalid B at 9. No swallowing requested outputs. |
| 9 Build | buildServiceUrl device://local/custom; buildApiKey empty/custom | Device engine remains local; not capability authorization. No secrets in report/export. Protected backend/auth settings outside T21. |
| 10 Build | readiness, progress, artifacts, cancellation/reentry | Same settings validator must run UI/requirements/engine before packaging; normal toolchain absence is E readiness, not settings repair. Filesystem/template/signer checks remain authoritative at use. Success only after signature/artifact verification, no 0% sequential settings surprises. |

Persistence preserves category/colors/toggles/config references via ProjectLibrary JSON; signing passwords/API key excluded. Import paths and source refresh remain distinct from pure settings validation. OFF must not erase remembered IDs/choices.

## Packaging and documents audit

T20 already derives template SHA-256 from compiled APK/AAB in Gradle generate metadata; runtime TemplateIntegrity checks compiled bytes and manifest baseline. Binary/proto rewrite, signer/CMS verification and output publication are separate gates. No digest bypass or stale-template allowance is authorized. Quick default remains normal by design, not an assertion to weaken. Fast AAB uses strict local source and SDK 26/37; normal fallback must be decided before quick packaging for URL/nonpinned SDK.

Both document Java variants differ by package only. Save uses ACTION_CREATE_DOCUMENT and bounded byte read-back; share uses ACTION_SEND direct chooser, ClipData/read URI grant and custom read-only ContentProvider (not AndroidX FileProvider). Provider authority is package.appforge.documents, 1h TTL, 16 MiB document and 64 MiB cache bounds. Existing custom provider behavior must not be claimed as AndroidX FileProvider. JS supports binary Blob PDF, UTF-8 CSV quoting, generated blob/data download; no AppForgeFileBridge adapter exists in inspected source. Native reject path currently resets an active request on competing begin: fix must preserve first request. Existing tests cover both variants and real Java state machine with Android stubs, not physical SAF/provider behavior.

KAYSERI_ZIP=NOT_AVAILABLE. Worktree file inventory contains no genuine V25 ZIP. No Kayseri source or installed app touched; V26 adaptation NOT_DONE.

## Implementation/test log and stages

Overall T21 status: **NOT_DONE**. The preserved interrupted implementation and the continuation are partial development evidence, not acceptance. This session started with 10 modified tracked files and this untracked report; it did not witness a clean starting tree. Branch and HEAD matched the supplied guards; HEAD remains unchanged.

Preserved interrupted changes: aggregate WizardReadinessEvaluator and typed diagnostics; blockers for category/primary color and missing automatic integrations; validation at UI, offline readiness and engine entry; AAB URL/SDK/alias routing; duplicate/concurrent document begin rejection preserving the active transfer; CSV SAF MIME normalization; executable Kotlin and Java harness extensions. No settings reset or integration bypass was added.

Continuation changes: translated normal-route reasons into Turkish and displayed them in the wizard before build; added production-evaluator assertions for default normal routing and route rationale, plus a UI wiring assertion. Existing PDF save and direct ACTION_SEND chooser structure were preserved. No source ZIP or business data was modified.

Observed first focused run: `node --test --test-concurrency=1 quality/tests/fast_html_advanced_routing_runtime.test.js quality/tests/html_document_bridge_runtime.test.js`: 22 passed, 0 failed, 0 skipped, exit 0. This preceded the continuation UI/rationale assertions. Final focused rerun after continuation: **23 passed, 0 failed, 0 skipped, exit 0**, duration 16.34 seconds, using the same command. Kotlin compiled the real ProjectDraft/policy/settings/capabilities; Java harnesses compiled real wrapper code against Android stubs. JavaScript tests executed the extracted real shim. These tests do not compile Compose or run Android SAF/providers.

| Stage | This session evidence |
|---|---|
| Full quality / Gradle / Android build | NOT_RUN, resource limit and external invoking-shell gates |
| Hosted CI | NOT_RUN, no dispatch authorized |
| Physical child runtime / SAF / chooser recipient delivery | NOT_RUN |
| Second Brain generation/full audit | NOT_RUN; documentation freshness remains external |
| Delivery | NOT_RUN, prohibited |

### Remaining blockers and limits

- Source-to-template SHA derivation is NOT_DONE. `generateFastHtmlIntegrity` hashes compiled APK, AAB and proto manifest bytes. That prevents mismatched artifact metadata but is not an independent digest of source inputs. Runtime checks were preserved; no stale template was accepted or checks disabled.
- APK/AAB/BOTH binary/proto provider authority, configuration/permission parity, version/signing/certificate verification and multi-output atomic publication require additional focused artifact tests and actual build evidence. Source has these mechanisms in separate layers; this session did not establish complete parity or transactionality. Do not treat the matrix as compiled artifact verification.
- Settings aggregation covers pure evaluator rules, not every filesystem, icon decoder, signer, installed toolchain or external host prerequisite. Consequently the requested guarantee against all sequential 0% failures is NOT_DONE. Persistence round-trip and all imported invalid/mixed combinations were not exhaustively executed.
- Declaration-only NFC/wake lock/network/notifications remain declarations, not implemented APIs. Diagnostics disclose this. Primary colors and category are blocked for Android web engines; unsupported provider SDK injection and QR/native media remain blocked. Native Android source projects own their own integration behavior; the wizard cannot certify those projects from field selections.
- Normal-route reasons are now visible before build; Compose compilation and on-device presentation are NOT_RUN. Toolchain readiness and template validation remain mandatory at use.
- Native harness tests cover exact PDF/UTF-8 CSV bytes, CSV quoting/Turkish text in shim, MIME, origin, main-frame transport, token, opt-in, cancel, replay, competing begin, read-back failure and OFF settings. They do not prove actual provider URI expiry, OS grant revocation, process death, malicious external SAF providers or all lifecycle races. Complete requested expiry/path/limit/permission/lifecycle acceptance remains NOT_DONE.
- KAYSERI_ZIP=NOT_AVAILABLE: a focused repository filename inventory found no actual V25 ZIP (a v25-named quality test is not the app source). Ignored/untracked external payloads were not exhaustively searched. The `pdf-transfer.js` CSV legacy `AppForgeFileBridge` adapter requirement remains unresolved pending the real ZIP; no V26 is invented. Real business data, two-minute session behavior, printer templates and storage identities remain unmodified and unverified.

### Device acceptance checklist — all NOT_RUN here

- Restore representative persisted drafts; verify all ten wizard steps retain enabled/OFF, invalid and mixed selections and show every applicable Turkish blocker together. Verify default normal-route rationale and unchanged user choices.
- Build quick APK, AAB and BOTH with freshly derived templates. Inspect binary/proto manifest authority `<package>.appforge.documents`, SDK, permissions, identity, JSON settings and certificate. Corrupt/stale templates must fail; failed second output must not publish a partial success.
- Exercise each normal-routed feature (upload, clipboard/haptics, capture/location consent, fullscreen/orientation, icon, splash/colors, deep links), browser settings and bridge master/OFF behavior. Denial and foreign/subframe origins must fail closed. Confirm no unsupported SDK or API is advertised as working.
- Save PDF and escaped UTF-8 CSV using SAF and compare bytes; cancel chooser, interrupt/navigation/pause/rotation/process death, retry and race competing requests. Verify size/time/cache bounds, traversal rejection, expired URIs and read-only grant behavior with real providers.
- Share PDF/CSV with ACTION_SEND chooser, exact MIME/read grant and readable bytes; separately witness recipient delivery if claimed. Preserve the resolved T20 PDF save and direct chooser behavior.
- Verify custom/debug signing, version/package validation, normal fallback with no silent toggle changes, Windows combined outputs, and external toolchain/host prerequisites.
- Obtain actual Kayseri V25 ZIP before adapting its legacy CSV bridge; verify unchanged business data, printer templates, storage identities and two-minute sessions against that real source.


Final hygiene: `git diff --check` exit 0. `node scripts/wiki-audit.mjs --changed-source-check` exit 0, Yellow, 0 errors / 15 warnings (coverage review, missing frontmatter and oversized pages); this is advisory evidence, not a full Second Brain freshness pass. No commit, branch change, reset, stash, clean, install, device/data operation or delivery was performed. All original dirty files remain.

## Integrated correction continuation — 2026-10-16

The supplied dirty branch and exact HEAD were verified before edits. All pre-existing changes remain; no delivery operation was performed. Earlier 1071/1071 Node, Second Brain and Stability Gate results precede these edits and are not current acceptance evidence.

### Actual changes and matrix amendments

- P0 UI: replaced the enclosing overlay Box with Column. A scrollable warning panel receives 0.4 weight against the step viewport's 1.0 weight, so warning volume cannot consume the complete content viewport. Reasons, aggregated diagnostics and step navigation share that bounded panel; step components keep their own scroll ownership. Every blocking step has a labelled button that changes only `step`, retaining draft selections and picker callbacks. Warnings use a polite accessibility live region. Structural wiring is checked; Compose compile, keyboard/narrow-phone layout and accessibility runtime acceptance are NOT_RUN.
- P0 Native: capability eligibility now admits READY or EXPERIMENTAL only when every requested output is explicitly in readyOutputs. Native EXE remains EXPERIMENTAL with a visible nonblocking Turkish disclosure, never production-ready. Native APK/AAB/Portable/combined mismatches, experimental engines with no outputs, unknown engines, PLANNED and EXTERNAL_TOOL_REQUIRED remain blocked. Real MinGW/source/signing availability remains authoritative runtime I/O, not a pure-settings PASS.
- Step 9 matrix refinement: `windows-native` + `native-exe`/`windows-native-exe` = N (experimental warning); its default Android output and other outputs = B. Static Portable and static Android+Portable = N. Expo APK/AAB/BOTH = N; Expo Windows or combined Windows = B. Kotlin assertions execute these combinations, defaults and unsupported engine families.
- P1 appearance: retained the existing aggregate category/game/none and unsupported primary-color blockers. No unproven category/resource injection or repainting of imported pages was added. The existing appearance matrix remains a settings/consumer inventory, not artifact parity evidence.
- P1 documents: both AppForgeDocuments variants remain equal except package. Existing exact PDF/UTF-8 CSV, save/share, MIME, competing-request and OFF regressions passed unchanged. Added a provider resolver regression executing actual resolver and filename sanitizer methods with stub Context/Uri boundaries. It checks authority/scheme/query/fragment, malformed paths, oversized/empty files, TTL expiry, future timestamps and symlink escape without deleting the external target. This is local JVM/stubbed evidence, not OS URI grants or recipient delivery acceptance.
- P1 packaging: executed existing production Kotlin binary/proto writer regressions, including distinct package authorities, insecure/duplicate provider rejection and wrong proto digest rejection. Fixtures are synthetic/pinned manifests, not freshly compiled Android artifacts. No template or signer checks were bypassed. Source-input provenance, selected-config/permission artifact parity, APK signature/AAB certificate/custom-key execution and atomic BOTH failure-publication coverage remain NOT_DONE; Android artifact generation is blocked by the unavailable configured toolchain.

### Executed evidence for this continuation

`TMPDIR=/workspace/.t21-test-tmp node --test --test-concurrency=1 quality/tests/fast_html_advanced_routing_runtime.test.js quality/tests/html_document_bridge_runtime.test.js quality/tests/html_template_rewrite_runtime.test.js`: **25 passed, 0 failed, 0 skipped**, exit 0 (27.44 seconds). This executes production Kotlin evaluator/policies and manifest writers, actual JavaScript shim, and real Java document logic against Android stubs. The later UI live-region/formatting-only edits do not have Compose compile evidence.

`TMPDIR=/workspace/.t21-test-tmp node --test --test-concurrency=1 quality/tests/t21_share_provider_runtime.test.js`: **1 passed, 0 failed**, exit 0. First harness compilation failed because sanitizer helpers were missing from the extracted test unit; the harness now includes actual helpers and rerun passed. No production validation was removed.

| Stage | Current continuation status |
|---|---|
| TARGETED_TESTS | 25 + 1 tests passed as scoped above; independent parent verification pending |
| FULL_QUALITY / full Node / Stability Gate | NOT_RUN after new edits; parent-shell gates requested |
| ANDROID_BUILD / Compose / compiled APK+AAB | NOT_RUN: ANDROID_HOME/ANDROID_SDK_ROOT not configured, no android-app/local.properties or Gradle wrapper found; full Gradle prohibited by task resource constraint |
| CI | NOT_RUN, dispatch prohibited |
| PHYSICAL_RUNTIME | NOT_RUN, device operations prohibited |
| Source ZIP | SOURCE_ZIP_ADAPTATION=NOT_DONE_MISSING_ZIP |

Overall **NOT_DONE** until the remaining artifact, complete settings/runtime-I/O and physical gates above are independently established. Pure settings eligibility must not be interpreted as installed toolchain readiness, signer validity, source ZIP validity or production readiness. The original ten-step matrix remains the inventory; exhaustive persisted/imported combinations and every filesystem/provider lifecycle failure are not claimed tested. No business source, user records, sessions, signing identity or D1 migration was changed.

Final continuation hygiene: `git diff --check` passed. Snapshot generation and `--check` passed; wiki secret scan Green. Changed-source wiki audit exited 0, Yellow (0 errors, 16 advisory warnings); this is not a full wiki audit. Final UI-only structural rerun passed 2 tests after the accessibility edit, without implying visual acceptance. Full Second Brain audit remains NOT_RUN; snapshot freshness is the executed scope. Temporary harness files/logs were confined to the worktree and removed after use. HEAD and branch remain the supplied values; changes remain uncommitted.

## Final integrated local hardening continuation — 2026-10-16

Identity rechecked: existing dirty isolated branch and HEAD/base
`d1d99cca3a78328cda6d1c1df6d7badc463b98ce` match the supplied guards. All original
tracked/untracked T21 changes and the existing snapshot were preserved. The
owner-supplied prior **1073/1073** full-quality outcome is historical evidence,
not certification of these edits. No delivery or device operation occurred.

Changes in this continuation:
- Shared Fast HTML rationale now requires a static HTML/HTTPS source and Android-only
  outputs before collecting any Android fallback reasons. Node, Expo, Gradle,
  non-HTML local sources, Windows Native/Portable and mixed targets receive no
  misleading Tiny routing explanation. Candidate eligibility uses the same source predicate.
- UI title now explicitly scopes the rationale to HTML/HTTPS Android. Engine-specific
  Turkish prerequisite diagnostics describe Node, Expo, Gradle, Python, Windows host
  and experimental MinGW/CMake needs independently. Informational-only panels no
  longer show an empty “steps needing correction” heading. The bounded warning
  viewport and step navigation are preserved.
- Pure preflight explicitly discloses deferred source/ZIP/HTML/icon, SDK/toolchain,
  template digest and key/certificate I/O. Nonblocking external prerequisites remain
  distinct from blocking unsupported settings; they do not certify tool readiness.
  Expo sourceBuildReady=false and global unsupported injection selections now match
  DeviceBuildEngine guards, including imported native/Windows drafts.
- Category/primary-color support was not invented: all selected unsupported fields
  remain aggregated and editable. Additional executable Kotlin matrix covers both
  web engines, APK/AAB/BOTH, auto/game/none and Dark/Light/OLED/custom primary values.
  Background/bar colors still route through normal runtime; no imported CSS changed.

Ten-step matrix amendments (the detailed original inventory remains above):

| Step | Current treatment / remaining boundary |
|---|---|
| 1 | Identity/source settings checks; Expo source flag matched; actual ZIP/HTML bounds and file existence deferred to runtime. |
| 2 | Permission blockers/notes retained; manifest declarations never imply functioning APIs. |
| 3 | Download/cache/WebView switches retained; JS OFF disclosed; native/OS behavior requires runtime acceptance. |
| 4 | Category and primary blockers aggregated without mutation; icon decoding and normal color rendering require runtime evidence. |
| 5 | Bridge OFF/security guards retained; unsupported automatic QR/media injection rejected across engine families. |
| 6 | Unsupported AdMob/Billing/Firebase injection rejected before engine entry; external configuration cannot implement absent SDKs. |
| 7 | Deep-link validation and separate assetlinks requirement retained. |
| 8 | Missing custom-key selections block; certificate/password/readability checks explicitly deferred without logging secrets. |
| 9 | HTML Android fallback scoped correctly; other engines and Windows get their own prerequisite information; Native remains experimental. |
| 10 | Same evaluator used by UI, offline requirements and engine; pure checks explicitly do not promise complete runtime readiness. |

Packaging review: generateFastHtmlIntegrity derives SHA-256 from compiled APK/AAB
and proto bytes, **not source-input provenance**. Binary/proto rewrite and negative
fixture tests remain intact. Quick BOTH keeps candidates private until both succeed,
checks custom APK certificate against the upload identity, and clears candidates on
failure. Normal Gradle collection copies APK and AAB sequentially into artifactRoot
and assigns JobState fields before later signing/final success. The outer failure
cleanup shown in runBuild is fast-only. Therefore normal BOTH atomic publication
and cleanup on second-output/signing failure remain **NOT_DONE**, requiring a
focused transactional implementation and executable failure harness before acceptance;
this inspection does not establish that failed artifacts are externally downloadable.
Debug APK/AAB certificate parity, normal compiled permission/config parity and source
provenance likewise remain unverified. No digest/signature guard was bypassed.

Document sources were not changed in this continuation. A byte comparison confirmed
both AppForgeDocuments variants equal after the package line. Existing real Java/JS
regressions and provider regression were preserved. SAF, read grants, OS chooser and
recipient delivery still require physical evidence. Kayseri V25 ZIP remains unavailable;
V26 and real user/session/form behavior are NOT_VERIFIED/NOT_DONE. No source app edited.

Executed tests: four-file focused command (fast_html_advanced_routing_runtime,
html_document_bridge_runtime, t21_share_provider_runtime, html_template_rewrite_runtime)
with `TMPDIR=/workspace/.t21-test-tmp`, `node --test --test-concurrency=1`: **26/26 PASS**,
0 skipped, 26.18 seconds. The first run failed 1/26 because the added prerequisite
note called isCandidate for invalid output; corrected short-circuit ordering restored
the existing invalid-output diagnostic. No tests removed or weakened. A subsequent
routing-only rerun includes the final expanded appearance matrix (result recorded below).

| Evidence stage | This continuation |
|---|---|
| TARGETED_TESTS | 26/26 focused tests passed; final appearance-matrix rerun recorded below. Kotlin/Java execution and Android stubs, synthetic/pinned manifests; not compiled Android artifact evidence. |
| FULL_QUALITY | NOT_RUN after edits; parent shell must rerun. Prior 1073/1073 preserved as historical. |
| SECOND_BRAIN | Existing snapshot preserved; now requires parent regeneration/freshness and full audit. Changed-source advisory audit recorded below. |
| STABILITY_GATE | NOT_RUN; parent shell required. |
| ANDROID_BUILD | NOT_RUN; full Gradle prohibited here. Real quick/normal APK/AAB/BOTH artifact and Compose checks remain mandatory. |
| HOSTED_CI | NOT_RUN; dispatch prohibited. |
| PHYSICAL_RUNTIME | NOT_RUN; device operations prohibited. |

Overall **NOT_DONE**: complete artifact provenance/parity/normal atomic publication,
Android/phone presentation and physical document acceptance remain outstanding.

Final routing/appearance rerun: **6/6 PASS**, 0 skipped, exit 0, 11.42 seconds,
using the routing test file alone with the same local TMPDIR. This includes the
final APK/AAB/BOTH appearance matrix. `git diff --check` passed. Changed-source wiki
audit: exit 0, Yellow, 0 errors / 16 advisory warnings. No full audit or snapshot
regeneration was performed; the supplied snapshot bytes were not changed by this
continuation. Branch/HEAD unchanged; all original dirty paths remain. Temporary
harness directory was empty after tests. Files edited in this continuation only:
MainActivity.kt, FastHtmlApkPolicy.kt, WebviewProjectSettings.kt,
fast_html_advanced_routing_runtime.test.js, Device_Build_Runtime_V3.md and this report.

## Targeted legacy-contract regression repair — 2026-10-16

Scope: existing dirty `/workspace` branch
`fix/t21-unified-wizard-fast-html-documents-20261016`; HEAD and supplied base both
`d1d99cca3a78328cda6d1c1df6d7badc463b98ce`. Existing T21 edits and untracked
reports/tests were preserved. No implementation or wiki page changed in this pass.

The complete `/mnt/prior-targeted-failure.log` reports **44 tests, 42 pass, 2 fail**.
Every recorded failing assertion is identified below (no additional failure is
shown in that log):
- `fast_html_apk_v1_contract.test.js`, test originally at line 33,
  “only HTML/HTTPS URL, APK output, debug/local signing supported”.
- `fast_html_ui_recovery_v1_contract.test.js`, test originally at line 41,
  “HTML URL bypass stays limited to APK without touching native or Expo build engines”.
Both failed `assert.match` expecting literal `/engine != "webview-static"/`.
The refactor moved engine eligibility into `hasFastRouteSource`, using the positive
`engine == "webview-static"` predicate shared by candidate routing and explanations.
Inspection found no engine-isolation regression; these were obsolete syntax checks.

Each obsolete assertion now invokes a shared executable Kotlin regression helper,
`quality/tests/helpers/fast_html_policy_regression.js`. It compiles the real
ProjectDraft, FastHtmlApkPolicy, DeviceBuildCapabilities and WebviewProjectSettings
(including WizardReadinessEvaluator); only generated BuildConfig is stubbed.
Compiler/runtime errors propagate, and temporary harnesses are removed in finally.
All other original assertions remain. The matrix exercises local HTML and HTTPS,
APK/AAB/BOTH aliases, every Windows output alias and mixed outputs; rejects foreign
engines including Expo, node-web, android-gradle and Windows engines at candidate
and APK packager preflight boundaries; checks normal classification and absence of
misleading Android fallback reasons; verifies selected upload/camera/microphone/
permissions use normal routing without mutation; rejects unknown permissions,
unsafe Android URLs and custom fast APK signing. Local AAB/BOTH remain independent
fast candidates, while HTTPS AAB/BOTH route normally. This does not execute Android
packaging, native toolchains, installation or OS permissions.

Executed in this pass (both commands exit 0, no skipped/cancelled/todo tests):

```sh
TMPDIR=/workspace/.t21-test-tmp node --test --test-concurrency=1 quality/tests/fast_html_advanced_routing_runtime.test.js quality/tests/fast_html_apk_v1_contract.test.js quality/tests/fast_html_ui_recovery_v1_contract.test.js quality/tests/html_document_bridge_runtime.test.js quality/tests/t21_share_provider_runtime.test.js quality/tests/html_template_rewrite_runtime.test.js
# 39/39 PASS; 46.72 seconds
TMPDIR=/workspace/.t21-test-tmp node --test --test-concurrency=1 quality/tests/web_wrapper_config_contract.test.js
# 6/6 PASS; 0.53 seconds
```

Combined targeted evidence: **45/45 PASS**, including all 44 prior test cases and
the additional template-writer test. Integrated wizard 1–10, PDF/CSV, both document
wrappers and ShareProvider regressions were not edited in this pass. Unsupported
integration and experimental/external prerequisite labels remain unchanged.
`git diff --check` passed. Kotlin/JVM/Java tooling was available and executed.

Files modified by this repair only: the two legacy contract files above, the new
shared helper, and this report. All existing dirty paths remain uncommitted.
Full Quality, Stability Gate, Second Brain regeneration/freshness/full audit,
Android build/Compose/real APK+AAB validation, hosted CI and physical/device gates
are **NOT_RUN in this pass** under the scoped authorization. Parent must regenerate
Second Brain against the changed selected test sources before Full Quality; the
existing snapshot was preserved. Prior normal BOTH atomic-publication/cleanup,
artifact provenance/parity, source ZIP and physical document-delivery blockers
remain unresolved as recorded above. Overall T21 remains **NOT_DONE**. No delivery,
network write, device operation, commit or push occurred.

## Scoped final source remediation — 2026-10-16

Same dirty branch and HEAD/base `d1d99cca3a78328cda6d1c1df6d7badc463b98ce`.
All prior dirty files were retained, including the supplied generated snapshot;
this pass did not regenerate or hand-edit it. No delivery or device commands ran.
The complete 1–10 field/state matrix above remains applicable: (1) identity,
source/engine and import; (2) built-in/additional permissions; (3) downloads/cache
and WebSettings; (4) icon/orientation/fullscreen/colors/category/splash; (5) bridge,
remote opt-in, scanner/media; (6) Ads/UMP/Billing/Firebase; (7) deep links; (8)
signing/key selection; (9) output/SDK/engine requirements; (10) runtime checks.
Unsupported category, primary color/themes and SDK integrations remain blocked
with selections preserved. No Kayseri source ZIP or business data was changed.

Root cause: normal Gradle collection assigned APK before AAB selection/copy, and
only the fast route cleared Android state on failure. New production
`AndroidArtifactPublication.kt` stages all requested selections in a job-private
pending directory, validates canonical bounds, size and ZIP type, calls real
verification before/after copy, compares SHA-256 bytes, checks cancellation during
copy and before/after finalization, and rolls back both directories on failure.
Recovery removes interrupted directories for that same job identity. This is
logical publication with explicit rollback, not a multi-file atomic rename and
not an app-start orphan sweep. Cleanup failure is retained as a suppressed error;
visibility still fails closed even if storage refuses deletion.

DeviceBuildEngine uses ApkVerifier plus Android package identity, consumes AAB
payloads through verifying JarFile with an expanded-byte cap, checks signer
consistency and BOTH certificate parity, and checks custom certificate identity
from the same copied normal-route keystore (PKCS12/JKS/BKS, preserving SAF rather
than applying the fast-route vault restriction). JobState receives Android paths
only after collection succeeds. APK/AAB snapshots and download API require final
success; outer failure cleanup clears Android state for all routes, including a
later Windows-stage failure. Completion and cancellation share a lock; terminal
jobs cannot be cancelled after success. EXE identity/download selection remains
unchanged. Normal AAB proto package identity is not independently parsed here.

The wizard's default informational runtime diagnostic no longer opens a weighted
panel. A lightweight help button expands optional routing/toolchain notes;
blockers appear automatically in a separately scrolling panel capped at 160 dp,
with engine, step, field and navigation. Existing step controls/draft callbacks
remain. Compose source contracts are not Android compilation or phone acceptance.

The original three compiled APK/AAB/proto SHA-256 metadata rows and TemplateIntegrity
parser are unchanged. A separate versioned selected-source fingerprint hashes
sorted relative paths, byte lengths and contents of template src/main plus selected
repository Gradle inputs. Capture runs before template preBuild; integrity generation
requires the same fingerprint after compilation before embedding a separate
`html-template-source-inputs.txt`. It excludes environment, local.properties,
credentials and generated outputs. It is not an artifact digest, full build-input
attestation, reproducible-build claim or signed source attestation. AGP task wiring
and compiled output freshness remain ANDROID_BUILD NOT_RUN. A JVM regression
executes the actual Gradle fingerprint function with a filesystem-only project
boundary, proving deterministic bytes, ignored mtime/generated/local-property
changes, and detection of Java/manifest/Gradle drift.

Executable fault injection compiles the production publisher and uses real files
and ZIPs: missing second output, partially written second copy, verifier rejection,
invalid format, cancellation before/during copy and after first final rename,
second rename failure, interrupted-directory recovery, escaped source, and single
APK/AAB/BOTH success. The verifier rejection is injected: it does not claim actual
Android APK crypto or normal AAB JAR crypto was executed by that harness. Existing
Kotlin routing, synthetic manifest/provider identity, Java/JS PDF/CSV byte handling
and ShareProvider regressions are retained.

Files changed by this pass: AndroidArtifactPublication.kt (new), DeviceBuildEngine.kt,
MainActivity.kt, app/build.gradle.kts, t21_android_publication_runtime.test.js (new),
fast_html_advanced_routing_runtime.test.js, fast_html_apk_v1_contract.test.js,
Device_Build_Runtime_V3.md and this report. The old contract's count of two fast
candidate calls included fast-only cleanup; it now checks the single routing gate
and transactional/all-route visibility wiring. No behavioral test was removed.
The first focused run was 45/46 (stale gate-count assertion); the next auxiliary
run was 12/13 (missing selection log markers). Both were corrected: selection logs
were restored, and the routing contract now reflects all-route cleanup. Final
combined result is recorded below; these earlier failures are not hidden.

### Exact-SHA Android and Compose acceptance plan (not executed)

1. After separate authorization and parent validation, bind an immutable reviewed
   source SHA to the full dirty diff. Current HEAD alone does not identify these
   changes. Never accept an older T20 or pre-remediation hosted result.
2. Review `.github/workflows/android-debug.yml` at that SHA: JDK 21, Gradle 9.3.1,
   SDK 37.0 alias, NDK 28.2.13676358 and CMake 3.22.1; generated-wrapper javac plus
   document/config Node tests; CI signing verification; then
   `gradle -p android-app :app:testDebugUnitTest :app:signingReport :app:assembleDebug --stacktrace`.
   This builds both embedded templates via generation dependencies and executes
   CompiledHtmlTemplateIdentityTest. No device/Compose instrumentation is present
   in this workflow. Keep all release/publishing/test-mode inputs disabled and
   require future explicit dispatch authorization; no dispatch occurred here.
3. Authenticate run head_sha against the reviewed SHA; download only
   `AppForgeStudio-debug-<SHA>` from that successful run. Independently SHA-256 the
   host APK and extract both template assets, three-row integrity metadata and the
   separate source-input record. Recompute artifact/proto pins and selected-source
   fingerprint from the exact checkout; reject missing/stale metadata. Preserve
   raw tool versions, outputs and artifact hashes without key/password material.
4. Use apksigner to verify host APK and generated child APK signatures. On real
   generated children (two distinct custom packages), use aapt/aapt2 binary APK
   manifest inspection and bundletool dump manifest for AAB. Check exact package,
   version/SDK, provider authority `<package>.appforge.documents`, nonexported
   grant-capable provider, permissions and config parity. Synthetic rewrite tests
   do not establish these results. Run quick and normal APK/AAB/BOTH; ensure
   BOTH signer parity and custom selected certificate match. Independently test
   unsigned/bad-signature/missing-AAB/second-copy/cancel failures: no download,
   failed history contains no Android artifacts, no pending/partial files.
5. In a separately authorized Android UI/physical session with that host artifact,
   exercise steps 1–10 on a small phone at normal/large font with keyboard open:
   default help collapsed, every control reachable by scroll, blocker panel below
   controls without overlay, field/engine/step labels and step jumps correct,
   typed drafts survive navigation/help toggles. Test category game/none,
   Light/OLED/custom primary and unsupported SDK toggles remain selected/blocked.
   Verify IME dismissal, rotation and source/project changes without lost values.
6. Physical document acceptance remains separate: exact PDF/UTF-8 CSV bytes for
   save and share, picker cancellation/failure, recipient read grants and received
   bytes, master/share/download/remote OFF and ON, and preserved application data
   and signing identity. No ADB/install/data/keystore action was authorized here.

| Evidence stage | This remediation |
|---|---|
| TARGETED_TESTS | Final combined result recorded below; JVM/Java/JS plus structural contracts only. |
| FULL_QUALITY | NOT_RUN; parent runner must rerun all 1073 historical tests plus additions. |
| SECOND_BRAIN | NOT_RUN; parent must regenerate/check snapshot and full audit. |
| WIKI_SECURITY | PASS: `python3 scripts/wiki-secret-scan.py`, Green. |
| STABILITY_GATE | NOT_RUN; parent runner owns this gate. |
| ANDROID_BUILD | NOT_RUN; no Gradle/AGP/compiled manifest or Compose acceptance claimed. |
| HOSTED_CI | NOT_RUN; no dispatch/network write authorized. |
| PHYSICAL_RUNTIME | NOT_RUN; device and user-data operations prohibited. |

Outstanding NOT_DONE: exact-SHA Android build and real APK/AAB manifests/signatures,
normal AAB proto package verification, actual Gradle fingerprint task ordering,
phone/Compose and document recipient acceptance, Kayseri V25 source ZIP unavailable,
and independent parent Full Quality/Second Brain/Stability. Overall T21 is not an
accepted build or release. No commit or push.

Final combined targeted command (exit 0):

```sh
node --test --test-concurrency=1 quality/tests/fast_html_advanced_routing_runtime.test.js quality/tests/fast_html_apk_v1_contract.test.js quality/tests/fast_html_ui_recovery_v1_contract.test.js quality/tests/html_document_bridge_runtime.test.js quality/tests/t21_share_provider_runtime.test.js quality/tests/html_template_rewrite_runtime.test.js quality/tests/web_wrapper_config_contract.test.js quality/tests/t21_android_publication_runtime.test.js quality/tests/deterministic_gradle_artifact_selection_contract.test.js
```

**52/52 PASS**, 0 skipped/cancelled/todo, 66.64 seconds. Includes the prior 45
focused cases, five selector contracts and two new executable publication/provenance
cases (each executes multiple fault/mutation scenarios). `git diff --check` passed.
Changed-source wiki audit earlier returned Yellow, 0 errors / 20 advisory warnings;
final documentation/security check recorded below. Android integration source is
not compiled by this JVM-only command. All full/hosted/device gates remain NOT_RUN.

Final advisory audit: Wiki health: Yellow (errors: 0, warnings: 20). Final wiki secret scan Green; diff whitespace check passed.


## Narrow test cwd/source-path repair — 2026-10-16

Verified existing dirty branch `fix/t21-unified-wizard-fast-html-documents-20261016`;
HEAD/base remain `d1d99cca3a78328cda6d1c1df6d7badc463b98ce`.
Reproduced the target test from `/workspace/quality` before repair: exit 1,
2 tests / 0 pass / 2 fail, duration_ms 4074.910311. The production Kotlin source
argument was relative to process cwd and failed compilation with source-not-found;
the Gradle source read failed ENOENT for the same reason.

Only test path resolution changed: repository root comes from
`fileURLToPath(new URL('../../', import.meta.url))`; production Kotlin, Gradle
source and temporary harness paths now resolve absolutely from that root.
The real production Kotlin publisher compilation/execution, rollback/fault/
cancellation checks and deterministic Gradle fingerprint assertions are unchanged.
No production source or fingerprint/hardening implementation changed in this pass.

Post-repair executions (both exit 0):

```text
cwd /workspace
node --test --test-concurrency=1 quality/tests/t21_android_publication_runtime.test.js
✔ production Kotlin Android publication rolls back faults and recovers without exposing partial sets (7720.614737ms)
✔ Gradle selected-source fingerprint executes deterministically and detects source/config drift (5861.556873ms)
ℹ tests 2
ℹ suites 0
ℹ pass 2
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 13866.149473

cwd /workspace/quality
node --test --test-concurrency=1 tests/t21_android_publication_runtime.test.js
✔ production Kotlin Android publication rolls back faults and recovers without exposing partial sets (7066.706664ms)
✔ Gradle selected-source fingerprint executes deterministically and detects source/config drift (6469.664112ms)
ℹ tests 2
ℹ suites 0
ℹ pass 2
ℹ fail 0
ℹ cancelled 0
ℹ skipped 0
ℹ todo 0
ℹ duration_ms 13802.060619
```

`git diff --check` passed. Existing T21 changes remain in place. Only this report
and `quality/tests/t21_android_publication_runtime.test.js` were edited in this
pass. FULL_QUALITY, Second Brain and Stability are NOT_RUN here and remain with
the parent runner; no Android/hosted/device acceptance or delivery is claimed.

### Narrow terminal-state race repair (2026-10-16)

Same supplied dirty branch and HEAD; prior T21 changes preserved. Inspection found normal completion could overwrite cancellation after its last cancellation check; fast completion wrote progress=100 before its locked cancellation check; catch could observe cancelled=false then overwrite a concurrent cancellation with failed; advanceProgress could overwrite terminal status. Windows snapshot/download access lacked the existing Android success guard.

DeviceBuildTerminalState is now the production superclass of JobState, retaining volatile status/progress and atomic cancellation. Success, failure, cancellation and descriptive progress transitions share the job monitor. The first terminal transition wins: cancellation before completion stays cancelled, success before cancellation rejects cancellation, failure rejects subsequent completion, and repeated cancellation returns false. Only accepted completion sets 100 and adds its success-only completion log. Duplicate out-of-lock cancellation assignment is removed. Snapshot captures status and artifact visibility under that monitor; APK/AAB and both Windows artifact kinds require successful, uncancelled completion. Artifact filesystem checks, shell cancellation and existing cleanup remain outside the monitor. Existing successful results remain downloadable.

Changed in this narrow continuation only:
- android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt
- android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildTerminalState.kt (new)
- quality/tests/t21_terminal_state_runtime.test.js (new)
- docs/reports/t21-unified-development.md

Final regression commands: from /workspace, `node --test quality/tests/t21_terminal_state_runtime.test.js`; from /workspace/quality, `node --test tests/t21_terminal_state_runtime.test.js`. Both: 2 passed, 0 failed, 0 skipped. Each compiles and executes the real production Kotlin helper with 600 latch-ordered cross-thread scenarios (no sleep-based ordering), covering cancellation/success/failure ordering, repeated cancellation, terminal progress protection, success-only logs and the visibility predicate. Supplementary source assertions verify engine wiring and both Windows artifact guards; they do not execute Android snapshot/download APIs. Related fast_html_apk_v1_contract, fast_html_ui_recovery_v1_contract and t21_android_publication_runtime tests: 15 passed, 0 failed, 0 skipped. `git diff --check`: exit 0. Advisory changed-source wiki audit: exit 0, Yellow, 0 errors/20 warnings across the existing dirty worktree; no full freshness acceptance implied.

Android engine/Compose compilation, full quality suite, hosted CI and physical device/runtime/download acceptance: NOT_RUN. The JVM helper harness requires no Android SDK and does not establish Android integration acceptance. No full gate PASS claimed. No delivery actions; HEAD unchanged and worktree intentionally dirty.


### Narrow Windows publisher fail-closed contract repair (2026-10-16)

Preserved the existing dirty `/workspace` worktree, branch
`fix/t21-unified-wizard-fast-html-documents-20261016` and HEAD/base
`d1d99cca3a78328cda6d1c1df6d7badc463b98ce`. Before editing, independently
reproduced the negative publisher suite failure: 10/11 passed; only the obsolete
outer-catch direct `state.status = "failed"` assertion failed. The real Kotlin
terminal-state regression independently passed 2/2 before repair.

Source inspection confirms requested signing errors delete `finalWindowsExe`,
clear `state.exe`, and rethrow `signingFailure` into the outer build catch. That
catch immediately calls `state.fail` with the propagated error. The synchronized
production transition preserves cancellation or establishes terminal failure;
subsequent completion cannot replace failure. Snapshot visibility and artifact()
require successful, uncancelled completion. Completion follows signing and its
post-verification on the same runBuild path; the fast completion returns before
that path. No production defect requiring a source change was found.

Changed only the obsolete assertion in
`quality/tests/windows_publisher_signing_negative_contract.test.js`: it now
requires the outer Throwable catch to immediately call the actual terminal
`state.fail(...)` API with the caught error message/class. Every existing signing
security assertion, including EXE deletion, null artifact state and rethrow,
remains intact. No production or Kotlin regression source changed in this pass.

Post-repair commands (both exit 0, no skipped/cancelled/todo tests):

- cwd `/workspace`: `node --test quality/tests/windows_publisher_signing_negative_contract.test.js quality/tests/t21_terminal_state_runtime.test.js` — 13/13 PASS (publisher 11/11; terminal 2/2).
- cwd `/workspace/quality`: `node --test tests/windows_publisher_signing_negative_contract.test.js tests/t21_terminal_state_runtime.test.js` — 13/13 PASS (publisher 11/11; terminal 2/2).

Each terminal run compiles the real production Kotlin transition class and runs
600 latch-ordered scenarios, including failure versus completion and artifact
visibility; engine catch/download wiring remains source-contract evidence,
not execution of the Android engine or a physical signing provider.

`git diff --check` passed. Full Quality was not rerun: the supplied prior failure
remains historical evidence, and this narrow repair does not establish a full-suite
PASS. Android/Compose compilation, hosted CI, physical signing/download acceptance,
Second Brain freshness and Stability remain open/unexecuted in this pass. Overall
T21 remains NOT_DONE. No commit, push, merge, rebase, reset, clean, stash, CI, Play,
deploy, install, uninstall or user-project/data action was performed.

### One-pass obsolete terminal contract migration (2026-10-16)

Verified the supplied dirty `/workspace` branch and unchanged HEAD
`d1d99cca3a78328cda6d1c1df6d7badc463b98ce`; preserved prior dirty changes.
Audited quality test expectations for direct state status/progress assignments
and cancellation flag writes. Three obsolete positive expectations were found:
fast_web_535_v151_upload_signing_contract, build_progress_flow, and
 device_build_cancel_reader_contract (all under quality/tests). Migrated only
these contracts to the actual synchronized terminal APIs. Existing negative
assignment guards and valid success-read guards remain. The earlier Windows
publisher negative-contract repair was retained without edits.

The V15.1 contract retains every key/PKCS12/certificate and candidate cleanup
assertion, and now checks fast completion after cancellation checking, guarded
success/logging, synchronized snapshot visibility for both Android outputs,
download success/cancellation gating and outer failure transition. Progress
checks require the real completion API and exclusive terminal 100 write;
cancellation checks follow the guarded cancellation call through to the real
flag write before shell teardown. No production source was changed.

Expanded t21_terminal_state_runtime.test.js: each of its 600 latch-ordered real
Kotlin transition scenarios starts at progress 73, with cancellation/failure
required to retain that milestone. Added three candidate visibility cases using
the production superclass/predicate: first APK only and then APK+AAB remain hidden
before terminal success; cancellation/failure reject completion and hide both;
accepted success reveals both. Candidate fields/snapshot are harness models;
Android engine snapshot/download APIs are source-contract evidence, not executed
Android integration. The existing production publication fault harness remains
unchanged and was rerun.

Initial local combined run: 26/27; an overbroad new no-assignment assertion also
matched existing intermediate engine progress assignments. Narrowed it to terminal
status/100 writes; no production change or removed test. Final commands, exit 0:

```sh
# cwd /workspace
node --test --test-concurrency=1 quality/tests/fast_web_535_v151_upload_signing_contract.test.js quality/tests/windows_publisher_signing_negative_contract.test.js quality/tests/t21_terminal_state_runtime.test.js quality/tests/t21_android_publication_runtime.test.js quality/tests/build_progress_flow.test.js quality/tests/device_build_cancel_reader_contract.test.js
# cwd /workspace/quality
node --test --test-concurrency=1 tests/fast_web_535_v151_upload_signing_contract.test.js tests/windows_publisher_signing_negative_contract.test.js tests/t21_terminal_state_runtime.test.js tests/t21_android_publication_runtime.test.js tests/build_progress_flow.test.js tests/device_build_cancel_reader_contract.test.js
```

Both final runs: **27/27 PASS**, zero failed/skipped/cancelled/todo;
duration_ms 26890.934937 and 28336.068062 respectively. `git diff --check`
passed. Only the three contract files above, terminal runtime regression and this
report changed in this pass. Full Quality remains for the external wrapper; no
Full Quality PASS claimed. Android build/CI and physical acceptance remain NOT_RUN;
Second Brain/Stability were not run. Overall T21 remains NOT_DONE. No delivery,
device, user-data or prohibited Git operations occurred.
