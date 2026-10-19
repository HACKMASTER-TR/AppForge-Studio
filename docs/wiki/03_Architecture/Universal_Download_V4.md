---
title: Universal download coordination V4
source_files:
  - android-app/app/src/main/java/com/appforge/studio/build/PersistentOperationGraph.kt
  - android-app/app/src/main/java/com/appforge/studio/build/ModuleOperationJournal.kt
  - android-app/app/src/main/java/com/appforge/studio/build/RuntimeGenerationStore.kt
  - android-app/app/src/main/java/com/appforge/studio/build/RuntimeArchiveRemoval.kt
  - android-app/app/src/main/java/com/appforge/studio/build/OwnedModuleFiles.kt
  - android-app/app/src/main/java/com/appforge/studio/build/ModuleOwnershipStore.kt
  - android-app/app/src/main/java/com/appforge/studio/terminal/RootfsActivationTransaction.kt
  - android-app/app/src/test/java/com/appforge/studio/build/PersistentOperationGraphTest.kt
  - android-app/app/src/test/java/com/appforge/studio/build/LifecycleStorageSafetyV7Test.kt
  - android-app/app/src/test/java/com/appforge/studio/build/NetworkSelectionV6Test.kt
  - android-app/app/src/test/java/com/appforge/studio/build/OperationNetworkConsentTest.kt
  - android-app/app/src/main/java/com/appforge/studio/build/PersistentDownloads.kt
  - android-app/app/src/main/java/com/appforge/studio/build/DownloadNetwork.kt
  - android-app/app/src/test/java/com/appforge/studio/build/PersistentDownloadIntegrationTest.kt
  - android-app/app/src/main/java/com/appforge/studio/build/DownloadCoordinator.kt
  - android-app/app/src/main/java/com/appforge/studio/build/DownloadControl.kt
  - android-app/app/src/main/java/com/appforge/studio/build/VerifiedModuleDownload.kt
  - android-app/app/src/main/java/com/appforge/studio/build/ModuleArtifactBroker.kt
  - android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt
  - android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt
  - android-app/app/src/main/java/com/appforge/studio/ModuleDownloadService.kt
  - quality/tests/lifecycle_storage_safety_v7_contract.test.js
---

# Universal download coordination V4

Pinned native archives and structured external telemetry share a bounded observation registry. The registry is observational, not an installation receipt. Native content digests identify artifacts; source URLs are hashed in observations. Target locks reject duplicate writers while permitting independent targets. Aggregation counts native content identities once and remains indeterminate when external observations cannot be safely combined.

VerifiedModuleDownload retains TLS, final SHA-256, size/storage limits, validated Content-Range and atomic promotion. Interrupted prefixes are sealed with a SHA-256 sidecar; a changed prefix is discarded before Range. Legacy prefixes still require the pinned final digest. HTTP 408/429/5xx and selected network exceptions have at most three retries. Retry-After seconds or RFC1123 dates are respected; waits beyond one minute return an error rather than retry early. TLS, digest, source-validator and local filesystem failures do not enter automatic retry. Cancellation is checked during backoff.

User stop requests are cooperative. Direct Node/native archive and rootfs/Windows byte callbacks can stop acquisition. Broker acquisition stops publish an error response so the shell exits normally. Third-party package managers finish the current command before stopping at a checkpoint. Pending cancellation does not kill dpkg. Service timeout/destruction remains a forced cancellation boundary; staged-generation recovery must be reconciled on reopening and is not a guarantee of completing a background installation.

Rootfs forwards the original transfer sample instead of measuring resumed bytes a second time. Terminal external samples clear rates and ETA. Download completion does not set module READY; existing offline probes, ownership and generation activation remain required.

## Limits

The registry is in-memory; ModuleOperationJournal remains the persistent per-module recovery record. It does not persist a complete per-artifact execution graph. Package-manager checkpoint pause is not byte-level pause. Windows Portable retains its existing transfer engine and does not inherit the native downloader's new retry policy. No new metered-network preference or boot scheduler is implemented. Device screen-off, FGS timeout, force-stop, reboot and process-death behavior require physical Android acceptance. Raw wire-byte observations do not establish payload totals or a valid aggregate percentage.

## V5 continuation (2026-10-19)

The V4 limits above are historical. V5 extends the same coordinator with schema-1
PersistentDownloads in app-private no-backup storage. Records contain hashed
source identities, content pins, scoped staging identities, state, network policy,
retry budget and timestamps; no source URLs or credentials. Atomic fsynced writes,
bounded records and strict enum/path validation fail closed. Unknown schemas are
not guessed. V4 module journals are retained; V4 source-URL partial sidecars are
migrated to hashed identities only on a source-authorized native resume.

The native downloader fsyncs periodic prefix checkpoints (first chunk, then up to
8 MiB / 1.5 seconds between checkpoints). On hard process death, an unsealed tail
is excluded; only a rehashed checkpoint may be truncated/resumed after source
re-authorization. A fallback SHA scan is used if digest cloning is unavailable.
Recovery rehashes sealed prefixes and returns PAUSED only for locally consistent
metadata. Even a matching completed file becomes RECOVERY_REQUIRED on reopening:
a fresh module request must re-authorize the pinned source. No artifact record is
a module READY receipt. Missing, damaged, foreign or symlink staging is rejected.

