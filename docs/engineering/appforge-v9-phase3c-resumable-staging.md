APPFORGE V9 PHASE 3C — resumable DEB local staging acceptance

Scope: read-only pinned Ubuntu 20261018T000000Z snapshot; local content-addressed
staging only. Does not change GitHub, Cloudflare R2, the Android app, system APT,
or engine readiness gates. Verified against the prior 32/32 signed Packages
closure; packages are still independently verified byte-for-byte.

The tool:
- gates with signed GPG InRelease / Packages catalog closure for arm64+amd64
- verifies the exact pinned manifest and full local-object inventory
- uses a bounded 5-file, 2MiB default batch (caller may lower it)
- retains temporary partial data separately, allows HTTP Range only with 206
  and strictly matching Content-Range when resuming
- enforces pinned SHA256, size, DEB ar magic before accepting any object
- requires Linux renameat2(RENAME_NOREPLACE), demonstrated by a no-overwrite
  capability probe. This ensures final-name atomicity and protects existing data
- prevents concurrent Phase3C resume runs with an exclusive flock lock
- refuses unsupported platform/permissions/disk state with no distribution

The first Android acceptance should remain capped to five small DEB files.
Do NOT run more than one phase3c batch at once or use another writer.
If a transfer fails, leave the partial untouched and share the error log.
Never erase or modify the verified object store to repair a failed transfer.

Global hard limits: 5 files per batch, 40 MiB per object, 64 MiB per batch.
First Termux acceptance should request ONE new DEB (max-files 1, max-total-bytes
1048576, max-single-bytes 1048576). A kernel without working
renameat2(RENAME_NOREPLACE) must stop before any network transfer.
