---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-09-21
last_verified: 2026-09-21
confidence: high
tags:
  - status
  - validation
related:
  - "[[Hot_Context]]"
  - "[[Bug_Index]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/security/GoogleAdminIdentityClient.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/OwnerAccessPolicy.kt"
  - "android-app/app/src/main/java/com/appforge/studio/AdminOpsScreen.kt"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/src/index.mjs"
  - "cloudflare/control-plane/src/google_oidc.mjs"
  - "cloudflare/control-plane/tests/google_oidc_admin.test.mjs"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioBillingManager.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ProPurchasesActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/security/StudioSecurityClient.kt"
  - "quality/tests/device_build_toolchain_scope_contract.test.js"
  - "android-app/app/src/main/assets/device-build/install-toolchain.sh"
  - "quality/tests/appforge_terminal_viewport_stability_contract.test.js"
  - "android-app/app/src/main/java/com/appforge/studio/terminal/LocalPtyTerminalPanel.kt"
  - ".appforge/runtime-blockers.json"
  - "android-app/app/src/main/java/com/appforge/studio/UpdateGateActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ui/DownloadedApkFolder.kt"
  - "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  - "android-app/app/src/main/java/com/appforge/studio/build/DeviceBuildEngine.kt"
  - "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
  - "quality/tests/device_only_build_ui_contract.test.js"
  - "android-app/app/src/test/java/com/appforge/studio/UpdateGatePlayVisibilityTest.kt"
  - ".github/workflows/pro-staging-http-matrix.yml"
  - ".github/workflows/pro-staging-live-audit.yml"
---

# Current Status

## Verified repository state

- This status page includes live Pro staging evidence verified through 2026-09-21; dated sections preserve earlier checkpoints rather than rewriting their historical state.
- The old `.secondbrain` and legacy `docs` content were intentionally deleted before this wiki bootstrap.
- Legacy brain references in scripts, CI policy, Android navigation, owner sync, and local-AI context were removed during this bootstrap.

## Validation evidence

- A pre-refocus `npm test` run in `build-service` reported 683 passes and 37 failures in this local environment; most failures could not load `adm-zip` because `build-service/node_modules` was absent.
- `appforge_terminal_integration.test.js` previously read a deleted legacy privacy document. It now verifies only the Android connection, encryption, and configuration sources and passed 7/7 direct tests on 2026-09-15.
- `fast_signing_key.test.js` had an independent byte-length assertion failure (expected 384, actual 438) in the pre-refocus run.
- Android Gradle tests were not run: no wrapper or local Android toolchain configuration was present.

- The terminal native-viewport touch-scroll regression now has a source fix and a targeted contract test. The targeted test passes 2/2 and `git diff --check` is clean on the current work branch. On-device scroll/fling acceptance passed on 2026-09-16.

- AppForge Terminal's Ubuntu/proot runtime exposed host Java/Gradle tools that can crash natively with exit 139. Autopilot now detects the Android-hosted/proot environment before starting Java or Gradle and defers the authoritative Android JVM suite to `android-debug.yml`. Ordinary Linux hosts retain the Gradle-version compatibility guard.

- Android/proot `safe_stage()` now uses the verified `git add --all` index-write path after forbidden-file validation; ordinary hosts keep explicit pathspec staging.

## Shipping state

`BUG-7` device acceptance passed on 2026-09-16. Scroll/fling, Copy -> Write persistence, leaving and returning to Terminal, workspace-selection persistence, keyboard open/close smoothness, shortcut placement, restart, `pwd`, and `echo APPFORGE_OK` were confirmed on-device. There is no active BUG-7 runtime blocker.

## 2026-09-17 Android correction package

- Normal-user update prompts now require a newer version to be visible from
  Google Play Core for the current account/device; CI/GitHub-only versions
  are not sufficient.
- Successful APK cards include Android 11+ MediaStore `Çöpe taşı`.
- Terminal `+ Oturum` moves persisted creation off the UI dispatcher and
  selects the new session before PTY startup. Device acceptance remains
  pending for this new multi-session correction.

## 2026-09-19 device-only Android CI compile correction

- Android Debug CI exposed an accidental structural deletion in
  `MainActivity.kt` during retired Railway callback cleanup.
- The known-good activity/app-shell prefix was restored from the immediate
  parent revision, then Railway authorization state and callbacks were removed
  surgically without removing lifecycle, navigation, URI persistence or
  `AppForgeApp`.
- `DeviceBuildEngine` Kotlin visibility and sequence-log collection compile
  errors were corrected.
- Local `file://` artifact handling in `DownloadedApkFolder.kt` now constructs
  `java.io.File` explicitly.
- A fresh Android Debug CI run remains the authoritative compile acceptance
  before real-device APK/AAB acceptance.

## 2026-09-19 real-device device-build acceptance follow-up

