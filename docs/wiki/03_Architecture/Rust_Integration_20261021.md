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

rust-project.py is also used by the in-app source-execution-consent flow below for reviewed disposable sources.
Cargo.lock is mandatory; frozen/offline builds never resolve fresh dependencies.
Only registry checksummed crates are accepted. CLI Cargo offline flags are NOT an
OS socket sandbox for arbitrary build.rs/proc macros. The managed in-app preparation flow below separates pinned native transfers
from source execution; the standalone CLI itself provides no socket isolation. Do not infer Wi-Fi-only or
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

## Local continuation — in-app preparation and managed crates

The Rust branch now extends ProjectDependencyPreparation (same controller/FGS and
BuildModuleUsage preparation lease). Online preparation parses lockfiles with
app-shipped Python data tools, downloads only fixed crates.io URLs through
VerifiedModuleDownload, verifies SHA-256 and creates Cargo directory-source cache.
It never executes Cargo/project scripts in that online stage. Dependency resolution
is the complete locked closure, not an optimized per-target closure.

Source execution is a second explicit action, requiring all Android networks to
be absent and monitored. Frozen Cargo uses the prepared vendor source twice.
The active runtime is never committed from a project-execution stage. Runtime
compiler/interpreter/config changes, verifier changes, forged vendor checksums,
source drift and partial output prevent publishing. Proot remains unsuitable for
hostile/untrusted source: users must authorize trusted build.rs/proc macro code.

Private prepared-project metadata is atomic and schema-checked. Reopen validates
original/prepared fingerprints and binary/source hashes. The user separately
selects the prepared copy; this resets dependency-download permission and preserves
original source files. No record grants execution or mobile-data consent.

Node-Web copies the verified WASM into the bundler output before APK/Portable
packaging, preserves authored JS and rejects conflicting/escaping output files.
Android controller/Compose/FGS behavior still needs real-device acceptance. Native
JNI/PyO3/JSI/Windows toolchain adapters remain BLOCKED; no all-engines readiness.

### Rust prerequisites and source binding continuation

A clean Ubuntu Base does not guarantee Python. Rust's internal Python 3.12 tools
are now installed through the unchanged signed snapshot/catalog, using the
reviewed PYTHON_ANDROID package set for a fixed Python-only request. Rust owns
its own lock identity; no Android SDK/Chaquopy/Node module is installed. The Rust
adapter derives the shared configure function at a checked single adaptation
point, then retains the original signature/hash/version/architecture checks.
Fresh Ubuntu Base amd64 was tested with Docker --network none, verified cached
.deb files, no preinstalled Python, no Android SDK/Node/JDK, followed by real Rust
installation and repeated Cargo WASM probes. This is HOST_OFFLINE_VERIFIED,
not Android ARM64/Proroot/FGS acceptance. Restrictive network policy still blocks
an APT phase that cannot technically honor it; the guard is not removed.

Prepared WASM receipt schema 2 binds non-cache project inputs, including data
files and build scripts. Cargo target, dependency/VCS caches and the reserved
appforge-rust export are excluded. Build scripts must not depend on ignored
cache/external inputs. Legacy schema 1 is not accepted as fresh source evidence.
Rebuild clears exactly two hash-verified generated export files only in a new
private copy, rejecting foreign contents; the original project is unchanged.
Node projects with prepared WASM retain the separate npm dependency-preparation
flow. Selecting a prepared copy never transfers download/execution consent.
