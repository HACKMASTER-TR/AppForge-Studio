#!/usr/bin/env bash
# Opt-in host compile/link only. No runtime or production acceptance.
set -euo pipefail
root=$(pwd)
test "$(git branch --show-current)" = feat/v10-oss-unified-20261028
if [[ -n ${GITHUB_SHA:-} ]]; then test "$(git rev-parse HEAD)" = "$GITHUB_SHA"; fi
case "${CANDIDATE_ABI:-arm64-v8a}" in arm64-v8a) ;; *) exit 64;; esac
: "${RUNNER_TEMP:?isolated host temp required}"
: "${ANDROID_HOME:?verified Android SDK required}"
contract="$root/quality/fixtures/v10/phase12-abi-candidate/contract.py"
python3 "$contract" source "$root/examples/rust-expo-jsi"
test "$(gradle --version | sed -n 's/^Gradle //p')" = 8.14.3
ndk="$ANDROID_HOME/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin"
test -x "$ndk/aarch64-linux-android26-clang"
test -x "$ndk/llvm-readelf"
test -x "$ndk/llvm-nm"
rustc +1.85.1 --version
fixture=$(mktemp -d "$RUNNER_TEMP/phase12-1-arm64-XXXXXX")
cp -R "$root/examples/rust-expo-jsi/." "$fixture/"
cd "$fixture"
python3 "$contract" source "$fixture"
node - <<'JS'
const fs=require('fs');const p=JSON.parse(fs.readFileSync('app.json'));p.expo.newArchEnabled=true;
fs.writeFileSync('app.json',JSON.stringify(p,null,2)+'\n');
JS
npm ci --ignore-scripts --no-audit --no-fund
npm ci --offline --no-audit --no-fund
npx --no-install expo prebuild --platform android --no-install
grep -q '^newArchEnabled=true' android/gradle.properties
grep -q '^hermesEnabled=true' android/gradle.properties
python3 "$contract" prepare "$fixture" "$RUNNER_TEMP"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ndk/aarch64-linux-android26-clang"
cargo +1.85.1 build --locked --release --target aarch64-linux-android
mkdir -p android/app/src/main/jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/libappforge_jsi.so android/app/src/main/jniLibs/arm64-v8a/
gradle -p android :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:externalNativeBuildDebug --no-daemon --stacktrace -PreactNativeArchitectures=arm64-v8a
mapfile -t bridges < <(find android/app/build/intermediates/cxx/Debug -path '*/obj/arm64-v8a/libappmodules.so' -type f)
test "${#bridges[@]}" = 1
python3 "$contract" elf "$ndk/llvm-readelf" "$ndk/llvm-nm" target/aarch64-linux-android/release/libappforge_jsi.so "${bridges[0]}" "$fixture/abi-evidence" arm64-v8a
python3 "$contract" locks "$fixture"
sha256sum Cargo.lock package-lock.json src/lib.rs
printf '%s\n' 'ABI_LINK_ONLY; HERMES_RUNTIME=NOT_RUN; evidence retained in runner temp'
