---
type: integration
status: draft
project: AppForge Studio
created: 2026-10-28
updated: 2026-10-28
last_verified: 2026-10-28
confidence: medium
related:
  - "[[Build_And_Worker_Architecture]]"
  - "[[APT_Snapshot_Offline_Toolchain]]"
  - "[[Terminal_And_Developer_Tools]]"
source_files:
  - "android-app/app/src/main/assets/device-build/build-metrics.init.gradle"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineToolCatalog.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
  - "quality/tools/appforge_v10_oss_gate.py"
  - "quality/tools/v10-oss-pins.json"
  - "docs/engineering/v10/osv-assessment-evidence.json"
  - "quality/tests/v10_osv_real_scanner.py"
  - "quality/tests/v10_forge_real_runtime.cjs"
  - "quality/tools/v10_osv_offline_exec.py"
  - "quality/tests/test_v10_oss_gate.py"
  - "quality/tests/v10_build_telemetry_runtime.test.js"
  - "quality/tests/v10_offline_catalog_runtime.test.js"
---

# V10 optional OSS program: specification and decisions

This draft supplements existing governance; it grants no delivery authority.
The complete candidate inventory and observed upstream identities are under
`docs/engineering/v10/`. Architecture and blockers are recorded in
`docs/engineering/appforge-v10-unified-oss-integration.md`. Actual local gate
results belong to `V10_OSS_INTEGRATION_REPORT.md`; this page is not acceptance.

## Feature contract

1. Observe existing Gradle builds without replacing the engine, changing cache
   keys, skipping tasks or adding a remote build path. Count executed, up-to-date,
   restored-from-cache, skipped and failed tasks separately. Record monotonic
   elapsed duration and a bounded anonymous task dependency topology. Executed
   work does not prove a cache miss; invalidation causes remain UNKNOWN.
2. Preview a canonical reviewed offline-tool catalog in an app-private cache.
   Bind package name, version, runtime ABI (Bionic versus GNU are distinct),
   upstream HTTPS URL, size, package digest, SPDX family and notice digest.
   Completeness is a dry-run result, never installation authority.
3. Optional Gitleaks and Syft host scans require reviewed version/archive pins.
   Never download or run imported repository scripts. Scan a bounded temporary
   snapshot of nonignored repository inputs; suppress scanner stdout/stderr and
   export rule/location summaries or a restricted CycloneDX manifest SBOM.
   Preserve the existing secret hygiene guard. Missing tools are NOT_RUN with a
   nonzero gate exit. Unsupported or unverified offline scans are BLOCKED.
4. Preserve V9 authentication, witness registry, enrollment, signing and
   revocation. Preview work cannot access production routes or deployments.

## Decisions

- CodeAssist remains GPL reference only. The observer is original AppForge code;
  no GPL runtime/source was imported. Gradle retains build semantics.
- Existing editor/LSP/restore-point/path protections remain authoritative. Sora
  and Tree-sitter stay disabled pending distribution obligations and ARM64 audit.
- Termux packages cannot replace Ubuntu runtime packages. The catalog is an
  inspection API only. Existing generation prepare/activate/rollback paths stay
  authoritative; this preview cannot invoke them.
- Gitleaks/Syft remain optional host tools outside the APK. Linux architecture
  pins are not Android ABI acceptance. OSV production integration remains blocked; the later internal assessment
  verifies database provenance/age and kernel network denial without approving
  distribution, complete dependency coverage or clean vulnerability status.
- Bundletool, JADX and uv receive no executable adapter until artifact/transitive
  review and platform acceptance are complete. No fake analyzer or parser.
- No esbuild replacement is selected without measured benefit on equal fixtures.
  Existing electron-builder 26.15.3 remains unchanged.

## Acceptance matrix and discipline

The specification/contract/decision separation adapts Spec Kit practices; the
short plan, failing behavioral test, implementation and independent rerun adapts
Superpowers practices. These are original procedures inside existing memory;
no external agent prompt or competing policy file is imported.

