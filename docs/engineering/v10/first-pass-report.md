# V10 OSS integration report

**Overall: PARTIAL — mandatory quality, Android and Windows gates are not all passing. This is not a fully implemented or accepted release.**

## Baseline and authority

- Initial branch: `feat/v10-oss-unified-20261028`; initial HEAD and V9 base ref: `afb3ad9209bcce8b84773d8873d037b15b4d804d`.
- Initial `git status --short` was empty; `python3 scripts/current-state.py show` independently reported the same live identity and clean state.
- Clone is shallow. Historical workstream/CI/wiki claims were not inherited as acceptance.
- No commit, push, PR, merge, deploy, release, publication, key rotation, signing identity change or APK installation occurred.
- Full architecture, licensing obligations, threat model, code/test links, rollback and per-component next steps: [engineering architecture](docs/engineering/appforge-v10-unified-oss-integration.md).

## Package matrix

DONE applies to the scoped methodology adaptation only. PARTIAL never means scanner/device/release acceptance. No upstream source or executable binary was vendored.

| Repository | State | Result / exact remaining blocker |
| --- | --- | --- |
| [codeassist](https://github.com/tyron12233/CodeAssist) | **PARTIAL** | Original Gradle duration/outcome observer and bounded task topology; real no-op/change/failure fixture passed. Cache misses and invalidation causes remain UNKNOWN; device execution NOT_RUN. |
| [sora-editor](https://github.com/Rosemoe/sora-editor) | **BLOCKED** | Maven 0.24.7 metadata checked; no LGPL relinking/source distribution plan, recursive transitive review or editor parity/physical acceptance. Existing APK packaging strips notices. |
| [tree-sitter](https://github.com/tree-sitter/tree-sitter) | **BLOCKED** | Sora not selected; binding/grammar artifacts, licenses and Android ARM64 acceptance unverified. No native library or startup grammar download added. |
| [termux-packages](https://github.com/termux/termux-packages) | **PARTIAL** | App-private deterministic catalog dry-run validator and offline completeness checks implemented. No real package set/notice approval, prepare/activate/rollback adapter or Bionic-to-Ubuntu compatibility; nothing installed. |
| [bundletool](https://github.com/google/bundletool) | **BLOCKED** | Official 1.18.3 all-JAR metadata/digest recorded; no local verified JAR, shaded dependency notice audit, valid signed AAB/device fixture or APKS identity/reproducibility proof. No invocation was found to validate or reuse. |
| [gitleaks](https://github.com/gitleaks/gitleaks) | **PARTIAL** | Pinned optional host wrapper, redacted rule/location summaries and CI opt-in; existing guard preserved. Actual scanner NOT_RUN: archive absent. Transitive notice/executable platform acceptance remains incomplete. |
| [osv-scanner](https://github.com/google/osv-scanner) | **BLOCKED** | Official v2.7.0 metadata checked. Offline entry fails closed without invoking scanner/network. No verified preseeded DB, age/coverage manifest or enforced network sandbox; no vulnerability scan claimed. |
| [syft](https://github.com/anchore/syft) | **PARTIAL** | Pinned optional host wrapper, restricted CycloneDX export, source/output hashes, coverage and pinned CI artifact upload. Actual SBOM NOT_RUN: archive absent; distribution/transitive/coverage acceptance incomplete. |
| [esbuild](https://github.com/evanw/esbuild) | **BLOCKED** | Existing project-owned esbuild transform probe reused unchanged. No equivalent pinned bundling fixture and measured speed/sourcemap benefit; no new bundler or unpinned install. |
| [spec-kit](https://github.com/github/spec-kit) | **DONE** | Original feature spec/contracts/decisions/acceptance matrix adapted inside existing wiki; no external prompt/template source imported or competing policy added. |
| [superpowers](https://github.com/obra/superpowers) | **DONE** | Original failing-test → implementation → independent runtime rerun/evidence discipline applied within existing governance; no external authority or copied agent prompts. |
| [jadx](https://github.com/skylot/jadx) | **BLOCKED** | Official v1.5.6 ZIP digest recorded; transitive notices, Java/platform/resource acceptance and isolated user-APK fixture corpus not approved. No decompilation/analyzer execution. |
| [uv](https://github.com/astral-sh/uv) | **BLOCKED** | Official 0.13.6 metadata and dual Apache-2.0 OR MIT license evaluated. No Android/Bionic ARM64 acceptance or Chaquopy Python 3.12 coexistence proof; no host environment change or remote build routing. |

Counts: **DONE=2, PARTIAL=4, BLOCKED=7**. The missing scanner executions and SBOM are separately **NOT_RUN**.

## License and pin evidence

Canonical task inventory: [integrations.json](docs/engineering/v10/integrations.json). All 13 default-branch HEADs, license-file URLs/hashes, release tags and published artifacts were queried from official GitHub APIs; exact results and the task inventory hash are in [upstream-evidence.json](docs/engineering/v10/upstream-evidence.json). GitHub release metadata is observation evidence; it is not local byte verification or recursive license clearance. Release/latest returned HTTP 404 for Sora, so official Maven Central metadata was checked separately.

Sora published artifact: `io.github.rosemoe:editor:0.24.7` (LGPL-2.1-or-later). Exact POM/module hashes and dependencies are in [sora-maven-evidence.json](docs/engineering/v10/sora-maven-evidence.json). Direct published dependencies: annotation 1.11.0, collection 1.6.0, Kotlin stdlib 2.4.10. No AAR or recursive transitive closure was installed or approved. A distribution review must supply LGPL text, attribution, corresponding modifications/source where required, and a practical relinking/replacement path; existing APK notice exclusions require explicit handling. Keeping the current editor avoids counterfeit integration.

CodeAssist is GPL-3.0-or-later reference only; no copied source, bundled library or binary. Tree-sitter core is MIT; selected bindings and grammar licenses remain independent requirements. Termux has varied per-package licenses; a repository-level license does not approve a package. uv is dual Apache-2.0 OR MIT. Other candidate licenses/release pins are in the architecture matrix. No binary download or dependency update was performed.

Optional host scanner archive pins (official release asset SHA-256, **not downloaded/executed here**):

| Tool | ABI | Exact archive | Expected SHA-256 |
| --- | --- | --- | --- |
| gitleaks v8.30.1 / MIT | aarch64 Linux | `gitleaks_8.30.1_linux_arm64.tar.gz` | `e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080` |
| gitleaks v8.30.1 / MIT | x86_64 Linux | `gitleaks_8.30.1_linux_x64.tar.gz` | `551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb` |
| syft v1.55.0 / Apache-2.0 | aarch64 Linux | `syft_1.55.0_linux_arm64.tar.gz` | `929bd163db2d7cd2255d3bcb8967208079813d7b46b0c952f7b794d8e2c7d304` |
| syft v1.55.0 / Apache-2.0 | x86_64 Linux | `syft_1.55.0_linux_amd64.tar.gz` | `bb65249542db5fedeeb4d3f9b018b75e7264dbfbbb10486f044581a92f90490d` |

Release-license bytes for these scanner tags were fetched and hashed in [v10-oss-pins.json](quality/tools/v10-oss-pins.json). Local execution requires byte-verifying the pre-seeded archives and approved transitive license evidence. Both pins currently set `license_closure_reviewed=false`; a valid archive still fails closed with LICENSE_CLOSURE_UNVERIFIED until a reviewed source update approves its license closure. No CLI/environment override exists. No transitive license distribution approval or scanner ABI pass is inferred. New optional artifact upload action is full-SHA pinned, with official ref/license/runtime evidence in [ci-action-evidence.json](docs/engineering/v10/ci-action-evidence.json). Existing workflow action references and branch semantics are unchanged.

Existing Windows dependencies remain pinned to electron-builder 26.15.3 and Electron 43.4.1 in unchanged manifests/lockfile. Portable x64/`--publish never`/disabled host signing remain unchanged. Offline Windows cache completeness, repeatable EXE hashes and signatures require a proper Windows runner.

## Actual execution

Full command/exit/time records: [verification.json](docs/engineering/v10/verification.json). Logs are ignored local files under `.appforge/tmp/v10/`. JDK 21 initially aborted Gradle/Kotlin with `ProtoLogNative`; the already installed Termux JDK 25.0.3 worked. Successful runtime fixtures used existing Gradle 9.6.0/Kotlin 2.4.0. No install/update was used.

| Stage / actual command | Exit | Result |
| --- | --- | --- |
| quality: `npm --prefix quality test` | 1 | Initial full run; 1145/1149 passed; 4 environment/history failures. |
| stability: `sh scripts/appforge-stability-gate ci` | 0 | PASS, fail 0. |
| android_unit: `gradle -p android-app :app:testDebugUnitTest --offline --no-daemon` | 1 | BLOCKED before tests: offline AGP 9.1.1 unavailable. |
| android_debug_build: `gradle -p android-app :app:assembleDebug --offline --no-daemon` | 1 | BLOCKED before build: offline AGP 9.1.1 unavailable. |
| windows: `npm --prefix windows-host test` | 1 | 4 passed, 1 failed; adm-zip absent. |
| v9_regression: `python3 -m unittest discover -s quality/tests -p test_v9_*.py` | 0 | 440/440 Python offline regression tests passed. |
| v10_targeted: `node --test quality/tests/v10_build_telemetry_runtime.test.js quality/tests/v10_offline_catalog_runtime.test.js quality/tests/v10_oss_gate_runtime.test.js` | 0 | 3/3 runtime suites passed at this run. Final full run also passed all V10 suites after license-gate hardening. |
| v10_python: `python3 -m unittest discover -s quality/tests -p test_v10_oss_gate.py` | 0 | 13 Python tests passed at this earlier run. |
| gitleaks: `python3 quality/tools/appforge_v10_oss_gate.py gitleaks --cache .appforge/tmp/v10/empty-cache` | 2 | NOT_RUN: archive missing. |
| osv-scanner: `python3 quality/tools/appforge_v10_oss_gate.py osv-scanner --cache .appforge/tmp/v10/empty-cache --offline` | 3 | BLOCKED: offline DB/network isolation unverified. |
| syft: `python3 quality/tools/appforge_v10_oss_gate.py syft --cache .appforge/tmp/v10/empty-cache` | 2 | NOT_RUN: archive missing; no SBOM generated. |
| quality_final: `npm --prefix quality test` | 1 | Final full run: 1145/1149 passed; the same 4 environment/history failures. New V10 suites passed. |
| v10_python_final: `python3 -m unittest discover -s quality/tests -p test_v10_oss_gate.py` | 0 | Final 14/14 Python tests passed, including transitive license fail-stop. |
| stability_final: `sh scripts/appforge-stability-gate ci` | 0 | Final PASS, fail 0. |
| wiki_changed_source: `node scripts/wiki-audit.mjs --changed-source-check` | 0 | Exit 0; Yellow, 0 errors / 20 advisory coverage warnings. New V10 page records scope; no physical acceptance inferred. |
| snapshot_check: `python3 scripts/generate-second-brain-snapshot.py --check` | 0 | PASS: canonical generated snapshot freshness. |
| diff_check: `git diff --check` | 0 | PASS: no whitespace errors in tracked diff. |

Failing tests were introduced before implementation: Python scanner suite initially exited 1 because its module did not exist; Gradle/Kotlin runtime suites each initially exited 1 because their production implementation did not exist. During implementation the Gradle fixture exposed immutable task-list sorting and actionless-task classification issues; production code was corrected before successful reruns. These are retained as development history, not hidden successful first attempts.

The full quality failures are:

1. `apt_acquire_payload_runtime.test.js`: required `/usr/lib/apt/methods/http` absent (exit 127 instead of expected APT outcome).
2. The same file’s installer-shell test: `/tmp/appforge-apt-methods.XXXXXXXX` could not be created (permission denied).
3. `apt_snapshot_lock_runtime.test.js`: `/tmp/appforge-apt-plan.XXXXXXXX` permission denied.
4. `web_wrapper_config_contract.test.js`: historical object `44614aa3c77925934b1eaf43ffafdf2dcbfe9fba` absent from the shallow clone; independently confirmed by `git cat-file -t` exit 128.

These tests/source paths were not changed or weakened. A clean Ubuntu runner with the required APT layout/writable `/tmp` and full repository history is the minimal next step for the full quality gate. No claim is made that an unexecuted baseline/full-history run passed. Windows needs its exact pinned dependencies pre-seeded; Android needs its existing approved toolchain/plugin caches.

Measured fixture outcomes: first build **1 executed**, no-op **1 UP-TO-DATE / 0 executed**, changed input **1 executed**, failing task **1 failed**. These are task counts, not speedup benchmarks. No same-device/toolchain before/after performance comparison was run. Host test durations in verification JSON are execution records only. Cache restoration, invalidation causes and Android performance remain unproven.

The V9 suite exercises real Python protocol/crypto/policy/SQLite/recovery logic where those tests implement it; source regex contracts in the Node suite remain static evidence. Neither establishes physical device authentication. The new Gradle and catalog host suites execute production Groovy/Kotlin; scanner wrappers have behavioral boundary tests but no real Gitleaks/Syft/OSV scan acceptance.

## Reproduce safely

Use a supported isolated Linux host with existing Node/Python/JDK/Gradle; do not dynamically install latest tools. On this Termux host, select the existing JDK 25 via JAVA_HOME and prepend its bin directory to PATH.

```sh
npm --prefix quality test
sh scripts/appforge-stability-gate ci
gradle -p android-app :app:testDebugUnitTest --offline --no-daemon
gradle -p android-app :app:assembleDebug --offline --no-daemon
npm --prefix windows-host test
python3 -m unittest discover -s quality/tests -p 'test_v9_*.py'
python3 -m unittest discover -s quality/tests -p 'test_v10_oss_gate.py'
node --test quality/tests/v10_build_telemetry_runtime.test.js quality/tests/v10_offline_catalog_runtime.test.js quality/tests/v10_oss_gate_runtime.test.js
python3 scripts/generate-second-brain-snapshot.py --check
node scripts/wiki-audit.mjs --changed-source-check
git diff --check
```

Optional real scanners first require a reviewed pin update approving transitive license closure, then operator-preseeded archives matching the pinned URLs/hashes, a reviewed supported isolated Linux runner and the stated resource limits. Use `python3 quality/tools/appforge_v10_oss_gate.py gitleaks --cache /reviewed/cache` and `... syft --cache /reviewed/cache --output /fresh/artifact.cdx.json`. Output must not already exist. No auto-download occurs. OSV offline remains blocked by design until its DB/network implementation is verified; no `--offline` request is silently routed online.

## Changed files

The exact final file list and tracked/untracked diff summary are also recorded in [git-diff-summary.txt](docs/engineering/v10/git-diff-summary.txt). The engineering evidence directory contains original public metadata, copied canonical inventory and verification records; no credentials or binary artifacts.

- [.github/workflows/appforge-stability-gate.yml](.github/workflows/appforge-stability-gate.yml)
- [V10_OSS_INTEGRATION_REPORT.md](V10_OSS_INTEGRATION_REPORT.md)
- [android-app/app/src/main/assets/device-build/build-metrics.init.gradle](android-app/app/src/main/assets/device-build/build-metrics.init.gradle)
- [android-app/app/src/main/assets/second_brain_snapshot.json](android-app/app/src/main/assets/second_brain_snapshot.json)
- [android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt](android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt)
- [android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt](android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt)
- [android-app/app/src/main/java/com/appforge/studio/build/OfflineToolCatalog.kt](android-app/app/src/main/java/com/appforge/studio/build/OfflineToolCatalog.kt)
- [android-app/app/src/test/java/com/appforge/studio/build/OfflineToolCatalogTest.kt](android-app/app/src/test/java/com/appforge/studio/build/OfflineToolCatalogTest.kt)
- [docs/engineering/appforge-v10-unified-oss-integration.md](docs/engineering/appforge-v10-unified-oss-integration.md)
- [docs/engineering/v10/ci-action-evidence.json](docs/engineering/v10/ci-action-evidence.json)
- [docs/engineering/v10/git-diff-summary.txt](docs/engineering/v10/git-diff-summary.txt)
- [docs/engineering/v10/integrations.json](docs/engineering/v10/integrations.json)
- [docs/engineering/v10/sora-maven-evidence.json](docs/engineering/v10/sora-maven-evidence.json)
- [docs/engineering/v10/upstream-evidence.json](docs/engineering/v10/upstream-evidence.json)
- [docs/engineering/v10/verification.json](docs/engineering/v10/verification.json)
- [docs/wiki/03_Architecture/V10_OSS_Integration.md](docs/wiki/03_Architecture/V10_OSS_Integration.md)
- [docs/wiki/Index.md](docs/wiki/Index.md)
- [quality/tests/test_v10_oss_gate.py](quality/tests/test_v10_oss_gate.py)
- [quality/tests/v10_build_telemetry_runtime.test.js](quality/tests/v10_build_telemetry_runtime.test.js)
- [quality/tests/v10_offline_catalog_runtime.test.js](quality/tests/v10_offline_catalog_runtime.test.js)
- [quality/tests/v10_oss_gate_runtime.test.js](quality/tests/v10_oss_gate_runtime.test.js)
- [quality/tools/appforge_v10_oss_gate.py](quality/tools/appforge_v10_oss_gate.py)
- [quality/tools/v10-oss-pins.json](quality/tools/v10-oss-pins.json)

## Final acceptance and safety

ANDROID_PHYSICAL=NOT_RUN; WINDOWS_EXE=NOT_RUN; WINDOWS_SIGNATURE=NOT_RUN; PLAY_PRODUCTION=NOT_RUN; CLOUDFLARE=NOT_RUN; HOSTED_CI=NOT_RUN. Physical Android build/editor/LSP/lifecycle, V9 auth/enrollment/revocation, device Gradle compatibility, actual offline packs, APK/APKS identity and Windows packaging/signature tests remain external acceptance.

Signing configuration, Android Gradle dependency manifests, Windows package manifests/lockfile and Cloudflare/V9 source were independently diff-checked unchanged. Generated Second Brain snapshot was regenerated through its canonical script, not hand-edited. All changes remain uncommitted on the authorized V10 branch. No push, merge, deploy, production signing or installation occurred.
