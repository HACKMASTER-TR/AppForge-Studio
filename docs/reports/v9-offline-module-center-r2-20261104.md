# Issue 78: V9 Offline Module Center R2

This is a feature-branch implementation and evidence record, not production or
physical-device acceptance. Base: `a9c1739b025a939b120888220d827a82f1538467`.
Branch: `codex/v9-offline-module-center-r2`. PR target:
`feat/v9-global-package-distribution-20261027`. Issue:
<https://github.com/HACKMASTER-TR/AppForge-Studio/issues/78>.

## Behavior

The managed APT adapter uses R2 content addresses and verified local caches.
Snapshot URLs remain the immutable APT package provenance/solver identity; neither
metadata acquisition nor package acquisition falls back to the Snapshot service.
The Android native broker rejects live Snapshot requests. Existing non-APT SDK,
JDK, Gradle, Node and Rust upstream archives retain their pinned trusted sources:
these objects are not in the approved 218-object R2 release. They have not been
pretended to exist in R2, and production distribution stays disabled.

The same service now owns download, verification, staged installation, actual
functional probes, ownership/receipt validation and atomic generation activation.
The source-selection card no longer starts a second, screen-owned download job.
Original manifest/signature bytes are persisted privately; Ed25519 verification
and the sequence floor are checked again at use. A device-local floor is not an
independent server anti-rollback witness. The existing policy only admits the
exact reviewed release sequence and production remains disabled.

R2 transport uses bounded 8 MiB ranges, exact Content-Range/length/size, SHA-256,
private durable prefixes and atomic promotion. A completed cache object is always
rehashed; damaged complete cache entries are rejected during inspection. An explicitly restarted authenticated acquisition moves damaged cache bytes to a bounded private quarantine before re-downloading; working files and valid objects are preserved. Interrupted
prefixes can resume; a completed invalid prefix is removed before retry. Pausing
is cooperative at acquisition/transaction boundaries, not a kill of dpkg.

Both install-time and read-only READY checks now close the previous signature-only false positive: all four pinned InRelease
files and all sixteen native-architecture Packages lists are mandatory. The
checks use gpgv-authenticated plaintext, the fixed Ubuntu 2018 fingerprint,
architecture/components, unique SHA256 entries, signed hash **and size**, signed
Date/future-clock checks and Valid-Until when published. None of the four actual
pinned Ubuntu releases publishes Valid-Until; no expiry field was invented and
no existing expiry check was disabled. Authenticated zero-size indexes may be
materialized only with the actual signed SHA-256 of empty bytes. Locally sealing
a checksum list does not substitute for signed index admission.

APT planning and installation have network-denying proxies; the installation
also uses `--no-download --no-remove` and exact versions. Packages come from the
reviewed module catalog and are rechecked with dpkg-deb, dpkg installed state,
dpkg file verification and the installed-file SHA-256 inventory. Existing
RuntimeGenerationStore and ModuleOwnershipStore protect activation and rollback.
The manager rechecks existing ready modules before activating a stage. The
historical Node recipe is accepted only from byte-exact archived base recipes,
in addition to its version/file/ownership checks; no unknown recipe is blessed.

Functional probes do real local work: Node JS file/hash assertions; Python 3.12
stdlib/sqlite/TLS initialization; Java compilation/execution and aapt2 resource
compilation; Cargo offline compilation/execution; NDK Android shared-library
compilation; MinGW PE generation. Chaquopy's existing offline APK-build probe is
still required. An NDK compile is not an Expo application run; PE generation is
not Windows execution. Project-specific dependency readiness remains separate.

## Observed tests and delivery

| Gate | Status | Evidence / boundary |
| --- | --- | --- |
| Exact base, clean isolated worktree | PASS | Verified before edits; main untouched |
| Baseline V9 Python security | PASS | 476 tests, no skips, real gpgv provided |
| Real Ubuntu InRelease signatures | PASS | Four pinned fixture files; gpgv, exact fingerprint/hash |
| Signed index inventory | PASS | 32 references / 30 unique objects across arm64 and amd64 |
| APT metadata runtime regressions | PASS | 13 existing/strengthened + 12 new tests, no skips |
| Actual offline APT/dpkg installation | PASS | Synthetic signed package, disposable Ubuntu container, `--network none` |
| Offline APT lock reuse | PASS | Same real installed package, no network; installed tamper rejected |
| Node 22.23.3 JS functional probe | PASS | Official x64 archive matched existing pinned SHA-256; Ubuntu container, network none |
| Baseline full Node quality (historical) | FAIL | 1,146 tests: 1,138 pass, 8 fail, no skips; retained as initial evidence |
| Final full Node quality | PASS | 1,159/1,159 tests, no skips; relevant stale UI/runtime contracts corrected |
| Final V9 Python security | PASS | 482/482 tests, no skips |
| Final Android unit tests | PASS | 696 tests in 125 suites; zero failures, errors or skips |
| Android debug APK build | PASS | Actual Gradle 9.3.1 / JDK 21 / SDK 37 / NDK build; identity/signature verified |
| Hosted CI | NOT_RUN | Pending feature-branch push at this evidence commit; final status is in PR checks |
| Real R2/Ubuntu package installation on Android | NOT_RUN | Missing signed indexes and inaccessible read-only endpoint |
| Python + Chaquopy / Android SDK / Expo / NDK/Rust on device | NOT_RUN | No physical execution evidence |
| Windows target execution | NOT_RUN | No Windows host acceptance in this session |
| Physical ENOSPC / network-loss / rollback | NOT_RUN | Host faults do not establish device behavior |
| Production mutations / Play release | NOT_RUN | Explicitly prohibited; none performed |

