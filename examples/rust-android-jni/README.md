# Explicit Rust → Android Gradle JNI example

The authored Java Activity calls the exact exported Rust JNI symbol. AppForge does
not inject bridges. This example targets ARM64/API26+ only. Use `both` for APK+AAB.

1. Install and verify Rust + Cargo. An existing verified Android toolchain must
   also provide a host-compatible NDK linker for ARM64/API26. AppForge does not
   silently install Expo or declare the Google x86_64 linker Android-ARM64-ready.
2. Select this local project with Android Gradle engine; set package id to
   `com.example.rustjni`. Explicitly approve locked Cargo/target downloads.
3. Turn off all Android networks; separately approve trusted source execution.
   AppForge uses private runtime/compiler/source copies. It builds twice with
   Cargo `--frozen --offline`, validates the ELF/JNI exports and publishes only a
   source/artifact consistency receipt. This is not device runtime acceptance.
4. Explicitly select the prepared project copy. Prepare Gradle dependencies with
   the existing separate approval/cache flow; an installed SDK alone is not proof
   that project dependencies are cached. Run a real offline Android build.
5. The engine stages the verified library into its build copy and checks exact
   APK/AAB payload hashes. `keepDebugSymbols` is deliberately configured here;
   silent native stripping/substitution is rejected. Real minSdk is checked.

The lockfile has no external crate dependencies. Local host tests produced a
real ARM64 ELF and APK/AAB with the NDK 28.2 x86_64 host linker. Installation and
JNI execution on Android16 ARM64, FGS/network recovery, and screenshots are
NOT_RUN. New native targets do not imply PyO3, RN/JSI or Windows support.
