---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - operations
  - automation
  - ci
related:
  - "[[Deployment_And_CI]]"
  - "[[Test_And_CI_Map]]"
  - "[[Current_Status]]"
source_files:
  - ".github/workflows/appforge-stability-gate.yml"
  - ".github/workflows/android-debug.yml"
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/cleanup-old-runs.yml"
  - "scripts/appforge"
  - "scripts/appforge-stability-gate"
  - "scripts/appforge-delivery-preflight"
  - "scripts/termux/github-apk.sh"
  - "quality/tests/test_termux_github_apk.py"
  - "quality/tools/verify_apk_upgrade.py"
  - ".github/workflows/v9-isolated3-inplace-upgrade.yml"
---

# Operations and Automation

Current AppForge operations are centered on local/device execution plus
GitHub CI and Google Play distribution.

Remote build deployment, Railway autoscaling, Worker-image publication and
Windows Worker automation are no longer active production responsibilities.

The AppForge automation scripts remain fail-stop. Source, tests, CI and real
runtime evidence are authoritative.

Android-hosted/proot environments must not treat an incompatible host JVM or
Gradle installation as authoritative. Android application CI remains the
repository compilation authority until the device-side validation phase is
complete.

## Google Play

Google Play publishing remains separate from project compilation. Device
builds do not replace the AppForge Studio application's own Play release
workflow.

Publishing, GitHub Releases, commits, pushes and merges remain explicit
delivery actions rather than automatic consequences of a local source edit.

## Termux GitHub / debug APK handoff (2026-11-12)

`scripts/termux/github-apk.sh` controls GitHub from a normal Termux shell while
compiling the APK on the existing GitHub Ubuntu debug runner. It does not attempt
to execute desktop SDK/NDK host binaries natively on Android ARM64. See the
[Termux instructions](../../../scripts/termux/README.md).

The driver checks the actual authenticated account's push permission, a supplied
source bundle's SHA-256, base ancestry and development branch before an ordinary
non-forced push. It creates a draft PR, dispatches `android-debug.yml` with release
publication explicitly false, correlates the exact commit/run, and downloads only
a successful development debug artifact. Main, merging, Play/Cloudflare, app
installation, user data and existing Node are outside the procedure. Local work
and downloads use new directories; Git directory environment overrides are refused.

The existing workflow was observed active through GitHub's read-only workflow
metadata API. Driver regressions use mocked Git/GitHub boundaries; they do not
prove a real phone session, push, PR or hosted build. Those run only after the user
authenticates an account with actual repository write access in Termux. The prior
cloud connector 403 does not establish the phone account's permissions.

## Existing isolated3 update identity (2026-11-12)

A user-supplied artifact established that the installed app is
`com.appforge.studio.v9r2isolated3` / versionCode 534 / certificate
`dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082`.
The standard com.appforge.studio debug APK with its different CI signer is a
parallel app, not an update. Reference metadata and APK SHA-256 are pinned in
`quality/fixtures/appforge-v9r2isolated3-reference.json`.

The `upgrade` Termux route preserves original R2 commit 404e0edb ancestry and uses
a push-triggered workflow restricted to codex upgrade branches. It reuses the
original isolated signer secret, sets only the isolated3 debug output to code
535, and independently compares the signed APK against the original artifact.
`verify_apk_upgrade.py` rejects mismatched package/certificate/SDK/version/hash;
positive evidence is uploaded only after real unit tests pass without skips.
Neither local debug signing nor a green standard-package workflow proves an
in-place update. Do not uninstall or clear data to overcome an identity mismatch.
