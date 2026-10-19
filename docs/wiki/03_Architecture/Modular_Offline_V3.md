---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-18
updated: 2026-10-18
last_verified: 2026-10-18
confidence: medium
related:
  - "[[Fast_HTML_V2]]"
  - "[[Device_Build_Runtime_V3]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/FeatureSupportMatrix.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineModulePlan.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/VerifiedModuleDownload.kt"
  - "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
  - "android-app/app/src/test/java/com/appforge/studio/build/ModularOfflineV3Test.kt"
  - "android-app/app/src/test/java/com/appforge/studio/build/VerifiedModuleDownloadTest.kt"
---

# Modular Offline V3

This is an implementation boundary record, not full V3 acceptance. See
`docs/reports/fast-html-v2-modular-offline-v3-20261018.md` for remaining work and evidence.

FeatureSupportMatrix is shared by UI, analysis recommendations, readiness and
settings validation. Saved incompatible choices remain visible and block builds.
Source requirements are rechecked against the actual packaged HTML/JS snapshot.
Plain file inputs do not imply camera/microphone. Capture and unsupported APIs
produce findings, not automatic permissions. Normal HTML routing is explicit;
unsupported Fast HTML settings do not silently select the normal toolchain.

OfflineModules resolves deduplicated dependencies. Fast HTML uses verified bundled
APK/AAB templates. Android Core, standalone Node, Python, Expo and Windows Native
share only their declared dependencies; Windows Portable needs no Linux or SDK.
Selected installation replaces the former install-all operation. Core prewarm is
not proof that every arbitrary project's dependency graph is cached. Normal
builds default to offline; dependency download consent is session-only and is
never restored from imported project metadata.

Module readiness checks real critical-file SHA-256 attestations, specification
version and recursive dependencies; legacy marker presence is insufficient.
These receipts are not full ownership manifests. Existing installations remain
untouched during inspection. Node archive and Windows host downloads expose real
byte telemetry and verified resume; their atomic file promotion follows digest
verification. Other script-driven transfers remain indeterminate. Download 100%
is not installation completion. No overall percentage is invented.

Process-local operation flows survive navigation and rotation. Android process
survival, foreground download service, automatic network retry, transactional
APT/Gradle rollback, safe ownership-based removal, complete package SHA-256
migration and per-project dependency acquisition remain incomplete. The screen
states these limitations rather than providing destructive or misleading actions.
Physical Android 16 and live SDK-service acceptance must be recorded separately.

## Completion continuation

Unknown feature keys now fail closed. ModuleOperationJournal persists bounded,
credential-free metadata atomically; restored active/READY records become paused
until file validation and explicit consent. User-started ModuleDownloadService
uses dataSync foreground execution, handles Android timeout, and never starts from
boot or promises survival. BuildModuleUsage excludes module mutation from active
local builds. Rootfs byte events and direct curl file/header telemetry feed typed
progress; package-manager internal downloads remain indeterminate. Portable host
removal has a narrow pinned-hash/allowlisted-file ownership boundary and atomic
predelete quarantine rollback. Shared APT/rootfs removal and unverified updates
stay disabled. See `docs/reports/fast-html-v3-completion-20261018.md` for remaining
acceptance gaps; this continuation does not establish full V3 acceptance.

## Final development: generation transactions

Rootfs archives now share the pinned resumable transfer boundary, including
If-Range validators, manifest size limits and persistent private partial files.
RuntimeGenerationStore clones the private build root without following links,
verifies the copy, runs module installation and probes against the staged root,
then journals an atomic activation. Earlier working roots stay immutable as
rollback generations. Recovery occurs under mutation ownership before builds,
never from a readiness observer. The latest isolated module can be reversed only
when no installed dependent requires it; older/shared arbitrary removal stays
closed. Real device POSIX, free-space and process-death behavior still requires
acceptance; host fault tests do not replace Android testing.

