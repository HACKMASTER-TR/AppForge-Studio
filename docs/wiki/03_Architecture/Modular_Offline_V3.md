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
