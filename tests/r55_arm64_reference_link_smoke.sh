#!/usr/bin/env bash
# R55 research-only: real AArch64 host linker smoke in a throwaway GitHub runner.
# Does not run third-party installation scripts or alter an AppForge device.
set -Eeuo pipefail
umask 077

: "${RUNNER_TEMP:?Requires an ephemeral GitHub Actions runner}"
[[ "$(uname -m)" == "aarch64" ]] || { echo R55_ARM64_HOST=UNAVAILABLE_NOT_AARCH64; exit 2; }
for tool in curl sha1sum sha256sum unzip tar cmake ninja readelf file python3; do
  command -v "$tool" >/dev/null || { echo "R55_TOOL_MISSING=$tool"; exit 2; }
done
WORK="$(mktemp -d "$RUNNER_TEMP/r55-ref-XXXXXXXX")"
REPORT="$RUNNER_TEMP/r55-arm64-report.txt"
: > "$REPORT"
trap 'rm -rf -- "$WORK"' EXIT
log() { printf '%s\n' "$*" | tee -a "$REPORT"; }
show_error() { local label="$1" file="$2"; log "${label}=FAIL"; { grep -E 'error:|fatal error:|cannot open|library|No such file|not found|failed|ld.lld' "$file" 2>/dev/null || true; } | tail -n 12 | cut -c 1-300 | while IFS= read -r line; do log "${label}_ERROR=$line"; done; }
smoke_elf() {
  local label="$1" obj="$2" out="$3"
  if [[ ! -s "$obj" ]]; then log "${label}=FAIL_NO_ELF"; return 1; fi
  readelf -h "$obj" > "$WORK/$label.elf.txt" 2>&1 || { log "${label}=FAIL_ELF_HEADER"; return 1; }
  grep -Eq 'Machine: *AArch64' "$WORK/$label.elf.txt" || { log "${label}=FAIL_ARCH"; return 1; }
  grep -Eq 'Type: *DYN' "$WORK/$label.elf.txt" || { log "${label}=FAIL_SHARED_TYPE"; return 1; }
  readelf -lW "$obj" > "$WORK/$label.load.txt" || { log "${label}=FAIL_SEGMENTS"; return 1; }
  if ! python3 - "$WORK/$label.load.txt" <<'PY'
import re,sys
s=open(sys.argv[1],encoding='utf-8').read()
loads=[line.split() for line in s.splitlines() if line.strip().startswith('LOAD ')]
assert loads, 'NO_LOAD_SEGMENT'
assert all(int(parts[-1],16)>=0x4000 for parts in loads), 'BELOW_16K_ALIGNMENT'
PY
  then log "${label}=FAIL_PAGE_ALIGNMENT"; return 1; fi
  log "${label}=PASS_ARM64_ANDROID_DYN_16K"
}
log R55_ARM64_REFERENCE_SMOKE=START
log R55_EXECUTION_ENV=GITHUB_EPHEMERAL_UBUNTU_ARM64
log "R55_HOST_ARCH=$(uname -m)"
log R55_DEVICE_OR_APPFORGE_MUTATION=NONE
log R55_ANDROID_API=26
log R55_NDK_VERSION=27.1.12297006

# The ARM64 tar contains independently built HOST tools only. The official
# NDK zip supplies Android target headers/libraries. Never mix executable paths.
BASE_URL='https://github.com/zhuwanhong/android-sdk-linux-arm64/releases/download/v1.0.0'
NDK_ARM="$WORK/arm64-tools.tar.gz"
NDK_OFFICIAL="$WORK/android-ndk-r27b-linux.zip"
HERMES="$WORK/hermesc.tar.gz"
log R55_REFERENCE_ARM64_RELEASE=v1.0.0
if ! curl -fLsS --retry 3 --connect-timeout 30 --max-time 1200 \
  "$BASE_URL/android-ndk-27.1.12297006-linux-aarch64-ours.tar.gz" -o "$NDK_ARM"; then
  log R55_ARM64_TOOLS=UNAVAILABLE_DOWNLOAD; exit 2
fi
printf '%s  %s\n' '218286f50194da7470e20b4fad261cf05d7923cc8b81613ad09e118052595bb1' "$NDK_ARM" | sha256sum -c - >/dev/null || { log R55_ARM64_TOOLS=FAIL_DIGEST; exit 1; }
log R55_ARM64_TOOLS=VERIFIED_SHA256
if ! curl -fLsS --retry 3 --connect-timeout 30 --max-time 1200 \
  'https://dl.google.com/android/repository/android-ndk-r27b-linux.zip' -o "$NDK_OFFICIAL"; then
  log R55_OFFICIAL_NDK=UNAVAILABLE_DOWNLOAD; exit 2
fi
printf '%s  %s\n' '6fc476b2e57d7c01ac0c95817746b927035b9749' "$NDK_OFFICIAL" | sha1sum -c - >/dev/null || { log R55_OFFICIAL_NDK=FAIL_DIGEST; exit 1; }
log R55_OFFICIAL_NDK=VERIFIED_SHA1