| Contract | Executable evidence | Remaining acceptance |
| --- | --- | --- |
| First/no-op/changed-input/failing Gradle work | `quality/tests/v10_build_telemetry_runtime.test.js` | Device Gradle 9.3.1 and physical Android |
| Canonical catalog, checksum, ABI, notice, no mutation | `quality/tests/v10_offline_catalog_runtime.test.js` | Reviewed real package set; no install authorization |
| Hash/ABI/missing-tool/redaction/resource/zip-slip boundary | `quality/tests/test_v10_oss_gate.py` via `v10_oss_gate_runtime.test.js` | Real pinned scanners on supported isolated Linux runner |
| Offline request cannot fall back to network | OSV/Syft fail-closed tests in the Python suite | Preseeded verified OSV DB, freshness and enforced network isolation |
| V9 unchanged | Diff review and `test_v9_*.py` | Physical device authentication acceptance remains separate |

Tests were introduced before implementations; absence failures and later runtime
results are recorded in the engineering report. Host fixtures test real Gradle
and Kotlin behavior. They are not Android physical, EXE, signature, hosted CI,
Play or Cloudflare acceptance. No performance improvement is claimed.

## Continuation verification, 2026-10-28

The original observer now reports graph-output I/O failures as UNAVAILABLE without
failing otherwise valid Gradle work. Its runtime fixture compares output with
observation disabled and the output destination unavailable; timing is a small
host Copy fixture, not an Android speed claim. Catalog checks remain dry-run only.

All 13 immutable upstream HEAD license hashes were rechecked. Sora core AAR
0.24.7 matches its published SHA-256; it contains no native libraries or notices.
This does not resolve LGPL distribution/relinking, parser ABI or memory gates.
The audited inventory preserves its original bytes in integrations.initial.json.

Historical storage assertions pass unchanged after read-only retrieval of the
referenced commit, which does contain TinyHtmlActivity.java. Termux still lacks
the Linux APT method and writable absolute /tmp; all 14 APT runtime cases pass
using real Ubuntu PRoot APT. Windows Node tests pass after workspace-scoped npm ci
with lifecycle scripts disabled; physical EXE packaging remains NOT_RUN.
An isolated Gradle cache resolves unchanged AGP 9.1.1, then Android unit/debug
configuration stops for missing SDK. No SDK, APK signing or installation occurs.
Canonical current commands/results are in the root report and continuation JSON;
no earlier PASS is inherited as release acceptance.

## Phase3 verification boundary

A separate manual workflow, `.github/workflows/v10-host-verification.yml`, is
restricted to the V10 feature branch and Ubuntu x86_64. It retains project SDK,
NDK, CMake, JDK and Gradle pins, and requests unit tests plus Kotlin/native
compilation without packaging or signing tasks. It has read-only contents
permission, no secrets, no persisted checkout credentials and no publication
step. Static contracts protect these boundaries; hosted execution is NOT_RUN.
This does not establish Android SDK acceptance or physical editor acceptance.

Phase3 archive inspection hashes actual Linux x86_64 Gitleaks/Syft release
archives without executing them. Their release notices do not establish recursive
module license closure; the existing `license_closure_reviewed=false` gate stays
in force. The phase3 records under `docs/engineering/v10` preserve earlier
continuation evidence and keep package statuses at 2 DONE / 4 PARTIAL / 7 BLOCKED.

## OSV genuine offline assessment, 2026-10-28

**OSV FINDINGS; overall V10 BLOCKED.** Earlier OSV NOT_RUN claims are historical.
The production adapter stays fail-closed; no license, detection or release gate
is relaxed. Verified official OSV-Scanner 2.7.0 Linux ARM64 bytes execute in the
existing Ubuntu PRoot under an inherited AArch64 kernel seccomp filter. Real
IPv4/IPv6 exec-child socket probes return EPERM. PRoot namespaces are not used
as isolation evidence. No system/rootfs or project dependency upgrade occurs.

Complete npm/Maven/PyPI/crates.io exports from the documented OSV-owned GCS
bucket are pinned to immutable generations. HTTPS origin, size/upstream MD5,
local SHA-256, all ZIP CRCs/JSON records and 72-hour freshness are checked;
signed database provenance is not claimed. OSV 2.7.0's actual Scalibr source
requires `OSV_SCALIBR_LOCAL_DB_CACHE_DIRECTORY` with `osv-scalibr/{ecosystem}/all.zip`,
despite its guide documenting the older scanner variable/directory. Trust
pinned source and observed execution when those conflict.

