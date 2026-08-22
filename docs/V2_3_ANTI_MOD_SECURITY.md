# AppForge Studio v2.3 — Anti-Mod + Server-Authoritative Pro

No Android client can be made mathematically impossible to patch.

v2.3 changes the trust model so patching the APK is no longer enough to obtain official Pro behavior.

## Main change: Pro is not a local boolean

The old local `proUnlocked` preference was removed.

The official server is now authoritative for Pro entitlement.

## Server-side Pro build enforcement

The official Build Service checks the account before accepting configs that use Pro-only capabilities.

Current server-enforced Pro features:
- custom signing / custom keystore
- Firebase Analytics or Crashlytics wiring
- Billing-enabled generated apps
- Native Bridge on remote URL content

Changing the Android UI cannot bypass these checks on the official server.

## Play Integrity

Android dependency:
`com.google.android.play:integrity:1.6.0`

The client uses Standard Integrity requests with `requestHash`.

The server validates:
- request hash binding
- package name
- `PLAY_RECOGNIZED`
- `LICENSED`
- `MEETS_DEVICE_INTEGRITY` or stronger
- configured Play release certificate digest

The server issues only a short-lived integrity session after a passing verdict.

## Signing certificate self-check

The Android app can additionally compare its installed signing certificate SHA-256 against:

`APPFORGE_RELEASE_CERT_SHA256`

This is defense-in-depth only. The server remains the security authority.

## Release hardening

Release builds enable:
- R8 minification
- resource shrinking
- backups disabled

## Setup

Gradle:
`APPFORGE_RELEASE_CERT_SHA256=<hex sha256 cert>`

Server:
- `PLAY_INTEGRITY_ENABLED=true`
- `PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER=<number>`
- `STUDIO_ANDROID_PACKAGE=com.appforge.studio`
- `STUDIO_RELEASE_CERT_SHA256=<Play app signing cert digest>`
- `PRO_REQUIRE_INTEGRITY=true`

Google Play Integrity must be enabled and linked to the app's Cloud project.

## Rollout recommendation

Do not immediately hard-block all existing users.

First collect Integrity telemetry, verify genuine users receive expected verdicts, then enable strict Pro enforcement.
