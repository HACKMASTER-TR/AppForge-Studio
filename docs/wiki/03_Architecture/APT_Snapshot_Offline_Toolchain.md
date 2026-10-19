---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-19
updated: 2026-10-19
last_verified: 2026-10-19
confidence: high
tags:
  - offline-build
  - apt
  - security
related:
  - "[[Modular_Offline_V3]]"
  - "[[Device_Build_Runtime_V3]]"
source_files:
  - "android-app/app/src/main/assets/device-build/apt-snapshot-lock.sh"
  - "android-app/app/src/main/assets/device-build/apt-snapshot-profile.tsv"
  - "android-app/app/src/main/assets/device-build/apt-module-packages-v1.tsv"
  - "android-app/app/src/main/java/com/appforge/studio/build/AptModuleLocks.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/AptDownloadConsent.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/AptSnapshotMigration.kt"
  - "quality/tools/apt_snapshot_reproducibility.py"
---

# Reproducible APT toolchain

Managed module APT uses Ubuntu noble snapshot `20261018T000000Z`, never an
uncontrolled rolling mirror. Four InRelease SHA-256 pins bind noble, updates,
security and backports to the Ubuntu Archive 2018 signing fingerprint
`F6ECB3762474EDA9D21B7022871920D1991BC93C`. APT authentication, gpgv, TLS and
validity checks remain enabled. Local transformed Packages lists are decompressed
and checked against their signed SHA-256 entries. No expiry bypass is added.

The packaged module catalog contains real solver results from the verified
Ubuntu Base 24.04.4 amd64/arm64 archives. New artifacts must match module,
architecture, URL, size and SHA-256 before download. The catalog is a reviewable
allowlist, not evidence that every listed package has been executed on Android.

An APT transaction stores its exact arguments, distro/architecture/profile,
pre/post installed versions, dependencies, exact archive identities, installed
file hashes and metadata identity. Its directory is the SHA-256 of its request.
The completed manifest is published only after `apt-get --no-download --no-remove`
installs exact versions and installed state passes validation. The result is
`APT_NO_DOWNLOAD_INSTALLED`, not a blanket project OFFLINE_READY claim.

Shared archives are addressed by SHA-256. Module references do not permit deleting
the shared store. Cache hits are verified; bad bytes stop the operation. Shared
package version changes conflicting with any existing lock are rejected. Replay
requires the exact baseline or an already compatible verified installed set.

The existing APT payload telemetry remains authoritative for bytes/rates/ETA.
Snapshot resolution, signature/hash validation and local installation are separate
phase events. After initial module consent authorizes metadata resolution, missing
package bodies require a second exact-plan approval through an operation-scoped
workspace. Imported project settings cannot grant that approval. Unknown totals
remain unknown; a completed transfer does not mark the module ready.

APT mutation requires the approved module-driver context; normal builds needing APT return an explicit modular-install requirement. The existing generation store isolates module mutations. Publication still needs
runtime probes, APT lock preflight and module ownership verification. Old common
APT installations without a managed snapshot origin are not automatically
migrated; update controls explain the block and preserve the previous generation.
New snapshot/key/catalog revisions require reviewed packaged changes; no remote
latest-version switch is offered. Whole-root rollback retains its previous
unchanged-tree and integrity requirements.

Host proof: three real APT package sets were prepared and replayed on the exact
production amd64 vendor base with Docker network disabled. Full installed version
inventories match, and the production Kotlin inspector validates the resulting
root. ARM64 signed metadata and dependency resolution were exercised, not native
execution. Physical Android lifecycle, ARM64 installation, real ENOSPC and new CI
are separate NOT_RUN boundaries. The proof does not establish arbitrary project
Maven/npm/Chaquopy caches or full Android/Expo build acceptance.
