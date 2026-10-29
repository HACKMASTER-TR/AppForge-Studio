# V10 Phase 8.1 — quality recovery

Date: 2026-10-29. Verification in progress.

## Identity and scope

Branch `feat/v10-oss-unified-20261028`; supplied base and unchanged HEAD
`2ecf73fcbda900a2a658da72fff90ea44bf76b63`. Initial dirty paths were
`.github/workflows/v10-host-verification.yml`,
`quality/tests/v10_host_verification_contract.test.js`, and untracked
`docs/engineering/v10/phase8-report.md`. These Phase 8 edits are preserved.
No main reference is available; Phase 8 records the V9 merge-base as
`afb3ad9209bcce8b84773d8873d037b15b4d804d`.

## All 35 previous nonpassing tests

The raw Phase 8 log is `/data/data/com.termux/files/usr/tmp/v10-phase8-quality.log`.
Its summary is 1153 tests, 1118 pass, 35 fail, zero skipped, exit 1.
The command is `npm --prefix quality test`, which executes
`node --test tests/*.test.js` from quality. The following is the complete
failure inventory recovered from the log's final failure section.

| # | Test location and name | Actual cause |
|---|---|---|
| 1 | `tests/advanced_permission_route_runtime.test.js:11:1` — all permission aliases execute normalization, restored toggles and actual manifest generation (2554.984165ms) | Java runtime abort: ProtoLogNative registration |
| 2 | `tests/apt_acquire_payload_runtime.test.js:9:1` — APT original acquire worker measures live known/unknown HTTP bodies and interruption without parsing console percentages (73.995469ms) | APT HTTP method absent |
| 3 | `tests/apt_acquire_payload_runtime.test.js:52:1` — APT index observer remains compatible with fail-fast installer shells (45.844323ms) | Hard-coded /tmp fixture creation denied |
| 4 | `tests/apt_snapshot_lock_runtime.test.js:76:1` — actual snapshot transaction rejects insufficient download space before acquisition (156.286146ms) | Hard-coded /tmp fixture creation denied |
| 5 | `tests/fast_html_advanced_routing_runtime.test.js:11:1` — real Kotlin policy enforces output-aware settings and Android URL security (2421.26677ms) | Java runtime abort: ProtoLogNative registration |
| 6 | `tests/fast_html_advanced_routing_runtime.test.js:273:1` — actual Java media handler denies disabled, denied and foreign requests and grants only selected resources (470.801822ms) | Java runtime abort: ProtoLogNative registration |
| 7 | `tests/fast_html_advanced_routing_runtime.test.js:317:1` — production file picker honors OFF, camera consent and Android 8 fallback (421.845833ms) | Java runtime abort: ProtoLogNative registration |
| 8 | `tests/fast_html_advanced_routing_runtime.test.js:380:1` — actual DeviceBuildEngine capability guard accepts implemented quick QR/media and still rejects absent SDK/normal-engine injection (1988.842655ms) | Java runtime abort: ProtoLogNative registration |
| 9 | `tests/fast_html_apk_v1_contract.test.js:34:1` — only HTML/HTTP(S) URL, APK output, debug/local signing supported (2298.390416ms) | Java runtime abort: ProtoLogNative registration |
| 10 | `tests/fast_html_billing_runtime.test.js:11:1` — production Billing controller uses SDK readiness/offers and never completes pending, unverified or unacknowledged purchases (421.159687ms) | Java runtime abort: ProtoLogNative registration |
| 11 | `tests/fast_html_location_runtime.test.js:10:1` — real local location controller requires consent, actual permission and fresh provider fixes; rejects stale/denied/canceled requests (567.067812ms) | Java runtime abort: ProtoLogNative registration |
| 12 | `tests/fast_html_media_runtime.test.js:11:1` — actual private media service enforces local assets, preparation/focus, controls, denial, cancellation and cleanup (461.238229ms) | Java runtime abort: ProtoLogNative registration |
| 13 | `tests/fast_html_ui_recovery_v1_contract.test.js:42:1` — HTML URL bypass stays limited to APK without touching native or Expo build engines (2418.299374ms) | Java runtime abort: ProtoLogNative registration |
| 14 | `tests/fast_html_v2_runtime.test.js:15:1` — V2 production picker state drains stale results, bounds selection and rejects unsafe URI/MIME input (506.126146ms) | Java runtime abort: ProtoLogNative registration |
| 15 | `tests/fast_html_v2_runtime.test.js:40:1` — V2 executes shipped native actions: opt-in, bounds, permission denial, channel blocking and actual notification dispatch (676.816145ms) | Java runtime abort: ProtoLogNative registration |
| 16 | `tests/fast_html_v2_runtime.test.js:120:1` — V2 real connectivity observer tracks validated restoration and drops paused/stale callbacks (522.943437ms) | Java runtime abort: ProtoLogNative registration |
| 17 | `tests/fast_html_v2_runtime.test.js:152:1` — V2 actual foreground wake controller releases on pause, is idempotent and rolls back failed acquire (420.323959ms) | Java runtime abort: ProtoLogNative registration |
| 18 | `tests/fast_html_v2_runtime.test.js:173:1` — V2 real HTTPS media/location controller requires opt-in, trusted tap, explicit consent and actual Android grant; stale and concurrent requests deny (422.990156ms) | Java runtime abort: ProtoLogNative registration |
| 19 | `tests/fast_html_v2_runtime.test.js:214:1` — V2 deep-link receiver validates bounded scheme/host/path data and never accepts injected or traversal URLs (399.827448ms) | Java runtime abort: ProtoLogNative registration |
| 20 | `tests/fast_html_v2_runtime.test.js:226:1` — V2 actual NFC reader only delivers one bounded foreground ID; hardware failures, timeout, pause and stale results deny (454.570989ms) | Java runtime abort: ProtoLogNative registration |
| 21 | `tests/fast_html_v2_runtime.test.js:250:1` — V2 real foreground scanner handles camera denial, reentry, decoded result, timeout, surface failure and cleanup (494.388854ms) | Java runtime abort: ProtoLogNative registration |
| 22 | `tests/html_document_bridge_runtime.test.js:413:27` — android-app/app/src/main/assets/device-build: execute native bytes, SAF, intents, failure and security regressions (448.11927ms) | Java runtime abort: ProtoLogNative registration |
| 23 | `tests/html_document_bridge_runtime.test.js:413:27` — android-app/html-apk-template/src/main/java/com/appforge/htmlstub: execute native bytes, SAF, intents, failure and security regressions (617.577604ms) | Java runtime abort: ProtoLogNative registration |
| 24 | `tests/html_template_rewrite_runtime.test.js:31:1` — production Kotlin writers isolate provider authority and reject insecure/colliding templates (2096.807291ms) | Java runtime abort: ProtoLogNative registration |
| 25 | `tests/http_web_url_runtime.test.js:9:1` — actual Tiny HTTP navigation method accepts selected host and ports without secure-origin or cross-host bypass (459.19427ms) | Java runtime abort: ProtoLogNative registration |
| 26 | `tests/jvm_sdk_network_runtime.test.js:9:1` — SDK JFR observer measures real socket reads, keeps unknown total and exits with the JVM (563.298072ms) | Java runtime abort: ProtoLogNative registration |
| 27 | `tests/local_network_permission_runtime.test.js:6:1` — actual two-engine LAN gate executes OS/target/grant/deny/retry/revoke/recovery without permission loops (HOST simulation) (440.235104ms) | Java runtime abort: ProtoLogNative registration |
| 28 | `tests/offline_build_preflight_gate_v1_contract.test.js:218:1` — Builder preflight keeps settings failures local and routes only missing dependencies (1680.058958ms) | Java runtime abort: ProtoLogNative registration |
| 29 | `tests/second_brain_snapshot_v2_contract.test.js:187:1` — V2 generator check reproduces exact committed snapshot (288.628072ms) | Second Brain snapshot stale after Phase 8 contract edit |
| 30 | `tests/t21_android_publication_runtime.test.js:9:1` — production Kotlin Android publication rolls back faults and recovers without exposing partial sets (1733.713801ms) | Java runtime abort: ProtoLogNative registration |
| 31 | `tests/t21_android_publication_runtime.test.js:57:1` — Gradle selected-source fingerprint executes deterministically and detects source/config drift (1949.885312ms) | Java runtime abort: ProtoLogNative registration |
| 32 | `tests/t21_share_provider_runtime.test.js:9:1` — T21 provider actual resolver rejects expired, oversized, foreign and escaping files (stubbed Android boundary) (357.238489ms) | Java runtime abort: ProtoLogNative registration |
| 33 | `tests/t21_terminal_state_runtime.test.js:11:1` — real Kotlin job transitions serialize cancel, success, failure and artifact visibility (1701.834739ms) | Java runtime abort: ProtoLogNative registration |
| 34 | `tests/v10_build_telemetry_runtime.test.js:11:1` — real Gradle fixture records first, no-op, changed-input and failed task outcomes (877.29151ms) | Java runtime abort: ProtoLogNative registration |
| 35 | `tests/v10_offline_catalog_runtime.test.js:10:1` — real Kotlin offline catalog validates identity, licenses, ABI and cache without mutation (1914.264322ms) | Java runtime abort: ProtoLogNative registration |

