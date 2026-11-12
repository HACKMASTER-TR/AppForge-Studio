# Updating the existing isolated3 app from Termux

The user-provided old APK was independently inspected. Its package is
`com.appforge.studio.v9r2isolated3`, versionCode 534, and certificate SHA-256
`dd1dbb994df46f9815c08fe14758f60b39d5787be41a1123bbc2579589f73082`.
The previous standard `com.appforge.studio` APK used a different certificate and
therefore installed alongside it. **Do not uninstall either app or clear data.**

For this existing installation, use the new command:

```sh
bash github-apk.sh upgrade "$PWD/source-change.bundle"
```

It preserves the old R2 source history, pushes a fresh codex upgrade branch and
opens a draft PR. The push triggers `v9-isolated3-inplace-upgrade.yml`; no new
workflow needs to be merged into main for dispatch. The runner restores the
original isolated signer from the existing isolated2/3 repository secrets, checks
its pinned certificate, builds the original package with versionCode **535**,
and compares the actual result against the exact old artifact. A missing/wrong
key, package, certificate, SDK, version, source SHA, APK hash or failed/skipped
unit test prevents an upgrade artifact from being delivered. Secrets are never
included in the package or requested through chat.

The verified APK is retained under the unique Termux session's `artifacts`
directory. If shared-storage access was already granted, a unique, hash-checked
copy is also written to Download. The command never starts installation. Android
must still confirm the update on the actual device; CI proof is not a claim of
successful physical installation or preserved runtime behavior.

The `publish` command below remains the standard-package route. **It is not the
update command for the user's isolated3 app.** `download RUN_ID` recognizes the
separate upgrade workflow and validates its evidence before handing over an APK.
Default `download` refuses the previously wrong standard APK. Legacy standard
artifacts require the explicit `download-standard RUN_ID` command.

# GitHub and APK operations from Termux

This driver sends the reviewed source bundle to a development branch, creates a
**draft** PR targeting `feat/v9-global-package-distribution-20261027`, triggers the
existing `android-debug.yml` workflow with `publish_latest_release=false`, waits
for successful CI, and downloads the exact commit's debug APK artifact.

Termux controls GitHub; the Android build runs on a GitHub Ubuntu runner. It does
not claim that desktop Android SDK/NDK host binaries run natively on Android ARM64.
No main push, merge, release, Cloudflare/Play deployment, app installation, user
project cleanup or Node update is performed. Work is created in unique directories
under `$HOME/AppForge-Termux` (or `APPFORGE_WORK_ROOT`). Existing directories/APKs
are not overwritten. A failed CI never proceeds to APK download.

## On the phone

Install Termux from its official supported distribution and run:

```sh
pkg install git gh jq curl unzip
gh auth login --hostname github.com --git-protocol https --web --scopes repo,workflow
```

Authenticate with the GitHub account that has **write permission** on
`HACKMASTER-TR/AppForge-Studio`. Do not paste credentials into a chat or script.
An authorization statement does not change repository permissions; the driver
checks the actual account's repository access and stops if it cannot push.

Extract the delivered package to a new private Termux directory. The package
contains `github-apk.sh`, `source-change.bundle` and
`source-change.bundle.sha256`. From the extracted directory:

```sh
bash github-apk.sh publish "$PWD/source-change.bundle"
```

The bundle requires base commit `a9c1739b025a939b120888220d827a82f1538467`;
the fresh clone includes and checks that history. The script verifies the local
SHA-256 sidecar, bundle HEAD, base ancestry and clean development branch, and
pushes without force. Workflow publication guards are checked before push.
Each step is visible; local work is retained if a later step fails. PR URL and
run ID are saved in the session directory before waiting. If Android closes
Termux, the hosted build can continue; use the saved run ID with `download`.

The PR may trigger the separate read-only integration workflow too. Debug APK
production uses the existing default-branch-known `android-debug.yml`, so the
new integration workflow does not need to exist on main for manual dispatch.
No changes to main are required to run this route.

To redownload an already successful development debug run:

```sh
bash github-apk.sh download RUN_ID
```

Download-only mode needs repository/artifact read access, not push permission.
The driver checks the workflow name, codex branch, successful completion and
artifact commit identity. For the publish route it also compares the run SHA
with the exact pushed source. APKs and local SHA-256 files are saved in a fresh
session's `artifacts` directory; the CI URL and JSON metadata are retained.
Termux checks ZIP integrity and computes SHA-256; the workflow independently
checks Android package/SDK/signature. Neither is a physical-device acceptance test.

## Failure and device data

- **403 / wrong account:** log in with the account actually permitted to push;
  ensure the OAuth `workflow` scope is granted for the workflow file changes.
- **Actions unavailable:** inspect repository Actions permissions. No production
  credentials or deployments should be enabled for this procedure.
- **CI failed:** inspect the printed Actions URL; fix the reported failure and
  rerun. A failed workflow is not an installable delivery success.
- **APK signature mismatch:** keep the existing app and its data. The workflow
  uses its configured CI debug identity; replacing an app requires a matching
  signer. Do not uninstall or clear data to get past a signature mismatch.

The script does not inherit `GIT_DIR`, `GIT_WORK_TREE` or `GIT_INDEX_FILE` overrides;
it refuses them to avoid touching a previously opened repository. It never asks
for or prints a token. Review the source before running the GitHub write command.

## Local regression validation

```sh
bash -n scripts/termux/github-apk.sh
python3 -m unittest discover -s quality/tests -p test_termux_github_apk.py -v
```

The tests execute the real shell driver while mocking Git/GitHub service
boundaries. They validate protected branch/production guards, checksum rejection,
account permission failures, CI failures, wrong workflow/commit rejection and
preservation of existing files. They do not constitute an actual phone, push, PR
or hosted Actions run.
