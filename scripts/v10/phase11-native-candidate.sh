#!/usr/bin/env bash
# Isolated, unsigned compile probe. Success is NOT runtime/registration acceptance.
set -euo pipefail
mode=${1:?rn-cli or sdk54-newarch required}
case "$mode" in rn-cli) example=rust-react-native-turbo;; sdk54-newarch) example=rust-expo-jsi;; *) exit 64;; esac
root=$(pwd)
: "${RUNNER_TEMP:?host runner temp required}"
fixture=$(mktemp -d "$RUNNER_TEMP/phase11-$mode-XXXXXX")
cp -R "examples/$example/." "$fixture/"
cd "$fixture"
if [ "$mode" = rn-cli ]; then
  node - <<'JS'
const fs=require('fs');const p=JSON.parse(fs.readFileSync('package.json'));
for(const k of ['@react-native-community/cli','@react-native-community/cli-platform-android']) p.devDependencies[k]='20.2.0';
fs.writeFileSync('package.json',JSON.stringify(p,null,2)+'\n');
JS
  npm install --package-lock-only --ignore-scripts --no-audit --no-fund
  # Refuse resolver drift from the locally reviewed full candidate lock.
  echo '158b8207452fa933a61e1397176b9131765bbc2ee400d510436784aa6df34846  package-lock.json' | sha256sum -c -
else
  node - <<'JS'
const fs=require('fs');const p=JSON.parse(fs.readFileSync('app.json'));p.expo.newArchEnabled=true;
fs.writeFileSync('app.json',JSON.stringify(p,null,2)+'\n');
JS
fi
npm ci --ignore-scripts --no-audit --no-fund
npm ci --offline --no-audit --no-fund
node - <<'JS'
const assert=require('assert/strict');
assert.equal(require('./node_modules/react/package.json').version,'19.1.0');
assert.equal(require('./node_modules/react-native/package.json').version,'0.81.5');
JS
if [ "$mode" = sdk54-newarch ]; then
  npx --no-install expo prebuild --platform android --no-install
  grep -q 'newArchEnabled=true' android/gradle.properties
  grep -q 'add(com.appforge.rustjsi.RustJsiPackage())' android/app/src/main/java/com/example/rustjsi/MainApplication.kt
  cmp native/RustJsiModule.java android/app/src/main/java/com/appforge/rustjsi/RustJsiModule.java
  cmp native/RustJsi.cpp android/app/src/main/cpp/RustJsi.cpp
  npx --no-install expo export --platform android --max-workers 2
  test -n "$(find dist -name '*.hbc' -print -quit)"
else
  node "$root/quality/tests/v10_phase11_server_runtime.cjs" "$fixture"
  npx --no-install react-native config > "$fixture/config.json"
  npx --no-install react-native bundle --platform android --dev false --entry-file index.js --bundle-output "$fixture/index.android.bundle" --max-workers 2
  node_modules/react-native/sdks/hermesc/linux64-bin/hermesc -emit-binary -out "$fixture/index.android.hbc" "$fixture/index.android.bundle"
  test -s "$fixture/index.android.hbc"
fi
# Build exact authored Rust library for imported CMake dependency; no cargo-ndk install.
export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$ANDROID_HOME/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang"
cargo +1.85.1 build --locked --release --target aarch64-linux-android
mkdir -p android/app/src/main/jniLibs/arm64-v8a
cp target/aarch64-linux-android/release/libappforge_jsi.so android/app/src/main/jniLibs/arm64-v8a/
# Compile only: no assemble/package/install/signing task.
gradle -p android :app:compileDebugKotlin :app:compileDebugJavaWithJavac :app:externalNativeBuildDebug --no-daemon --stacktrace -PreactNativeArchitectures=arm64-v8a
printf '%s\n' 'COMPILE_ONLY_COMPLETE; HERMES_NATIVE_REGISTRATION_RUNTIME=NOT_RUN'