- AppForge Studio 5.0.29 installed and opened successfully on the physical Android device.
- The new StudioHomeV2 was judged too minimal; the next UI iteration must restore a richer device-first dashboard without restoring remote build infrastructure.
- FIKSTUR TAKIP entered the device-local build path and reported `Build cihaz üzerinde çalışacak`, then failed at 0%.
- The failure UI claimed that live logs were available but no log renderer remained after the device-only cutover.
- The current correction restores sanitized local device-build logs, highlights the first critical error, removes stale Worker/Autoscale wording from the active Builder surface, and aligns the admin stress-test concurrency with the two-thread local device engine.
- APK/AAB physical-device acceptance remains pending until the newly exposed log identifies and the project fixes the actual local build failure.

## 2026-09-19 device-build toolchain scope correction

- Sanitized on-device logs identified the next real FIKSTUR TAKIP blocker before project Gradle execution: Ubuntu package resolution failed while the device toolchain attempted to install Node/npm.
- `DeviceBuildEngine` no longer requires the full Terminal development profile before every project build. It prepares the verified Ubuntu base environment and invokes the device toolchain installer with the selected source-build engine.
- `install-toolchain.sh` keeps the Android/JDK base toolchain common, installs Node.js/npm only for `node-web`, and installs Python packages only for `python-android`.
- Android Gradle and static WebView builds therefore no longer depend on npm availability.
- `device_build_toolchain_scope_contract.test.js` protects this separation. The targeted contract, existing device-only contracts and `git diff --check` passed before final local acceptance.
- Physical-device FIKSTUR TAKIP APK/AAB acceptance is still pending a fresh AppForge Studio APK containing this correction.

## 2026-09-19 deterministic device JDK correction

- The first real-device retry after source-engine toolchain scoping confirmed that the earlier Node/npm dependency-resolution failure was removed.
- The next blocker occurred before project Gradle execution while Ubuntu `dpkg` configured `openjdk-17-jre-headless`.
- Device build Java provisioning now uses checksum-pinned Temurin JDK 17 archives instead of installing OpenJDK through Ubuntu APT.
- ARM64 and x86_64 JDK artifacts are pinned by exact SHA-256 values.
- `DeviceBuildEngine` explicitly exports `/opt/appforge-device/jdk-17` as `JAVA_HOME` for Gradle.
- The device-toolchain readiness marker was bumped so older apt-JDK installations are not accepted as the corrected toolchain.
- A dedicated regression contract protects deterministic JDK provisioning.
- Fresh Android Debug CI and physical-device FIKSTUR TAKIP APK/AAB acceptance remain required.

## 2026-09-19 stale Node/npm rootfs repair

- Physical-device validation confirmed that the earlier unconditional Node/npm installation behavior had left broken Node packages inside the persistent Ubuntu rootfs.
- Those stale packages could make a later Android/Kotlin build fail during an unrelated APT transaction even though the active source engine did not require Node.
- Non-Node device builds now inspect package state and remove only incomplete/broken Node/npm packages.
- Healthy installed Node packages are preserved.
- `node-web` builds skip this cleanup and retain the Node toolchain.
- Dedicated regression coverage protects this engine-aware cleanup behavior.
- Fresh Android Debug CI and physical-device FIKSTUR TAKIP build acceptance remain required.

## 2026-09-19 Clean Device Build Runtime V3

- The repeated npm/OpenJDK/stale-dpkg chain showed that repairing a persistent Terminal rootfs is the wrong project-build boundary.
- Device project builds now have a dedicated V3 runtime architecture which is versioned and disposable independently from Terminal Linux.
- A capability/output registry records current READY engines and future Android/Windows engine families.
- APK/AAB remain the currently proven local artifacts.
- Windows Portable EXE remains a required local target but must not be declared ready until its device-local packager and real Windows acceptance pass.
- Existing AppForge APK/EXE conversion contracts remain product requirements.

## 2026-09-19 AAPT2 ARM64 device follow-up

- The dedicated Runtime V3 reached the Android SDK toolchain installation.
- AAPT2 failed its executable smoke test with a missing libdl.so dependency.
- Project Gradle execution had not started at this failure point.
- The selected ARM64 Build-Tools executables are replaced by SHA-256-pinned
  Linux-glibc ARM64 builds.
- Toolchain readiness is invalidated so the previously installed binary is
  not silently reused.
- Real-device execution and APK/AAB output remain pending acceptance.

## 2026-09-19 Modern Home and five-build retirement

- The temporary five-build device stress panel is retired from production UI.
- Its dedicated tester allow-list, batch state and Builder controls are removed.
- Normal device build and visible device-build logs remain.
- Studio Home is upgraded from the rejected minimal layout to a richer modern
  dashboard with project/build status, AI, conversion, import, recent projects
  and management actions.
- Terminal and Admin remain owner-only.

## 2026-09-19 HTTPS control-plane separation correction

