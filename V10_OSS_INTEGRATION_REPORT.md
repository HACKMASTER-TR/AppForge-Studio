# V10 OSS integration report — continuation

**Overall BLOCKED after genuine OSV findings. This work is uncommitted and is not accepted for release.** The OSV continuation below supersedes earlier PARTIAL/NOT_RUN labels; earlier stage evidence remains historical. The [first-pass report](docs/engineering/v10/first-pass-report.md) and original verification records are retained as history.

## Identity and safeguards

Continued the supplied isolated clone on `feat/v10-oss-unified-20261028`. Base and final HEAD remain `afb3ad9209bcce8b84773d8873d037b15b4d804d`; existing uncommitted edits were retained. No reset, branch switch, clone, ZIP replay, commit, push, PR, merge, deploy, release, signing identity change or APK installation occurred. Origin push remains `file:///APPFORGE_V10_PUSH_FORBIDDEN`; existing pre-commit/pre-push hooks remain intact. Live Termux repository, V9 auth/revocation/witness code, editor/LSP, signing configuration and rootless device compilation were not changed.

## Diagnosed blockers and actual changes

- **Quality:** inherited `TMPDIR=$PREFIX/tmp` run is 1146/1149 (exit 1). The three failures are exactly `APT original acquire worker measures live known/unknown HTTP bodies and interruption without parsing console percentages`, `APT index observer remains compatible with fail-fast installer shells`, and `actual snapshot transaction rejects insufficient download space before acquisition`. The first requires Linux `/usr/lib/apt/methods/http`; the others explicitly use absolute `/tmp`, so TMPDIR alone cannot fix them. No APT source/test was mocked or weakened. Both complete APT files passed 14/14 (exit 0) in existing Ubuntu 26.04 PRoot using its real APT HTTP method. Node is the existing Termux executable inside PRoot; this is separate targeted Linux evidence, not a full native Linux quality pass.
- **History:** read-only fetch with `--no-tags --no-write-fetch-head` populated `44614aa3c77925934b1eaf43ffafdf2dcbfe9fba` without branch changes. `git ls-tree` and `git show` prove that commit contains both TinyHtmlActivity and AppForgeLocalAssets. File history includes introduction `be36094` and update `0236be9`. Original `/android_asset/site/` + `Uri.Builder().scheme("file")` and HTTPS START_URL checks pass unchanged. The assertion was unavailable in the shallow clone, not intrinsically invalid.
- **Windows:** unchanged lockfile SHA-256 `812623db44dcb94bb236c7e3259c081abcbc0c30552e93af15000d4fa3aeab2d`; adm-zip 0.6.0 MIT archive uses lockfile SHA-512 `XleryMhbuksdKtofnWZ9Sk+4CUTbms4Mb/EU32SZwToAyZ5RgVos/ki8n+yr0LWHOGKuakbXTuuYNHLQjhddgg==`. Workspace-scoped `npm ci --ignore-scripts --no-audit --no-fund` installed 285 packages with integrity enforcement and no lifecycle execution (exit 0). Node tests now pass 7/7 (exit 0). Electron 43.4.1/electron-builder 26.15.3 remain unchanged, no global reinstall. Physical packaging/signature/EXE execution NOT_RUN.
- **Android:** project pins AGP 9.1.1, Compose plugin 2.4.10, compileSdk 37, NDK 28.2.13676358, Java source/target 17; CI uses JDK 21/Gradle 9.3.1, no wrapper in this clone. Existing Termux JDK 25.0.3/Gradle 9.6.0 runs host fixtures. Initial offline unit/debug attempts exit 1 for absent AGP. Exact dependency prefetch in `.appforge/tmp/v10/continuation/network-gradle-cache` resolved unchanged AGP 9.1.1 from configured repositories. Official Google POM returned 200; cached JAR matches Google's published SHA-1, and local SHA-256s are recorded in [android-cache-manifest.json](docs/engineering/v10/android-cache-manifest.json). Published SHA-1 consistency is not a modern signature/independent SHA-256 attestation. Online unit attempt and both subsequent offline unit/debug attempts exit 1 for **SDK location not found** before compilation. No SDK/NDK, signing or APK install performed; disk had ~363 GiB available, so disk is not the observed blocker. Do not install an incompatible Linux x86 SDK into Android or change AGP to conceal this.
- **Safe implementation extension:** original telemetry now catches graph-output I/O failure and reports `UNAVAILABLE` without failing valid Gradle work or printing exception paths. Runtime tests independently compare compiled fixture bytes with telemetry disabled and with an unavailable graph output, plus first/no-op/changed/failing outcomes. This is an original Copy-task host timing, not a CodeAssist import, cache-invalidation implementation or Android performance claim. No GPL source copied.
- **Evidence repair:** all 13 immutable HEAD license-file hashes rechecked successfully; uv MIT file verified in addition to Apache. Sora 0.24.7 AAR matches SHA-256 `cdfdacdb3ac8605b190768d93f504935400c9aa37b773dc6f6c840311faf2b4c`; zip inventory has no `jni/` entries or notices. It was inspected in memory and not imported. LGPL distribution/source/relinking, transitive license, parser ABI and memory gates remain open. [integrations.json](docs/engineering/v10/integrations.json) now records audited status/source/tests instead of stale NOT_STARTED labels; [integrations.initial.json](docs/engineering/v10/integrations.initial.json) preserves the original inventory bytes and hash.