The full Node baseline failures are stale current-source contracts (runtime
replacement and Module Center/first-run UI) plus generated Second Brain drift.
The relevant contracts were updated to the selected-module UI, service-owned
readiness, measured progress and narrowly scoped scratch cleanup. Assertions for
active-runtime preservation, lifecycle permissions and final integrity remain;
none were deleted or skipped. First-run explanatory text was restored, and the
canonical snapshot was regenerated. Initial failures remain in the execution logs. Initial environment checks also found a JRE without
javac, no Kotlin CLI, no Gradle, no gpgv and no Android SDK. Tools were installed
in task-private scratch directories; the initial SDK-missing build failure is
retained separately from the successful build. A subsequent run caught two
obsolete Snapshot-network retry assertions; they now assert bounded native R2
transport and rejection of every Ubuntu live/Snapshot transport origin. Final
Android unit tests pass. A build during ongoing edits also observed a stale
main/test compilation mismatch; a complete rebuild of the final source resolved it.

Repository-mandated Codex CLI handoff was attempted after `codex login status`
reported logged in. Actual execution failed with HTTP 401 and an unrefreshable
authentication token. Implementation continued in the active Codex session under
the user's instruction to continue safe work when resources are unavailable.
No credentials were printed, changed or committed.

## External blockers

The issue reports a release of 214 .deb + four InRelease objects with no Packages
indexes. The authenticated inventory is in
[v9-offline-module-center-r2/signed-packages-inventory.json](v9-offline-module-center-r2/signed-packages-inventory.json).
It lists the exact signed raw index hashes, sizes, R2 addresses and Snapshot
provenance. It is not proof those bodies exist remotely. Read-only GET requests
for all four pinned R2 InRelease objects returned HTTP 403 in this Cloud
session. The connected Cloudflare API subsequently confirmed 218 content objects,
all four InRelease keys/sizes, and zero of the 30 required unique raw Packages
objects in the staging bucket, using GET-only calls. Object listing verifies
presence/advertised size, not body hashes. No alternate egress path or production
configuration change was used.

An owner must supply/publish the original signed index bodies under their exact
content addresses through a separately authorized process. This work cannot
invent those bytes, mint a release signature, mutate a bucket, or change the
Worker's deployed access policy. A complete cached signed chain is reusable;
partial/corrupt/expired metadata stops before dpkg and before READY. Legacy
runtimes without the original vendor APT baseline remain blocked; the side-by-side
recovery candidate is not silently activated over working Node or projects.

## Device acceptance without ADB

1. Obtain the CI artifact and verify `sha256sum -c SHA256SUMS` in Termux. Install
   the APK with Android's package installer. The new package is
   `com.appforge.studio.v9r2issue78`; it preserves production and previous test-app
   data/signing identity. It is a separately debug-signed test app, not a
   production update APK. Do not uninstall an existing app to work around a
   signing mismatch.
2. Open the unified Module Center and import the original release manifest plus
   its 64-byte signature. Select a module and use the single installation action.
   Missing indexes must show an error, never READY. Retain the displayed error,
   package count, installed version, app version, time and source SHA.
3. After the owner supplies authentic metadata, install each supported module.
   Confirm that download completion is followed by GPG/hash validation, isolated
   installation, offline/functional test and final receipt validation before READY.
4. Disconnect all networks and repeat the verified cache/reinstall case. Do not
   infer arbitrary-project readiness from a tool module receipt. Python acceptance
   must build a Chaquopy APK and execute it; Expo/Rust/Windows need corresponding
   real target artifacts and execution evidence.
5. Exercise pause/resume, transient disconnect, wrong signature, missing index,
   corrupted cache and insufficient storage. Preserve the previous active module
   and projects. Use the UI's dependency-aware rollback only when its ownership
   and generation checks permit it. Record before/after hashes and real outcomes.

A shell outside the Android app cannot read its private module files or drive its
UI without extra privileges. There is no fake one-command physical PASS or
mandatory ADB step. All device gates remain NOT_RUN until real evidence exists.

## Local APK evidence

The locally built APK is 61,393,242 bytes, package
`com.appforge.studio.v9r2issue78`, version 5.0.34 / 534, minSdk 26 / targetSdk 37.
Its SHA-256 at local verification was
`0625ea6d0df27aabac27a23a111b29d58719bc63dc9614b57c313f5a90779f1a`.
`apksigner verify --verbose --print-certs` passed. Hosted CI has its own debug
signing identity and source stamp; use the hosted artifact's SHA256SUMS for that
APK, not this local hash. This evidence does not authorize a production update.
