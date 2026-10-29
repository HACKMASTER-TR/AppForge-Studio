# V10 Phase 5 — targeted security fixes

Date: 2026-10-28. **V10_PHASE5_STATUS=BLOCKED.** One advisory repaired through a
supported parent migration. Image-size, PyO3, other residual vulnerabilities and
platform acceptance remain blocked. No security suppression or scanner scope
change was made.

## Worktree and boundaries

Continued the existing dirty `feat/v10-oss-unified-20261028` worktree at
HEAD/recorded base `afb3ad9209bcce8b84773d8873d037b15b4d804d`. Git reports a
single feature worktree and no parent commit for this imported base; no independent
main merge-base claim is possible. Existing V10 changes were preserved.
No commit, push, merge, deploy, release, production, Play, signing, SDK or AGP
change occurred. The original residual report and every advisory record remain
available in `osv-residual-report.md` and `osv-residual-evidence.json`.

## Zod — supported parent fix

Both Expo examples now pin **Expo 54.0.38**, previously 54.0.22. Its published
CLI 54.0.28 removes the MCP tunnel dependency; Zod and zod-to-json-schema are no
longer in either resolved graph. React 19.1.0, React Native 0.81.5, the existing
scoped PostCSS 8.5.23 override and node-forge 1.4.2 remain intact. No Zod override
was added. Each npm-generated lock has 71 changed/added/removed versioned paths;
exact paths, licenses and versions are recorded in the companion evidence.
This is removal of the affected optional developer-tool dependency by its
supported owner, not a version-only scanner workaround or Zod 4 API substitution.
Neither example source uses MCP tunnel directly.

