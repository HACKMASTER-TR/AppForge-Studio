# AppForge Fast Web 535 — Phase 1: source fixes and AAB template feasibility

**Status: LOCAL PREPARATION ONLY. No working AAB producer or 535 APK yet.**

Scope strictly **static locally packaged HTML/CSS/JavaScript**. Existing
HTTPS URL APK support remains unchanged; no new URL AAB support is claimed.
React, Vite, Node, Gradle, native Android, Python and Expo build engines remain
outside this AAB project.

1. New `ProjectDraft` output defaults to `apk`, not `both`. AAB, both and
   other explicit outputs stay available; existing saved drafts are not migrated.
2. Quick Create no longer activates notification permission by default,
   because the fast HTML packager explicitly rejects it. Other source build
   engines can enable permissions explicitly.
3. Newly materialized local HTML templates start with APK.
4. A fast HTML candidate with unsupported settings stays in Builder with a
   specific error instead of being mislabeled as a missing offline pack.
   Other engines retain the existing offline toolchain check.
5. CI Android Gradle build prepares the HTML shell's *AAB format* via
   `:html-apk-template:bundleRelease` and embeds this candidate in the
   AppForge APK assets. This template alone is **not** a dynamic, signed AAB.

## Phase 2 — gated implementation

Before enabling AAB in the UI or `DeviceBuildEngine`, implement and verify:
- Safe rewrite of protobuf `base/manifest/AndroidManifest.xml` package name,
  version name/code, app label, SDK constraints (without modifying class path).
- Bounded and path-safe static web assets under `base/assets/site/`; config.
- Correct JAR-style AAB signing with persistent AndroidKeyStore upload
  credentials or an explicitly imported upload key. Never `apksigner` an AAB.
- Actual `bundletool validate`, `build-apks`, `jarsigner -verify`, package
  identity, SDK and version checks, without Google Play production mutation.
- Output selection `apk`, `aab`, `both` for eligible LOCAL static web only.
- Contract and physical acceptance including unsupported options and
  zero-toolchain operation. Play Console acceptance is separate evidence.
- Only when the feature is proven, bump normal source to versionCode 535
  (from installed 534), re-run tests, CI, signer and data-preserving upgrade
  review. Do not overwrite installed version 534 or user projects now.

Bundling a precompiled shell **does not** imply automatic Play acceptance or
proof of a valid dynamic AAB. Treat proof of concept as pending until
bundletool-generated APKs are executed and signed output is verified.