## All 13 audited rows

DONE is limited to the original methodology adaptation. No upstream library/binary is newly shipped. SPDX observations at upstream HEAD do not approve release artifact/transitive distribution. GPL/LGPL GitHub legacy identifiers are retained as observations rather than inventing an `only`/`or-later` grant. Termux packages require individual SPDX review.

| Candidate | Status | Actual level | Observed version / license | Evidence / blocker |
| --- | --- | --- | --- | --- |
| codeassist | **PARTIAL** | reference-only; original observer integrated | `v3.33.0` / GPL-3.0 | No GPL import. Original baseline/observed fixture verifies equal output and telemetry I/O failure compatibility. No cache-invalidation implementation or device benchmark. |
| sora-editor | **BLOCKED** | reference-only | `0.24.7` / LGPL-2.1 | Verified core AAR hash only. No notices in AAR; LGPL relinking/source delivery, dependency closure, parser ABI, memory and editor parity unresolved. |
| tree-sitter | **BLOCKED** | reference-only | `v0.27.1` / MIT | No selected binding/grammar/versioned Android native library; Sora core AAR contains no jni entries. ABI and memory not proven. |
| termux-packages | **PARTIAL** | integrated original catalog preview | `bootstrap-2026.10.25-r1+apt.android-7` / NOASSERTION | Dry-run only; no reviewed real package set, package-specific SPDX/notice closure or prepare/activate/rollback integration. Bionic cannot satisfy GNU ABI. |
| bundletool | **BLOCKED** | reference-only | `1.18.3` / Apache-2.0 | Metadata digest only. No verified local JAR, shaded license closure, valid AAB/APKS fixture, signing identity or reproducibility test. |
| gitleaks | **PARTIAL** | CI-only optional wrapper | `v8.30.1` / MIT | Real scanner NOT_RUN: ARCHIVE_MISSING; license_closure_reviewed=false. Redaction/status tests do not prove scanner/platform acceptance. |
| osv-scanner | **BLOCKED** | CI-only fail-closed stub | `v2.7.0` / Apache-2.0 | No verified DB origin/digest/freshness/coverage or enforced network isolation. Offline request returns BLOCKED, exit 3, without scan. |
| syft | **PARTIAL** | CI-only optional wrapper | `v1.55.0` / Apache-2.0 | Real scanner/SBOM NOT_RUN: ARCHIVE_MISSING; license closure unreviewed. Manifest-only SBOM sanitization tests are not real inventory acceptance. |
| esbuild | **BLOCKED** | reference-only; existing probe retained | `v0.28.2` / MIT | Existing transform probe is not a meaningful bundled Web target. No equivalent bundling fixture, measured benefit or Android executable acceptance. |
| spec-kit | **DONE** | reference-only; original methodology integrated | `v1.2.4` / MIT | DONE covers original specification/contracts under AGENTS.md only; no upstream runtime/template imported. |
| superpowers | **DONE** | reference-only; original methodology integrated | `v7.0.1` / MIT | DONE covers test/verification discipline under AGENTS.md only; no external prompt authority imported. |
| jadx | **BLOCKED** | reference-only | `v1.5.6` / Apache-2.0 | Release ZIP digest is metadata only. No reviewed transitive notices, supported JVM execution, bounded analysis adapter or user-APK fixture corpus. |
| uv | **BLOCKED** | reference-only | `0.13.6` / Apache-2.0 OR MIT | Dual Apache-2.0 OR MIT HEAD license verified; no Android/Bionic ARM64 support or Chaquopy coexistence proof. No host toolchain/environment replacement. |

