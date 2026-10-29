# Phase12.1 — isolated arm64 ABI acceptance preparation

Status: **PREPARED_BLOCKED_ACCEPTANCE**. Branch
`feat/v10-oss-unified-20261028`, starting/final HEAD
`a2c998c67b5b9753dd2773aa86833fe61ee9f1da`. No production promotion.
Machine evidence: `phase12-1-evidence.json`. Private commands, output and hashes:
`.appforge/tmp/v10/phase12-1/`. All existing tracked files, the two untracked
Phase12 documents, previous evidence and preexisting `.t21-*` contents are preserved.
No Git index mutation, delivery, installation, packaging, signing or device action.

## API and provenance review

The exact Phase12 candidate is copied into
`quality/fixtures/v10/phase12-abi-candidate/RustRuntimeModule.h`.
It uses the genuine RN0.81.5 `TurboModule` and `TurboModuleWithJSIBindings` C++
interfaces. The real `TurboModuleBinding::getModule` calls the installer with
`jsi::Runtime&`; the interface implementation dynamically casts the actual module.
The provider is inserted into RN's actual default `OnLoad.cpp`, preserving the
existing autolinking fallback and `JNI_OnLoad` registration of the default delegate.
There is no invented Java runtime-executor API or new Java JNI installation method.

The authored SDK54 full npm lock SHA256 is
`73ad88db4be03a3b25415fb85952755ab47e355543a43e5c13809e9276e40375`.
Its npm registry RN0.81.5 tarball is available in the existing npm cache; this run
verified its complete SHA512 against lock integrity, then compared all ten pinned
API/CMake files to tar members. All match. `provenance.json` in the fixture pins
these files, the complete authored SDK54 source inventory and candidate bytes.
The private provenance record includes registry URL, integrity and actual local
compiler/tool versions. No fake, reduced or header-only RN replacement was used.

Reviewed source includes RuntimeExecutor's safe nonconcurrent callback contract,
CallInvoker's runtime-reference overloads, C++ binding installer and binding call
site, JNI BindingsInstallerHolder and TurboModuleManager, and default CMake/OnLoad.
Java's real installer holder is not invoked by this C++ candidate. The existing
production `RustJsiModule.java` still uses JavaScriptContextHolder and explicitly
excludes bridgeless support; production C++ still casts the Java handle. Neither
is accepted or modified. The temporary candidate removes legacy Java package
registration and selects the new JNI CMake entry, so those legacy sources do not
supply the candidate's native registration or link symbols.

The candidate contains no arbitrary jlong cast, spoofed pointer, retained runtime
pointer, captured `this`, or queued off-thread runtime access. RN supplies the
runtime synchronously at module access. Both methods call the real external Rust
`appforge_jsi_answer`; authored Rust defines that exported function returning42.
The installer is idempotent per module, refuses an already-owned global and captures
a shared atomic revocation permit. Destruction revokes the permit without touching
the runtime. Copy/move are disabled. These are reviewed source properties, not
Hermes lifetime acceptance: module/runtime association, invalidation/reload ordering,
thread ownership, exceptions and library lifetime still require physical evidence.

## Runner and future hosted gate

`scripts/v10/phase12-abi-link-candidate.sh` accepts only arm64-v8a and the exact
feature branch. On hosted CI it verifies checkout HEAD equals GITHUB_SHA. It checks
authored file hashes before copying into a fresh RUNNER_TEMP directory, keeps the
complete dependency lock, runs npm ci with scripts disabled then normal offline ci,
and prebuilds SDK54 with New Architecture and Hermes. The adapter verifies genuine
RN/Expo versions and pinned APIs, rejects paths outside runner temp and refuses an
existing JNI directory. Only that new temporary project is adapted.

The previous ignored template's adapter accepted only paths below Phase12 while its
runner used a separate temporary directory; it could not be used unchanged. The new
adapter explicitly receives runner temp, and a real generated SDK54 project copy
passed the positive adaptation contract. Its original dependencies are only read.