Missing/corrupt database controls return 127; a registry lodash 4.17.20 fixture
returns 1 and includes GHSA-35jh-r3h4-6jhm. The real offline project scan returns
1: 22 manifests, 1,993 package occurrences, 930 unique coordinates, 47 advisory
IDs and 22 affected package occurrences. These IDs may share CVE aliases.
No suppression, call-analysis/reachability claim or automatic remediation.
PyPI requirements are comment-only. Main Android resolved/transitive inventory,
SDK/build acceptance, distribution license closure and production integration
remain BLOCKED. Findings include Windows dependencies and example Rust/npm
locks; source hashes and full advisory/affected-range evidence are retained.

See `docs/engineering/v10/osv-assessment-report.md` and
`docs/engineering/v10/osv-assessment-evidence.json`. Acquisition is explicit and
separate from scanning; the opt-in real harness never downloads or falls back
to online operation. Failed fixture/layout attempts remain evidence, not PASS.
No V9, signing, Android runtime or production implementation changed.

## Compatible security remediation, 2026-10-28

Current local manifest assessment supersedes the original counts above: 47 → 12
advisory IDs, 52 → 12 affected coordinate/advisory pairs, 40 original pairs no
longer matched and no new package/advisory relationships. Same OSV 2.7.0 binary,
four verified complete exports, 22 manifests and kernel offline controls.
Windows ZIP/Electron, constrained build transitives, Babel/body-parser and scoped
Expo PostCSS 8 updates are implemented. Major API migrations remain blocked;
three no-fixed-event pairs still match. The fourth baseline no-fixed-event
adm-zip record ends at last_affected 0.6.0; absence at 0.6.1 is not physical
symlink acceptance. V10 overall remains BLOCKED, including unresolved findings,
Android inventory/SDK, native Windows acceptance and distribution notice closure.
See `docs/engineering/v10/osv-remediation-report.md` and its structured evidence
for exact changes, residual risks and current local verification. Historical
quality/physical results are not transferred to the new dependency pins.

## Residual signature remediation, 2026-10-28

Expo JSI and Turbo locks now resolve node-forge 1.4.2 within their existing
parent ranges. The opt-in `quality/tests/v10_forge_real_runtime.cjs` rejects
extra nested DigestAlgorithm elements, with a demonstrated failure on 1.4.0
and pass on 1.4.2; valid signatures and PEM Buffer compatibility also pass.
This is dependency verification, not platform signing acceptance.
Other residual packages remain BLOCKED pending compatible fixes or verified
parent migrations. See `docs/engineering/v10/osv-residual-report.md` for the
equivalent-database scan and final verification evidence.

## Phase 5 targeted parent migration, 2026-10-28

The Expo JSI/Turbo examples use Expo 54.0.38, preserving React 19.1.0 and
React Native 0.81.5. Its supported CLI 54.0.28 removes the old MCP tunnel/Zod
dependency rather than forcing Zod 4 into a Zod 3 consumer. Both authored native
plugins pass isolated config/prebuild and JavaScript-only Android bundle export;
Hermes compilation on Termux and native acceptance remain BLOCKED. Metro remains
0.83.3 with image-size 1.2.1. Verified patched parser artifacts do not authorize
an incompatible override. PyO3 0.29.0 artifact/abi3 inspection does not establish
compile/import acceptance without Cargo/rustc. See
`docs/engineering/v10/phase5-report.md` for final scan counts and verification.

## Phase 6 host acceptance preparation, 2026-10-28

The manual V10 host workflow now prepares three branch-restricted jobs with
read-only repository permission and credential-free pinned checkouts: existing
Android compilation plus Hermes exports, a temporary PyO3 0.29.0 Linux fixture,
and unsigned Windows portable packaging/launch/relaunch. No artifacts are
uploaded. These hosted checks are NOT_RUN, and do not establish platform or
physical acceptance. Local Termux and Ubuntu PRoot have no Cargo/rustc;
repository PyO3 pins remain unchanged. Workflow PowerShell parsing is an explicit
Windows step, not a locally verified claim. See
`docs/engineering/v10/phase6-report.md` for evidence and remaining blockers.

