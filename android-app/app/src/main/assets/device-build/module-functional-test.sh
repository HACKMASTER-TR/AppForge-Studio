#!/bin/sh
# Installed tools must perform real local work, not merely print versions.
# No user project, signing key, network resolver or mutable project cache is used.
set -eu
MODULE=${1:?module required}
WORK=$(mktemp -d /tmp/appforge-functional.XXXXXXXX)
trap 'rm -rf "$WORK"' EXIT HUP INT TERM
export APPFORGE_DEVICE_OFFLINE=1 GRADLE_USER_HOME=/root/.gradle-appforge
ROOT=/opt/appforge-device
case "$MODULE" in
 NODE_WEB)
  "$ROOT/node-22.23.3/bin/node" - "$WORK" <<'JS'
const fs = require('node:fs'); const crypto = require('node:crypto'); const assert = require('node:assert/strict');
const file = process.argv[2] + '/roundtrip.json';
fs.writeFileSync(file, JSON.stringify({answer: 6 * 7}));
assert.equal(JSON.parse(fs.readFileSync(file)).answer, 42);
assert.equal(crypto.createHash('sha256').update('abc').digest('hex'), 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');
assert.equal(process.versions.node, '22.23.3');
JS
  ;;
 PYTHON_ANDROID)
  /usr/bin/python3.12 -I - "$WORK" <<'PY'
import hashlib, json, pathlib, sqlite3, ssl, sys, zlib
assert sys.version_info[:2] == (3, 12)
p = pathlib.Path(sys.argv[1]) / 'roundtrip.json'
p.write_text(json.dumps({'answer': 6 * 7}))
assert json.loads(p.read_text())['answer'] == 42
with sqlite3.connect(':memory:') as db:
    assert db.execute('select 6 * 7').fetchone()[0] == 42