mkdir -p "$WORK/ours" "$WORK/google" "$WORK/hermes"
# Verify relative archive paths before extraction; stay within ephemeral workdir.
python3 - "$NDK_ARM" <<'PY'
import sys,tarfile,pathlib
with tarfile.open(sys.argv[1], 'r:gz') as tar:
    for m in tar.getmembers():
        p=pathlib.PurePosixPath(m.name)
        if p.is_absolute() or '..' in p.parts or m.isdev(): raise SystemExit('UNSAFE_NDK_ARCHIVE_MEMBER')
        if m.issym() or m.islnk():
            t=pathlib.PurePosixPath(m.linkname)
            if t.is_absolute() or '..' in t.parts: raise SystemExit('UNSAFE_NDK_ARCHIVE_LINK')
PY
(cd "$WORK/ours" && tar -xzf "$NDK_ARM")
# Official archive is not executed. Extract only its target sysroot and revision metadata.
ZIP_ROOT='android-ndk-r27b/toolchains/llvm/prebuilt/linux-x86_64/sysroot/*'
(cd "$WORK/google" && unzip -oq "$NDK_OFFICIAL" "$ZIP_ROOT")
O="$WORK/google/android-ndk-r27b/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
T="$WORK/ours/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-aarch64"
[[ -f "$O/usr/include/unistd.h" ]] || { log R55_TARGET_SYSROOT=FAIL_NO_UNISTD; exit 1; }
for p in crtbegin_so.o crtend_so.o libc.so libdl.so; do
  [[ -f "$O/usr/lib/aarch64-linux-android/26/$p" ]] || { log "R55_TARGET_SYSROOT=FAIL_MISSING_$p"; exit 1; }
done
[[ -x "$T/bin/clang" && -x "$T/bin/clang++" ]] || { log R55_NATIVE_ARM64_CLANG=FAIL_MISSING; exit 1; }
log R55_TARGET_SYSROOT=PASS_HEADER_CRT_BIONIC
if ! "$T/bin/clang" --version > "$WORK/clang-version.txt" 2>&1; then
  show_error R55_NATIVE_ARM64_CLANG "$WORK/clang-version.txt"; exit 1
fi
log R55_NATIVE_ARM64_CLANG=EXECUTABLE_ON_ARM64

# Baseline: ask the real ARM64 NDK Clang to drive Android API26 linking. The
# NDK sysroot is explicit; neither R50/R51/R52 wrappers nor -nostartfiles used.
mkdir -p "$WORK/probe"
cat > "$WORK/probe/probe.c" <<'C'
#include <unistd.h>
int appforge_reference_probe(void) { return (int)getpid(); }
C
cat > "$WORK/probe/probe.cpp" <<'CPP'
#include <cstddef>
extern "C" int appforge_reference_cpp_probe(void) { return sizeof(void*) == 8 ? 7 : 0; }
CPP
CC="$T/bin/clang"
CXX="$T/bin/clang++"
COMMON=(--target=aarch64-linux-android26 --sysroot="$O" -B"$T/bin" -shared -fPIC -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384)
failed=0
if "$CC" "${COMMON[@]}" "$WORK/probe/probe.c" -o "$WORK/probe/libc_probe.so" >"$WORK/c.log" 2>&1; then
  smoke_elf R55_C_API26_LINK "$WORK/probe/libc_probe.so" "$REPORT" || failed=1
else show_error R55_C_API26_LINK "$WORK/c.log"; failed=1; fi
if "$CXX" "${COMMON[@]}" "$WORK/probe/probe.cpp" -o "$WORK/probe/libcpp_probe.so" >"$WORK/cpp.log" 2>&1; then
  smoke_elf R55_CPP_API26_LINK "$WORK/probe/libcpp_probe.so" "$REPORT" || failed=1
else show_error R55_CPP_API26_LINK "$WORK/cpp.log"; failed=1; fi

# Read-only driver route, with bounded excerpts. It does NOT build an ELF.
"$CC" -### "${COMMON[@]}" "$WORK/probe/probe.c" -o "$WORK/probe/print-only.so" >"$WORK/driver.log" 2>&1 || true
if grep -q 'ld.lld' "$WORK/driver.log"; then
  log R55_LLVM_DRIVER_ROUTE=FOUND
else log R55_LLVM_DRIVER_ROUTE=NOT_CONFIRMED; failed=1
fi

