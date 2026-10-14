# AppForge V15.3 — Fast HTML output-aware routing (local candidate)

**Status:** isolated local candidate, not committed, pushed, merged, built or installed.

## Route policy

- Local HTML / static webview APK remains the fast path.
- Local static HTML AAB, APK+AAB, and `android` retain the V15.x fast AAB path, signing rules and fail-closed cleanup.
- Windows Portable EXE, aliases (`exe`, `windows-exe`, `portable-exe`, `windows-portable-exe`) and combined Android+Windows (`all`, `apk+aab+exe`) bypass the fast Android route and enter the existing normal build engine. They still require the installed toolchain and validated Windows Portable Host.
- Windows Native EXE aliases bypass the fast route. The existing local C/C++/CMake input requirement, experimental status, PE verification, and signing rules remain unchanged; HTML sources cannot be silently treated as native C++.
- Other engines (`node-web`, Expo, Gradle, Python, Universal) retain their existing output capability checks and toolchain requirements.
- Unknown requested output is rejected; there is no arbitrary fallback or automatic new artifact type.
- Shared `FastHtmlApkPolicy.isCandidate` is called by UI preflight, engine routing, and Android-only error cleanup. Every call is now output-aware, preventing phantom missing-toolchain navigation and silent incorrect artifact outputs.

## Evidence and acceptance limits

- Added Kotlin scenario matrix across Android-only, Windows-only, mixed, and output aliases; JS source-contract guard plus original quality suite.
- Second Brain V2 derivative updated, never weakened or bypassed.
- Local source/Node tests do not prove actual Windows x64 EXE outputs on the target device or Windows launch; hosted Android CI and separate physical acceptance are required.
- Existing installed AppForge 534, prior V15.1.1/V15.2 worktrees, main, keys, projects, Play and releases are out of scope.