Counts after audit: **DONE=2, PARTIAL=4, BLOCKED=7**. Original observer/catalog are integrated project-owned code; CodeAssist itself is reference-only. Gitleaks/Syft have optional disabled-by-default CI adapters, not successful real scans. OSV is a fail-closed stub, not a vulnerability integration. Spec Kit/Superpowers remain subordinate to AGENTS.md.

Each row's exact upstream HEAD, license SHA-256, source/test paths, metadata version and provenance are in the audited inventory. [upstream-evidence.json](docs/engineering/v10/upstream-evidence.json) retains published artifact digests and initial-inventory hash, with a separate current audited-inventory hash. [continuation-upstream-audit.json](docs/engineering/v10/continuation-upstream-audit.json) records new local license/AAR byte verification. Release metadata is not local binary verification. Gitleaks/Syft `license_closure_reviewed=false` pins remain fail-closed; no override was added. No real OSV DB, Gitleaks scan, Syft SBOM, JADX analysis, bundletool AAB/APKS validation or uv device environment is claimed.

## Commands and exit codes

Complete records, test counts and ignored-log SHA-256s: [continuation-verification.json](docs/engineering/v10/continuation-verification.json). Earlier failures remain there, including a transient stale-snapshot failure after telemetry changes; the snapshot was then regenerated canonically. Logs stay under ignored `.appforge/tmp/v10/continuation/` and are not uploaded.

| Stage | Command | Exit / result |
| --- | --- | --- |
| quality | `npm --prefix quality test` | 1; {'tests': 1149, 'pass': 1146, 'fail': 3} |
| stability | `sh scripts/appforge-stability-gate ci` | 0 |
| v9 | `python3 -m unittest discover -s quality/tests -p "test_v9_*.py"` | 0 |
| v10 | `node --test quality/tests/v10_build_telemetry_runtime.test.js quality/tests/v10_offline_catalog_runtime.test.js quality/tests/v10_oss_gate_runtime.test.js` | 0; {'tests': 3, 'pass': 3, 'fail': 0} |
| v10_python | `python3 -m unittest discover -s quality/tests -p test_v10_oss_gate.py` | 0 |
| android_unit | `gradle -p android-app :app:testDebugUnitTest --offline --no-daemon` | 1 |
| android_debug | `gradle -p android-app :app:assembleDebug --offline --no-daemon` | 1 |
| secret_hygiene | `python3 scripts/secret-hygiene-guard.py` | 0 |
| gitleaks | `python3 quality/tools/appforge_v10_oss_gate.py gitleaks --cache .appforge/tmp/v10/empty-cache` | 2 |
| syft | `python3 quality/tools/appforge_v10_oss_gate.py syft --cache .appforge/tmp/v10/empty-cache` | 2 |
| osv | `python3 quality/tools/appforge_v10_oss_gate.py osv-scanner --cache .appforge/tmp/v10/empty-cache --offline` | 3 |
| stability_hardened | `sh scripts/appforge-stability-gate ci` | 1 |
| android_unit_prefetched | `gradle -p android-app :app:testDebugUnitTest --offline --no-daemon --gradle-user-home $PWD/.appforge/tmp/v10/continuation/network-gradle-cache` | 1 |
| android_debug_prefetched | `gradle -p android-app :app:assembleDebug --offline --no-daemon --gradle-user-home $PWD/.appforge/tmp/v10/continuation/network-gradle-cache` | 1 |
| history_fetch | `git -c maintenance.auto=false fetch --no-tags --no-write-fetch-head https://github.com/HACKMASTER-TR/AppForge-Studio.git 44614aa3c77925934b1eaf43ffafdf2dcbfe9fba` | 0 |
| windows_ci | `npm --prefix windows-host ci --ignore-scripts --no-audit --no-fund --cache .appforge/tmp/v10/npm-cache` | 0 |
| windows | `TMPDIR=$PREFIX/tmp npm --prefix windows-host test` | 0; {'tests': 7, 'pass': 7, 'fail': 0} |
| apt_linux | `proot-distro login ubuntu --bind "$PWD:$PWD" -- /usr/bin/env TMPDIR="$PWD/.appforge/tmp/v10/continuation/linux-tmp" PATH="/usr/bin:/bin:$PREFIX/bin" "$PREFIX/bin/node" --test "$PWD/quality/tests/apt_acquire_payload_runtime.test.js" "$PWD/quality/tests/apt_snapshot_lock_runtime.test.js"` | 0; {'tests': 14, 'pass': 14, 'fail': 0} |
| v10_hardened | `node --test quality/tests/v10_build_telemetry_runtime.test.js quality/tests/v10_offline_catalog_runtime.test.js quality/tests/v10_oss_gate_runtime.test.js` | 0; {'tests': 3, 'pass': 3, 'fail': 0} |
| quality_pre_snapshot_refresh | `npm --prefix quality test` | 1; {'tests': 1149, 'pass': 1145, 'fail': 4} |
| android_online_prefetch | `timeout 180 gradle -p android-app :app:testDebugUnitTest --no-daemon --gradle-user-home "$PWD/.appforge/tmp/v10/continuation/network-gradle-cache"` | 1 |
| quality_final | `npm --prefix quality test` | 1; {'tests': 1149, 'pass': 1146, 'fail': 3} |
| stability_final | `sh scripts/appforge-stability-gate ci` | 0 |
| wiki_audit | `node scripts/wiki-audit.mjs --changed-source-check` | 0 |
| snapshot_final | `python3 scripts/generate-second-brain-snapshot.py --check` | 0 |
| diff_final | `git diff --check` | 0 |

