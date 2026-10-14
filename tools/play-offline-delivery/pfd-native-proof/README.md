# AppForge V15.5 — standalone Play Feature Delivery proof

Research fixture only; **NOT compiled, not signed, not installed, not published**.
Does not include or modify the AppForge application, its keystores, user projects, Play Production or main.

## Question this experiment can answer
Can Google Play deliver an **on-demand dynamic feature** containing a standard Android NDK ARM64 JNI `.so`, and can that `.so` be loaded once downloaded?

This does **not** demonstrate that JDK, Gradle, Ubuntu/PRoot or Node binaries can be delivered/executed. Java JNI `System.loadLibrary` is a different operation from Linux ELF executable `execve`.

## Requirements (isolated CI or dev host only)
- JDK 17, Gradle 9.3.1, Android Gradle Plugin 9.1.1
- Android SDK platform 36 / Build-Tools 36.0.0
- Android NDK 28.2.13676358 and CMake 3.22.1
- Play Feature Delivery client 2.1.0

Official compatibility: https://developer.android.com/build/releases/agp-9-1-0-release-notes

## Build command on suitable CI host (NOT Termux)
`gradle --no-daemon :app:bundleDebug`

Test output path (if build succeeds): `app/build/outputs/bundle/debug/`.
Unsigned/debug artifact build is not evidence of Google Play on-demand delivery.

## Real delivery acceptance (requires separate package + test channel + explicit approval)
1. Inspect generated AAB manifest: module `nativeprobe` must have `dist:on-demand`, not install-time.
2. Install base from Google Play internal testing under a **separate application ID**.
3. Verify module initially absent, request `nativeprobe`, record session `INSTALLED`.
4. Open module Activity, verify `PFD_NATIVE_RESULT=APPFORGE_V155_PFD_NATIVE_PASS`.
5. Repeat with missing network, cancel, app update, storage pressure and app process death.
6. Check libraries are correctly ABI-specific for arm64-v8a.

No release, upload, or user-device install should happen without explicit authorization.

Google references:
- https://developer.android.com/guide/playcore/feature-delivery/on-demand
- https://developer.android.com/guide/playcore/feature-delivery