DownloadNetwork binds managed HTTP connections to Android's validated active
Network. Restrictive policies permit unmetered Wi-Fi only, reject ambiguous
VPN/cellular/metered paths and retain each transfer's original narrower policy.
Default-network callbacks update status and disconnect managed connections which
violate policy. Mobile approval may be scoped to one module operation; preference
is restored afterwards. No process-wide routing or TLS bypass is used.

External package-manager commands cannot be safely bound to Wi-Fi across a
handover by this implementation. New online commands are therefore BLOCKED under
restrictive policies; explicit any-network consent is required. They are not
advertised as byte-pausable. ProjectDependencyPreparation now requests cooperative
stop after its current build command instead of killing it via the UI; FGS timeout
retains forced staged-recovery handling. Source-execution/offline-evidence consent
remains separate. This is not a universal traffic firewall for arbitrary user code.

Per-artifact history and manual re-authorized resume are in the existing package
screen. Live bytes/rates are separate from verified-prefix bytes. Windows retains
its accepted downloader with conservative safe-restart recovery classification;
its final host digest is recorded after original validation. Native single-flight
allows independent targets; shared runtime/module mutations still serialize.
Android screen-off, notification denial, reboot, process death and FGS timeout
remain physical acceptance scenarios, not host-test PASS claims. No boot or
force-stop auto-start guarantee and no duplicate WorkManager scheduler are added.

## V6 continuation (2026-10-19)

Artifact journal schema 2 adds observed downloaded bytes and a module parent ID.
Schema 1 is read conservatively with zero invented bytes and migrated on recovery;
unknown versions fail closed. The coordinator writes a bounded, fsynced, atomic
`operations.json` task tree alongside the artifact and module journals. Module
phases, project preparation and artifact children share hashed operation IDs.
Rootfs artifacts created under a terminal transfer ID are adopted by the initiating
module operation. The files are independently atomic, not a multi-file transaction:
recovery rehashes artifact files, rebuilds missing children, validates parent/module
relationships and downgrades previous completion to re-authorization required.
Neither a historical COMPLETE nor graph stage grants module/project READY.
Corrupt module journals now surface a blocking error instead of silently disappearing.

Network selection considers validated available networks, so an eligible Wi-Fi can
be selected even when cellular is the default. Active VPN remains conservatively
blocked in restrictive modes. Each managed connection retains its exact Android
Network as well as its captured policy; callbacks recheck that bound network,
not a different currently active Wi-Fi. A later broader consent never widens an
older transfer. No process-wide routing or bypass is introduced.

External APT/npm/Gradle/Maven/pip/sdkmanager commands still require explicit
ALLOW_ANY. Offline flags alone do not prevent lifecycle/build scripts from opening
sockets. There is no verified per-process network sandbox in the Android PRoot
launcher, so restricted external execution remains BLOCKED. Native pinned archive
acquisition supports the restricted policy; this does not imply a complete module
installation can run under it. Physical Android Wi-Fi/mobile handover, FGS timeout,
reboot and process-death acceptance remain NOT_RUN until tested on a device.

One-operation mobile approval no longer changes a global temporary policy. A
single-use consent is claimed by the initiating module coroutine, propagated to
its children through a thread context element, and revoked on completion. Other
operations retain the stored preference. Connection callbacks evaluate their own
captured consent lease. Host coroutine tests cover sibling isolation, dispatcher
changes, revocation and failed-start cleanup; Android routing remains NOT_RUN.

## V7 continuation: lifecycle and storage safety (2026-10-19)

`PersistentOperationGraph` now includes `REMOVAL` and `ROLLBACK` stages and
deterministic terminal-subtree retention pruning (`pruneRetained`). When the
512-node limit is exceeded, only complete root subtrees whose nodes are all in
terminal states (`COMPLETED`, `CANCELLED`, `FAILED`) are evicted oldest-first as
atomic units; active, `PAUSED`, `WAITING_FOR_*`, and `RECOVERY_REQUIRED` subtrees
are never evicted. `ModuleInstallOperations.lifecycle` records removal and
rollback transitions in `DownloadCoordinator` and blocks startup if strict
journal restoration fails.

`ModuleOperationJournal`, `RuntimeGenerationStore`, `RuntimeArchiveRemoval`,
`OwnedModuleFiles`, `ModuleOwnershipStore`, and `RootfsActivationTransaction`
enforce pre-write disk-space reserves, ancestor symlink rejection, and non-symlink
`.tmp` cleanup on write failure. `RuntimeGenerationStore` checks remaining disk
space before copying each regular file during stage cloning; if interrupted
during `COPYING` before activation, an unmodified partial clone of the active
root is safely reclaimed instead of leaking `.v3-discard-*` directories, while
any modified or post-switch failed generation remains quarantined and inspectable
via `quarantinedDirectories()` / `quarantinedBytes()`. `RuntimeArchiveRemoval`
fails closed if both active and backup runtime directories exist during recovery.