Original host fixture timing: standalone baseline **4666 ms**, observed with unavailable graph output **4707 ms**; full-suite repeat **3785 ms / 4021 ms**. Both pairs produced identical output. These single Copy-fixture observations include process startup and are not an Android benchmark or evidence of speed improvement.

The V9 Python suite executes 440 tests and is not physical authentication acceptance. V10 runtime runs actual Gradle/Kotlin production code and scanner boundary tests; 14 Python gate tests separately verify bad hashes, unsafe paths, limits, redaction, license fail-stop and offline status behavior. Existing catalog inspection tests prove no activation/mutation; no package installation or transaction rollback acceptance is inferred. Telemetry removal/unavailable-output compatibility is checked against equal real fixture output.

## Reproduction and remaining work

For this host, inherit the existing writable TMPDIR and select the already installed JDK (do not print environment values). A full quality pass still needs the correct Linux APT layout and writable absolute /tmp; the targeted Ubuntu evidence must not be added to the Termux count as a fabricated 1149/1149.

```sh
export TMPDIR="$PREFIX/tmp"
export JAVA_HOME="$PREFIX/lib/jvm/java-25-openjdk"
export PATH="$JAVA_HOME/bin:$PATH"
npm --prefix quality test
sh scripts/appforge-stability-gate ci
python3 -m unittest discover -s quality/tests -p 'test_v9_*.py'
python3 -m unittest discover -s quality/tests -p 'test_v10_oss_gate.py'
node --test quality/tests/v10_build_telemetry_runtime.test.js quality/tests/v10_offline_catalog_runtime.test.js quality/tests/v10_oss_gate_runtime.test.js
npm --prefix windows-host ci --ignore-scripts --no-audit --no-fund --cache .appforge/tmp/v10/npm-cache
npm --prefix windows-host test
gradle -p android-app :app:testDebugUnitTest --offline --no-daemon --gradle-user-home "$PWD/.appforge/tmp/v10/continuation/network-gradle-cache"
gradle -p android-app :app:assembleDebug --offline --no-daemon --gradle-user-home "$PWD/.appforge/tmp/v10/continuation/network-gradle-cache"
python3 scripts/generate-second-brain-snapshot.py --check
node scripts/wiki-audit.mjs --changed-source-check
git diff --check
```

The Android commands reproduce the missing-SDK blocker here. A supported isolated Android build host must provide the existing pinned SDK/NDK/CI toolchain; host tests are not permission to compile remotely as an AppForge product feature. Physical Windows packaging requires a proper Windows host and separate authorization. Conditional examples below are future review commands, not executions or permission to bypass license/platform gates:

