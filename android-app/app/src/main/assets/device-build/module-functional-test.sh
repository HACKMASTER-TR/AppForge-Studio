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
  PATH="$ROOT/rust-1.85.1/bin:$PATH" "$ROOT/rust-1.85.1/bin/cargo" build --offline --manifest-path "$WORK/Cargo.toml"
  [ "$("$WORK/target/debug/offline_probe")" = 42 ]
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
