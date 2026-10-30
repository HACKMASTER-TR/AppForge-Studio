# AppForge V9 — Stage 5E.6 Android Keystore acceptance gates

**Status:** SOURCE/PREFLIGHT ONLY. No production APIs, no APK modification, no Google login, no device enrollment, no Cloudflare deployment.

## Inputs and immutability
- GitHub baseline: `befab2ac58fae62f8ae2f18e897fbce269807f88` (V9 only). `main` pinned to `63e496913332cf2e2ae96b2e67ae7414226f09fe`.
- All six Stage 5E.4 and six Stage 5E.5 files remain byte-identical to their SHA-256 manifests.
- Additional Stage 5E.6 files go only to `quality/v9-stage5e6-keystore-probe`, `quality/tools`, `quality/tests`, `.github/workflows`, and `docs/engineering`.
- Nothing calls `:app:assemble*`, `:app:bundle*`, signing, publish, GitHub Release, Cloudflare deploy, or Google Admin login.

## Gate A — isolated source and offline regression
1. Verify exact baseline commit and 12 Stage 5E.4/5E.5 SHA-256 values; deny modified tracked source, mismatched names, or existing paths.
2. Verify 117 Node tests and 38 Kotlin/JVM tests (JDK 25 in Termux as in previous acceptance). Validate zero failures/skips.
3. Validate this isolated app has a **different application ID** (`com.appforge.v9.keystoreprobe`), an explicit **absence of INTERNET permission**, backup disabled, no user-data permissions and no Gradle dependency on the AppForge app.

## Gate B — real Android SDK compile (NOT equivalent to a physical test)
1. Use Ubuntu 24.04, JDK 21, Gradle 9.3.1, AGP 9.1.1, Android platform 37.0, NDK 28.2.13676358, CMake 3.22.1.
2. Compile actual existing V9 Android app source with `:app:compileDebugKotlin` and `:app:compileDebugUnitTestKotlin` including the 5E.4/5E.5 sources.
3. Compile a separate diagnostic Android app against **identical copies** of the exact 5E.4 `V9AdminProofWire.kt` and 5E.5 `V9AdminP256SigningCore.kt`, `V9AdminKeystoreRequestSigner.kt`. `:compileDebugKotlin` ONLY. No diagnostic APK is built by this workflow.
4. Fail if an `.apk` exists in either compiled target's build outputs.
5. This workflow can only run after a **separately approved V9 development-branch commit**. It is not deployed by the local Stage 5E.6 script.

## Gate C — physical Android Keystore acceptance (PENDING; operator-gated)
A different application UID is essential: a separate test app cannot read, create, or test a key owned by `com.appforge.studio`. A sidecar diagnostic checks Android hardware and the exact signing core on the same physical device, **not** the real app's private Keystore namespace. Never represent it as production admin-key acceptance.

After a **separate explicit decision** to build/install the diagnostic APK only:
1. Build an isolated, *debug-only* APK from `quality/v9-stage5e6-keystore-probe` **without** building, signing, updating, uninstalling, or replacing the existing AppForge APK. Verify application ID and no INTERNET permission before installation. Install only the standalone package; no signing or upload of release artifacts.
2. Open *V9 Keystore Diagnostic (Isolated)* and read results in the app UI. It creates `appforge-v9-admin-proof-s5e6probe2026` in its **own application UID**, never self-registers an admin device, never contacts any server and deletes its disposable test key in a `finally` block.
3. Require `S5E6_PHYSICAL_KEYSTORE=PASS`, P-256 generated, `PrivateKey.encoded == null`, public-key pin bound, real `SHA256withECDSA` signature valid, tampered body/proofId rejected, two proof IDs distinct. Record observed `S5E6_SECURITY_LEVEL` (`STRONGBOX`, `TEE`, `HARDWARE_REPORTED`, or `SOFTWARE_OR_UNKNOWN`). A source-only check, simulator or JVM fixture is *not* a physical pass.
4. Confirm the app's UI has no personal/account data, no Google token, no network access, and alias cleanup; remove the standalone diagnostic app after recording the result. A failure or software-only security level must be reviewed rather than auto-accepted for privileged enrollment.
5. Real AppForge app-UID key check, key attestation chain + trusted server verifier, secure out-of-band provisioning, explicit authenticated administrator enrollment, revocation, audit, replay TTL/GC, trusted rate limits, and real Google admin login remain separately gated. Do not claim production readiness.

## Explicit acceptance markers
- Gate A: `S5E6_SOURCE_PREFLIGHT=PASS`, `S5E6_NODE=PASS;TESTS=117`, `S5E6_KOTLIN=PASS;TESTS=38`.
- Gate B: `S5E6_APP_ANDROID_SDK_COMPILE=PASS`, `S5E6_PROBE_ANDROID_SDK_COMPILE=PASS`, `S5E6_COMPILE_ACCEPTANCE=PASS;APK=NO`.
- Gate C: `S5E6_PHYSICAL_KEYSTORE=PASS` (from the installed **separate** diagnostic application) and actual security-level observation.
- **No Gate B or C PASS is issued by the local source preparation command.** Production readiness remains BLOCKED.