- **codeassist:** Run the V10 runtime command below; repeat on approved device Gradle 9.3.1 before device acceptance.
- **sora-editor:** Review LGPL source/relinking/notices and recursive POM closure first; repeat `python3 -m json.tool docs/engineering/v10/continuation-upstream-audit.json` to inspect verified AAR evidence. No library-enable command is approved.
- **tree-sitter:** Select and hash exact Android binding/grammar artifacts, then ABI/memory fixtures on the existing editor. `rg -n "tree-sitter|sora" android-app/app/build.gradle.kts` confirms no new dependency; absence is not integration.
- **termux-packages:** Run the V10 runtime command below. Review real package SPDX/notices, ABI and staged rollback before extending preview into activation.
- **bundletool:** After checksum/transitive review on an approved host: `java -jar /reviewed/cache/bundletool-all-1.18.3.jar validate --bundle=/reviewed/fixture.aab`; test existing signed APKS identity separately. No signing/installation here.
- **gitleaks:** After license closure/platform approval and exact cache seeding: `python3 quality/tools/appforge_v10_oss_gate.py gitleaks --cache /reviewed/cache`. Current unchanged pin policy still blocks unreviewed closure.
- **osv-scanner:** `python3 quality/tools/appforge_v10_oss_gate.py osv-scanner --cache .appforge/tmp/v10/empty-cache --offline` reproduces exit 3. Implement verified DB age/digest/coverage and OS network boundary before a real scan.
- **syft:** After closure/platform approval and seeding: `python3 quality/tools/appforge_v10_oss_gate.py syft --cache /reviewed/cache --output /reviewed/fresh-manifest.cdx.json`. Existing output is rejected; no real SBOM yet.
- **esbuild:** `rg -n esbuild android-app/app/src/main/java/com/appforge/studio/build` locates the existing probe. A pinned import/export/source-map Web fixture and equal-output timing are prerequisites to selecting bundling.
- **spec-kit:** `python3 -m json.tool docs/engineering/v10/integrations.json`; inspect contracts in the existing V10 wiki page. No external generator required.
- **superpowers:** Run V10 runtime and Python commands below; independent external acceptance remains required by AGENTS.md.
- **jadx:** After ZIP hash/notices/platform review: bounded JVM/timeout/memory analysis of an approved user-APK fixture in a fresh temporary output. `python3 -m json.tool docs/engineering/v10/upstream-evidence.json` reproduces the recorded pin; no executable adapter exists.
- **uv:** After supported-host artifact/closure review, exercise a project-owned locked Python fixture with a dedicated environment and offline cache. `python3 -m json.tool docs/engineering/v10/continuation-upstream-audit.json` shows dual license evidence. Android integration remains disabled.

## Changed files and final acceptance

Exact modified/untracked file list relative to the preserved base is in [git-diff-summary.txt](docs/engineering/v10/git-diff-summary.txt). Final protected-file diff, HEAD/push-lock checks and safety-hook hashes are in [continuation-safety.json](docs/engineering/v10/continuation-safety.json). Source manifest/wiki were refreshed where durable behavior/evidence changed; snapshot was regenerated with the canonical script. No generated secrets or binary dependencies are committed. Whitespace verification uses `git diff --check` (tracked modifications); no staged/committed changes.

- `.github/workflows/appforge-stability-gate.yml`
- `V10_OSS_INTEGRATION_REPORT.md`
- `android-app/app/src/main/assets/device-build/build-metrics.init.gradle`
- `android-app/app/src/main/assets/second_brain_snapshot.json`
- `android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt`
- `android-app/app/src/main/java/com/appforge/studio/build/OfflineToolCatalog.kt`
- `android-app/app/src/test/java/com/appforge/studio/build/OfflineToolCatalogTest.kt`
- `docs/engineering/appforge-v10-unified-oss-integration.md`
- `docs/engineering/v10/android-cache-manifest.json`
- `docs/engineering/v10/ci-action-evidence.json`
- `docs/engineering/v10/continuation-safety.json`
- `docs/engineering/v10/continuation-upstream-audit.json`
- `docs/engineering/v10/continuation-verification.json`
- `docs/engineering/v10/first-pass-report.md`
- `docs/engineering/v10/git-diff-summary.txt`
- `docs/engineering/v10/integrations.initial.json`
- `docs/engineering/v10/integrations.json`
- `docs/engineering/v10/sora-maven-evidence.json`
- `docs/engineering/v10/upstream-evidence.json`
- `docs/engineering/v10/verification.json`
- `docs/wiki/03_Architecture/V10_OSS_Integration.md`
- `docs/wiki/Index.md`
- `quality/tests/test_v10_oss_gate.py`
- `quality/tests/v10_build_telemetry_runtime.test.js`
- `quality/tests/v10_offline_catalog_runtime.test.js`
- `quality/tests/v10_oss_gate_runtime.test.js`
- `quality/tools/appforge_v10_oss_gate.py`
- `quality/tools/v10-oss-pins.json`

