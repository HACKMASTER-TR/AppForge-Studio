---
type: architecture
status: active
project: AppForge Studio
created: 2026-10-21
updated: 2026-10-21
last_verified: 2026-10-21
confidence: medium
source_files:
  - "android-app/app/src/main/java/com/appforge/studio/build/RustProjectPolicy.kt"
  - "android-app/app/src/main/assets/device-build/install-rust-module.py"
  - "android-app/app/src/main/assets/device-build/rust-project.py"
  - "android-app/app/src/main/assets/device-build/rust-toolchain-v1.json"
  - "android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt"
---

# Rust integration — partial, local-only

Base V7 b56bf0a56de91e60beb862e10c8632f1e4f10ff3; isolated branch
feat/rust-all-engines-20261021. GitHub writes are prohibited.

Rust 1.85.1 + Cargo uses pinned official component archives for aarch64/x86_64
Linux GNU hosts and wasm32 std. The existing VerifiedModuleDownload,
RuntimeGenerationStore, ModuleOwnershipStore and user consent flow are reused.
Module readiness requires an isolated install, real offline Cargo WASM compile,
repeat compile, exact version and ownership attestation. Terminal package presence
is never readiness. A new version must have a new verified manifest/recipe.

rust-project.py is a source-execution-consent CLI for reviewed disposable sources.
Cargo.lock is mandatory; frozen/offline builds never resolve fresh dependencies.
Only registry checksummed crates are accepted. CLI Cargo offline flags are NOT an
OS socket sandbox for arbitrary build.rs/proc macros. This CLI is not attached to
the restricted-network project preparation wizard. Do not infer Wi-Fi-only or
OS-disconnected execution from the CLI results.

Prepared WASM + source/artifact consistency receipt can be packaged in local HTML
APK/AAB; JS bindings are authored by the project, never injected. Cargo root
projects without matching preparation are blocked before silent HTML packaging.
This scoped root-package detector is not a general monorepo/workspace resolver.
Native Android JNI/NDK, Node native addon, PyO3/Chaquopy, JSI/Expo, Windows native
and Universal target orchestration remain NOT_DONE/BLOCKED. Host ctypes is not
PyO3 acceptance. Node WASM execution is not Android WebView acceptance.

Android /data/data and /data/user/0 may be aliases of each other. Only these
two exact system paths are mapped to the other; translated paths then undergo
the existing no-symlink anchor check (cycles and secondary links are rejected).
No arbitrary app workspace aliases are allowed. Device-specific behavior remains
NOT_RUN until physical Android 16 testing.

Module UI separates modules/network/history/diagnostics and collapses ownership,
APT, rollback and update details. Existing action guards and dialogs remain.
Compose rendering/screenshots, touch, TalkBack, rotation and dark/small-screen
acceptance require Android execution (NOT_RUN); source/host tests do not replace it.

Detailed frozen-source results and limitations are in the external delivery report.
ALL_ENGINES_READY is false; main merge/release/Production/phone actions are forbidden.