# CMake cross-build with a fixed, minimal toolchain. The full Android NDK
# CMake integration (official android.toolchain.cmake) is NOT claimed tested.
mkdir -p "$WORK/cmake-source"
cat > "$WORK/cmake-source/CMakeLists.txt" <<'CMAKE'
cmake_minimum_required(VERSION 3.22)
project(AppForgeR55Reference LANGUAGES C CXX)
add_library(appforge_c_reference SHARED probe.c)
add_library(appforge_cpp_reference SHARED probe.cpp)
CMAKE
cp "$WORK/probe/probe.c" "$WORK/probe/probe.cpp" "$WORK/cmake-source/"
cat > "$WORK/arm64-android26.cmake" <<CMAKE
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_SYSTEM_PROCESSOR aarch64)
set(CMAKE_C_COMPILER "$CC")
set(CMAKE_CXX_COMPILER "$CXX")
set(CMAKE_C_FLAGS_INIT "--target=aarch64-linux-android26 --sysroot=$O -B$T/bin")
set(CMAKE_CXX_FLAGS_INIT "--target=aarch64-linux-android26 --sysroot=$O -B$T/bin")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384")
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
CMAKE
if cmake -S "$WORK/cmake-source" -B "$WORK/cmake-build" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$WORK/arm64-android26.cmake" -DCMAKE_BUILD_TYPE=Release > "$WORK/cmake-config.log" 2>&1 && \
   cmake --build "$WORK/cmake-build" > "$WORK/cmake-build.log" 2>&1; then
  smoke_elf R55_CMAKE_C_API26_LINK "$WORK/cmake-build/libappforge_c_reference.so" "$REPORT" || failed=1
  smoke_elf R55_CMAKE_CPP_API26_LINK "$WORK/cmake-build/libappforge_cpp_reference.so" "$REPORT" || failed=1
else
  show_error R55_CMAKE_CONFIGURE "$WORK/cmake-config.log"
  [[ ! -f "$WORK/cmake-build.log" ]] || show_error R55_CMAKE_BUILD "$WORK/cmake-build.log"
  failed=1
fi
log R55_CMAKE_OFFICIAL_NDK_TOOLCHAIN=NOT_TESTED

# Rust cdylib into the same host Clang. Only a fixed AppForge-owned symbol;
# no third-party Rust crates and no project-supplied build scripts.
cat > "$WORK/probe/rust_probe.rs" <<'RUST'
#[no_mangle]
pub extern "C" fn Java_com_appforge_test_R55_probe() -> i32 { 55 }
RUST
cat > "$WORK/rust-linker.sh" <<RUSTLINK
#!/bin/sh
exec "$CC" --target=aarch64-linux-android26 --sysroot="$O" -B"$T/bin" "\$@"
RUSTLINK
chmod 0700 "$WORK/rust-linker.sh"
if command -v rustup >/dev/null && command -v rustc >/dev/null && rustup target add aarch64-linux-android > "$WORK/rustup.log" 2>&1; then
  if rustc "$WORK/probe/rust_probe.rs" --edition=2021 --crate-type cdylib \
       --target aarch64-linux-android -C linker="$WORK/rust-linker.sh" \
       -C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384 \
       -o "$WORK/probe/librust_probe.so" >"$WORK/rust.log" 2>&1; then
    smoke_elf R55_RUST_API26_LINK "$WORK/probe/librust_probe.so" "$REPORT" || failed=1
  else show_error R55_RUST_API26_LINK "$WORK/rust.log"; failed=1; fi
else log R55_RUST_API26_LINK=UNAVAILABLE_RUSTUP_OR_TARGET; fi

# Hermes is independently measured and does not prove a full Expo app build.
if curl -fLsS --retry 3 --connect-timeout 30 --max-time 180 \
  "$BASE_URL/hermesc-250829098.0.17-linux-aarch64.tar.gz" -o "$HERMES"; then
  if printf '%s  %s\n' 'a9551c476081a48cf668ca146ef6d7143e86c605a774e5d6be69da10d99c4764' "$HERMES" | sha256sum -c - >/dev/null; then
    (cd "$WORK/hermes" && tar -xzf "$HERMES")
    HERMESC="$(find "$WORK/hermes" -type f -name hermesc -perm /111 -print -quit)"
    if [[ -n "$HERMESC" ]]; then
      printf 'var appforgeR55Hermes = 55;\n' > "$WORK/probe/hermes.js"
      if "$HERMESC" -emit-binary -out "$WORK/probe/hermes.hbc" "$WORK/probe/hermes.js" > "$WORK/hermes.log" 2>&1 && [[ -s "$WORK/probe/hermes.hbc" ]]; then
        log R55_HERMES_BYTECODE=PASS_ARM64
      else show_error R55_HERMES_BYTECODE "$WORK/hermes.log"; failed=1; fi
    else log R55_HERMES_BYTECODE=UNAVAILABLE_BINARY; fi
  else log R55_HERMES_BYTECODE=FAIL_DIGEST; failed=1; fi
else log R55_HERMES_BYTECODE=UNAVAILABLE_DOWNLOAD; fi
log R55_EXPO_REACT_NATIVE_FULL_BUILD=NOT_TESTED

if [[ "$failed" -eq 0 ]]; then
  log R55_REFERENCE_LINK_RESULT=COMPLETED_WITH_REPORTED_LIMITATIONS
  log R55_ARM64_REFERENCE_SMOKE=END
else
  log R55_REFERENCE_LINK_RESULT=FAIL_ONE_OR_MORE_REAL_PROBES
  log R55_ARM64_REFERENCE_SMOKE=END
  exit 1
fi
