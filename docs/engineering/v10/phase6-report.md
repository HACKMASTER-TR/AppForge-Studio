# V10 Phase 6 — host acceptance preparation

Date: 2026-10-28. Overall **BLOCKED** pending unresolved security findings and
actual host acceptance. Hosted CI **NOT_RUN**. This report describes the dirty
worktree, not acceptance of its base commit or a delivery authorization.

## Worktree and authority

Continued the explicitly authorized dirty isolated feature worktree
`feat/v10-oss-unified-20261028`, HEAD/base
`afb3ad9209bcce8b84773d8873d037b15b4d804d`. The imported base has no recorded
parent, so an independent main merge-base cannot be established. Existing changes
were preserved. No reset, commit, push, merge, deploy, release, signing, publishing,
main edit, V9 authorization or Play change occurred. No global packages installed.

## Exact Phase 5 image-size diagnosis

Phase 5 contains several distinct failures. The first standalone Metro 0.83.8
PNG probe had a malformed positive header (`Invalid png image asset`); its
corrected retry passed. Initial shared-node_modules fixture exports resolved
AppEntry relative to another directory. Independent installations corrected that.
Subsequent complete 568-module exports failed with
`Unsupported host platform for Hermes compiler: android`; JavaScript-only export
passed. None of these failures establishes that image-size 2.0.4 is incompatible.
The actual migration blocker is the parent dependency graph.

Expo 54.0.38 requires @expo/metro ~54.2.0; that wrapper pins Metro 0.83.3.
The existing full lock also retains root Metro 0.83.2; the nested wrapper Metro
must be distinguished from the root coordinate. Both affected Metro versions
retain image-size ^1.0.2, resolving 1.2.1. No Metro major override or image-size
major override was applied. Expo's recorded sdk-54 latest is already 54.0.38.

The nearest reviewed parent candidate is Expo 55.0.32 -> @expo/metro 55.1.2 ->
Metro 0.83.8, which removes image-size. Its downloaded Expo tarball was checked
against registry SHA-512 and its bundledNativeModules specifies React 19.2.0 and
RN 0.83.10. Existing examples use React 19.1.0/RN 0.81.5, a reviewed RN0.81
registration anchor, and JSI explicitly selects the old architecture. Thus this
is a native platform migration. DeviceBuildEngine explicitly restricts the ready
path to Expo 54/RN 0.81; RustProjectPolicy requires the 0.81 JSI declaration.
Isolated candidate
installation/prebuild evidence is retained privately; no unvalidated candidate
was applied to the shipped examples.

The old 1.2.1 zero-length ICNS entry hangs in a child and is killed after one
second with SIGKILL; no adversarial parser executes in the controlling process.
The exact 2.0.4 ICNS/JXL/HEIF rejection/truncation controls pass under the existing
five-second child timeout and 128 MiB heap bound. These probes do not claim
exhaustive correctness and do not clear the unresolved shipped dependency.

## PyO3

Termux Python and Ubuntu PRoot Python are both 3.13.13. Neither exposes Cargo
or rustc, including the checked conventional Cargo locations in Ubuntu.
No global installation attempted. Repository Cargo.toml/lock remain unchanged.
PyO3 compile/import/runtime acceptance remains **BLOCKED**, not inferred from
Phase 5's verified 0.29.0 crate and Rust 1.83 minimum metadata.

`scripts/v10/pyo3-host-acceptance.sh` provides a Linux host probe: copy the actual
example into mktemp, change only its exact PyO3 candidate to 0.29.0, resolve its
lock, build locked release against the actual Python executable, import the real
shared object and check repeated answer() == 42 and invalid-argument rejection.
The temporary fixture is removed on exit. A future run must retain its exact
resolved lock/toolchain and logs before deciding whether to apply the migration.
The workflow uses the hosted runner's existing Rust/Python, failing if absent
or incompatible. This is candidate acceptance only; repository pin remains old.

## Host workflow

The existing manual `.github/workflows/v10-host-verification.yml` now contains
Android, Linux extension and Windows jobs. Every job restricts the exact V10
branch; permissions remain contents:read and every checkout disables persisted
credentials. All five action references are full SHA pins; the three original
unique action identities retain Phase 3 provenance. No new action dependency,
secret, upload, distribution or delivery permission was introduced.

Android retains project SDK 37, NDK 28.2.13676358, CMake 3.22.1, Java 21 and
Gradle 9.3.1. Existing unit/Kotlin/native compilation remains. Both examples get
independent temporary installs, plugin prebuild and default Hermes bytecode
exports with a required .hbc output. No Android packaging or signing task added.