HOSTED_CI=NOT_RUN; WINDOWS_PHYSICAL_PACKAGING=NOT_RUN; WINDOWS_EXE=NOT_RUN; WINDOWS_SIGNATURE=NOT_RUN; ANDROID_PHYSICAL=NOT_RUN; PLAY_PRODUCTION=NOT_RUN; CLOUDFLARE_PRODUCTION=NOT_RUN. Physical editor/LSP/lifecycle, device-only build, V9 enrollment/revocation/witness and APK/APKS identity remain unaccepted. A local stability PASS does not authorize delivery or establish vulnerability clearance.

```text
V10_OVERALL=PARTIAL
V10_TARGETED=PASS
V10_QUALITY=FAIL_1146_OF_1149
V10_APT_LINUX=PASS_14_OF_14
V10_HOSTED_CI=NOT_RUN
PACKAGE_MATRIX_DONE=2
PACKAGE_MATRIX_PARTIAL=4
PACKAGE_MATRIX_BLOCKED=7
SECURITY_GATE=PARTIAL_EXISTING_GUARD_PASS_REAL_SCANNERS_NOT_RUN
ANDROID_UNIT=BLOCKED_MISSING_SDK_EXIT_1
ANDROID_DEBUG_BUILD=BLOCKED_MISSING_SDK_EXIT_1
WINDOWS_TEST=PASS_7_OF_7_EXIT_0
V9_REGRESSION=PASS_440_OF_440_EXIT_0
ANDROID_PHYSICAL=NOT_RUN
NO_PUSH=YES
NO_MERGE=YES
NO_DEPLOY=YES
```

## Phase3 continuation (latest local evidence)

The [phase3 report](docs/engineering/v10/phase3-report.md) and
[command ledger](docs/engineering/v10/phase3-verification.json) supersede the
continuation section's current-run counts without modifying its historical
results. Added a manual feature-branch-only host compilation workflow and two
safety/toolchain contracts. Statically verified both pinned x86_64 scanner
archives and recorded their embedded module identities; recursive license closure
and real scans remain blocked. No upstream binary is shipped and no package is
newly marked DONE. Package counts remain **2 DONE / 4 PARTIAL / 7 BLOCKED**.

Full-suite PRoot attempts, final Termux/PRoot results, exact commands and exits,
Android SDK failures and remaining acceptance are in the phase3 records. These
records do not claim hosted CI or physical acceptance. No push, merge or deploy.

Phase3 measured outcomes: **Termux 1148/1151, exit 1** (same three APT environment
failures); **Ubuntu PRoot 1151/1151, exit 0, zero skips**, using the real Linux APT
method, workspace-owned `/tmp` and existing Termux curl bind. V9 **440/440** and
Windows **7/7** pass. Stability, wiki audit, snapshot check, secret hygiene and
diff check each exit 0. Android unit/debug remain **BLOCKED_MISSING_SDK, exit 1**.
Real scans remain **BLOCKED/NOT_RUN**; hosted CI remains **NOT_RUN**. The two new
passing workflow contracts account for the denominator increase from 1149 to
1151; no old test was removed or weakened.


## Phase4 continuation — real Linux ARM64 scanner TEST execution

This section supersedes earlier **NOT_RUN** statements only for the new internal
Gitleaks/Syft executions. All earlier reports and failed attempts remain historical.
[Phase4 security report](docs/engineering/v10/phase4-security-report.md) and
[command/artifact evidence](docs/engineering/v10/phase4-security-evidence.json)
record verified official ARM64 archives, checksums, ELF architecture and actual
Ubuntu PRoot runtime versions. Provenance signatures remain unverified.

