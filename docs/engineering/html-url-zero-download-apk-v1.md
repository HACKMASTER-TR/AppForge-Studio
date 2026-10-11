# AppForge HTML/HTTPS → APK — isolated experimental branch, 2026-10-11

Base: `8ff582f384e75349b0d667d38296b6865b8835f7`.
Only applies to a **new local feature worktree**. The wrapper never pushes, merges, deploys or installs.

## Scope

- Source **only** `webview-static` + `.html` file or `https://` URL.
- Output **only** signed APK (no AAB/EXE).
- Local `.html`: one selected HTML file bundled inside shell; no separate CSS/JS assets; embedded JS is disabled in the tiny WebView shell.
- URL: HTTPS requires internet at runtime, not APK build time; JavaScript disabled.
- No device downloads (SDK/JDK/Gradle/Node): the minimal shell is compiled by **host CI when AppForge is built** and bundled in AppForge's assets.
- Fast path bypasses `OfflineBuildPackManager` requirements and `DeviceBuildRuntimeV3` / Gradle.
- Other engine paths remain unchanged. Full offline pack may still be installed intentionally for other technologies.
- Default local AndroidKeyStore signer. **Custom signing is not supported yet**; signing certificate stability and installer acceptance must be physically tested.
- Custom icon, AAB and advanced Android permission features are **explicitly refused**, not silently omitted.

## Acceptance status

- [x] Kotlin binary-manifest helper **host compiler syntax pass** (locally run `kotlinc`).
- [ ] Full Android Gradle compile (requires project build environment).
- [ ] AppForge host CI tests and artifact build.
- [ ] Real Samsung install test with a **disposable sample package**, signed APK verify, version, orientation, icon; negative scenarios.
- [ ] AppForge existing user data, existing projects and other engines preserved.
- [ ] Release approval and integration.

**Do not install, distribute, publish or merge based solely on source/static tests.**

Note: `apksig:9.1.1` is a candidate build-time dependency and must be resolved in CI. A modern AGP and Java toolchain will be needed to produce the embedded shell only *at AppForge application-build time*; the phone downloading tools is not required.

## Android Debug CI configuration failure (2026-10-11)

GitHub Actions run `38166458210` failed before Android source compilation at
`android-app/app/build.gradle.kts:497`: AGP 9.1 rejects a `Provider<Directory>`
passed directly to the legacy `AndroidSourceSet.assets.srcDir` API.
The feature-only repair resolves the generated assets location to `File`
(`.get().asFile`) and keeps the existing explicit `merge*Assets` dependency
on `generateFastHtmlTemplate` so task ordering is maintained. This is a
configuration compatibility fix, not evidence of Android compilation or
physical APK acceptance. The HTML WebView shell also retains DOM Storage
enabled for JavaScript pages without adding a native JavaScript bridge.
