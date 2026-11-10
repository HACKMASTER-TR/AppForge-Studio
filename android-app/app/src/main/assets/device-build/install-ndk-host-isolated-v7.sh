#!/bin/sh
# V8: signed, pinned Ubuntu ARM64 host tools with explicit glibc loader.
# NEVER apt-install, dpkg-install or overwrite shared libc. Fail closed on any unverified byte.
set -eu
[ "${APPFORGE_MODULE_ID:-}" = RUST_ANDROID_NDK ] || exit 60
[ "${APPFORGE_APT_MANAGED_INSTALL:-0}" = 1 ] || exit 60
[ "${APPFORGE_R2_RELEASE_AUTHORIZED:-0}" = 1 ] || exit 60
[ "$(dpkg --print-architecture)" = arm64 ] || exit 60
[ "$(dpkg-query -W -f='${Version}' libc6)" = '2.39-0ubuntu8.7' ] || {
  echo 'APPFORGE_NDK_HOST_BASELINE_UNSUPPORTED: libc6 version changed' >&2;exit 62;
}

CATALOG=/workspace/runtime/apt-module-packages-v1.tsv
EXPECTED_CATALOG=77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f
printf '%s  %s\n' "$EXPECTED_CATALOG" "$CATALOG" | sha256sum -c - >/dev/null || exit 63
. /workspace/runtime/apt-snapshot-lock.sh
as_configure || exit 63
as_verify_indices || { echo 'APPFORGE_NDK_HOST_SIGNED_INDEX_INVALID' >&2;exit 63; }

PREFIX=/opt/appforge-device/ndk-host-v7
ROOT="$PREFIX/sysroot"
BIN="$PREFIX/bin"
STAMP="$PREFIX/verified.tsv"
if [ -f "$STAMP" ];then
  [ ! -L "$STAMP" ] && [ -x "$BIN/clang" ] && [ -x "$BIN/cmake" ] || exit 63
  "$BIN/clang" --version >/dev/null && "$BIN/cmake" --version >/dev/null || exit 63
  echo APPFORGE_NDK_HOST_REUSED=PASS
  exit 0
fi
[ ! -e "$PREFIX" ] && [ ! -L "$PREFIX" ] || {
  echo 'APPFORGE_NDK_HOST_PARTIAL_OR_UNTRUSTED_PREFIX' >&2;exit 63;
}
# The extracted signed LLVM/CMake host payload is much larger than its .deb cache.
# Avoid running out of disk mid-transaction; the existing generation stays active.
free_kb=$(df -Pk /opt/appforge-device | awk 'END{print $4}')
[ "$free_kb" -ge 1048576 ] || {
  echo 'APPFORGE_NDK_HOST_PRIVATE_STAGE_REQUIRES_1GIB_FREE' >&2;exit 64;
}
STAGE="$PREFIX.stage.$$"
[ ! -e "$STAGE" ] && [ ! -L "$STAGE" ] || exit 63
mkdir -p "$STAGE/sysroot" "$STAGE/bin"
trap 'if [ -d "$STAGE" ] && [ ! -L "$STAGE" ];then rm -rf -- "$STAGE";fi' 0 HUP INT TERM

# Take ONLY the existing 73 Ubuntu-signed EXPO_REACT_NATIVE/arm64 catalog objects.
# Four glibc packages are deliberately omitted from the private prefix. The
# already installed 2.39-0ubuntu8.7 remains the sole host dynamic C runtime.
TAB=$(printf '\t')
count=0
skipped=0
awk -F '\t' '$1=="EXPO_REACT_NATIVE" && $2=="arm64"{print $3 "\t" $4 "\t" $5 "\t" $6 "\t" $7 "\t" $8}' "$CATALOG" \
  > "$STAGE/selected.tsv"
[ "$(wc -l < "$STAGE/selected.tsv" | tr -d ' ')" = 73 ] || {
  echo 'APPFORGE_NDK_HOST_CATALOG_ROW_COUNT_CHANGED' >&2;exit 63;
}
# Pre-verify every input BEFORE extracting even one byte.
while IFS="$TAB" read -r pkg ver arch sha size url;do
  case "$pkg" in ''|*[!a-zA-Z0-9+.-]*) exit 64;;esac
  case "$sha" in *[!a-f0-9]*) exit 64;;esac
  [ "${#sha}" = 64 ] || exit 64
  case "$size" in ''|*[!0-9]*) exit 64;;esac
  case "$url" in https://snapshot.ubuntu.com/ubuntu/20261018T000000Z/pool/*) ;;*) exit 64;;esac
  file="$as_store/archives/$sha.deb"
  [ -f "$file" ] && [ ! -L "$file" ] && [ "$(wc -c < "$file" | tr -d ' ')" = "$size" ] || {
     echo "APPFORGE_NDK_HOST_PINNED_OBJECT_MISSING:$pkg" >&2;exit 64;
  }
  printf '%s  %s\n' "$sha" "$file" | sha256sum -c - >/dev/null || exit 64
  [ "$(dpkg-deb -f "$file" Package)" = "$pkg" ] || exit 64
  [ "$(dpkg-deb -f "$file" Version)" = "$ver" ] || exit 64
  [ "$(dpkg-deb -f "$file" Architecture)" = "$arch" ] || exit 64
  count=$((count+1))