- `device://local` remains authoritative only for normal project compilation.
- Account, Admin, Pro/security and Update flows use the dedicated HTTPS control
  plane instead of inheriting the project's local build URL.
- The `unknown protocol: device` failure is prevented at the routing boundary
  and account/admin clients reject non-HTTPS production control-plane URLs.
- Admin authorization is loaded from the authenticated server response.
- Admin account management loads automatically when the screen opens.
- The obsolete Autoscale dashboard and 10/25/50 real-build stress controls are
  removed from Android Admin Ops.
- Update-policy failure retains the existing `Çevrimdışı devam et` path.
- Device Build Runtime V3, AAPT2 and normal device-local APK/AAB execution are
  unchanged.

## 2026-09-19 Cloudflare accountless pivot — staging only

The user chose no normal-user AppForge registration/login; the previously
staged Phase 2 password/schema path is replaced before deployment. Only
staging health and non-blocking update-policy routes are available. Existing
Android account screens, `session != null` purchase checks and bearer-based
admin have not yet been refactored. Google Play purchase verification,
Google admin sign-in, D1 migration, worker deployment and production domain
switch remain BLOCKED until real end-to-end validation. No Play publishing.

## 2026-09-19 Single lifetime Pro pivot — local staging

One-time non-consumable Pro replaces monthly/annual subscriptions and quota
add-ons in the new product design. The staging Worker returns 410 on retired
purchase endpoints and 503 for unimplemented Play verification; D1 migration
is not deployed. Android still has legacy session/monthly/add-on UI and must
be updated before a physical-device acceptance. No commit/push/deploy/Play
publish was done by this staging patch.

## 2026-09-19 Accountless Lifetime Pro Android staging

- Normal-user account/password UI is replaced by a no-login information screen.
- Pro purchase surfaces and Billing Manager offer only `appforge_pro_lifetime`
  as a non-consumable INAPP item, with INAPP-only restore.
- The client never locally grants Pro on a Billing callback: the receipt must
  pass the HTTPS `/api/pro/activate` server verification first.
- The staged Worker still returns 503 for that route and `/api/security/config`;
  its release is **blocked** until real Play Developer API and Play Integrity
  checks, purchase acknowledgement/refund handling and device acceptance pass.
- Owner/admin Google OIDC is not implemented. Never grant admin from email,
  local device ID or stale AppForge bearer token.
- This limited source export cannot prove Android compilation or the full app
  gate; full-repo CI and physical-device acceptance are still required.

## 2026-09-20 Google admin OIDC verifier — staging

- The Cloudflare staging Worker now verifies the Google RS256 ID token signature and issuer/audience/expiry; the optional authorized presenter (`azp`) must match the configured Android client.
- The stable Google `sub` is SHA-256 hashed and looked up in the explicitly active D1 `admin_identities` table. No email, local AppForge session or device ID grants admin.
- Only identity-check endpoints are enabled; other Admin operations, Pro verification and project builds remain fail-closed/device-local.
- **Not device accepted or deployed.** Android Credential Manager, admin `sub` provisioning, D1 migration and the Terminal owner-policy rewrite are separate required work.

## 2026-09-19 — Phase 6B admin identity staging (local only)

The A6B Android patch removes the obsolete AppForge email/password owner gate.
The accountless home exposes explicit Google admin sign-in. Credential Manager
asks for a Google ID token with a fresh nonce; the Cloudflare staging Worker
verifies the signature and nonce and checks the D1 Google-subject allowlist.
Android keeps approved identity in process memory only. Admin account-management
remains disabled because server routes are not migrated. This is **not** a live
admin access claim: Android CI, verified Google-subject provisioning, D1
migration, staging deployment and physical Terminal acceptance are pending.
Device Build V3, Play/Pro and the production custom domain were not changed.

## 2026-09-21 Pro staging deployment and live audit acceptance

- The dedicated feature branch remains isolated from
  `main`, Play Production, `appforge-failover` and migration
  application.
- The staging Worker version identified as `a1f3c746...`
  was observed receiving 100% traffic. Existing `DB`
  binding and Google configuration names were preserved.
- The HTTP Matrix tested three request profiles against
  `/health`: default curl, AppForge User-Agent and browser
  User-Agent. All returned HTTP 200 JSON with
  `database=reachable`.
- The earlier Python `urllib` 403 is not treated as a
  general GitHub Runner block after those curl results.
- Commit `d7fe56ffcdbd94a264bf402230cb941b66a2642d`
  converted the read-only Live Audit transport to curl.
- GitHub Actions Live Audit run 3 completed successfully:
  `STAGING_HEALTH=PASS`, `D1_REACHABILITY=PASS`,
  `PRO_OWNERSHIP_ROUTE=PASS` and
  `LIVE_STAGING_AUDIT=PASS`.
- The audit made no database writes, applied no migrations
  and initiated no new Worker deployment.
- `d1_migrations` reconciliation and physical-device Pro
  acceptance remain open gates.
