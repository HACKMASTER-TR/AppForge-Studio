# V10 Phase 10 — all five remaining original advisories

2026-10-29. **NO_SAFE_PARENT_FIX_ALL_5_BLOCKED** means no parent change met all
acceptance requirements in this invocation. It does not mean every candidate is
incompatible. No dependency migration or application mitigation was promoted.
All five remain in the genuine current offline scan. No release approval.

## Identity and scope

Branch `feat/v10-oss-unified-20261028`, immutable starting and final HEAD
`b286bb06d3e66947fc21a37734a1f70ad859f20b`. Initially no tracked modifications;
the four preexisting untracked `.t21-*` directories were preserved. Work stayed
in place. No git add, commit, push, reset, clean, rebase, merge, release, deploy,
APK, production, Play, signing, hook, ignore, permission or remote-config change.
No global installation. New fixture dependencies use workspace-private npm
installs with lifecycle scripts disabled. Existing ignored evidence is retained.

Reviewed Phase5/6 reports, Phase9/9.3 PyO3 provenance, Phase94 raw project/result
and source freeze, actual manifests and lock graphs, native policy and authored
plugins. Phase94 has no tracked phase94-report.md; its actual private JSON/raw
scan is the comparison baseline, not a substituted older report. The complete
13-integration inventory remains in scope; CodeAssist, Sora, Tree-sitter, Termux
packages, Bundletool, Gitleaks, OSV-Scanner, Syft, esbuild, Spec Kit, Superpowers,
JADX and uv retain their existing 2 DONE / 4 PARTIAL / 7 BLOCKED decisions; `preservation.json` records all IDs,
source/test paths and existing blockers, with no feature deletion. PyO3 remains
`=0.29.0`; Cargo.lock SHA-256 remains
`a3be190006d0345960589c281dc5b913a26325c4dd4efbd2b117a2fe3e948f1a`.

Private evidence root: `.appforge/tmp/v10/phase10/` (0700). Durable machine
records: `docs/engineering/v10/phase10-evidence.json`. Evidence hashes bind
files to this run; historical platform results do not transfer to dirty sources.

## Decisions and executable evidence

### image-size 1.2.1 — BLOCKED (two HIGH IDs)

**GHSA-5p2g-fcmc-qvqq**, **GHSA-w3rx-r6r6-pgpr**. Both Expo examples resolve
Expo 54.0.38 → @expo/metro ~54.2.0 (54.2.0) → pinned Metro 0.83.3 → image-size
^1.0.2 (1.2.1). Separate root Metro 0.83.2 also retains it. Full parent edges
and shortest root chains are in `current-lock-graphs.json`; deleting just one
Metro copy cannot remove the actual parser consumers. Current registry sdk-54
still returns 54.0.38. This is not an unused direct dependency.

Prior verified SDK55 55.0.32 / Metro0.83.8 / RN0.83.10 / React19.2 candidate
removes image-size, but generated JSI gradle.properties forces newArchEnabled=true
where authored app.json requests false. Source constraints are
`RustProjectPolicy.kt:131–160`, DeviceBuildEngine's Expo54 ready path,
`examples/rust-expo-jsi/appforge-rust-jsi.json`, `app.json`, `withRustJsi.cjs`,
and the authored native bridge. No SDK/Metro override or policy relaxation.
Existing Phase6 sdk55 fixture/results remain historical isolated evidence;
compiled Android, Hermes and actual native registration for SDK55 are NOT_RUN
here. Native compatibility cannot be inferred from JavaScript export.

Fresh executable `image-negative.cjs` runs original 1.2.1 zero-length ICNS in a
128 MiB child with a one-second SIGKILL timeout and reproduces the hang. Pinned advisory records declare image-size 2.0.3 fixed for both IDs. Existing
`v10_phase5_real_runtime.cjs` reruns exact verified 2.0.4 ICNS/JXL/HEIF malformed,
truncated and valid asset controls in five-second bounded children; PASS.
Logs: `image-vulnerable.log`, `image-patched.log`. This tests candidate parser
behavior; no input guard or patched parser is shipped. Next: reviewed SDK native
bridge migration, compile with pinned Android/NDK, Hermes bytecode export, actual
JSI/Turbo registration and offline/Gradle/policy regression before promotion.

### http-cache-semantics 4.2.0 — BLOCKED (HIGH)

**GHSA-ch52-4w7c-c8xp**. Actual Windows chain is electron-builder26.15.3 →
app-builder-lib26.15.3 → nested @electron/get3.1.0 → got11.8.6 →
cacheable-request7.0.4 → http-cache-semantics4.2.0. Electron43's separate
@electron/get5 is not the vulnerable chain. Source:
`windows-host/node_modules/app-builder-lib/node_modules/@electron/get/dist/cjs/GotDownloader.js`
passes download options to got.stream; got's actual default HTTP cache is
undefined. Electron's artifact download cache and got's response cache are
different mechanisms. No global override and no bump solely past last_affected.