Windows uses windows-2025, existing Electron portable packaging with publishing
never and automatic signing discovery disabled. It requires an unsigned PE,
appends the existing authored smoke payload to a test copy, launches with a
60-second process bound, and verifies payload plus localStorage/IndexedDB counts
across two launches in isolated local app data. Native command failures throw.
No artifact is uploaded or retained for distribution.

Local YAML parse, all job guards, action SHA formats and Bash syntax pass;
focused behavioral workflow contracts pass. PowerShell/pwsh and actionlint are
absent locally: PowerShell AST parsing is a mandatory Windows step before launch,
and native PowerShell syntax acceptance is **NOT_RUN locally**. Static validation
is not hosted acceptance. Android SDK/Hermes, PyO3 runtime and Windows native
execution remain NOT_RUN/BLOCKED until actual authorized host runs.

## Security and verification

Raw Phase 6 logs and fixture results are under ignored `.appforge/tmp/v10/phase6/`.
The existing real offline OSV harness and database are reused without changed
scope, freshness, hashes, flags or suppressions. Missing/corrupt database and
network-denial controls remain required. Phase 5 evidence is retained unchanged.
Existing Syft artifact/inventory evidence is preserved; no new packaged artifact
or platform SBOM acceptance is claimed. Scanner findings remain blockers.

Equivalent OSV rerun `phase6-security`: scanner 2.7.0, unchanged verified binary,
all four complete database exports, 22 manifests, 1,987 package occurrences,
925 unique coordinates, exit 1 FINDINGS, **10 advisory IDs / 4 HIGH**. Exact
advisory ID equality against phase5-final was independently checked; severity
uses the unchanged Phase 5 triage. Missing/corrupt database rejection, known
vulnerable dependency detection and inherited kernel IPv4/IPv6 network denial
all PASS. No suppression, scope reduction or database replacement occurred.

Fresh SDK54 fixtures initially failed offline installation with ENOTCACHED
(first default cache, then project cache). Those failed logs are retained.
Normal integrity-checked npm installation then succeeded and both exact current
examples passed config and JavaScript-only export in independent directories.
This revalidates the already-applied supported SDK54 parent migration; it does
not remove image-size. Expo55 candidate outcomes are separate from these checks.

Both SDK55 candidate fixtures completed install, authored prebuild and
JavaScript-only export successfully, with no image-size coordinate in either
lock (Metro 0.83.8, RN 0.83.10). Crucially, generated gradle.properties selects
`newArchEnabled=true` even for the JSI example that explicitly requested false.
This changes its reviewed legacy bridge contract. No native Rust/NDK/JSI compile,
Hermes or physical execution accepted this migration. Result:
**IMAGE_SIZE_STATUS=BLOCKED_NATIVE_PARENT_MIGRATION**, with a concrete candidate
rather than a forced override. Original example manifests/locks remain unchanged.

V9 regression PASS **440/440**, V10 Python PASS **14/14**, Windows Node tests
PASS **9/9** on ARM64 (native Windows NOT_RUN), and Stability Gate `ci` PASS.
Real Gitleaks 8.30.1 detector controls PASS and the fresh unchanged-scope source
scan PASS with zero findings across 1,330 files before final evidence rendering;
final source scan evidence is recorded separately. Its executable and Syft
records were preserved without scanner-rule or scope changes.

The first full quality run was **1151 PASS / 1 FAIL of 1152**, with zero skips:
Second Brain snapshot freshness failed while this task was still editing the
wiki/source inventory. This was a verification ordering error. The snapshot was
regenerated; all source/wiki edits were frozen before the full rerun. The first
failure remains in `quality-proot.log`; it is not reclassified as a passing run.
Wiki drift audit has zero errors and 22 advisory coverage warnings; secret hygiene
is Green. Local PowerShell syntax parsing and actionlint were unavailable, so
Windows CI is prepared with mandatory on-host syntax validation, not declared
fully locally validated. Native host acceptance remains NOT_RUN.

Frozen-source full quality rerun: **PASS 1152/1152, zero skips**.
The rerun is recorded in `quality-frozen.log` and `quality-frozen-result.json`.
The final source Gitleaks invocation is recorded under ignored
`.appforge/tmp/v10/phase6-final/`; no changed exclusion or allowlist was used.
Both equivalent offline OSV reruns (`phase6-security`, `phase6-final`) retain
10 advisories / 4 HIGH. All hosted tests remain NOT_RUN.