## Explicit project preparation and observed dependency transfers

ProjectDependencyEvidence performs bounded read-only manifest/lock analysis and
source/cache fingerprints. ProjectDependencyPreparation requires fresh explicit
source-execution consent, copies reviewed source and stages the build runtime.
A tool installation is never a project-ready receipt. Only a successful rebuild
with Android networks absent throughout, followed by source/tool/cache checks,
can publish the local offline receipt. Proot is not a malicious-code sandbox;
trust confirmation is explicit and private signing keys are excluded from this
preparation build. Device behavior and process-death continuation remain NOT_RUN.

APT observes real archive/partial files against a SHA-256 --print-uris plan;
unsupported index totals remain unknown. Node preloads observe original response
emission without installing data consumers or changing TLS/cache semantics.
Gradle observes typed resource-operation byte results, not console percentages;
it has no live chunk total and falls back to indeterminate for unsupported
versions/configuration cache. Child SDK/Chaquopy/native resolvers that bypass
these boundaries remain indeterminate. No universal pause/resume is claimed.
Four legacy Android SHA-1 archive callsites now also require reviewed fixed
SHA-256 pins; provenance is official HTTPS bytes matched to prior immutable
pins, not a claim of Google-published SHA-256 metadata.

The transaction inventory now includes POSIX permission bits. Bounded persisted
operation history is diagnostic only; restoring READY never proves readiness.
A journal write error stops installation rather than promising resumability.
Module rollback is latest-generation-only and dependent-aware; arbitrary shared
runtime uninstall/update and unbounded old-generation pruning remain disabled.

## Follow-up: verified transport and complete lifecycle boundaries

ModuleArtifactBroker connects pinned SDK/JDK/Gradle/NDK and signed-APT archive
requests to the same native resumable byte/SHA-256 downloader. No host target
path comes from a script; private content-addressed caches survive stage rollback.
Only curated HTTPS origins and bounded GitHub asset redirects are allowed.
Range/If-Range headers survive allowed redirects. APT resolves its signed index,
acquires exact SHA-256 artifacts, records versions and index hashes, and installs
with --no-download. This is a frozen local acquisition manifest, not a remote
Ubuntu snapshot-service guarantee. Metadata transfers without an observable body
remain explicitly indeterminate; sampled staged index files are not network bytes.
Python HTTP body telemetry adds pip/Chaquopy observation without changing trust.

ModuleOwnershipStore records exact before/after deltas and content-addressed
original file bytes. Independent modules can be reverted out of order; shared or
foreign modifications block removal. BaseRuntimeOwnership is captured only from
a fresh verified root, never inferred from an existing installation. Installers
cannot silently remove unowned data or alter another ownership receipt. Approved
recipe hashes distinguish stale installations from current catalog recipes.
Updates stage and probe the new recipe; existing data is kept on failure. Native
Node/JDK/Gradle repairs re-extract pinned inputs rather than blessing old binaries.

Registered full-root snapshots may be cleaned only after digest verification;
the latest rollback root and legacy ownership dependencies remain protected. A
persisted exact prune plan permits interruption recovery and rejects foreign
files. Content-addressed before-images preserve uninstall after old clones are
removed. Linux runtime unregistering archives its private directory and frees no
disk by itself; dependents block it. Bundled Fast HTML cannot be uninstalled.

RootfsActivationTransaction replaces destructive pre-install clearing, journals
both root and metadata, and retains old roots at each fault boundary. An invalid
runtime containing installed modules is not automatically reset. AndroidRuntimeTrust
bootstraps guest APT HTTPS with public Android system roots and hashed trust
configuration; signature checks stay enabled. Gradle's Proroot preparation now
has a verified persistent cache and obeys --offline instead of trying the network.

Foreground operation state identifies the active transfer. A consented in-process
network wait can reconnect with bounded retry; pause/timeout/process death still
require safe manual continuation as appropriate. No force-stop survival, live
service, hardware, or new CI acceptance is implied by this implementation record.