31 tests aborted before exercising their assertions because the default Termux
JVM could not register `com/android/internal/protolog/ProtoLogNative` (javac,
java, kotlinc or Gradle; SIGABRT/134). One APT test could not execute Ubuntu's
`/usr/lib/apt/methods/http`; two could not create their hard-coded `/tmp`
fixtures. These are environment failures, not demonstrated application defects.
The remaining failure was `SNAPSHOT_OUT_OF_DATE`: Phase 8 changed a selected
quality contract and workflow without regenerating the source-basis snapshot.

## Recovery and comparison

No V10 application defect was found in the original failure diagnostics.
The sole tracked recovery change is canonical regeneration through
`python3 scripts/generate-second-brain-snapshot.py`; only `sourceBasisSha256`
changed in `android-app/app/src/main/assets/second_brain_snapshot.json`.
No tests, security rules, advisory exclusions or production code were weakened.
The Electron local binary installer repair remains intact.

Phase 3 recorded Termux 1148/1151 with three APT environment failures and PRoot
1151/1151 with zero skips. Phase 6's first PRoot run had one stale snapshot;
after canonical regeneration and freezing source it passed 1152/1152, zero skips.
This recovery reuses the exact command array from
`.appforge/tmp/v10/phase6/quality-frozen-result.json`, including workspace bind,
`.appforge/tmp/v10/phase4/linux-tmp:/tmp`, Termux curl bound to `/usr/bin/curl`,
phase4 isolated HOME, explicit Java 25 JAVA_HOME, and Ubuntu-first PATH.
No package installation into global Termux was performed.

Current logs and command/exit records are under ignored
`.appforge/tmp/v10/phase81/`. Prior Phase 3/6 logs and real Gitleaks/OSV evidence
remain untouched. Phase 6 real OSV evidence retains all 10 advisories, including
four HIGH; none were suppressed. Those historical scans are not a fresh clean
security acceptance for this dirty checkout.