Toolchain values retain Phase11's reviewed arm64 configuration: Rust1.85.1,
NDK27.1.12297006, Android API26 linker/minimum, SDK36/build-tools36.0.0,
CMake3.22.1, Java21, Node22.22.0, Gradle8.14.3. No architecture expansion or broad
SDK/Gradle override. Rust uses `cargo +1.85.1 build --locked --release --target
aarch64-linux-android`. Final lock/manifests/Rust source hashes are verified again.
Gradle tasks are exactly `:app:compileDebugKotlin :app:compileDebugJavaWithJavac
:app:externalNativeBuildDebug`, with `-PreactNativeArchitectures=arm64-v8a`.
The temporary CMake uses the real RN app setup, imports authentic Rust and enforces
`--no-undefined`. IMPORTED_NO_SONAME requests basename linkage for Rust's cdylib.

New `.github/workflows/v10-phase12-1-abi-link-candidate.yml` has only the exact
feature-branch push trigger and job condition; no dispatch or PR trigger. All four
actions reuse Phase11's reviewed 40-character SHAs. Permissions are contents:read,
checkout credentials are not persisted, and Gradle cache is read-only. The job is
bounded to45 minutes and compile command to2100 seconds. Only official existing
SDK/rustup toolchain managers are used; no downloaded shell scripts, arbitrary
actions, artifact uploads, package/sign tasks, release or deploy steps are present.
This workflow is NEW and UNEXECUTED. No hosted acceptance is inherited.

## ABI contract and execution limits

On a suitable host the runner requires exactly one new Debug arm64 libappmodules.so
and inspects it and authentic libappforge_jsi.so with the pinned NDK llvm-readelf
and llvm-nm, including demangled symbols. It requires ELF64/DYN/AArch64, real Rust
text export appforge_jsi_answer, bridge import of that symbol, text export JNI_OnLoad,
and DT_NEEDED for Rust, reactnative, jsi, fbjni and the shared C++ runtime. It rejects
a legacy nativeInstall export. The precise JNI entry for this C++ provider is
`JNI_OnLoad`, not `Java_com_appforge_rustjsi_RustJsiModule_nativeInstall`.

Full undefined symbol and DT_NEEDED output, binaries' hashes and ABI JSON are kept
in runner temp. Direct C++ imports must link against RN/fbjni/JSI/C++ shared libraries
through --no-undefined; dynamic undefined entries can legitimately remain for the
Android loader. Their recorded presence alone does not prove loader resolution,
module lookup or runtime installation. No permissive unresolved-symbol flag is used.
A future successful result is **ABI_LINK_ONLY**. It is never JSI runtime acceptance.

Local actual ABI linking: **NOT_RUN**. No Rust compiler or sdkmanager is available;
there is no verified matching Android NDK. Local Gradle --version aborts (exit -6).
Invoking the host runner fails closed (exit1) at missing RUNNER_TEMP. Only genuine
RN-header syntax compilation and project adaptation ran locally. No binaries or
native link success have been fabricated. Dependency downloads and hosted runner
execution cannot be completed offline here. Historical Phase11 runs40865913157
(candidate rn-cli and sdk54-newarch) and40865913069 (platform) succeeded at this
base SHA, per the supplied confirmed baseline and preserved Phase12 logs. They do
not validate these newly authored files; no remote API query was made this run.

## Fresh validation and mandatory blocker

The exact complete Phase6 PRoot launcher was recovered from
`.appforge/tmp/v10/phase6/quality-frozen-result.json`, including original workspace,
/tmp/curl bindings, Java, PATH and Node glob. It was executed unchanged. The exact
command array and exit are in machine evidence; no reconstructed suite or altered
test rules were substituted. New tests are explicit opt-in Python fixture tests,
so the ordinary suite still has1156 tests; the eight opt-in contracts are separate.