Gitleaks detected one safe synthetic token (exit 42), then scanned a bounded
1302-file working-tree snapshot and reported eight untriaged findings (exit 42).
This is not a clean secret-hygiene result. Reports stay restricted and ignored;
only counts and non-sensitive file locations are shared. Syft generated a real
CycloneDX 1.7 SBOM for a synthetic npm lock fixture (exit 0, three components,
explicit is-number@7.0.0 identity/nonempty checks). Official structural schema
validation passed; format annotations were not validated. No workspace dependency
completeness or vulnerability clearance follows.

OSV remains BLOCKED: no reviewed checksum-pinned fresh ecosystem database or
established network boundary; PRoot unshare probe retained the same namespace.
No OSV scan or online proprietary-file submission occurred. Production gate and
license flags remain unchanged; verified-cache Gitleaks/Syft gate calls both
block on license closure (exit 3). Transitive licenses/notices and redistribution
remain NOT_APPROVED. Internal test execution is not shipping approval.

Current phase4 PRoot quality: **1151/1151 PASS**, exit 0; V10 Python **14/14 PASS**;
V9 **440/440 PASS**; stability, wiki audit, snapshot, secret-hygiene and diff checks
exit 0. Android remains blocked by absent SDK; hosted CI NOT_RUN. Phase3 Windows
7/7 is retained as historical evidence; Windows code/config was unaffected.
Package statuses remain **DONE=2, PARTIAL=4, BLOCKED=7**. Overall **PARTIAL**;
no release/device acceptance and no push, merge or deployment.

## OSV continuation — genuine offline assessment, 2026-10-28

This supersedes earlier OSV NOT_RUN/database-absent statements for **internal
assessment only**. **OSV FINDINGS; V10 overall BLOCKED.** The production fail-closed
adapter, license policy, Gitleaks rules and existing V10 changes are preserved.

Official OSV-Scanner v2.7.0 ARM64 bytes match release metadata and published
SHA256SUMS, and execute in existing Ubuntu PRoot. Four complete OSV-owned GCS
ecosystem exports (npm, Maven, PyPI, crates.io) are generation-pinned and verified
for size/MD5, local SHA-256, every ZIP CRC/JSON record and age below 72 hours.
The kernel seccomp launcher denies networking across exec into PRoot; IPv4 and
IPv6 denial controls pass. No online dependency submission or resolver is used.

Real missing/corrupted database tests return error 127. The known-vulnerable
lodash control returns findings exit 1. The project scan returns findings exit 1:
**22 manifests, 1,993 package occurrences, 930 unique coordinates, 47 advisory IDs,
22 affected package occurrences.** No vulnerabilities are hidden or upgraded away.
The main Android resolved dependency inventory remains absent (SDK blocker), and
PyPI manifests are comment-only. This is partial manifest coverage, not clean or
complete vulnerability acceptance. Signature attestation and redistribution
license closure remain unverified/unapproved.

[Detailed report](docs/engineering/v10/osv-assessment-report.md) and
[command/database/finding evidence](docs/engineering/v10/osv-assessment-evidence.json)
preserve all failures, exact provenance/digests, coverage limitations and advisory
IDs. The upstream versioned offline guide names an obsolete cache layout; the
accepted run follows its pinned Scalibr source. No Android/V9/signing/production
source, dependency versions or security gates changed. No commit, push, merge,
deploy or release occurred. Existing overall PARTIAL history is retained; current
security acceptance is BLOCKED by actual findings and incomplete coverage.

## Security remediation update — 2026-10-28

Compatible dependency remediation is implemented in the existing isolated dirty
worktree. The real equivalent-scope offline OSV scan now reports 12 advisory IDs
and 12 affected pairs (before: 47/52), with 40 original pairs no longer matched
and no new package/advisory relationships. Six HIGH IDs remain. Three remaining
matched pairs have no fixed event; the baseline adm-zip no-fixed-event record
uses last_affected 0.6.0 and no longer matches 0.6.1. This does not establish
symlink runtime acceptance. V10 overall remains BLOCKED. Current authoritative
remediation details and verification are in
`docs/engineering/v10/osv-remediation-report.md`; earlier result counts in this
report are historical. No delivery or production action occurred.
