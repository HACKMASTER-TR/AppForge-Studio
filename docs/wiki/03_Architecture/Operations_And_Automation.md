---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-15
last_verified: 2026-09-15
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
  - "build-service/docker-compose.yml"
  - ".github/workflows/appforge-stability-gate.yml"
  - ".github/workflows/android-debug.yml"
  - ".github/workflows/android-play-release.yml"
  - ".github/workflows/production-automation.yml"
  - ".github/workflows/source-worker-image.yml"
  - ".github/workflows/windows-worker-image.yml"
  - "scripts/appforge"
  - "scripts/appforge-stability-gate"
---

# Operations and Automation

The local Compose definition names PostgreSQL, Redis, MinIO, Mailpit, API, worker, and video-downloader services. It is local configuration evidence, not evidence that an environment is deployed or healthy.

The workflow directory contains Android debug and Play release, stability gate, conversion smoke, production automation, cleanup, autoscaling, and worker-image workflows. Shell scripts provide project automation and stability behavior. Wiki health scripts remain advisory and are not installed as a Git hook or required workflow gate.

Local Autopilot Android/JVM execution must not treat any host `gradle` binary as authoritative. On Android-hosted or proot sessions, detected from Android system markers and the kernel release, Autopilot defers Android JVM execution to `android-debug.yml` before starting Java or Gradle. On ordinary Linux hosts, the local Gradle version is compared with the CI baseline and older or unknown versions are also deferred to CI.

Android/proot staging also uses the verified `git add --all` path after forbidden-file validation. The ordinary host path retains explicit changed-file pathspec staging. This avoids a proot-specific staging crash without weakening the forbidden file guard.

Read workflow triggers, environment requirements, and the called script before changing delivery behavior. Do not infer remote deployment success, secret availability, or production state from names in configuration.

## GitHub cleanup open-PR dependency check

The post-merge GitHub cleanup uses an explicitly URL-encoded REST query
for source-branch open-PR discovery. A GitHub CLI transport/query failure
must remain fail-stop and expose its stderr instead of being reported only
as a generic dependency-check failure.

Source verification:
- `scripts/github-cleanup`

## GitHub CLI release compatibility

Autopilot release commands resolve both `gh` and `git` before publishing,
prepend their executable directories to the release subprocess `PATH`,
and pass the repository explicitly with `--repo`. CI failure-log
collection first uses `--log-failed` and falls back to `--log` for older
GitHub CLI builds that do not support the narrower flag.

## Release REST fallback

GitHub Release publishing normally uses the `gh release` command. Some
Android/proot environments can successfully use `gh api` while the higher
level `gh release` transport fails to connect. Autopilot therefore falls
back to the authenticated `gh api` Releases endpoint before classifying a
release as failed. The fallback preserves fail-stop behavior: delivery
still stops if both transports fail.


## Keyless Google Play publishing

Google Play publishing uses GitHub Actions OIDC through Google Workload
Identity Federation. `android-play-release.yml` receives `id-token: write`,
authenticates with `google-github-actions/auth@v3`, impersonates the
`appforge-play-publisher` service account, and passes the action-generated
temporary credentials file to the Play uploader. No long-lived Google
service-account private key or JSON credential is required.
