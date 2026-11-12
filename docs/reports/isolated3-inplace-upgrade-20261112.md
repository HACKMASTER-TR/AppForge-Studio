# In-place update identity correction — 2026-11-12

The user reported that the generated APK installed alongside their existing app.
The old APK artifact was then supplied explicitly. Both artifacts were downloaded
through the authenticated GitHub connector, their ZIP digests matched GitHub's
artifact metadata, and the extracted APKs were independently inspected with
AAPT2 and cryptographically verified by apksigner.

| Identity | Existing user reference | Incorrect standard delivery |
| --- | --- | --- |
| Run | 42891389496 | 43148968055 |
| Artifact | 13800435130 | 13912544567 |
| Source | 404e0edb404fbf4826b48f6a7d7007b40508568a | fa41391eeabf3ceee5785d710864d8bf0faf7bdc |
| Package | com.appforge.studio.v9r2isolated3 | com.appforge.studio |
| Version code | 534 | 534 |
| Minimum / target SDK | 26 / 37 | 26 / 37 |
| Certificate SHA-256 | dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082 | f04693e62080489f41554ff357bab0789bf87b864aa8c5a9927279667de9d91b |
| APK SHA-256 | 720bfb85a8dd77645391042a247b6954ad64aa1cbd382dea647cd32232c46e4d | e40d05c9bf50cc50e04338a5c6368cb8b6a3e0033f5f54c6a156f4845953272f |

Both package and signer differed. The earlier standard build was not an update
candidate for this installed app. This should have been checked before choosing
the build route. The fix targets the actual installed identity, rather than
asking the user to uninstall or clear private data.

## Corrected source and build route

The original R2 commit contains 81 changed files relative to the previously used
base. Its guest NDK loader, Rust home/linker, package cache and other fixes must
not be lost during an update. The new integration worktree starts at that actual
old source and merges the previously delivered features, resolving module-screen
conflicts while preserving the R2 operation/timing and on-demand diagnostics.
The old runtime installer scripts and production boundaries remain unchanged.

A debug-only `appforgeV9R2Isolated3Upgrade` option requires the original isolated3
identity and sets its output versionCode to 535. It does not change the ordinary
or release package/version. A separate push-triggered no-deploy workflow restores
the same original CI signer from existing isolated2/3 secrets, checks its exact
public certificate, downloads the old reference artifact, runs security and unit
tests, then compares the actual signed APK with the reference. Expired reference
artifacts, absent/rotated signing keys or an incompatible output fail closed.

The Termux `upgrade` route correlates the exact source/workflow, requires positive
package/certificate/version/SDK/hash evidence and zero failed/skipped unit tests,
and downloads only that upgrade artifact. It optionally copies to an accessible
Download folder using a new filename and verifies the copy hash. It never
installs an APK, changes app permissions or accesses private app data.

## Executed local evidence

- Isolated3 upgrade Gradle build and Android unit suite: PASS, 722 tests, zero
  failures/errors/skips; local output package com.appforge.studio.v9r2isolated3,
  versionCode 535, minimum/target 26/37.
- V9 Python security suite: PASS, 527 tests, zero skips. A missing host gpgv was
  supplied in a user-owned tool directory only after verifying Debian's signed
  Release, Packages index SHA-256 and package SHA-256. App APT controls were not
  disabled or relaxed.
- Upgrade guard regressions: PASS, eight tests covering identity/hash/version/SDK
  and workflow signer guards.
- Termux driver regressions: PASS, 18 tests; Git/GitHub boundaries mocked,
  including wrong/missing upgrade evidence and unique Download copies.
- Actual old/new standard APK comparison: rejected with UPDATE_PACKAGE_MISMATCH.
- Actual correct-package local debug APK comparison: rejected with
  UPDATE_CERTIFICATE_MISMATCH. The local debug key is not the old CI key; this
  locally built APK is deliberately not delivered as an installable update.
- Hosted upgrade signing/build: NOT_RUN here; source must be pushed through the
  user's authenticated Termux account. Earlier connector write 403 still blocks
  cloud publication. Private signing material was not fetched into this session.
- Physical update and user-data retention acceptance: NOT_RUN; no connected phone.
  The identity preconditions are enforced, but actual installation and existing
  projects/modules must still be checked on the user's device.

Commands and actual logs accompany the handoff package. No main ref, Play or
Cloudflare production environment, installed Node, user project or phone data was
modified. The unrelated parallel standard app should be left alone until the
correct original app update and its data are confirmed.
