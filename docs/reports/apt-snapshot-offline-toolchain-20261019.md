# APT Snapshot & Reproducible Offline Toolchain

Overall status: PARTIAL. Actual APT pinning and amd64 host offline installation
are implemented and verified; physical Android and ARM64 native installation are
NOT_RUN. New GitHub CI is NOT_RUN_USER_PROHIBITED. No GitHub write, phone install,
release or main/T21/T20 mutation is performed.

Incremental base: 629003bc851ef0ef84e86a987abf4e63fb16d99b.
Complete V3 base: 1e922b835b39f9f0d948e780ff4020f594124afd.
Exact final identity, test counts and file hashes are in delivered verification.json.

## Root cause and inventory

The previous adapter authenticated live Ubuntu indices and captured downloaded
.deb hashes, but candidate resolution still depended on changing mirror state.
Per-module TSVs did not provide a complete exact baseline transition/replay.

Production vendor rootfs remains Ubuntu Base 24.04.4, noble:

- amd64 SHA-256: c1e67ef7b17a6300e136118bd1dc04725009cb376c1aad10abcf8cd453628d58
- arm64 SHA-256: 04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2
- Official source: https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/

Both exact archives were downloaded and hash-verified; package inventories were
read from their real dpkg status files. Original sources are Ubuntu archive,
security and architecture-dependent ports mirrors. Managed APT now overrides
sources through private options without rewriting /etc/apt. Unsupported/foreign
architectures fail explicitly. Existing ARM32 rootfs registry data is retained,
but this toolchain snapshot path supports amd64/arm64 only.

## Fixed repository and package versions

Repository: https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/
Suites: noble, noble-updates, noble-security, noble-backports.
Components: main, restricted, universe, multiverse.
Signing fingerprint: F6ECB3762474EDA9D21B7022871920D1991BC93C.

The four real signed InRelease hashes are in apt-snapshot-profile.tsv. The
packaged catalog has 353 module/artifact rows, 214 unique .deb identities from
real amd64/arm64 solver output. package-lock-manifest.json lists every exact
version, architecture, URL, size, SHA-256 and dependency expression; no versions
or hashes were invented. The dependency graph preserves Debian alternatives
and version clauses rather than pretending to reimplement APT's solver.

The preinstalled baseline is covered by the exact vendor archive plus installed
versions. Its packages are not falsely assigned independently unknown .deb hashes.
The exact baseline is a replay prerequisite; a different baseline is rejected.

## Authentication and lock format

APT authenticates indices with its trusted Ubuntu keyring; gpgv verifies the
reviewed fingerprint; signed InRelease hashes are pinned. Locally transformed
Packages files are decompressed and checked against signed SHA-256 entries.
Metadata dates and any Valid-Until are checked. These Ubuntu records have no
Valid-Until field; no expiry override is introduced. TLS remains verified.

New artifact candidates must match the reviewed module/architecture catalog
before package download. Archive bytes, size, dpkg identity/version/architecture,
source and SHA-256 are checked. No unauthenticated or insecure option is added.

Each snapshot-v1 transaction under apt-locks-v5/<MODULE> stores exact arguments,
identity, profile/catalog hashes, baseline and resulting full installed versions,
package/dependency TSVs, metadata identity, installed file hashes and a checksum
manifest. The request SHA-256 names the transaction. Temporary .part directories
cannot pass preflight. A READY marker alone is insufficient.

Shared archive CAS objects are keyed by SHA-256. Cache hits are verified and
module copies may use hard links. Shared package conflicts are explicit errors.
Generation copies retain independent bytes for rollback; cross-generation disk
deduplication is not claimed. Shared archive garbage collection is not enabled.

## Installation, consent and lifecycle

Initial module consent authorizes signed metadata resolution. The resulting
missing package list and exact bytes require an additional operation-specific UI
approval before package bodies download. Imported projects cannot grant approval.
APT mutation is restricted to the approved generation installer; a normal build needing APT must return to the modular package flow, rather than mutating a shared root or waiting for an unavailable approval UI. Existing native/APT telemetry reports measured bytes/rate/ETA. Resolution,
verification, local installation and post-install checks have separate events;
unknown totals and whole-module work remain indeterminate.