done < "$STAGE/selected.tsv"
[ "$count" = 73 ] || exit 64

# No change whatsoever to /usr, /lib, /var/lib/dpkg or existing module locks.
# Only reviewed package data payloads are unpacked into a new private prefix.
while IFS="$TAB" read -r pkg ver arch sha size url;do
  case "$pkg" in
    libc6|libc-bin|libc6-dev|libc-dev-bin)
      skipped=$((skipped+1));continue;;
  esac
  dpkg-deb -x "$as_store/archives/$sha.deb" "$STAGE/sysroot" || exit 65
  printf '%s\t%s\t%s\n' "$pkg" "$ver" "$sha" >> "$STAGE/verified.tsv"
done < "$STAGE/selected.tsv"
[ "$skipped" = 4 ] && [ "$(wc -l < "$STAGE/verified.tsv" | tr -d ' ')" = 69 ] || exit 65

# V8: libedit2's signed package provides the real ARM64 ELF and its SONAME
# symlink. Ensure the loader can open an ordinary file at the exact SONAME,
# inside this PRIVATE stage only. Do not create /usr or /lib host aliases.
LIBDIR="$STAGE/sysroot/usr/lib/aarch64-linux-gnu"
LIBEDIT_REAL="$LIBDIR/libedit.so.2.0.72"
LIBEDIT_SONAME="$LIBDIR/libedit.so.2"
[ -f "$LIBEDIT_REAL" ] && [ ! -L "$LIBEDIT_REAL" ] || {
  echo 'APPFORGE_NDK_LIBEDIT_SIGNED_PAYLOAD_MISSING' >&2;exit 66;
}
[ -L "$LIBEDIT_SONAME" ] || {
  echo 'APPFORGE_NDK_LIBEDIT_SONAME_LINK_MISSING' >&2;exit 66;
}
[ "$(readlink "$LIBEDIT_SONAME")" = libedit.so.2.0.72 ] || {
  echo 'APPFORGE_NDK_LIBEDIT_SONAME_LINK_UNEXPECTED' >&2;exit 66;
}
file -L "$LIBEDIT_REAL" | grep -Eqi 'aarch64|ARM aarch64' || {
  echo 'APPFORGE_NDK_LIBEDIT_ARM64_ABI_MISMATCH' >&2;exit 66;
}
# The bytes were verified against the pinned Ubuntu archive before extract.
# Replace only this private link with a byte-identical regular file.
cp -p "$LIBEDIT_REAL" "$LIBDIR/.libedit.so.2.v8.$$"
cmp -s "$LIBEDIT_REAL" "$LIBDIR/.libedit.so.2.v8.$$" || exit 66
mv -f "$LIBDIR/.libedit.so.2.v8.$$" "$LIBEDIT_SONAME"
[ -f "$LIBEDIT_SONAME" ] && [ ! -L "$LIBEDIT_SONAME" ] || exit 66
printf 'APPFORGE_NDK_LIBEDIT_PRIVATE_SONAME=PASS\n'

# Wrapper resolves only executables extracted from the verified Ubuntu payload.
# Child processes see the same isolated library path, but never an alternate libc.
cat > "$STAGE/bin/host-tool" <<'WRAPPER'
#!/bin/sh
set -eu
name=${1:?host tool required};shift
prefix=/opt/appforge-device/ndk-host-v7
sysroot="$prefix/sysroot"
case "$name" in
 clang|clang++|ld.lld|llvm-ar|llvm-ranlib|llvm-strip|llvm-nm|llvm-objcopy|llvm-readelf)
  executable="$sysroot/usr/lib/llvm-18/bin/$name";;
 cmake|ninja)
  executable="$sysroot/usr/bin/$name";;
 *) echo 'APPFORGE_NDK_HOST_TOOL_NOT_ALLOWED' >&2;exit 66;;