New bounded probes exercise both actual CachePolicy and actual cacheable-request
with an in-memory synthetic transport: public max-age=0 plus client max-stale can
reuse principal A's synthetic body/cookie for B in the same store. Actual consumer
storage checks reject private/no-store; Vary: Cookie and separate stores prevent
reuse. Global policy.satisfiesWithoutRevalidation alone is not a storage guard:
an initial assertion incorrectly assumed it enforced private; corrected probes
include the actual consumer's storable check. First failure is retained in
`behavior.log`, followed by passing `behavior-frozen.log`. No external HTTP server,
real cookies, or network security change. Package inclusion in a real EXE is
NOT_RUN: package.json's explicit production file list and adm-zip-only runtime
dependency are intent, not an inspected artifact. No existing EXE/APK was touched.

Current registry latest is 4.3.0, but there is no verified patched event. Current
[GitHub advisory](https://github.com/advisories/GHSA-ch52-4w7c-c8xp) is withdrawn
October 28 as invalid, corroborated by a fresh OSV API record. The original
October28 authenticated export still matches it. Preserve the pinned HIGH finding
and discrepancy; do not reclassify it as fixed. Next: normal authenticated fresh
DB acquisition/reconciliation without weakening freshness or scope; inspect actual
unsigned host packaging and dependency closure on supported Windows. Keep
untrusted projects out of privileged build sessions and avoid shared authenticated
HTTP caches; these are operational recommendations, not implemented mitigations.

### fast-xml-parser 4.5.7 — BLOCKED; supported candidate prepared (MODERATE)

**GHSA-gh4j-gqv2-49f6**. `examples/rust-react-native-turbo` pins CLI20.0.0;
root cli-platform-android20.0.0 → cli-config-android20.0.0 and
CLI20.0.0 → cli-doctor20.0.0 → cli-platform-apple20.0.0 both reach parents
that declare parser ^4.4.1. Android's getMainActivity
uses XMLParser/XMLValidator; Apple getInfo and getBuildConfigurationFromXcScheme
use XMLParser. These are real build/config consumers; deleting CLI loses start,
autolinking and native build functions. No blind incompatible major override.

Current registry CLI20.2.0 supports RN ^0.81 and parents declare parser ^5.3.6.
Its exact isolated full example resolves 5.11.2, retaining React19.1/RN0.81.5 and
all authored source/native/Gradle files. Original CLI20.0 baseline and 20.2 candidate
both pass real CLI config and Android JavaScript bundle; normalized project and
autolinking output match. `cli-lock-delta.json` records all 32 versioned package
path changes. Changes include body-parser1.20.9 →2.4.0 via dev-server API;
there is no complete native/Hermes/registration or dev-server regression acceptance
here. Therefore retain the candidate privately and defer promotion. CLI20.1.0 is
not a fix: its parents still require ^4.4.1. This is an actionable supported
parent route, not a demonstrated incompatible parser API.

New bounded XMLBuilder probes cover comment and CDATA delimiter injection plus
valid text/comments/CDATA. Registry 4.5.3 is a true vulnerable negative control;
4.5.7, patched 5.7.0 and actual candidate5.11.2 escape the tested delimiters.
The initial test wrongly expected 4.5.7 injection; actual emitted XML revealed
backported behavior and the separate vulnerable control corrected the premise.
`behavior-retry.log` retains that failure. No test or OSV rule was weakened:
4.5.7 remains OSV-affected, and bounded escaping tests do not establish general
security clearance. The [upstream advisory](https://github.com/advisories/GHSA-gh4j-gqv2-49f6)
still declares 5.7.0 patched. Actual old/new Android manifest consumers pass valid,
missing-launcher and malformed input; actual Apple scheme consumers pass valid,
malformed and missing-file cases without running Xcode. Next: complete candidate
server/body-parser negative regression, Android native/Hermes/Turbo acceptance,
new equivalent offline scan for candidate graph and full quality, then promote
manifest and authentic complete npm lock together if accepted.

### deepmerge 4.3.1 — BLOCKED (LOW)

**GHSA-gcqx-w48m-w2h4**. Both Expo54 examples use @expo/config12.0.14 →
deepmerge^4.3.1; RN CLI20.0/cli-config/cli-doctor use ^4.3.0. Actual Expo Config.js
uses it for modifyConfigAsync modifications and existing plugin properties.
Inputs are project configuration; Expo config/plugins and React Native config
already execute project-controlled JavaScript. No direct AppForge production
server/runtime importer was found in the reviewed tracked graph. Do not import
hostile projects/plugins into privileged sessions. This is a trust boundary
observation, not an enforced new input guard or a scanner reachability waiver.

Latest published registry release remains4.3.1; upstream has no patched version.
New bounded real probes pass ordinary nested/array merges, ordinary hostile-source
key rejection and constructor payload/global-prototype negative controls, but
reproduce inherited attacker property on returned objects when hostile JSON is
the target or the target has a null prototype. Global Object.prototype stays
unchanged; returned-object poisoning still matters. No application wrapper would
protect these third-party internal consumers, so no cosmetic guard was added.
CLI20.2 still depends on deepmerge. Next: upstream release or supported parent
replacement, caller/target property ownership audit and actual config/plugin
regressions. The [upstream advisory](https://github.com/advisories/GHSA-gcqx-w48m-w2h4)
remains LOW and unsuppressed.

## Exact current offline OSV assessment

Unchanged official 2.7.0 executable SHA-256
`4061ef4c021ed3c47f203b818f337e351336cc5cd67c0274fc36bd1dfc728dd7`.
The existing genuine harness is executed with only its evidence-directory
assignment/parent guard relocated to Phase10; `osv-harness-relocation.json`
records this. Binary and four complete authenticated generation-pinned exports
remain in their original cache. Same hash/size/72-hour checks, original
22 manifests, source inventory, offline/no-resolve, no Go/Rust call analysis,
all-packages/all-vulns/no-ignore/recursive flags. Fresh current source mirror,
not Phase6 source copies. Raw commands and manifest hashes are in
`osv/commands.json`, `osv/source-inventory.json`, `osv/summary.json`.

Result: exit1 FINDINGS, 1,981 package occurrences, 919 unique coordinates,
7 vulnerable package occurrences, **5 IDs / 3 HIGH / 1 MODERATE / 1 LOW**.
Exactly the Phase94 ID set; no new IDs. PyO3 previous five IDs remain absent.
Known-vulnerable lodash fixture, missing DB127, corrupt DB127 and inherited
kernel seccomp IPv4/IPv6 exec-child socket-denial controls all PASS. Nothing
suppressed. OSV_MITIGATIONS_VERIFIED=0. Current upstream metadata is separate
from this pinned DB assessment; the scanner result is not silently reconciled.

## Verification and final evidence

Final result fields, commands, timestamps/inventories and log hashes are in the
machine record. Full quality must be backed by its final fresh PRoot log/result,
never inferred from an earlier run. Android SDK, Cargo/rustc and usable native
Hermes/JSI acceptance are unavailable on this host; those stages are NOT_RUN.
Previous Android/Windows/Rust hosted PASS belongs only to previous commits.
Physical/hosted acceptance of this dirty checkout and release approval: NOT_RUN.

Changes are limited to the new real behavior probes, this report, machine record,
V10 architecture wiki addition and canonical generated Second Brain. No shipped
manifest, lock, native source, Android policy, production implementation, CI or
scanner configuration changed. Full paths appear in the machine record and final
Git inventory. New probes are opt-in because they require exact external fixture
installations; full quality remains the complete ordinary suite, not these probes
counted as default tests. Failure logs and candidate fixtures are preserved.


## Final executed results

| Check | This invocation |
| --- | --- |
| Exact Phase6 PRoot full quality | PASS 1,154/1,154; fail0/cancelled0/skip0/todo0; 189.526 seconds |
| V9 Python regression | PASS 440/440 |
| V10 Python | PASS 14/14 |
| Windows Node | PASS 9/9; native EXE inspection NOT_RUN |
| Host workflow contracts | PASS 5/5 |
| New opt-in dependency and real consumer probes | PASS five bounded modes; includes vulnerable negative controls |
| Original/patched image parser probes | PASS; original hang is killed only in bounded child |
| CLI baseline and 20.2 candidate | Both config and Android JS bundle PASS; normalized project/autolinking equality PASS |
| Real OSV2.7.0 | FINDINGS exit1, same5 IDs/3HIGH, all four scanner controls PASS |
| Real Gitleaks8.30.1 | PASS zero source findings; positive and eight negative-path controls PASS |
| Canonical Second Brain | Generation and --check PASS |
| Wiki changed-source audit | PASS with existing advisory coverage warnings; no errors |
| Wiki secret hygiene | PASS |
| Stability Gate ci | PASS |
| Git diff whitespace / identity / manifest freeze | PASS |
| Native Android/Hermes/JSI/Turbo, physical, hosted dirty-tree acceptance | NOT_RUN; not inferred from any local PASS |

`quality-result.json` preserves the exact Phase6 command array, workspace and secure
/tmp/curl bindings, Java and Ubuntu-first PATH; no global tool install. Its log
hash binds the 1,154-test result. `checks.json`, `postchecks.json` and the durable
machine record preserve all executed command exits. The source freeze and all
22 manifest digests remain unchanged after quality. The final source Gitleaks
inventory is private, includes the final report/machine record, and uses the
same configuration and source-scope rule as earlier phases. Report/machine
evidence files are outside the canonical selected Second Brain prefixes; the
wiki and generated snapshot were frozen before full quality. CJS probes are
explicitly hashed as separate runtime evidence because the existing canonical
snapshot coverage policy does not select .cjs; that policy was not expanded.

No accepted patch, no mitigated original advisory, no delivery or release
approval. The safe next action is candidate host acceptance and ordinary fresh
OSV provenance reconciliation, not a claimed zero-advisory graph.

The initial preservation wrapper used a whitespace-sensitive Cargo.toml string
assertion and failed despite the correct lock hash. The corrected wrapper parses
TOML and verifies the same exact =0.29.0 pin/hash; both logs are retained. An
initial CLI-config call from repository root failed for the wrong working
directory; the recorded baseline/candidate runs use their actual fixture roots.
