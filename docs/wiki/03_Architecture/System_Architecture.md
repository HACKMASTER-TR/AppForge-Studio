---
type: architecture
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-19
last_verified: 2026-09-19
confidence: high
tags:
  - architecture
  - device-build
related:
  - "[[Project_Overview]]"
  - "[[Build_And_Worker_Architecture]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ProPurchasesActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/BuildApiClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/AndroidLinuxRuntimeManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/io/ProjectLibrary.kt"
---

# System Architecture

AppForge Studio uses a device-first architecture.

User project compilation runs inside the Android application through the
packaged rootless Linux/PRoot runtime. Project source is not uploaded to a
remote AppForge build Worker.

`DeviceBuildEngine` owns local build execution. The existing
`BuildApiClient` name is temporarily retained as an Android UI compatibility
facade, but the normal build path delegates to the device engine instead of
calling `/api/builds`.

Initial device build families are static Web, npm-based Web projects,
Android Gradle Kotlin/Java projects, and Python/Chaquopy projects.

APK and AAB outputs are stored locally. Project metadata and build history
remain device-local through `ProjectLibrary`.

GitHub remains repository/CI infrastructure. Google Play and Google Cloud
remain AppForge Studio distribution, billing and Play-integrity
infrastructure. They are not project-build Workers.

## Clean Device Build Runtime V3

Terminal Linux and project-build Linux are separate trust/lifecycle domains.

The Terminal rootfs is a persistent developer workspace. Project builds use a
versioned build-only rootfs which may be replaced when the runtime revision
changes. Project source is mounted through the build workspace and is not
uploaded to a remote AppForge Worker.

`DeviceBuildCapabilities` is the compatibility registry for source engines and
artifact targets. Unsupported or unvalidated engines fail explicitly.

## HTTPS Control Plane Separation

Project-build transport and product control-plane transport are separate
boundaries.

`DEFAULT_BUILD_SERVICE_URL` is `device://local` and is only for device-local
project compilation through `BuildApiClient` / `DeviceBuildEngine`.

Account, Admin, Pro/security and Android update-policy traffic uses the
dedicated HTTPS control plane at `https://api.appforgecloud.com`. Those flows
must never inherit a project's `buildServiceUrl` and must never fall back to a
remote project-build Worker.

Admin authorization is confirmed by the authenticated
`/api/admin/system-status` response. Pro entitlement remains confirmed by
`StudioSecurityClient` against `/api/pro/status`. Update-policy network failure
does not block normal offline app entry; maintenance remains the only cached
blocking state.

## Cloudflare Control Plane V1

The standalone Cloudflare Worker is staged under
`cloudflare/control-plane`.

The existing PostgreSQL-backed Express API must not be deployed
unchanged to Cloudflare D1.

Until account migration, server authorization, Play verification
and endpoint contracts are complete, protected API routes must
remain unavailable rather than grant synthetic access.

Normal Device Build V3 remains independent of Cloudflare.

## Cloudflare Control Plane Phase 2: New Accounts (staging)

User chose NEW accounts, not a legacy PostgreSQL import. D1 schema and WebCrypto
password/session implementation are staged under `cloudflare/control-plane`.
Auth signup/login requires a separate Cloudflare rate limiter binding; registration
remains unverified until genuine email verification is delivered. Admin and Pro
permissions remain fail-closed, never inferred from a local owner/email string.
Android `/api/client/android/policy` is deliberately non-blocking pending actual
release policy. No production deployment, domain switch or Google Play activation
was performed. Device Build V3 is unchanged.

## 2026-09-19 — Accountless Cloudflare Control Plane (staging)

The earlier Cloudflare Phase 2 *new account* design is superseded before
any deployment: users will not create AppForge email/password accounts.
`cloudflare/control-plane/migrations/0001_accountless_control_plane.sql`
replaces the un-applied accounts schema. Normal device builds stay local.
Google Play Billing + server-side Google Play Developer API verification
will own Pro entitlements; Google OpenID Connect verified `sub` and an
explicit server allow-list will own admin. Neither the store account, client
email, legacy bearer, nor device ID alone establishes server identity.
The staged Worker fails closed for unfinished Admin/Pro/device/quotas.
Android legacy session and purchase UX must be refactored before deployment.

## 2026-09-19 — Single lifetime Pro contract (staging)

The normal-user accountless model has one Google Play non-consumable,
one-time INAPP Pro product (proposed ID `appforge_pro_lifetime`), not a
subscription, renewal or add-on system. The Cloudflare D1 staging schema
stores one-time purchase status and revocation, not monthly expiration.
No Pro right is granted until Google Play Developer API verification and
Android Billing purchase/restore integration are complete. Device Build V3
is unchanged.

## Accountless Lifetime Pro Android staging

Regular users do not create AppForge accounts. Android Billing queries only
`appforge_pro_lifetime` as a non-consumable Play INAPP item. No SUBS product
or add-on product is requested or consumed. A completed Play purchase is not
a Pro entitlement by itself; the accountless client sends its purchase token
to the HTTPS `/api/pro/activate` verifier and accepts only a positive, matching
server result. Outages must fail closed for paid entitlement while device-local
build remains usable. Admin requires a separately verified Google identity.
The staged Worker does not yet provide real Play verification, so paid purchase
must stay disabled until a genuine ready config is available.
