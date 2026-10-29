# V10 Phase11 — isolated native CI candidates

Status: NATIVE_CI_CANDIDATE_PREPARED. Starting and final Git HEAD:
`de0b2ee1c7105140d27900b6f450ec664fe928e6`, branch
`feat/v10-oss-unified-20261028`. This is uncommitted local work; no hosted result
belongs to this workflow yet. Phase10 hosted completion was not queried or assumed.
Raw evidence: `.appforge/tmp/v10/phase11/`; durable result: `phase11-evidence.json`.

## Scope and source identity

All initially tracked regular files were SHA-256 frozen before edits in
`baseline-source-hashes.json`. Existing four `.t21-*` directories were retained.
Production examples, npm manifests/locks, all Cargo manifests/locks, Android and
Windows implementation and V9 remain unchanged. PyO3 remains `=0.29.0` and its
original authentic lock is preserved. No Git index, branch, hook, ignore, remote,
scanner, security policy, seccomp or provenance change. No delivery action.

## CLI20.2 and fast-xml-parser

The exact authored RN Turbo example was copied without node_modules into Phase11.
Only its private package.json and npm-generated full package-lock.json were taken
from the Phase10 candidate. This is not a production lock promotion. Fresh
registry responses for CLI20.2.0 and both parser-owning parent packages are saved
in `upstream-metadata.json`, including declared dependencies and dist integrity.
The installed graph independently verifies React19.1.0, RN0.81.5,
CLI20.2.0 and parser5.11.2. Parent parser range is ^5.3.6. No parser override.
CLI20.2/RN0.81 support is the previously established Phase10 compatibility basis;
a CLI package peerDependencies field is not invented as independent proof.

The full lock has exactly 32 package-path version changes against production,
recorded in `cli-lock-delta.json`. All authored native, Android, Turbo, Rust,
Babel and Metro files are byte-identical. Changes include body-parser1.20.9 to
2.4.0; this migration is not treated as a parser-only substitution.
Fresh offline npm ci with scripts disabled passed; normal offline npm ci also
has its own explicit result. npm performs lock integrity verification. The first
SDK replay attempts used the default cache and failed ENOTCACHED; subsequent
private-cache replays passed without downloading or changing their locks.

Fresh CLI config and Android Metro bundle passed. Actual CLI server middleware
and its actual body-parser are exercised with in-memory streams: ordinary JSON,
malformed JSON, primitive JSON, excessive size, unsupported content encoding and
packager status. Invalid bodies never reach the editor-launch handler. Initial
response mock lacked compression's native response methods; the retained failed
log was followed by a passing real ServerResponse-based probe. No listening
server or external editor was started. This does not test sockets/WebSocket clients.
The original Phase10 five-mode command was also rerun with all four fixture paths;
its exact command, exits and log digest are in `behavior-result.json`. Real XML
Android/Apple consumers and bounded XMLBuilder controls are included there.

The authored native project already exists; CLI init is not substituted for its
compilation. Local SDK/NDK, Gradle, Rust and native Hermes executables are absent.
Native Gradle, Turbo registration and Hermes execution remain NOT_RUN.
No packages were promoted and no advisory was declared remedied.

## SDK54 New Architecture and SDK55

An independent exact SDK54 JSI copy changes only app.json's architecture flag
before prebuild. Expo prebuild succeeds, generates newArchEnabled=true and
hermesEnabled=true, preserves the authored RustJsiPackage registration, and copies
the exact Java/C++ source. `candidate-inspection.json` records these comparisons.
This is a generated-project probe, not a working bridge migration.

Precise blocker: RustJsiModule.install() synchronously reads
ReactApplicationContext.getJavaScriptContextHolder(). Its source explicitly says
bridgeless is unsupported. A nonzero legacy runtime pointer and safe JS-thread
installation cannot be assumed under SDK54 New Architecture interop. No policy
or bridge shim was rewritten to hide this issue. Required replacement: reviewed
RN0.81-supported runtime-executor/CallInvoker installation with correct lifetime
and thread ownership, compiled native library, actual package availability,
Hermes installation returning true, global appforgeRustAnswer() returning42,
reload/reinstall/teardown checks and fail-closed absent-module behavior.

A separately isolated existing SDK55 candidate was replayed using its authentic
full lock and prebuilt successfully. Its graph has zero image-size occurrences,
Expo55.0.32, React19.2.0 and RN0.83.10. It retains the existing narrowly scoped
postcss override already authored in SDK54; no new override was added, and no
image-size or Metro override is used. SDK55 also generates New Architecture and
preserves source registration, but that does not make the legacy bridge supported.
SDK55 native compile and runtime remain NOT_RUN. It is not promoted.

