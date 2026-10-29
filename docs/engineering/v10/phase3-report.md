# V10 phase3 — local verification and host compilation preparation

Overall PARTIAL; no delivery or device acceptance. This continues the same dirty
feature worktree at afb3ad9209bcce8b84773d8873d037b15b4d804d. Previous first-pass
and continuation records are retained. The phase3 verification JSON is the command,
exit and count ledger; earlier numbers are not combined with new runs.

## Substantive changes

- Added `.github/workflows/v10-host-verification.yml`: manual dispatch only,
  restricted to `feat/v10-oss-unified-20261028`, Ubuntu 24.04 x86_64, read-only
  contents, no persisted checkout credentials, secrets or publishing. All three
  actions are fixed to full commit identities observed through GitHub's commit
  API. Existing Android build configuration is unchanged. The workflow installs
  project-pinned SDK 37, NDK 28.2.13676358 and CMake 3.22.1, then requests unit
  tests and Kotlin/native compilation with Gradle 9.3.1 / JDK 21. It requests no
  packaging or signing task. Static configuration is tested; HOSTED_CI=NOT_RUN.
  This is development verification, not an AppForge remote build service.
- Added two quality contracts for the manual/branch/permissions/no-publication
  boundary, pinned actions and consistency with current Android toolchain pins.
  Parsed the complete YAML using the existing workspace `js-yaml` dependency and
  checked both Bash scripts with `bash -n`. This does not resolve or execute an
  Android task graph and cannot establish build acceptance.
- Downloaded the two pinned Linux x86_64 Gitleaks/Syft archives to ignored local
  review cache. Actual archive SHA-256 values match the existing pins. Static ELF
  headers identify x86_64 (62); neither executable was extracted to a runnable
  path or executed. Each archive contains one LICENSE file. Original static Go
  build-info parsing records 66 and 295 module records respectively. This is
  compiler metadata, not a scanner-generated SBOM. The separate license inventory
  explicitly records module licenses as NOASSERTION and distribution/execution
  as unapproved. Release provenance attestation and version-specific transitive
  notices/licenses remain unreviewed. No license gate was enabled.

## Environment and failures preserved

The initial PRoot full-suite launcher exited 1 before tests because the outer
expanded wildcard was joined into a single argument by the command wrapper. The
next full run expanded the wildcard inside Ubuntu and executed all 1149 then
existing tests: 1148 passed, one failed, exit 1. Its sole failure was the real curl
telemetry test: `/usr/bin/curl` was absent, producing exit 127. The existing Termux
curl executable was then bound at that path; both telemetry tests passed (2/2,
exit 0). The final full repeat includes the two new contracts. No tests or APT
implementation were rewritten. Linux APT remains Ubuntu's real HTTP method.

PRoot binds only this workspace, its ignored dedicated writable `/tmp`, and the
existing curl executable. Node, JDK and Gradle remain existing Termux tools;
there is no claim of a native Linux Node/JVM toolchain. No rootfs package was
installed. The full command is recorded literally in phase3-verification.json.
The final Termux suite remains separate from every PRoot run.

Android SDK variables were unset. Read-only checks of `/opt/android-sdk`,
`/usr/local/lib/android/sdk`, and the conventional Termux home/prefix SDK
locations found no SDK or pinned NDK. Both actual offline Android commands with
the previously populated Gradle cache exit 1 at SDK discovery. They do not pass
unit tests or compile Debug. No SDK download, incompatible host binary install,
AGP change, SDK target change, signing or APK installation was attempted.

Real wrapper invocations return Gitleaks/Syft NOT_RUN/ARCHIVE_MISSING (exit 2)
against the original empty cache, and OSV BLOCKED (exit 3) for unverified offline
database and network isolation. The downloaded review archives are x86_64 and
unapproved for execution; the wrappers were not redirected to them or overridden.
No real secret scan, Syft SBOM or vulnerability clearance is claimed. A static
build-info diagnostic initially failed decoding Go's binary sentinels; the parser
was corrected to remove framing before UTF-8 decoding, and then succeeded on both
hash-verified archives. This diagnostic failure was not a scanner invocation.

## Package acceptance stays unchanged

DONE=2 (spec-kit, superpowers): existing original methodology adaptation only.
PARTIAL=4 (codeassist, termux-packages, gitleaks, syft).
BLOCKED=7 (sora-editor, tree-sitter, bundletool, osv-scanner, esbuild, jadx, uv).
No package was relabeled on the strength of static metadata or a host fixture.

CodeAssist remains GPL reference-only; telemetry is not cache invalidation or an
Android speedup. Termux activation still lacks a reviewed real package/license
closure, ABI, quota and atomic rollback corpus. Sora still lacks LGPL source and
relinking distribution plus recursive dependencies/runtime acceptance; current
Android packaging excludes LICENSE/NOTICE resources, so AAR integration cannot
assume notices survive. Tree-sitter has no approved Android binding/grammar/ABI
or lifecycle/performance evidence. Bundletool lacks reviewed executable closure,
AAB/APKS corpus and signing invariants; esbuild lacks an equal-output bundling
benchmark; JADX lacks reviewed closure and bounded synthetic APK execution; uv
lacks an isolated supported locked-fixture execution. None was forced onto the
device. OSV requires a digest-pinned, coverage/freshness-reviewed database and an
enforced no-network boundary before offline scanning can be claimed.

## Reproduction and records

Run from the supplied repository. Use the commands in phase3-verification.json;
logs remain ignored under `.appforge/tmp/v10/phase3/` with recorded SHA-256s.
`phase3-review-archives.py` reproduces the bounded static archive download/hash and
notice inventory; then `phase3-extract-go-buildinfo.py` rechecks hashes and reads
module metadata without execution. These scripts do not approve distribution.
`phase3-ci-action-provenance.json` records action identities, and
`phase3-safety.json` compares protected configuration and delivery hooks.

Hosted CI, physical Android/editor/LSP, Windows EXE packaging/signatures, AAB/APKS
validation, Play and production remain NOT_RUN. No push/merge/deploy occurred.

## Measured final gates

| Gate | Actual result |
| --- | --- |
| Termux full quality | exit 1; 1148/1151 pass, 3 APT environment failures, 0 skips |
| Ubuntu PRoot full quality with curl bind | exit 0; 1151/1151 pass, 0 skips |
| V9 regression | exit 0; 440/440 pass |
| Windows tests | exit 0; 7/7 pass |
| V10 scanner Python regressions | exit 0; 14/14 pass (boundary tests, not real scans) |
| Android unit | exit 1; SDK location not found, no unit acceptance |
| Android Debug | exit 1; SDK location not found, no build acceptance |
| Stability | exit 0 |
| Wiki audit / snapshot check / secret hygiene / diff check | each exit 0 |
| Workflow configuration | exit 0; YAML parsed, two Bash scripts syntax checked; two new contracts pass in both full suites |
| Hosted CI / real security scans | NOT_RUN / BLOCKED, respectively |

The prior Termux 1146/1149 and PRoot 1148/1149 records remain historical. Adding
two passing workflow tests explains the final 1151 denominator; it does not fix
the Termux environment. The complete Ubuntu pass comes from an actual full rerun,
not adding targeted results to an earlier count. Gradle fixture timings in the
full logs are host observer checks only; no Android cache or speed claim follows.
