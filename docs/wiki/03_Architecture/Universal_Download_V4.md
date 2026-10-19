---
title: Universal download coordination V4
source_files:
  - android-app/app/src/main/java/com/appforge/studio/build/DownloadCoordinator.kt
  - android-app/app/src/main/java/com/appforge/studio/build/DownloadControl.kt
  - android-app/app/src/main/java/com/appforge/studio/build/VerifiedModuleDownload.kt
  - android-app/app/src/main/java/com/appforge/studio/build/ModuleArtifactBroker.kt
  - android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt
  - android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt
  - android-app/app/src/main/java/com/appforge/studio/ModuleDownloadService.kt
---

# Universal download coordination V4

Pinned native archives and structured external telemetry share a bounded observation registry. The registry is observational, not an installation receipt. Native content digests identify artifacts; source URLs are hashed in observations. Target locks reject duplicate writers while permitting independent targets. Aggregation counts native content identities once and remains indeterminate when external observations cannot be safely combined.

VerifiedModuleDownload retains TLS, final SHA-256, size/storage limits, validated Content-Range and atomic promotion. Interrupted prefixes are sealed with a SHA-256 sidecar; a changed prefix is discarded before Range. Legacy prefixes still require the pinned final digest. HTTP 408/429/5xx and selected network exceptions have at most three retries. Retry-After seconds or RFC1123 dates are respected; waits beyond one minute return an error rather than retry early. TLS, digest, source-validator and local filesystem failures do not enter automatic retry. Cancellation is checked during backoff.

User stop requests are cooperative. Direct Node/native archive and rootfs/Windows byte callbacks can stop acquisition. Broker acquisition stops publish an error response so the shell exits normally. Third-party package managers finish the current command before stopping at a checkpoint. Pending cancellation does not kill dpkg. Service timeout/destruction remains a forced cancellation boundary; staged-generation recovery must be reconciled on reopening and is not a guarantee of completing a background installation.

Rootfs forwards the original transfer sample instead of measuring resumed bytes a second time. Terminal external samples clear rates and ETA. Download completion does not set module READY; existing offline probes, ownership and generation activation remain required.

## Limits

The registry is in-memory; ModuleOperationJournal remains the persistent per-module recovery record. It does not persist a complete per-artifact execution graph. Package-manager checkpoint pause is not byte-level pause. Windows Portable retains its existing transfer engine and does not inherit the native downloader's new retry policy. No new metered-network preference or boot scheduler is implemented. Device screen-off, FGS timeout, force-stop, reboot and process-death behavior require physical Android acceptance. Raw wire-byte observations do not establish payload totals or a valid aggregate percentage.
