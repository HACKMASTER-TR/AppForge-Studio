---
type: status
status: active
project: AppForge Studio
created: 2026-09-15
updated: 2026-10-03
last_verified: 2026-10-03
confidence: high
tags:
  - status
  - validation
related:
  - "[[Hot_Context]]"
  - "[[Bug_Index]]"
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/WindowsPortableExePackager.kt"
  - "windows-host/tests/payload.test.cjs"
  - "windows-host/payload.cjs"
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

## Historical checkpoint — 2026-10-03

For current cross-workstream claims use [[Current_State_Handoff]] and `01_Project/current_state.json`, subordinate to Git and actual evidence. This page preserves release-integration checkpoint history; its PASS claims do not transfer to newer source SHAs.

Release Integration V1 remains the active integration line.

- PR #62 Play Production fail-closed: INTEGRATED.
- PR #63 Portable persistence: SEMANTICALLY INTEGRATED.
- Full Quality: `826/826 PASS`.
- Windows Portable physical persistence/relaunch/relocation/crash retest on
  Tulpar: PASS.
- Portable localStorage and IndexedDB physical sequence: `1 → 2 → 3 → 4 → 5`
  PASS.
- Native Android physical CMake build: PASS.
- Native PE32+ x64 Windows launch: PASS.
- server-verified publisher authorization staging acceptance: PASS.
- physical Authenticode cryptographic signing: PASS.
- DigiCert RFC3161 timestamp and signature verification: PASS.
- debug-only self-signed publisher acceptance: PASS.
- missing-provider/certificate physical fail-closed build `AF-0000001058`: PASS.
- non-admin publisher-signing denial build `AF-0000001059`: PASS.
- requested-signing failure deletes the final EXE instead of publishing an
  unsigned artifact: PHYSICALLY PASS.
- protected `main`: UNTOUCHED.
- Play Production release: NOT STARTED.

## Portable Authenticode payload regression

The Native-only post-sign validator defect is fixed and the patched Android
build preserved the signed Portable EXE.

Physical inspection then exposed a second A-class regression: Authenticode
correctly appended the PE Certificate Table after the AppForge overlay payload,
while the Windows Host still looked for `APPFORGE-EXE-V1!` at physical EOF.
The signed EXE therefore had valid Authenticode, Code Signing EKU and DigiCert
RFC3161 timestamp data but the runtime reported that the AppForge payload
signature could not be found.

The release-integration fix resolves the logical payload end from
`IMAGE_DIRECTORY_ENTRY_SECURITY`, preserves unsigned EOF behavior, and
revalidates the Portable payload after signing before build success is
published. Windows Host CI run `37143822118` passed, and Android pins the
accepted staging host `windows-host-v1-c7e4b2a` at SHA-256
`f4aa9c8bee1b919cfb7e3cd6e8073ab4097b0b8e050bc8fb3758f5a198b98c1e`
and `375039759` bytes.

Exact-head Android Debug run `37144740820` passed at commit
`252875794e5c288953da369a81dc4074625499b8`. Physical build
`AF-0000001057` then produced the signed Portable EXE successfully. On Tulpar,
the generated project rendered normally, no payload-signature error dialog
appeared, and the application launch counter reached `2`.

`REAL_AUTHENTICODE_END_TO_END=PASS`.

## Remaining release gates

- decide production publisher endpoint enablement;
- wait for Play Production access approval;
- reconcile final release integration into protected `main`.

`PRODUCTION_READY=NO`.

## Historical record

Detailed earlier implementation and validation history remains in
[[archive/Current_Status_History_2026-10-02]].

See [[Release_Integration_V1]], [[Windows_Publisher_Authorization]],
[[Hot_Context]] and [[Open_Questions]].