The installer warms APT cache only from verified shared objects and installs exact
versions using --no-download --no-remove with APT network proxies disabled.
It verifies dpkg state and selected installed file hashes. It records
APT_NO_DOWNLOAD_INSTALLED; this is not a blanket OFFLINE_READY assertion for
arbitrary source projects or network-capable maintainer scripts.

The existing RuntimeGenerationStore prepares and publishes the root only after
APT preflight, tool probes and ownership checks. Failure retains the previous
verified root. Whole-root rollback is conditional on previous integrity and an
unchanged active tree; user/cache changes can intentionally block it. Existing
shared APT removal restrictions remain in force.

Legacy roots with module receipts but no managed snapshot origin are not silently
migrated. Their update control stays blocked with a reason. A new snapshot/key/
catalog needs reviewed packaged changes and a separately proven migration; there
is no automatic remote snapshot update or downgrade to an available newer package.

## Actual evidence

Signed metadata and package resolution succeeded for amd64 and arm64 using exact
vendor baselines. Expo amd64 has no extra APT set; its normal module dependencies
remain Android Core and Node. ARM64 Expo's clang/lld/llvm/CMake/Ninja set is solved
but not executed.

Actual Android Core, Python Android and Windows Native APT sets were installed
from authenticated captured artifacts on pristine Ubuntu Base amd64 and replayed
with Docker --network=none. The complete installed inventories matched SHA-256:
db1bed46f38b68e5491c0b219da7af08224b2556fa55e41cd262cd77e11ca912.
Production Kotlin preflight also ran inside the real resulting root, not only a
synthetic fixture. Exact counts/bytes and final-source evidence accompany delivery.
This is APT package acceptance, not full SDK/Chaquopy/Expo/Windows application
acceptance. The host JDK used for read-only inspection is not an Android install
requirement and is not included in delivered patches as a binary.

New tests cover signed metadata, invalid signatures despite replaced local hashes,
unknown catalog rows/URLs/architecture, partial and corrupt archives, symlinks,
request identity, distro/version conflicts, missing dependencies, partial markers,
exact user consent and phase semantics. Existing generation cancellation/disk
preflight/crash/active-build/user-data tests remain. Final complete quality,
Android, templates, offline AGP, Wiki, Second Brain and stability outputs are in ZIP.

Development failures are retained honestly: an early JUnit assertion expected a
different exception subtype; argument validation was made consistent. The host
lacked gpgv, so host tests use real gpg in an isolated temporary keyring when needed;
production gpgv was exercised in Ubuntu. Catalog generation initially compared
encoded APT URLs to decoded Filename fields; canonical URL decoding fixed it.
An initial host JVM root inspection used an ASCII locale and rejected a Unicode
certificate filename; the isolated JVM proof now explicitly uses C.UTF-8.
A low-space fixture initially bound shell function arguments incorrectly; the fixture was corrected and proves rejection before acquisition. A review also caught approval waiting outside the module driver; context gating and driver-scoped consent prevent that path. No tests were removed or skipped to obtain success.

## Remaining acceptance boundaries

NOT_RUN: physical Android 12–16, Samsung S24 Ultra Android16, ARM64 native .deb
installation, real filesystem exhaustion, device process-death/background limits,
full SDK/JDK/Gradle/Chaquopy/Expo project acceptance after each lifecycle action,
and new CI (user prohibited). Existing lifecycle tests inject disk/termination
faults; they do not establish Android or power-loss durability.

Only the three amd64 APT package sets have full real network-none execution in
this task. Node's listed APT prerequisites overlap core; no new Node runtime
acceptance is claimed. Windows Portable and Fast HTML keep their independent
paths, and Fast HTML still bypasses APT/SDK/JDK/Gradle setup.

Any local debug APK is unapproved for phone upgrade until certificate/version
comparison, independent new CI and physical acceptance are completed. No APK is
installed or distributed as an accepted upgrade. Delivery is local commits,
verified incremental/complete format-patch series and evidence ZIP only.