esac
[ -x "$executable" ] || { echo "APPFORGE_NDK_HOST_EXE_MISSING:$name" >&2;exit 66; }
# Use the verified host glibc loader through the ELF PT_INTERP normally.
# Invoking ld.so as a command changes /proc/self/exe to the loader; that
# breaks CMake module discovery and Clang subprocess/path resolution.
# The V8 verified, private libedit.so.2 SONAME copy remains mandatory.
interpreter=/lib/ld-linux-aarch64.so.1
[ -x "$interpreter" ] || { echo 'APPFORGE_NDK_HOST_GLIBC_LOADER_MISSING' >&2;exit 66; }
libpath="$sysroot/usr/lib/llvm-18/lib:$sysroot/usr/lib/aarch64-linux-gnu:$sysroot/lib/aarch64-linux-gnu"
[ -f "$sysroot/usr/lib/aarch64-linux-gnu/libedit.so.2" ] || {
 echo 'APPFORGE_NDK_HOST_LIBEDIT_MISSING_AT_RUNTIME' >&2;exit 66;
}
export LD_LIBRARY_PATH="$libpath"
export PATH="$prefix/bin:$PATH"
exec "$executable" "$@"
WRAPPER
chmod 0755 "$STAGE/bin/host-tool"
for name in clang clang++ ld.lld llvm-ar llvm-ranlib llvm-strip llvm-nm llvm-objcopy llvm-readelf cmake ninja;do
  cat > "$STAGE/bin/$name" <<EOF
#!/bin/sh
exec /bin/sh /opt/appforge-device/ndk-host-v7/bin/host-tool "$name" "\$@"
EOF
  chmod 0755 "$STAGE/bin/$name"
done

# Move as one unit, making the fixed-prefix launchers resolvable, and
# refuse READY until actual tool execution succeeds on this physical host.
[ ! -e "$PREFIX" ] || exit 66
mv -T "$STAGE" "$PREFIX"
trap - 0 HUP INT TERM
if ! "$BIN/clang" --version >/dev/null ||
   ! "$BIN/ld.lld" --version >/dev/null ||
   ! "$BIN/cmake" --version >/dev/null ||
   ! "$BIN/ninja" --version >/dev/null ||
   ! "$BIN/llvm-ar" --version >/dev/null;then
  echo 'APPFORGE_NDK_HOST_EXECUTION_FAILED: signed ARM64 toolchain retained in staging for diagnosis; installation blocked' >&2
  # Report file types and symlink targets, not file contents or secrets.
  if [ -L "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/libedit.so.2" ];then
    echo 'APPFORGE_NDK_LIBEDIT_RUNTIME=SYMLINK' >&2
  elif [ -f "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/libedit.so.2" ];then
    echo 'APPFORGE_NDK_LIBEDIT_RUNTIME=REGULAR_FILE' >&2
  else
    echo 'APPFORGE_NDK_LIBEDIT_RUNTIME=MISSING' >&2
  fi
  exit 67
fi
# Smoke-test real CMake modules and actual Clang reading a source file.
# --version is insufficient: CMake may report a missing CMAKE_ROOT and exit 0.
[ -s "$ROOT/usr/share/cmake-3.28/Modules/CMakeDetermineSystem.cmake" ] || {
  echo 'APPFORGE_NDK_HOST_CMAKE_MODULES_MISSING' >&2;exit 68;
}
probe_dir=$(mktemp -d /tmp/appforge-ndk-v9.XXXXXXXX) || exit 68
trap 'rm -rf -- "$probe_dir"' 0 HUP INT TERM
cat > "$probe_dir/check.cmake" <<'CMAKE'
if(NOT EXISTS "${CMAKE_ROOT}/Modules/CMakeDetermineSystem.cmake")
  message(FATAL_ERROR "APPFORGE_NDK_HOST_CMAKE_ROOT_INVALID")
endif()
CMAKE
printf 'int appforge_ndk_host_source(void) { return 42; }\n' > "$probe_dir/readable.c"
[ -s "$probe_dir/readable.c" ] || exit 68
if ! "$BIN/cmake" -P "$probe_dir/check.cmake";then
  echo 'APPFORGE_NDK_HOST_CMAKE_ROOT_PROBE_FAILED' >&2;exit 68
fi
printf 'APPFORGE_NDK_HOST_CMAKE_MODULES=PASS\n'
if ! "$BIN/clang" --target=aarch64-unknown-linux-gnu -x c -fsyntax-only "$probe_dir/readable.c";then
  echo 'APPFORGE_NDK_HOST_SOURCE_READ_PROBE_FAILED' >&2;exit 69
fi
printf 'APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS\n'
rm -rf -- "$probe_dir"
trap - 0 HUP INT TERM
[ "$(dpkg-query -W -f='${Version}' libc6)" = '2.39-0ubuntu8.7' ] || exit 67
printf 'APPFORGE_NDK_HOST_DIRECT_ELF_EXEC=PASS\n'
printf 'APPFORGE_NDK_HOST_PRIVATE_TOOLS=PASS\nAPPFORGE_NDK_HOST_GLIBC_2_39_8_7_PRESERVED=PASS\nAPPFORGE_NDK_HOST_SIGNED_PACKAGES=69\n'