assert zlib.decompress(zlib.compress(b'offline')) == b'offline'
assert hashlib.sha256(b'abc').hexdigest() == 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad'
ssl.create_default_context()  # Initialize real TLS trust without opening a socket.
PY
  # Chaquopy's actual offline APK build is performed by prepare-offline-pack.sh python.
  ;;
 ANDROID_CORE)
  printf '%s\n' 'public final class OfflineProbe { public static void main(String[] a) { if (6*7 != 42) throw new AssertionError(); System.out.print("42"); } }' > "$WORK/OfflineProbe.java"
  "$ROOT/jdk-17/bin/javac" -d "$WORK" "$WORK/OfflineProbe.java"
  [ "$("$ROOT/jdk-17/bin/java" -cp "$WORK" OfflineProbe)" = 42 ]
  mkdir -p "$WORK/res/values"
  printf '%s\n' '<resources><string name="app_name">Offline probe</string></resources>' > "$WORK/res/values/strings.xml"
  "$ROOT/android-sdk/build-tools/36.0.0/aapt2" compile --dir "$WORK/res" -o "$WORK/resources.zip"
  [ -s "$WORK/resources.zip" ]
  ;;
 RUST)
  mkdir -p "$WORK/src"
  printf '%s\n' '[package]' 'name = "offline_probe"' 'version = "0.1.0"' 'edition = "2021"' > "$WORK/Cargo.toml"
  printf '%s\n' 'fn main() { assert_eq!(6*7,42); println!("42"); }' > "$WORK/src/main.rs"
  RUST_BIN="$ROOT/rust-1.85.1/bin"
  if [ ! -x "$RUST_BIN/rustc" ]; then echo 'APPFORGE_RUSTC_MISSING_OR_NOT_EXECUTABLE' >&2; exit 66; fi
  if ! "$RUST_BIN/rustc" -vV >/dev/null; then echo 'APPFORGE_RUSTC_EXECUTION_FAILED' >&2; exit 67; fi
  # This module promises the official host Rust compiler/standard library and
  # wasm32-unknown-unknown target. It does NOT promise a glibc-native host
  # executable linker: the V4 Clang path would have upgraded locked libc6.
  # Prove genuine native ELF code generation without a C linker, then prove
  # Cargo+rust-lld can link a real WebAssembly artifact offline.
  rust_host=$("$RUST_BIN/rustc" -vV | sed -n 's/^host: //p')
  case "$rust_host" in
    aarch64-unknown-linux-gnu|x86_64-unknown-linux-gnu) ;;
    *) echo 'APPFORGE_RUST_HOST_UNSUPPORTED' >&2; exit 68 ;;
  esac
  printf '%s\n' 'pub fn appforge_native_answer() -> i32 { 6 * 7 }' > "$WORK/native.rs"
  "$RUST_BIN/rustc" --crate-name appforge_native_probe --edition=2021 \
      --crate-type=lib --target "$rust_host" --emit=obj \
      -o "$WORK/native.o" "$WORK/native.rs"
  [ -s "$WORK/native.o" ] || { echo 'APPFORGE_RUST_HOST_OBJECT_MISSING' >&2; exit 69; }
  [ "$(head -c 4 "$WORK/native.o" | od -An -tx1 | tr -d ' \n')" = '7f454c46' ] || {
    echo 'APPFORGE_RUST_HOST_OBJECT_NOT_ELF' >&2; exit 69;
  }
  rm -f "$WORK/src/main.rs"  # Discard previous host-binary Cargo target.
  printf '%s\n' '[package]' 'name = "offline_probe"' 'version = "0.1.0"' \
      'edition = "2021"' '[lib]' 'crate-type = ["cdylib"]' > "$WORK/Cargo.toml"
  printf '%s\n' '#[no_mangle] pub extern "C" fn appforge_answer() -> i32 { 6 * 7 }' > "$WORK/src/lib.rs"
  RUSTC="$RUST_BIN/rustc" CARGO_NET_OFFLINE=true PATH="$RUST_BIN:$PATH" \
      "$RUST_BIN/cargo" generate-lockfile --offline --manifest-path "$WORK/Cargo.toml"
  RUSTC="$RUST_BIN/rustc" CARGO_NET_OFFLINE=true PATH="$RUST_BIN:$PATH" \
      "$RUST_BIN/cargo" build --frozen --offline --release \
      --target wasm32-unknown-unknown --manifest-path "$WORK/Cargo.toml"
  wasm="$WORK/target/wasm32-unknown-unknown/release/offline_probe.wasm"
  [ -s "$wasm" ] || { echo 'APPFORGE_RUST_WASM_OUTPUT_MISSING' >&2; exit 69; }
  [ "$(head -c 8 "$wasm" | od -An -tx1 | tr -d ' \n')" = '0061736d01000000' ] || {
    echo 'APPFORGE_RUST_WASM_HEADER_INVALID' >&2; exit 69;
  }
  # The second frozen/offline build confirms a real cache replay.
  RUSTC="$RUST_BIN/rustc" CARGO_NET_OFFLINE=true PATH="$RUST_BIN:$PATH" \
      "$RUST_BIN/cargo" build --frozen --offline --release \
      --target wasm32-unknown-unknown --manifest-path "$WORK/Cargo.toml"
  echo 'APPFORGE_RUST_HOST_NATIVE_OBJECT=PASS'
  echo 'APPFORGE_RUST_CARGO_WASM_LINK=PASS'
  echo 'APPFORGE_RUST_HOST_ELF_EXECUTION=NOT_TESTED_LINKER_NOT_BUNDLED'
  ;;
 RUST_ANDROID_NDK|EXPO_REACT_NATIVE)
  printf '%s\n' 'int appforge_offline_answer(void) { return 42; }' > "$WORK/probe.c"
  "$ROOT/android-sdk/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" --target=aarch64-linux-android26 -shared -fPIC "$WORK/probe.c" -o "$WORK/libprobe.so"
  [ -s "$WORK/libprobe.so" ]
  # This is a real Android native compilation, not an Expo project/phone execution claim.
  ;;
 WINDOWS_NATIVE)
  printf '%s\n' 'int main(void) { return 6*7 == 42 ? 0 : 1; }' > "$WORK/probe.c"
  /usr/bin/x86_64-w64-mingw32-gcc-posix "$WORK/probe.c" -o "$WORK/probe.exe"
  [ "$(head -c 2 "$WORK/probe.exe")" = MZ ]
  # PE generation is covered; execution requires a Windows machine.
  ;;
 *) echo "Unsupported functional module: $MODULE" >&2; exit 60;;
esac
printf 'APPFORGE_FUNCTIONAL_PASS:%s\n' "$MODULE"
