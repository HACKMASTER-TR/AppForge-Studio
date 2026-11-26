# V537 bounded storage candidate

Base: `1acce4b9ea703abc7bd1dbb858d9dfe33042b186`.
Branch: `codex/v9r2-inplace-upgrade-storage-537`.
The existing PR #81 and its branch are unchanged.

## Behavior and preservation

- A failed mutated stage is retained; another installation cannot create another
  generation until review/approved cleanup. Unmodified interrupted clones retain
  the existing verified recovery path. Old divergent quarantines remain protected.
- Optional FICLONE is capability tested; unsupported filesystems use an independent
  copy with conservative admission. Hardlinks never connect active and staged data.
- Cleanup requires ownership, exact tree evidence, a reviewed scope and revalidation
  under a filesystem lock. Active data, projects, verified caches, receipt references
  and at least one verified rollback are protected.
- Turkish storage inspection runs off the UI thread, supports cancellation and
  reports logical bytes separately from allocated blocks. Physical savings are not
  inferred from reflink or logical sizes. Cleanup reports measured free space again.
- Existing artifact/recipe/catalog bytes remain unchanged. Cache hits precede network
  acquisition and remain distinct from installation/READY verification. npm/Gradle
  retain their lock and offline replay checks.
- Module growth limits are conservative explicit reservations, not download estimates.
  The policy intentionally prefers blocking an installation over deleting uncertain
  data. Partial cleanup after a process death can require inspection.

## Verified v536 reference

Authenticated source run:
https://github.com/HACKMASTER-TR/AppForge-Studio/actions/runs/43164099834

Artifact ID: `13919253544`.
ZIP SHA-256: `4299ef2121065fef3e057c27d82a6b32aa6653f7ebaa6afb744748a74ba1a9d9`.
APK SHA-256: `7b25ea712b26e702f3c515eba306a305a781c4159f458c7a9dba7b51246527d1`.
Package: `com.appforge.studio.v9r2isolated3`; code 536; min/target 26/37.
Certificate SHA-256:
`dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082`.
The downloaded ZIP digest, APK digest, AAPT2 identity and ApkVerifier signature were
independently checked. This proves the reference identity, not phone data retention.
No private signing key was retrieved.

## Verification boundaries

The portable JVM runner is `quality/tools/storage_host_tests.py`. It compiles actual
storage/transport sources and tests with explicit Android network/journal boundaries;
it does not establish Android networking, package-manager policy or physical acceptance.
Its dependency jars are supplied externally. Native tests can load the actual C library
with `--native-library-dir`. The host filesystem reported reflink unsupported and
exercised independent-copy isolation; no real reflink savings are claimed.

Initial local V9 execution lacked gpgv and failed with eight skips. After installing
an isolated gpgv verified against Debian's signed InRelease, Packages hash and package
hash, the complete V9 suite passed 527 tests with zero skips. Early broad quality runs
also exposed missing host Kotlin/Javac and a stale generated snapshot; these failures
are retained in the session logs and do not establish current acceptance.

GitHub remote branch creation was attempted through the HACKMASTER connection and
rejected with `403 Resource not accessible by integration`. The available CLI account
has read-only repository permission. Consequently remote branch, draft PR, exact-SHA
hosted CI and the original-signer v537 APK are BLOCKED until repository write access
is available. No fork or replacement signing identity was substituted.

The new emulator script refuses every serial except `emulator-N`. Its fixtures are
synthetic and its result must never be represented as a physical Galaxy test. There
has been no phone installation, uninstall, data clearing, cleanup or permission change.

## Final local verification matrix

| Stage | Result | Evidence / limits |
| --- | --- | --- |
| Android build | PASS | `:app:assembleDebug` and `:app:assembleDebugAndroidTest`; actual v537 package metadata inspected. |
| Android JVM unit tests | PASS | 739 executed, 0 failures, 0 errors, 0 skips; actual JUnit XML. |
| V9 security | PASS | 527 executed, zero skips, including real GPG verification. |
| Full Node quality suite | PASS | Final serial run: 1,163 passed, zero failures/skips. Earlier parallel run had one 120-second compiler timeout; its isolated retry also passed. |
| Upgrade identity / release manifest | PASS | 8 historical identity guard tests and 2 v536/manifest tests. |
| Termux download/identity regressions | PASS | 18 tests, including v537, immutable-manifest requirement and rejection of stale/wrong identity. |
| Native clone bridge | PASS | Host GCC and Android API-26 ARM64 Clang with `-Wall -Wextra -Werror`; separate JNI-loaded host tests passed. Host reflink capability was unsupported; independent-copy fallback was tested. |
| Process exclusion / abrupt death | PASS | Separate JVM cannot acquire held lock; another JVM halts with a live staged transaction, then parent verifies recovery, unchanged active inventory and retained quarantine. |
| Wiki snapshot / secret scan | PASS | Generated snapshot check and wiki secret scan. Wiki audit has advisory warnings, no errors. |
| Original-signer positive upgrade | BLOCKED | Signer remains in CI; no signing secret was fetched. Local debug certificate is rejected by the real APK upgrade guard. |
| Remote branch / draft PR / exact-SHA CI | BLOCKED | GitHub integration rejected branch creation with HTTP 403; CLI repository permission is read-only. |
| Emulator instrumentation execution | NOT_RUN | APK built and CI gate added; no local KVM device, hosted execution blocked. |
| Galaxy / real installed modules / real storage reclaimed | NOT_RUN | No phone operation, install, data clearing, cleanup or physical acceptance. |

The first Android pass encountered a mixed-source compilation during ongoing edits.
A subsequent 737-test run had five lifecycle-fixture failures: the old test reused
one quarantined runtime for multiple fault boundaries. Each boundary now uses a fresh
fixture, retains its exact injected-failure assertion, and additionally asserts that
the next installation is denied without changing the working module. The final 739
unit tests passed. No security test or failure assertion was disabled.

## Measured fixture storage and cache evidence

These are disposable host fixtures, not GiB measurements from the Galaxy:

- Twenty attempts: first modified-stage cancellation retained one quarantine;
  the next nineteen attempts were denied before another clone. Active tool ownership,
  rollback, project text and cached bytes remained valid on every iteration.
- Logical bytes: 1,610 before, 11,115 after the first failure, unchanged through
  attempt twenty. Regular-file inode count: 12 before, 18 after.
- Actual host `du -sk`: 84 KiB before, 140 KiB after; one quarantine, zero staging
  copies, zero bytes claimed freed. A separate test cancels twenty unmodified clones
  and recovers each without accumulating quarantine.
- Cache fixture: first response GET=1 / body=15 bytes; verified reuse GET=0 /
  body=0 bytes; one cache hit in two requests. Cross-cache reuse also performs zero GETs.
- Unmeasured transport counters remain unknown; cache hits explicitly report zero.
  No physical-device savings, user cache hit ratio or supported-reflink savings were invented.

## Delivery after repository write access is restored

Use the supplied source bundle in a fresh clone/worktree; verify the exact base and
new source commit. Push only the supplied development branch and open a **draft** PR
against `codex/v9r2-inplace-upgrade-4c0d90932a04`. Leave PR #81, main and production
untouched. The dedicated push workflow must pass signer/package/version, complete
unit/security and disposable emulator gates before publishing its update artifact.
Do not install the locally signed debug APK on the user's phone. Phone actions need
separate explicit approval and are not authorized by this report.
