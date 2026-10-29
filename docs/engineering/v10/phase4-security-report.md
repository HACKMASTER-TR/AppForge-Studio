# V10 phase4 — real Linux ARM64 scanner verification

Overall **PARTIAL**, internal local TEST execution only; no release acceptance.
Continued the same dirty `feat/v10-oss-unified-20261028` worktree at
`afb3ad9209bcce8b84773d8873d037b15b4d804d`. Existing edits and phase3 evidence
are preserved. No delivery operation, system package/rootfs installation, global
PATH change, production access, signing change or APK installation occurred.

## Verified host and assets

Existing Ubuntu PRoot reports `aarch64`, dpkg `arm64`, glibc 2.43 and executes
`/bin/true`. Both downloaded executables have ELF64 little-endian e_machine 183
(AArch64), and actually execute in that PRoot. No Android Bionic compatibility
or native Linux Node/JVM toolchain acceptance is inferred.

Fetched exact release metadata and versioned checksum files from official GitHub
upstreams, then downloaded ARM64 tarballs into ignored restricted
`.appforge/tmp/v10/phase4/`. Locally computed SHA-256 matches both the upstream
checksum line and existing project pin:

| Tool | Official release archive | SHA-256 | Runtime version | Archive license |
| --- | --- | --- | --- | --- |
| Gitleaks | [gitleaks_8.30.1_linux_arm64.tar.gz](https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_linux_arm64.tar.gz) | `e4a487ee7ccd7d3a7f7ec08657610aa3606637dab924210b3aee62570fb4b080` | `gitleaks version 8.30.1`, exit 0 | MIT |
| Syft | [syft_1.55.0_linux_arm64.tar.gz](https://github.com/anchore/syft/releases/download/v1.55.0/syft_1.55.0_linux_arm64.tar.gz) | `929bd163db2d7cd2255d3bcb8967208079813d7b46b0c952f7b794d8e2c7d304` | `syft 1.55.0`, exit 0 | Apache-2.0 |

Checksum sources: [Gitleaks versioned checksums](https://github.com/gitleaks/gitleaks/releases/download/v8.30.1/gitleaks_8.30.1_checksums.txt),
[Syft versioned checksums](https://github.com/anchore/syft/releases/download/v1.55.0/syft_1.55.0_checksums.txt).
Their local SHA-256s are respectively
`061476c21adaf5441516f96f185c1a4706a83cd6329b9b38762271b3d4a52fae` and
`aba5ac8ee870bb93fd3b2058471ffe7e57492ba673b5e0bcbfca92006c2f4dc7`.
Archive LICENSE bytes match existing versioned license pins:
`e3884b252b3bfc045e55be43a34d1e80da070bc6f804ac95bf4660e97d62ebc6`
and `c71d239df91726fc519c6eb72d318ec65820627232b2f796219e87dcf35d0ab4`.

**Trust limit:** these are HTTPS upstream checksum consistency checks, not
independently authenticated signed provenance. Syft's published
[Sigstore checksum bundle](https://github.com/anchore/syft/releases/download/v1.55.0/syft_1.55.0_checksums.txt.sigstore.json)
was downloaded, but its signature/certificate/identity/transparency chain was
not verified. Gitleaks release assets reviewed did not include an equivalent
named bundle; repository attestations were not verified. Transitive
license/notice closure and redistribution remain **NOT_APPROVED**.

Downloads were capped at 100 MiB. Before binary extraction, every tar entry was
checked for safe relative paths, regular-file/directory types, no links, fewer
than 5000 entries and aggregate declared size below 200 MiB. Existing bounded
extractor copies only the exact tool entry, at most 100 MiB, into a newly created
private directory; executable mode 0500. LICENSE was read separately. No installer
script was fetched/executed, no x86 archive was executed, no binary was vendored
or added to Android packaging. PRoot is a compatibility environment, not a hostile
binary security sandbox.

## Actual scans and retained failures

Gitleaks uses an explicit project-owned config extending default rules,
`dir`, `--redact=100`, `--exit-code=42`, JSON output and no banner. Version runs
preceded scans. The first safe synthetic token had insufficient detection
characteristics: scanner exit 0 and empty report; the fixture assertion failed.
A revised high-entropy dummy GitHub-format token was detected by `github-pat`:
exit **42**, **1 finding**. These failures/retries are retained, not relabeled.
No dummy or real secret value is copied into this report.

The existing snapshot helper initially failed closed on five tracked operational
`.appforge` files (`LOCAL_DATA_IN_SOURCE_SET`). A separate internal test snapshot
explicitly excluded those five files. The production helper/gate was unchanged.
The resulting nonignored source snapshot contained **1302 files / 13,743,701
bytes**, source digest
`68a448905a54fce187897860d669259d7a759ec3cb8c63da7f62724618eece1d`.
Gitleaks scanned that snapshot: exit **42**, **8 findings**. This is detected,
not clean or scanner error. These historical findings are individually resolved in the triage addendum below. Non-sensitive locations only:

| Rule | Repository file | Line |
| --- | --- | --- |
| generic-api-key | android-app/app/src/main/java/com/appforge/studio/build/DownloadCoordinator.kt | 96 |
| generic-api-key | android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentIntelligenceTest.kt | 16 |
| generic-api-key | android-app/app/src/test/java/com/appforge/studio/terminal/TerminalUltimateFoundationTest.kt | 96 |
| generic-api-key | android-app/app/src/test/java/com/appforge/studio/terminal/TerminalPtyCopyCaptureTest.kt | 9, 75 |
| private-key | cloudflare/control-plane/src/google_service_account_oauth.mjs | 59 |
| generic-api-key | quality/tests/fast_web_535_v151_android_stage_diag_contract.test.js | 10 |
| generic-api-key | quality/tests/terminal_pty_autocopy_v3_test.py | 87 |

The snapshot predates this report. Scope excludes Git history, ignored files,
operational state, external projects and compiled artifacts. Even a clean result
would not prove complete secret hygiene. Restricted JSON reports and genuine
redacted scanner stdout/stderr stay ignored, never uploaded.

Syft `scan dir:<synthetic-fixture> -o cyclonedx-json=<cache>/fixture.cdx.json`
exited **0**. The fixture is a local package.json and npm lockfile v3 identifying
`is-number@7.0.0`; no npm install or package execution occurred. Real Syft output
is CycloneDX **1.7**, **3 components**, with a positive check for that exact
package/version and explicit rejection guards for empty/missing component sets.
The complete output passed the official CycloneDX 1.7 JSON structural schema
using existing workspace Ajv and its referenced SPDX/JSF/cryptography schemas.
Format annotations were disabled; full format validation is not claimed.
Schema acquisition used the official specification's `1.7` version reference;
local hashes are retained in the evidence manifest. Earlier validation attempts
failed at module resolution and missing schema reference; final validation exits
0. No static Go metadata or handwritten manifest is called the SBOM. This fixture
checks manifest cataloging, not installed dependency completeness, APK inventory,
license clearance or vulnerability status. No workspace Syft scan was needed.

## OSV and gate policy

Official `google/osv-scanner` v2.7.0 release metadata confirms a Linux ARM64 asset.
The [versioned offline documentation](https://github.com/google/osv-scanner/blob/v2.7.0/docs/offline-mode.md)
identifies ecosystem `all.zip` databases in the OSV-maintained GCS bucket. No
reviewed digest-pinned database with age/coverage evidence is present here.
No OSV binary/database was downloaded or executed and no proprietary manifest
was submitted to an online API.

A network namespace probe found the **same** `net:[4026531840]` before and inside
`unshare -n`; `/proc/net/route` was permission denied. A preliminary
`unshare -n /bin/true` returned 0, which was **not** accepted as isolation evidence.
PRoot did not establish a different network namespace. No network isolation is
claimed, and no offline vulnerability scan was fabricated. OSV is **BLOCKED**.

Real binaries now work for internal TEST execution. Production gate, pins,
defaults, tests and license policy are unchanged. Direct gate invocations with
the verified ARM64 cache return Gitleaks/Syft **BLOCKED**, exit **3**,
`LICENSE_CLOSURE_UNVERIFIED`; OSV `--offline` returns exit **3**,
`OFFLINE_DATABASE_AND_NETWORK_ISOLATION_UNVERIFIED`. No bypass option was added.
Package totals remain **2 DONE / 4 PARTIAL / 7 BLOCKED**. Local scanner execution
does not complete their release/license/platform gates.

## Verification and reproducibility

The private cache contains acquisition and run scripts (`acquire.py`, `run.py`,
`supplement.py`, `validate.cjs`), exact command arrays/cwd/exit/log SHA-256 in
`verification.json` and `supplement.json`, raw release metadata, checked checksum
files, artifact inventory, SBOM, redacted scanner logs/reports and location-only
summaries. Cache directories are mode 0700; evidence files are mode 0600 and binaries
0500. Python execution/acquisition used umask 077; final permissions were
normalized for shell-created diagnostics too. Scripts
use fresh-output creation and are not blindly rerunnable over existing evidence.
No real environment values or credentials were intentionally logged.
The shareable phase4 evidence JSON preserves command records and hashes without
raw scanner findings. Scanner runs were bounded by process timeouts; this is not
a claim that network access was prevented.

| Gate | Phase4 result |
| --- | --- |
| Ubuntu PRoot full quality | exit 0; 1151/1151 pass, zero skips |
| V10 scanner Python tests | exit 0; 14/14 pass |
| V10 JS runtime/contracts | included in full quality pass |
| V9 regression | exit 0; 440/440 pass |
| Stability | exit 0 |
| Wiki drift audit / snapshot freshness / diff check | each exit 0 |
| Secret-hygiene guard | exit 0 |
| Gitleaks synthetic / working-tree scan | exit 42 / 42; 1 / 8 findings |
| Syft synthetic SBOM / structural schema | exit 0 / 0; 3 components |
| Production Gitleaks / Syft / OSV gate | exit 3 / 3 / 3; fail closed |

[Shareable command and artifact evidence](phase4-security-evidence.json) records
literal commands, actual exit codes, counts, log digests and protected-path/hook
checks. Initial failures remain recorded separately from successful retries.

Android remains **BLOCKED: SDK absent** (phase3 evidence retained); no repeated
known-impossible Android commands. Hosted CI **NOT_RUN**. Windows code/config is
unchanged; phase3 7/7 remains historical, not a new phase4 execution. V9 local
regressions do not imply physical authentication acceptance. Existing push lock,
protected paths and delivery hooks are checked separately. No commit, push,
merge, PR, deployment, release or production operation occurred.

## Gitleaks triage continuation — 2026-10-28

**V10 source Gitleaks triage PASS**: 8 source findings, 8 confirmed false
positives, zero unresolved. This does not change overall PARTIAL or the
production license/platform gates. Branch and HEAD remain unchanged; no local
main ref exists, and no Git ref was modified. Existing worktree edits preserved.

The ninth finding is the separately scanned intentional synthetic fixture in
`secret-fixture/fixture.txt`: `github-pat`, line 1, exit 42, one finding.
It is never included in the source snapshot or suppressed.

| ID | Source location | Classification and exact use |
| --- | --- | --- |
| 1 | `android-app/app/src/main/java/com/appforge/studio/build/DownloadCoordinator.kt:96` | Confirmed false positive. Local operation/module map key computed from variables; no credential literal. |
| 2 | `android-app/app/src/test/java/com/appforge/studio/ai/AppForgeAgentIntelligenceTest.kt:16` | Confirmed false positive. Synthetic sequential provider-shaped text used only to assert sensitive context exclusion. |
| 3 | `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalUltimateFoundationTest.kt:96` | Confirmed false positive. Synthetic alphabet/sequential strings used only to assert terminal secret masking. |
| 4 | `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalPtyCopyCaptureTest.kt:9` | Confirmed false positive. Deterministic repeated hex PTY frame delimiter used by local parser tests. |
| 5 | `android-app/app/src/test/java/com/appforge/studio/terminal/TerminalPtyCopyCaptureTest.kt:75` | Confirmed false positive. Reverse sequential repeated hex delimiter distinguishes a second local PTY parser session. |
| 6 | `cloudflare/control-plane/src/google_service_account_oauth.mjs:59` | Confirmed false positive. Regex delimiters removed before decoding externally supplied PEM; no key body is embedded. |
| 7 | `quality/tests/fast_web_535_v151_android_stage_diag_contract.test.js:10` | Confirmed false positive. Array of diagnostic stage names asserted against source text; no credentials. |
| 8 | `quality/tests/terminal_pty_autocopy_v3_test.py:87` | Confirmed false positive. Deterministic repeated hex PTY frame delimiter constructed for a local shell capture test. |

`quality/tools/v10-gitleaks.toml` extends all default rules. Each of eight
allowlists requires both the exact anchored repository path and the exact
reviewed scanner match, scoped to its one rule. No blanket file/test directory,
line number, generic-api-key, private-key or fixture exclusion exists. The V10
wrapper copies this project-owned policy into its private execution directory;
license, ABI, source-path, archive, report and secret-hygiene guards are unchanged.
No original source/test finding was edited to evade detection.

Real Gitleaks 8.30.1 rerun: source exit **0**, zero findings; separate positive
fixture exit **42**, one finding. Negative controls append newly generated
high-entropy synthetic generic keys and disposable RSA private keys to all seven
reviewed files and an unrelated file: exit **42**, both rules detected at every
path. Values are never echoed or exported. Private temporary artifacts remain
ignored with restricted permissions. This demonstrates actual detector failure
semantics, not authentication to any provider.

The repeatable opt-in test `quality/tests/v10_gitleaks_real_scanner.py` accepts a
verified scanner command/launcher, independently creates the positive fixture
and generates negative controls, discards scanner stdout/stderr, and checks both
rules per path. It runs separately from tests that mock scanner execution.
The first standalone invocation failed because the launcher could not resolve
a relative binary path; the absolute-path rerun is recorded separately.

V10 Python security tests: **14/14 PASS**; V10 JS scanner/workflow contracts:
**PASS**. Secret-hygiene guard and diff whitespace check: exit **0**.
No V9, signing or production file was changed; no commit, push, merge, deployment,
release or production action occurred. Retained original Phase4 failures above
remain historical evidence. Sanitized command records and individual decisions
are in the evidence JSON `gitleaks_triage` field.