| Check | This run |
| --- | --- |
| Complete Phase6 quality | 1155/1156; fail1, cancelled0, skipped0, todo0; exit1 |
| New opt-in contracts | 8/8 PASS; real-header compiler and expected compiler failures |
| V9 / V10 Python | 440/440 PASS / 14/14 PASS |
| Windows Node | 9/9 PASS; native Windows runtime NOT_RUN |
| Canonical Second Brain freshness | BLOCKED, SNAPSHOT_OUT_OF_DATE, exit1 |
| Private canonical generation/check | PASS with output destination relocated only |
| Stability Gate ci | BLOCKED solely by canonical snapshot, exit1 |
| Wiki audit / wiki secret hygiene | PASS / PASS |
| Gitleaks unchanged controls / source scope | PASS / zero findings |
| Bash syntax / git diff --check | PASS / PASS |
| Android ABI link local / hosted | NOT_RUN / NOT_RUN |
| Hermes registration / physical execution | NOT_RUN / NOT_RUN |

The sole full-quality failure is `V2 generator check reproduces exact committed
snapshot`. The new shell runner and workflow enter the existing selected-source
inventory, making the existing generated asset stale. This task explicitly forbids
editing that existing Android/Second Brain asset. No gate, baseline, source selection
or test was weakened; no snapshot was overwritten. Generation plus --check was
executed with the canonical module's ASSET redirected to Phase12.1 private temp;
that private PASS is separate from the canonical FAIL. Resolving the blocker needs
explicit scope authorization for the canonical generated asset, then fresh gates.

Negative controls reject changed candidate hash, missing library, incorrect ABI
(including actual runner exit64), synthetic wrong-architecture/missing-export tool
output, wrong override, copied module and repository-path adaptation. Synthetic
ELF outputs test rejection only and are not native success evidence. Source checks
also prohibit raw pointer/legacy fallback and verify read-only branch-locked CI.

Original OSV risk remains **5 open including3 HIGH**, unsuppressed:
GHSA-5p2g-fcmc-qvqq, GHSA-w3rx-r6r6-pgpr, GHSA-ch52-4w7c-c8xp,
GHSA-gcqx-w48m-w2h4, GHSA-gh4j-gqv2-49f6. Fresh OSV scan NOT_RUN; security evidence
is historical Phase10, with its withdrawn-advisory discrepancy preserved. Candidate
package graphs do not fix production. PyO3 remains =0.29.0 and its authentic lock
SHA256 remains a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a.
All original manifests/locks and tracked bytes were checked against a prewrite
baseline. CLI20.2 and SDK55 remain unpromoted.

## Separate future debug-only physical checklist

This checklist is NOT_RUN and grants no APK/build/install permission in this task:

1. After separately authorized hosted compilation, bind source SHA, Rust/bridge ELF
   hashes, ABI and runtime build identity to the exact debug app under inspection.
2. Observe real C++ module registration and Hermes
   `TurboModuleRegistry.getEnforcing('NativeAppForgeRustRuntime').answer() === 42`.
3. Observe the real hook on the JS thread and
   `global.appforgeRustAnswer() === 42`; record thread identity, not an assumed pointer.
4. Exercise repeated lookup/install, reload, re-init, teardown, retained old references,
   invalidation, background/foreground and crash-free termination/restart. Confirm no
   duplicate global, stale runtime access or callback/library lifetime violation.
5. Remove the module/library/provider in an isolated negative debug build and prove
   absence fails closed; exercise a foreign existing global and argument misuse.
6. Independently prove RN CLI20.2 actual Hermes TurboModule accessibility and its
   native/platform regressions. Metro export/bytecode and compilation are insufficient.
7. Resolve snapshot scope, rerun unchanged quality/security/stability gates, obtain
   actual hosted exact-source ABI evidence and fresh equivalent OSV assessment before
   any separately authorized promotion review. No commit or push occurred here.