Gradle 9.3.1 typed OperationProgressDetails carries real intermediate byte counts
and content lengths; the observer now consumes those events and an actual Maven
fixture verifies live updates and server-stopped offline resolution. Failed
resource operations never infer a completed total. SDK license JVM observation
uses a bounded JFR recording sampled on daemon threads; observed socket bytes
include protocol/TLS overhead and never claim a payload total or percentage.
Python stdlib HTTP read/readinto and npm original response emission remain
non-consuming observers. Package-index bodies or native child resolvers bypassing
these APIs retain an explicit unknown total; no universal pause guarantee is made.

The Android system-root bootstrap also writes a public-certificates-only PKCS12
trust store for guest Java. APT PEM trust alone does not configure sdkmanager or
Gradle HTTPS. Guest commands explicitly select this store, while peer, hostname
and APT signature verification remain enabled. Its standard integrity password
protects no private key or credential; user-installed CA aliases are excluded.
A real isolated Core install exposed this trust boundary before acceptance.

The real Core probe found that the pinned API 37.0 archive lacks package.xml and
legacy scanning drops its decimal API version. Reviewed declarative metadata
matching API 37.0/revision 2 is now bundled; installation requires the real
archive and accepted license first. No API properties are invented when missing.
AGP SDK auto-download is disabled: absent platforms fail instead of silently
fetching an unplanned SDK. Fresh Python/Expo child modules explicitly reuse the
verified Core payload, while repairs retain the refresh path. New child installs
do not request --reinstall for shared APT packages.

Shared-file readiness now resolves the latest authorized ownership sequence,
checks bytes/modes of all module deltas and rejects unowned private-namespace
files. This avoids either blessing a foreign library edit or rejecting a signed
shared-package update solely because an older module recorded its prior hash.
Before activation, existing ready modules must still match their effective
inventory and execute minimal offline runtime probes. A failed compatibility
probe discards the stage and preserves the active generation. These probes
prove runtime startup, not all application behavior or hardware acceptance.

Volatile Gradle/npm/pip caches, APT indexes and logs remain outside immutable
module ownership; removal preserves them and project-ready receipts separately
attest dependency caches. Approved probe recipes carry an ownership-policy
revision. Project preparation protects ownership receipts, the Gradle observer
and effective installed system-file inventories, so it cannot silently replace
module-owned libraries while merely preparing project dependencies. Shared
package databases with conflicting deltas still block destructive removal.

The standalone Expo fallback now exports its pinned Node directory before npm
shebang execution; a real toolchain run found the missing-PATH failure. A runtime
regression covers absence of system Node. Real Core/Python disconnected builds,
Node standalone installation, Expo x64 tool probes and Windows PE cross-compiles
are recorded separately from physical Android/Windows acceptance in
[the completion report](../../reports/fast-html-v3-final-completion-20261018.md).

### Transfer observation corrections (2026-10-19)

The Node preload now observes fetch/undici response bodies as well as core HTTP
responses. Node 22's pinned bundled undici lacks the newer body-chunk diagnostic;
a scoped onData observer delegates the original callback with unchanged return
value and arguments. Newer Node uses diagnostic body events. Neither path drains
or duplicates the body, replaces TLS, or installs a global dispatcher. Unknown
observer coverage is not inferred from Content-Length as a completed transfer.

Curl telemetry distinguishes interruption from completion and uses the full
Content-Range size for resumed-file progress. This observes a transfer, not the
separate downloader's checksum/Range authorization policy. Structured errors do
not produce READY or an ETA. Staged APT index files do not represent network bytes
and cannot produce throughput, percent or ETA. SDK JFR wire counts remain distinct
from payload counts. All observations remain per-transfer, not a guarantee of
whole-module totals or coverage of arbitrary third-party/native HTTP clients.
