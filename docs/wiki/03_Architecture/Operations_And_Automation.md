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
