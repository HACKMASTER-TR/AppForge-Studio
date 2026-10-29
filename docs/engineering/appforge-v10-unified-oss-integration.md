# AppForge V10 unified OSS integration architecture

Local partial implementation on `feat/v10-oss-unified-20261028`, based on `afb3ad9209bcce8b84773d8873d037b15b4d804d`. This is not an accepted release. No upstream source or binary is vendored. The initial task-bundle inventory is preserved byte-for-byte in [v10/integrations.initial.json](v10/integrations.initial.json); [integrations.json](v10/integrations.json) records the audited partial status, not release acceptance.

## Observed upstream matrix

Read-only GitHub API checks recorded default-branch HEAD, license-file SHA-256 and latest release metadata. These are observation pins, not evidence of upstream authenticity beyond HTTPS, artifact byte verification, platform compatibility or transitive license clearance. Release assets and available published SHA-256 digests are in [upstream-evidence.json](v10/upstream-evidence.json). No automatic update path consumes this file.

| Candidate / official repository | Observed HEAD | Release candidate | License evidence |
| --- | --- | --- | --- |
| [codeassist](https://github.com/tyron12233/CodeAssist) | `1368787112e4e8da52e5329f79c75c05f0ad276f` | `v3.33.0` | `GPL-3.0`; `LICENSE` |
| [sora-editor](https://github.com/Rosemoe/sora-editor) | `94681e9e6523cbdc288e81def76a117f7740a195` | `GitHub latest: 404; Maven 0.24.7` | `LGPL-2.1`; `LICENSE` |
| [tree-sitter](https://github.com/tree-sitter/tree-sitter) | `ad067fddf76a32c3071f8f3a31e8719d348e2737` | `v0.27.1` | `MIT`; `LICENSE` |
| [termux-packages](https://github.com/termux/termux-packages) | `00d21d12bab7c46c46c16333e9a7b2f725d09cf5` | `bootstrap-2026.10.25-r1+apt.android-7` | `NOASSERTION`; `LICENSE.md` |
| [bundletool](https://github.com/google/bundletool) | `586a43a450712a1067f3d92cf7574dee68226302` | `1.18.3` | `Apache-2.0`; `LICENSE` |
| [gitleaks](https://github.com/gitleaks/gitleaks) | `5f6ec56cfa6f8c67648d863582c7ae2f206d5c1c` | `v8.30.1` | `MIT`; `LICENSE` |
| [osv-scanner](https://github.com/google/osv-scanner) | `666f85749ac142b9ddad8bd17e4c46d39d697465` | `v2.7.0` | `Apache-2.0`; `LICENSE` |
| [syft](https://github.com/anchore/syft) | `fb56f1eef1908ad249a59ea7b2fe913bae16304f` | `v1.55.0` | `Apache-2.0`; `LICENSE` |
| [esbuild](https://github.com/evanw/esbuild) | `f6058f8364fe7ab91ca57a83e02577ed74c9cae4` | `v0.28.2` | `MIT`; `LICENSE.md` |
| [spec-kit](https://github.com/github/spec-kit) | `222a973f8b8794366680c043fb935a31f71902af` | `v1.2.4` | `MIT`; `LICENSE` |
| [superpowers](https://github.com/obra/superpowers) | `2ce8aa15d05bd57213e6f746f953aafb7ded7a8b` | `v7.0.1` | `MIT`; `LICENSE` |
| [jadx](https://github.com/skylot/jadx) | `71dc1ed810c443174285cbf65b98999851f582e5` | `v1.5.6` | `Apache-2.0`; `LICENSE` |
| [uv](https://github.com/astral-sh/uv) | `e4c55567ada966de4911513b5c526070e6cddd3f` | `0.13.6` | `Apache-2.0`; `LICENSE-APACHE` |

The GitHub SPDX field is an index, not legal interpretation. CodeAssist documents GPL-3.0-or-later, with a limited API exception; no source is imported. Sora states LGPL-2.1-or-later. uv is dual Apache-2.0 OR MIT, not Apache-only. Termux repository licensing does not establish each package license. Every observed license file has a digest and official URL in the evidence JSON.

## Existing architecture and reuse

The inventory inspected `DeviceBuildEngine.kt`, `OfflineBuildPackManager.kt`, `UltimateCodeEditorCore.kt`, `UltimateCodeEditorPanel.kt`, `LinuxLspSession.kt`, `UltimateLspProtocol.kt`, the existing secret guard, stability workflow, Windows manifest/lockfile and related contracts. Build routing remains local through the packaged Linux runtime. Existing incremental behavior belongs to Gradle and existing per-project caches; fresh job workspaces are not a persistent incremental task engine. No device speed gain is inferred.

The existing editor already owns canonical-root traversal checks, sensitive-file policy, undo/redo, restore points, tabs and diagnostic display. LinuxLspSession/UltimateLspProtocol own LSP framing and diagnostics. No duplicate editor, LSP or terminal is added. OwnerAccessPolicy and normal terminal navigation remain unchanged. Managed Ubuntu package archives/generation management are distinct from the interactive terminal and from the new catalog preview cache.

## Implemented boundaries

### A: original build observation, CodeAssist reference only

`android-app/app/src/main/assets/device-build/build-metrics.init.gradle` observes the existing Gradle task graph and outcomes. `DeviceBuildEngine.kt` adds its init-script argument alongside the already-disabled configuration cache. The existing shell log path carries bounded numeric summary lines. A separate `gradle-task-graph-v1.json` stays in the isolated build workspace. Graph nodes are anonymous sorted task indices; edges point to dependency indices. Names, environment, source, coordinates and signing material are not exported. Limits are 5,000 nodes and 20,000 edges, with explicit truncation. The topology is diagnostic, not an authenticated source graph.

Counters distinguish executed work, UP-TO-DATE, FROM-CACHE, skipped/actionless and failed tasks. Duration uses a monotonic clock. Executed work is not necessarily a cache miss. Cache-miss counts and precise invalidation causes are not inferred; invalidation is explicitly UNKNOWN. Observers do not change task actions, cache keys or success/failure behavior. Listener APIs require configuration cache disabled and were executed here on Gradle 9.6.0; device Gradle 9.3.1/AGP execution remains unaccepted.

The behavioral test runs a real offline Gradle Copy fixture through first build, no-op, changed Unicode input and task failure. First/changed execute one action, no-op reports one UP-TO-DATE action, output matches changed bytes, dependency topology remains stable, failure counter is one. It does not benchmark a device or prove cache restoration; no performance increase is claimed.

### D: reviewed catalog preview, no package activation

`OfflineToolCatalog.kt` encodes a canonical sorted TSV manifest with schema header, package/version, runtime ABI, SHA-256, HTTPS upstream, SPDX family, license-notice SHA-256 and size. It rejects duplicates, invalid identities, unsupported license families, noncanonical serialization, oversized payloads, missing/corrupt cache bodies, license notices and symlink boundaries. `OfflineBuildPackManager.inspectToolCatalog` selects only `noBackupFilesDir/v10-oss/catalog-cache`. Inspection never creates that directory, downloads, extracts, installs, prepares, activates or rolls back a package. Empty/malformed catalogs cannot be complete. Runtime ABI strings distinguish `aarch64-linux-android` from `aarch64-linux-gnu`; CPU architecture equality is insufficient.

The supported SPDX family list is deliberately narrow; it does not certify the license of an arbitrary package. Only owner-reviewed packaged metadata may call this internal preview. A notice digest proves bytes, not legal permission. No real Termux package catalog has been approved or seeded. Reuse existing generation prepare/activate/rollback only after package-level licenses, dependency closure and Bionic-versus-Ubuntu compatibility are established.

### F/H: optional host scanner execution boundary

`quality/tools/appforge_v10_oss_gate.py` supports opt-in Gitleaks and Syft release archives pre-seeded by an operator. [v10-oss-pins.json](../../quality/tools/v10-oss-pins.json) fixes versions, official artifact URLs, Linux ARM64/x64 archive SHA-256 and release-license URLs/hashes. Release license bytes were fetched and hashed; executable archives were not downloaded or executed here. GitHub-provided artifact digests are expected pins, not local byte-verification results.

The pins explicitly set `license_closure_reviewed=false`; even a correctly hashed pre-seeded archive is BLOCKED with LICENSE_CLOSURE_UNVERIFIED until a reviewed source change records transitive notice/permission evidence and enables that pin. There is no CLI or environment override.

The wrapper verifies a bounded private copy of the archive before extracting only its regular executable; it never calls extractall, follows archive links or executes imported scripts. Unsupported ABI, bad hashes or runtime failures are BLOCKED. Missing archives are NOT_RUN. Linux pins do not authorize Android execution; Android hosts are explicitly rejected. CPU (120 s), wall clock (150 s), address space (2 GiB), file output (8 MiB), archive size (100 MiB), descriptor (128), source count (20,000), per-file (8 MiB) and total source (128 MiB) limits fail closed. Go tool compatibility with the address-space bound is NOT_RUN. This is a bounded subprocess boundary, not a kernel filesystem sandbox; only reviewed tools may run on a dedicated isolated CI runner.

The source snapshot includes tracked and nonignored untracked repository files, excludes Git/home data and rejects sensitive local paths/symlinks. Public PEM certificates are allowed; private PEM inputs are rejected. Repository configs cannot override explicit scanner configs. A clean process environment omits credentials; stdin/stdout/stderr cannot expose scanner findings. Gitleaks runs `dir . --redact=100 --exit-code=42` against the temporary snapshot, not history. Only bounded rule/file/line summaries are exported. Rule IDs and locations are untrusted report data; malformed fields become REDACTED. The existing secret-hygiene guard runs first and is not replaced.

Syft scans a snapshot of dependency manifests and LICENSE/NOTICE files. Its output is reduced to standardized CycloneDX 1.5 package identities, safe versions and SPDX IDs; arbitrary properties, source URLs, license text and external references are omitted to avoid embedding credentials. Output includes the pinned Syft version, output SHA-256, component count, deterministic scanned-source digest and explicit coverage limitations in the JSON runner result. It does not inventory compiled APK/EXE contents, dynamically resolved dependencies or user projects; transitive completeness is not certified. It is not a vulnerability clearance. No SBOM was generated here because no scanner archive is seeded.

Optional CI steps use `APPFORGE_V10_OSS_SCANS=1` repository variable and a separately configured pre-seeded `APPFORGE_V10_OSS_CACHE` path. Default CI behavior and branch semantics remain unchanged. The opted-in step fails on missing tools; it does not silently pass. Successful Syft output is uploaded as a seven-day artifact by a full-SHA-pinned upload action; its identity, license hash and Node 24 runtime are in [ci-action-evidence.json](v10/ci-action-evidence.json). Existing workflow action revisions are preserved. No hosted CI was triggered.

Wrapper exit mapping: 0 PASS for the stated scanner scope, 1 FINDINGS, 2 NOT_RUN, 3 BLOCKED. Gitleaks scanner exit 42 means findings; other nonzero scanner exits are blocked. A zero exit with missing/malformed/inconsistent report is blocked. Synthetic tests cover redaction and suppression without storing live secrets. Actual scanner/version/ABI acceptance remains NOT_RUN.

### G: offline OSV fail-stop

The OSV entry point returns BLOCKED for unverified scanning. `--offline` never invokes a scanner or network path. No implementation claims findings/no findings, database freshness, ecosystem coverage or scanner availability. Add an actual version-specific adapter only with a pre-seeded DB manifest binding hashes/collection time, a maximum age, supported ecosystem coverage and an enforced OS network boundary. Then distinguish findings, stale/missing DB, unsupported ecosystem, network failure and missing scanner. Report-only onboarding may retain existing backlog with explicit scope/date/rationale; missing/stale coverage still cannot be green.

## Deferred components and minimal next actions

| Component | Exact blocker | Minimal next action |
| --- | --- | --- |
| Sora | No approved LGPL distribution/relinking mechanism, transitive AAR review or editor parity acceptance. Existing APK resource rules exclude META-INF LICENSE/NOTICE/LGPL files. | Review 0.24.7 AAR and recursive POM/module closure, create a distributable notice/source/relinking plan and opt-in parity tests before adding the dependency. |
| Tree-sitter | No selected Sora adapter; Android binding and grammar libraries are distinct upstreams whose binaries, ABIs and licenses are not approved. | Audit Sora language-treesitter artifact plus Android binding and exact grammar closure; test incremental/Unicode/large-document/ARM64 behavior. Never fetch grammars at startup. |
| bundletool | No existing invocation found in inspected Android/scripts paths; no verified JAR in task cache, reviewed shaded-license inventory or valid signed AAB/device-spec fixture. | Seed/byte-verify official 1.18.3 all-JAR using observed digest, review shaded notices; implement isolated validation and explicit test-key APKS identity/reproducibility checks. No install/publish or production signing. |
| OSV | No verified scanner/DB snapshot, collection-time/freshness evidence or isolated no-network execution boundary. | Prepare and hash a supported-ecosystem DB snapshot and scanner in a dedicated isolated Linux runner; implement version-specific status tests. |
| esbuild | Existing node-web path already probes project-owned esbuild with transformSync; no equivalent pinned bundling fixture or before/after benchmark justifies replacement. | On supported isolated host, audit exact 0.28.2 artifact/platform closure and compare equivalent bundle semantics, sourcemaps and timings before selecting a build-path change. |
| JADX | No verified distribution/transitive notices, bounded imported-APK sandbox, resource acceptance or manifest/DEX fixture corpus. No analyzer or decompiler was added. | Audit 1.5.6 ZIP dependency notices and Java minimum; build host-first user-selected artifact isolation with zip-slip/bomb/time tests before optional manifest/DEX/decompile support. |
| uv | Official release platform list does not establish Android/Bionic ARM64 compatibility. Existing Chaquopy path targets Python 3.12 inside the managed runtime; coexistence is untested. | Keep device adapter disabled; evaluate a pinned host-only locked Python fixture or separately prove app-scoped Android runtime compatibility. No global Termux replacement or remote compilation. |

Sora published metadata was checked independently of GitHub releases: `io.github.rosemoe:editor:0.24.7`, direct published dependencies `androidx.annotation:annotation:1.11.0`, `androidx.collection:collection:1.6.0`, `org.jetbrains.kotlin:kotlin-stdlib:2.4.10`. [sora-maven-evidence.json](v10/sora-maven-evidence.json) stores exact POM/module hashes and variants. These differ from HEAD versions. No recursive dependency/license or AAR/native verification is claimed. Current AppForge uses AGP 9.1.1, Compose plugin 2.4.10, compileSdk 37/minSdk 26; no minimum-toolchain compatibility claim is inferred from matching Kotlin versions.

### J/K: original methodology adaptation

The existing wiki page [V10_OSS_Integration](../wiki/03_Architecture/V10_OSS_Integration.md) contains feature contracts, decisions, acceptance matrix and evidence routing. This adapts specification-first and test-first practices without copying external prompts/templates, adding competing policy files or changing authority. The original test suites failed for absent implementations before implementation; production fixes followed behavioral failures; independent reruns execute real Gradle/Kotlin and Python boundaries. Existing governance still requires external review and exact-source acceptance.

## Existing electron-builder assessment

`windows-host/package.json` and lockfile already pin electron-builder 26.15.3 and Electron 43.4.1. `dist` selects portable x64 with `--publish never`; `signAndEditExecutable` remains false. Packaged host, native EXE and project payload identities remain distinct. Continuation used workspace-scoped `npm ci --ignore-scripts --no-audit --no-fund` with a local npm cache; no global reinstall, signing configuration change or physical packaging attempt occurred. Windows repeatable packaging needs a proper Windows runner with an offline dependency/Electron cache, artifact hashes and independent signing-boundary review. Node tests are separate from EXE execution, signature acceptance and cache completeness.

## Threat model and rollback

Threats considered: GPL source contamination; stripped LGPL notices; malicious/path-traversing source and tar entries; archive bombs; stale/forged scanner reports; secret values in process output or SBOM URLs; unverified ABI execution; Bionic/GNU confusion; network fallback in offline mode; overclaiming static contracts; preview-to-V9 production authority escalation. Numeric topology/resource summaries and app-private cache separation reduce exposure. Host wrappers require reviewed trusted scanner archives and dedicated runner isolation; they do not create an OS security sandbox. License review, supply-chain authenticity and physical behavior remain independent gates.

Rollback is local and reversible: remove the two Gradle arguments and original init asset, remove the catalog preview method/class, and disable the optional CI variable/remove optional scanner steps. Existing editor, LSP, build cache implementation, offline generation manager, production signing, auth and terminal behavior are untouched. No migration, installed app, device rootfs, global package state or production credential was changed. Remove generated host reports/cache artifacts separately; never delete shared managed package archives.

## Verification and remaining acceptance

Commands, exit codes and elapsed execution times are in [verification.json](v10/verification.json) and the root [integration report](../../V10_OSS_INTEGRATION_REPORT.md). Durations describe test runs, not before/after build benchmarks. Local logs stay ignored under `.appforge/tmp/v10/`. Full quality success, Android build, external review, device acceptance and hosted CI are separate. Physical Android, Windows EXE/signature, Play Production and Cloudflare acceptance remain NOT_RUN.

Continuation: the original observer tolerates graph-output I/O failure; equal-output rollback and an original Copy fixture timing are exercised by the runtime test. Sora core AAR bytes were verified without importing them. Exact results and immutable upstream rechecks are in `v10/continuation-upstream-audit.json` and the root report. Existing editor/LSP and terminal implementations remain unchanged.