## Prepared hosted compile gate and staged acceptance

New workflow `.github/workflows/v10-phase11-native-candidate.yml` only runs on the
exact isolated feature branch, with contents:read, pinned existing action SHAs,
credential persistence disabled, 45-minute job and 2100-second command bounds.
It has rn-cli and sdk54-newarch matrix legs. Existing platform jobs are unchanged.
No artifacts, packaging, signing, publishing, release or deployment steps exist.

`scripts/v10/phase11-native-candidate.sh` creates runner-temp copies. CLI candidate
lock generation must exactly match the reviewed Phase11 lock SHA before any
install/build; resolver drift fails closed and requires renewed review. It uses
safe npm ci then ordinary offline ci, retains React/RN pins, checks server behavior,
Metro/Hermes bytecode, or SDK54 prebuild/registration equality and Expo Hermes
export. It builds the authentic locked Rust library with Rust1.85.1 for Android
arm64 using NDK27.1.12297006. Declared Gradle8.14.3 tasks are exactly
`:app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:externalNativeBuildDebug`,
with arm64 architecture; SDK36/build-tools36.0.0/CMake3.22.1 are installed explicitly.
Compile probe success prints native registration runtime NOT_RUN.

Next GitHub action, only after separate explicit commit/push authorization: run
V10 Phase11 isolated native candidates on the newly pushed exact source SHA;
review both matrix logs, lock identity and all compile/Hermes stages. A historical
Phase10 workflow run cannot validate these new files. These jobs intentionally
cannot provide runtime acceptance. Subsequent native Hermes/Turbo and JSI bridge
integration execution is still required, with actual module answer42 and negative
registration controls. Only after complete acceptance, review manifest+authentic
full lock together, rerun all current tests and genuine pinned OSV scope/controls,
then prepare a technical promotion. This is not a release plan or authorization.

## Remaining advisories and validation scope

Carry forward Phase10's blocked deepmerge4.3.1/no patch and the withdrawn upstream
http-cache-semantics advisory discrepancy against pinned OSV. No repeated attack
test was required for those findings beyond the explicitly requested original
probe rerun. No new supported parent patch was established here.
Production graph is unchanged. OSV is HISTORICAL_PHASE10, not a fresh Phase11 scan:
5 unsuppressed IDs / 3 HIGH / 1 MODERATE / 1 LOW over the original22 manifests.
Expected production count remains five; no fresh candidate clearance is asserted.
Candidate parser replacement and SDK55's absent image-size are graph observations,
not newly scanned security acceptance. Scanner scope and policy remain unchanged.

Final check exits, test counts, raw log hashes, selected-source freeze and exact
changed paths are recorded in the machine evidence. A successful local quality
run is not native, physical, hosted or production acceptance. Failed preliminary
harness attempts are retained alongside corrected results.

The initial Gitleaks source scan flagged a SHA-256 digest serialized under a
filename key containing `secret`. The evidence representation now uses explicit
path/sha256 objects; no rule, scope or suppression changed. The full unchanged
source scope then scanned clean. Positive and eight negative-path scanner controls
also passed. This formatting correction does not conceal credential content.

## Final executed checks

| Check | Result |
| --- | --- |
| Phase6 exact PRoot full-quality launcher | PASS 1156/1156, fail0/cancelled0/skipped0/todo0, exit0, 203.386 seconds |
| V9 Python / V10 Python | PASS 440/440 / 14/14 |
| Windows Node regression | PASS 9/9; native Windows host acceptance NOT_RUN |
| Existing host and new candidate contracts | PASS 7/7 |
| CLI isolated ignore-scripts offline replay / normal offline replay | PASS / PASS |
| CLI config / Metro / five original Phase10 modes / new server probe | PASS |
| SDK54 New Architecture / SDK55 prebuild | PASS / PASS; runtime NOT_RUN |
| Stability Gate ci | PASS |
| Canonical Second Brain generation and check | PASS |
| Wiki changed-source audit / secret hygiene | PASS / PASS |
| Real Gitleaks8.30.1 unchanged-scope source scan and controls | PASS zero findings; positive detected, eight negative paths PASS |
| Shell syntax / git diff whitespace / empty Git index | PASS |
| Native Android compile / Hermes native Turbo or JSI execution | NOT_RUN; hosted compile candidate prepared |
| Fresh OSV / new exact-source hosted CI / physical acceptance | NOT_RUN; historical OSV retained explicitly |

No commit, push, main update, deployment, release, signing or Play action occurred.