The [npm Zod 4.6.6 metadata](https://registry.npmjs.org/zod/4.6.6) and tarball were
fetched and SHA-512 integrity verified. Actual 4.6.6 parsing, JSON schema
conversion, valid/invalid objects and bounded oversized array/set probes pass.
An accessor probe demonstrates oversized arrays reject before element traversal.
The installed old MCP 0.1.0/Zod 3 parent also passes registration, schema
serialization and bounded valid/invalid input probes without starting transport.
A negative compatibility control shows its zod-to-json-schema converter loses
properties when given Zod 4 schemas. Forcing that override would be incompatible.
Published MCP latest 0.2.4 still requires Zod `^3.25.76`; next 0.3.1 requires
`^4.4.3` and changes serialization to `z.toJSONSchema`, but installed CLI
54.0.15 requires tunnel `~0.1.0`. No tunnel override was applied.

The parent upgrade was first tested in isolated copies of both existing examples.
Both pass config evaluation, authored Android plugin prebuild and complete Android
JavaScript bundle export (568 modules, approximately 1.09 MB each). Generated
native files contain the Rust bridge/registration and preserve each architecture
setting. Exact migrated lock installation passes offline `npm ci --ignore-scripts`
for both isolated examples. Scripts/binaries were not accepted as native builds.

Two failed fixture stages are retained: shared node_modules symlinks made AppEntry
resolve App from the wrong directory; separate installations corrected the fixture.
Default export subsequently reached Hermes but failed because the Termux Node
platform is Android and the compiler host is unsupported. `--no-bytecode` export
then passed; this proves JavaScript bundling only. Hermes/native acceptance is
still BLOCKED. Initial offline migration ci also returned ENOTCACHED; normal
integrity-checked acquisition followed by a genuine offline retry passed.

## Image-size — BLOCKED

The [npm 2.0.4 artifact](https://registry.npmjs.org/image-size/2.0.4) is published;
its downloaded tarball matches npm SHA-512 integrity. Its real parser passes
ICNS entry lengths 0/1/7 rejection, JXL partial-box lengths 0/1/8/11/oversize
rejection, HEIF short/zero/oversized ispe rejection and truncation termination
probes. Valid ICNS and HEIF controls pass. Child processes have five-second
and 128 MiB V8 heap bounds. Original 1.2.1 times out after one second on the same
zero-length ICNS entry, while 2.0.4 rejects it. These are bounded acceptance
probes, not a claim of exhaustive parser correctness.

Original chain: Expo 54.0.22 → @expo/metro 54.1.0 → Metro 0.83.2 → image-size
`^1.0.2`. The tested supported SDK 54 patch changes @expo/metro to 54.2.0,
which **pins Metro 0.83.3** and retains image-size 1.2.1. Metro 0.83.8 removes
the dependency and supplies its own limited format parser; its verified artifact
passes standalone valid/invalid PNG probes. Its owner is still pinned to 0.83.3,
so a partial Metro override is not an accepted Expo migration. Registry Expo 58
uses a newer Metro/RN generation; the authored plugins explicitly depend on the
reviewed RN 0.81 registration/native integration. No SDK-major migration or native
acceptance was established, and the existing SDK/AGP setup was preserved.

Both **GHSA-5p2g-fcmc-qvqq** and **GHSA-w3rx-r6r6-pgpr** remain matched by the
final real offline scan. No patched parser is shipped by this change and no
operational input restriction is counted as an implemented mitigation.

## PyO3 — BLOCKED

[Crates.io 0.29.0](https://crates.io/api/v1/crates/pyo3/0.29.0) exists. Downloaded
crate SHA-256 matches the registry checksum; its manifest declares Rust 1.83
and retains `extension-module` and `abi3-py312`. Existing rust-python directly
pins 0.23.5 with those features; source exposes only `answer() == 42` and module
registration. Those small APIs do not establish safety for the entire dependency.

Neither Termux nor Ubuntu PRoot exposes Cargo or rustc; Ubuntu Python is
3.13.13. No supported Cargo/Python migration host was available. Compile,
extension import, behavioral and ABI acceptance were NOT_RUN. Cargo.toml and
Cargo.lock remain unchanged; neither feature inspection nor artifact verification
is reported as a successful migration. All five PyO3 records remain BLOCKED.

## Equivalent real OSV scan

OSV-Scanner 2.7.0 SHA-256
`4061ef4c021ed3c47f203b818f337e351336cc5cd67c0274fc36bd1dfc728dd7`
ran through the unchanged kernel network-denial launcher in Ubuntu PRoot.
The same four complete npm/Maven/PyPI/crates.io exports passed their existing
hash/size and 72-hour freshness policy. No database was reacquired. All 22
manifest paths and scan flags are unchanged: offline, no-resolve, Go/Rust call
analysis disabled, all-packages, all-vulns, no-ignore, recursive. Missing/corrupt
database rejection, positive vulnerable dependency detection and exec-child
IPv4/IPv6 network denial controls pass.

Baseline rerun: 11 IDs, 5 HIGH, 1,993 occurrences, 928 unique coordinates.
Final rerun: **10 IDs, 4 HIGH, 1,987 occurrences, 925 unique coordinates**;
scanner exit **1 = FINDINGS**. Only GHSA-mrhx-c7xc-m3w5 no longer matches; no new
advisory ID appears. Both remaining no-fixed-event records are retained.
Raw baseline/final reports, manifests, hashes and commands are under ignored
`.appforge/tmp/v10/osv/phase5-after/` and `phase5-final/`.

| Package/version | Advisory | Severity | Disposition |
| --- | --- | --- | --- |
| pyo3 0.23.5 | GHSA-36hh-v3qg-5jq4 | HIGH | BLOCKED |
| pyo3 0.23.5 | GHSA-chgr-c6px-7xpp | MODERATE | BLOCKED |
| pyo3 0.23.5 | GHSA-pph8-gcv7-4qj5 | LOW | BLOCKED |
| pyo3 0.23.5 | RUSTSEC-2025-0020 | UNKNOWN | BLOCKED |
| pyo3 0.23.5 | RUSTSEC-2026-0177 | UNKNOWN | BLOCKED |
| image-size 1.2.1 | GHSA-5p2g-fcmc-qvqq | HIGH | BLOCKED |
| image-size 1.2.1 | GHSA-w3rx-r6r6-pgpr | HIGH | BLOCKED |
| deepmerge 4.3.1 | GHSA-gcqx-w48m-w2h4 | LOW | BLOCKED; no fixed event |
| http-cache-semantics 4.2.0 | GHSA-ch52-4w7c-c8xp | HIGH | BLOCKED; no fixed event |
| fast-xml-parser 4.5.7 | GHSA-gh4j-gqv2-49f6 | MODERATE | BLOCKED |

Severity/alias counting follows the original pinned triage. Build-only status,
manifest reachability observations and absence from declared production files
are not artifact clearance. Android resolved artifacts, packaged EXEs, native
Windows launch, physical acceptance and distribution notices remain unverified.
Neither deepmerge nor http-cache-semantics was suppressed or upgraded solely
past last_affected. Existing trust-domain/cache recommendations remain recommendations,
not newly enforced controls. OSV_MITIGATED=0.

## Verification

Final verification results and log hashes are recorded in `phase5-evidence.json`.
Raw private logs are in `.appforge/tmp/v10/phase5/`. Native Android SDK/build and
Hermes acceptance remain BLOCKED; no configured SDK variables or local.properties
were present. Existing Android Gradle/SDK/signing files were not changed.
Hosted CI, native Windows execution and physical device acceptance are NOT_RUN.
No delivery action was performed.

| Executed check | Final result |
| --- | --- |
| Ubuntu PRoot full quality after dependency migration | PASS 1,151/1,151; zero skips |
| V9 Python regression | PASS 440/440 |
| V10 Python | PASS 14/14; existing V10 JavaScript contracts included in quality |
| Windows Node tests | PASS 9/9 on ARM64 host; native Windows NOT_RUN |
| Exact patched image/Zod probes | PASS, separately invoked bounded runtime test |
| Existing MCP schemas / incompatible override negative control | PASS |
| Existing Forge signature/PEM regression against migrated graph | PASS |
| Both Expo examples | Offline ci, config, authored plugin prebuild and JS export PASS |
| Real Gitleaks 8.30.1 source scan and detector controls | PASS; zero source findings |
| Stability Gate ci | PASS |
| Second Brain generation/freshness, changed-source audit, secret hygiene | PASS |
| Android native/Hermes/physical acceptance | BLOCKED / NOT_RUN as described above |
| PyO3 compile/import/behavioral acceptance | BLOCKED / NOT_RUN; compiler absent |

Initial manually constructed positive ICNS/PNG fixtures had incorrect headers;
corrected format-valid fixtures pass. First-attempt logs are retained. No library
source was patched and no assertion was removed to clear those fixture errors.
An extra verification-wrapper OSV invocation refused reuse of its existing
`phase5-after` evidence directory; it performed no scan. The separate fresh
`phase5-final` execution is the authoritative post-migration result, including
all offline controls and final manifest hashes. No existing evidence was erased.

Gitleaks uses the original tracked plus nonignored untracked source inventory,
redacted output and unchanged configuration. Its executable matches the prior
verified SHA-256 `00e91bbe655bd7c47753e8cfe61cb76ea1a5d7e7702fe161ee40102b46b3823b`.
Ignored synthetic credential fixtures are outside that unchanged accepted scope;
no additional exclusion was introduced. Full security acceptance remains BLOCKED.

An intermediate Gitleaks scan flagged the generated SHA-256 stored beside the
`wiki-secret-final.log` property in the new evidence JSON. The value was verified
against that log's checksum. Evidence now uses explicit checksum/path fields;
the unchanged real scanner rerun passes with zero findings across 1,326 source
files. The flagged intermediate report remains in private evidence. No scanner
rule, allowlist, exclusion or scan-scope change was made.
