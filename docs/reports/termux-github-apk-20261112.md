# Termux GitHub / APK handoff — 2026-11-12

The requested GitHub and APK operations are now provided by
`scripts/termux/github-apk.sh`, with phone instructions in its adjacent README.
Termux authenticates the user's GitHub account and controls a hosted Ubuntu build;
this is not a native ARM64 Termux SDK/NDK compilation claim.

`publish BUNDLE` verifies its SHA-256 sidecar and base history, creates a unique
private working copy, pushes only a codex branch without force, creates/reuses a
draft PR against the development base, and dispatches the existing debug workflow
with `publish_latest_release=false`. It waits for success before downloading the
artifact for the exact pushed commit. `download RUN_ID` permits read-only access
and downloads an existing successful codex debug run into a new directory.

The run ID and PR URL survive in the private working directory if Termux closes.
No app is installed, no existing project is overwritten and no package upgrade,
Node update, main push, merge, release or production deployment is performed.
Actual source and APK checksums are retained; Android signature/SDK/package
verification is performed by the hosted debug workflow. A CI success is not
physical device acceptance.

## Executed validation

- `bash -n scripts/termux/github-apk.sh`: PASS.
- `python3 -m unittest discover -s quality/tests -p test_termux_github_apk.py -v`:
  12 PASS, with Git/GitHub boundaries mocked. Cases cover successful publish and
  download, no write permission, source corruption/checksum path misuse, foreign
  Git directory overrides, wrong base, absent workflow guards, CI failure, wrong
  commit/workflow/main run rejection, and preservation of existing files.
- YAML parsing of the integration workflow: PASS. It now runs these regressions.
- Snapshot generation/check and wiki audit/secret scan: recorded with the package;
  existing advisory wiki warnings are not presented as runtime acceptance.
- Real read-only GitHub API lookup: workflow ID 339780269,
  `.github/workflows/android-debug.yml`, name `AppForge Android Debug`, state
  `active`. Target branch still resolves to the audited a9c1739b commit.
- Real Termux session / account authentication / push / PR / dispatch / hosted
  build / phone install: NOT_RUN. These must occur on the user's phone with the
  proper GitHub account. No token is requested through chat or stored in source.

The previous 690 Android unit PASS and APK delivery results remain prior-run
results; no new full Android build is claimed for this shell/test/docs change.
The earlier cloud integration 403 does not prove that the user's separate Termux
OAuth session has write access; the script checks the actual account before push.