Isolated Expo55/RN0.83 fixtures remove image-size and pass authored prebuild and
JavaScript-only export, but the JSI fixture changes the requested legacy
architecture to newArchEnabled=true. The native ready policy remains Expo54/RN0.81;
no candidate migration was applied without native acceptance. Equivalent Phase6
OSV rerun retains the same 10 advisory IDs, including 4 HIGH.

## Phase 9.3 promoted PyO3, 2026-10-29

The shipped example now pins PyO3 =0.29.0 with the authentic 3,391-byte Cargo
lock supplied from candidate CI run 40836459198/job 123322262093 (SHA-256
`a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a`).
Host acceptance checks this exact pair with locked build/import/behavior in a
temporary fixture; active candidate lock export has been removed. Prior candidate
CI success is distinct from post-promotion host acceptance, which is NOT_RUN.
Historical OSV 10 advisories/4 HIGH is not a fresh scan or security clearance.
See `docs/engineering/v10/phase93-report.md` for provenance, local verification
and unverified platform gates. Earlier unchanged-pin statements above are
historical. No delivery authority is granted.

## Phase 10 residual advisory investigation, 2026-10-29

The five original residual IDs remain BLOCKED in the shipped lock graphs. Phase
10 adds opt-in bounded real dependency/consumer probes in
`quality/tests/v10_phase10_real_runtime.cjs`, without changing application
behavior, dependency pins, scanner scope or production security settings.
Deepmerge 4.3.1 target-side and null-target prototype poisoning is reproduced;
ordinary source-key rejection does not establish a complete defense. Actual
cacheable-request shared-store cross-principal reuse is reproduced with a
synthetic transport, alongside private/no-store/Vary/separate-store controls.
The Electron downloader's got default disables its HTTP cache; packaged EXE
content is still NOT_RUN. No operational recommendation is counted as mitigation.

Current upstream withdraws GHSA-ch52-4w7c-c8xp, while the authenticated pinned
export still reports it. Preserve both observations and the finding until the
normal fresh-database process reconciles them; no patch or suppression is implied.
Parser 4.5.7 escapes the tested comment/CDATA delimiters, but OSV still matches
it. A vulnerable 4.5.3 negative control proves these probes detect injection;
5.7.0 and the CLI candidate's 5.11.2 reject the same injection paths.

Supported CLI 20.2.0 replaces parser 4.x with ^5.3.6, preserving RN 0.81.5 in an
isolated candidate. Android manifest and Apple scheme consumer probes, project
and autolinking config comparison, and Android JavaScript bundling pass. Its
32 package-path changes include dev-server body-parser 2; native Android,
Hermes and actual Turbo registration acceptance remain NOT_RUN. Candidate
promotion is deferred rather than declaring it incompatible or accepted. CLI
20.1.0 still requires parser ^4.4.1. Expo SDK54 latest remains 54.0.38; the SDK55
candidate still needs a reviewed native architecture migration. Full details,
per-advisory decisions and fresh verification are in
`docs/engineering/v10/phase10-report.md` and `phase10-evidence.json`.

## Phase11 isolated native candidate gate

Phase11 prepares `.github/workflows/v10-phase11-native-candidate.yml` and
`scripts/v10/phase11-native-candidate.sh` for unsigned compile-only checks on the
isolated V10 branch. The shipped examples remain unchanged. CLI20.2.0 lock replay,
Metro and parser/server probes cannot establish native Turbo registration.
SDK54 New Architecture prebuild preserves the authored Java/C++ registration,
but `RustJsiModule.install()` still relies on `JavaScriptContextHolder` and
explicitly excludes bridgeless operation. A supported runtime-executor bridge
replacement and actual Hermes invocation returning 42 are required before SDK55
promotion. Compile-only CI is deliberately insufficient for that decision.
See `docs/engineering/v10/phase11-report.md` for exact evidence and outstanding
acceptance. Existing five pinned OSV findings remain unsuppressed; no dependency
or integration policy migration is shipped by this phase.
