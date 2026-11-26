#!/bin/sh
# V8: signed, pinned Ubuntu ARM64 host tools with explicit glibc loader.
# NEVER apt-install, dpkg-install or overwrite shared libc. Fail closed on any unverified byte.
set -eu
case "${APPFORGE_MODULE_ID:-}" in RUST_ANDROID_NDK|EXPO_REACT_NATIVE) ;; *) exit 60;; esac
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
# CMake's signed Ubuntu dependency libarchive13t64 is present in the 73
# verified package payloads. The pinned PRoot loader cannot always follow its
# SONAME symlink. Materialize ONLY the package-authenticated bytes, in this
# private stage, without touching the shared /usr or libc6.
LIBARCHIVE_REAL="$LIBDIR/libarchive.so.13.7.2"
LIBARCHIVE_SONAME="$LIBDIR/libarchive.so.13"
[ -f "$LIBARCHIVE_REAL" ] && [ ! -L "$LIBARCHIVE_REAL" ] || {
  echo 'APPFORGE_NDK_LIBARCHIVE_SIGNED_PAYLOAD_MISSING' >&2;exit 66;
}
[ -L "$LIBARCHIVE_SONAME" ] &&
[ "$(readlink "$LIBARCHIVE_SONAME")" = libarchive.so.13.7.2 ] || {
  echo 'APPFORGE_NDK_LIBARCHIVE_UNEXPECTED_SONAME' >&2;exit 66;
}
file -L "$LIBARCHIVE_REAL" | grep -Eqi 'aarch64|ARM aarch64' || {
  echo 'APPFORGE_NDK_LIBARCHIVE_ARM64_ABI_MISMATCH' >&2;exit 66;
}
cp -p "$LIBARCHIVE_REAL" "$LIBDIR/.libarchive.so.13.v540.$$"
cmp -s "$LIBARCHIVE_REAL" "$LIBDIR/.libarchive.so.13.v540.$$" || exit 66
mv -f "$LIBDIR/.libarchive.so.13.v540.$$" "$LIBARCHIVE_SONAME"
[ -f "$LIBARCHIVE_SONAME" ] && [ ! -L "$LIBARCHIVE_SONAME" ] || exit 66
printf 'APPFORGE_NDK_LIBARCHIVE_PRIVATE_SONAME=PASS\n'

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
# This pinned PRoot linker cannot resolve private libedit on ordinary ELF exec.
# V8 proved explicit glibc --library-path locates the verified ARM64 libraries.
# Preserve guest self-executable introspection for CMake and Clang via the
# PRoot guest executable override, without modifying shared /usr or libc6.
interpreter=/lib/ld-linux-aarch64.so.1
[ -x "$interpreter" ] || { echo 'APPFORGE_NDK_HOST_GLIBC_LOADER_MISSING' >&2;exit 66; }
libpath="$sysroot/usr/lib/llvm-18/lib:$sysroot/usr/lib/aarch64-linux-gnu:$sysroot/lib/aarch64-linux-gnu"
[ -f "$sysroot/usr/lib/aarch64-linux-gnu/libedit.so.2" ] || {
 echo 'APPFORGE_NDK_HOST_LIBEDIT_MISSING_AT_RUNTIME' >&2;exit 66;
}
[ -f "$sysroot/usr/lib/aarch64-linux-gnu/libarchive.so.13" ] &&
[ ! -L "$sysroot/usr/lib/aarch64-linux-gnu/libarchive.so.13" ] || {
 echo 'APPFORGE_NDK_HOST_LIBARCHIVE_PRIVATE_RUNTIME_MISSING' >&2;exit 66;
}
export LD_LIBRARY_PATH="$libpath"
export PATH="$prefix/bin:$PATH"
export PROROOT_GUEST_EXE="$executable"
# R41: only the trusted, tested guest-native Clang is allowed to read guest paths.
# Do not change CMake, other LLVM tools, global glibc or existing module files.
if [ "$name" = clang ] && [ -f "$prefix/clang-launch-mode-r41" ];then
  [ "$(cat "$prefix/clang-launch-mode-r41")" = guest-clang-direct ] || exit 66
  [ -f /usr/lib/llvm-18/bin/clang ] && [ ! -L /usr/lib/llvm-18/bin/clang ] || exit 66
  unset PROROOT_GUEST_EXE CMAKE_ROOT
  export LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/usr/lib/llvm-18/lib:/lib/aarch64-linux-gnu:$libpath"
  exec /usr/lib/llvm-18/bin/clang "$@"
fi
# R47: the private glibc-loader clang++ cannot read guest source paths.
# Use the R41/R42 byte-verified, source-reading guest Clang ELF in its real
# C++ driver mode. No shared /usr/libc mutation and no replacement compiler.
if [ "$name" = clang++ ] && [ -f "$prefix/clang-launch-mode-r41" ];then
  [ ! -L "$prefix/clang-launch-mode-r41" ] &&
  [ "$(cat "$prefix/clang-launch-mode-r41")" = guest-clang-direct ] || exit 66
  r47_clang_guest=/usr/lib/llvm-18/bin/clang
  r47_clang_signed="$sysroot/usr/lib/llvm-18/bin/clang"
  [ -f "$r47_clang_guest" ] && [ ! -L "$r47_clang_guest" ] &&
  [ -x "$r47_clang_guest" ] && [ -f "$r47_clang_signed" ] &&
  cmp -s "$r47_clang_guest" "$r47_clang_signed" || {
    echo 'APPFORGE_R47_GUEST_CXX_IDENTITY=FAILED' >&2;exit 69;
  }
  unset PROROOT_GUEST_EXE CMAKE_ROOT
  export LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/usr/lib/llvm-18/lib:/lib/aarch64-linux-gnu:$libpath"
  exec "$r47_clang_guest" --driver-mode=g++ "$@"
fi
# R48: signed guest llvm-ar must see the same namespace as guest Clang.
# The older private glibc-loader llvm-ar had --version PASS but could not read
# the proven regular C++ probe.o (PRoot host/guest path mismatch).
if [ "$name" = llvm-ar ] && [ -f "$prefix/llvm-ar-launch-mode-r48" ];then
  [ ! -L "$prefix/llvm-ar-launch-mode-r48" ] &&
  [ "$(cat "$prefix/llvm-ar-launch-mode-r48")" = guest-llvm-ar-direct ] || exit 69
  r48_signed_ar="$sysroot/usr/lib/llvm-18/bin/llvm-ar"
  r48_guest_ar=/usr/lib/llvm-18/bin/llvm-ar
  [ -f "$r48_signed_ar" ] && [ ! -L "$r48_signed_ar" ] &&
  [ -f "$r48_guest_ar" ] && [ ! -L "$r48_guest_ar" ] &&
  [ -x "$r48_guest_ar" ] &&
  cmp -s "$r48_signed_ar" "$r48_guest_ar" || exit 69
  unset PROROOT_GUEST_EXE CMAKE_ROOT
  export LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/usr/lib/llvm-18/lib:/lib/aarch64-linux-gnu:$libpath"
  exec "$r48_guest_ar" "$@"
fi
# R44: run ONLY the byte-verified guest-native LLD after its own real
# dependency closure and version test. Never redirect other LLVM tools.
if [ "$name" = ld.lld ] && [ -f "$prefix/lld-launch-mode-r44" ];then
  [ ! -L "$prefix/lld-launch-mode-r44" ] &&
  [ "$(cat "$prefix/lld-launch-mode-r44")" = guest-lld-direct ] || exit 66
  [ -f /usr/lib/llvm-18/bin/lld ] && [ ! -L /usr/lib/llvm-18/bin/lld ] &&
  [ -L /usr/lib/llvm-18/bin/ld.lld ] &&
  [ "$(readlink /usr/lib/llvm-18/bin/ld.lld)" = lld ] || exit 66
  unset PROROOT_GUEST_EXE CMAKE_ROOT
  export LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/usr/lib/llvm-18/lib:/lib/aarch64-linux-gnu:$libpath"
  exec /usr/lib/llvm-18/bin/ld.lld "$@"
fi
# CMake 3.28 locates CMAKE_ROOT relative to argv[0] (its executable path).
# When invoked through the explicit glibc loader, argv[0] can become the
# loader path under PRoot even though the signed cmake-data payload exists.
# glibc 2.39 supports --argv0; use it only for CMake, preserving all other
# verified private-tool launch paths and the guest-exe override.
if [ "$name" = cmake ];then
  cmake_modules="$sysroot/usr/share/cmake-3.28/Modules/CMake.cmake"
  # Candidate: some CMake builds consult CMAKE_ROOT before locating data
  # relative to argv[0]. The physical -P CMAKE_ROOT probe still gates READY.
  # Resource data remains inside this exact verified private sysroot.
  CMAKE_ROOT="$sysroot/usr/share/cmake-3.28"
  export CMAKE_ROOT
  [ -s "$cmake_modules" ] || {
    echo 'APPFORGE_NDK_HOST_CMAKE_DATA_MISSING' >&2;exit 66;
  }
  # Only the installation probe may temporarily choose a launcher.
  # Normal use reads the launcher which passed a real CMAKE_ROOT check.
  cmake_mode=${APPFORGE_CMAKE_PROBE_MODE:-}
  if [ -z "$cmake_mode" ];then
    cmake_mode_file="$prefix/cmake-launch-mode-v9"
    [ -f "$cmake_mode_file" ] && [ ! -L "$cmake_mode_file" ] || {
      echo 'APPFORGE_NDK_HOST_CMAKE_NOT_VERIFIED' >&2;exit 66;
    }
    cmake_mode=$(cat "$cmake_mode_file")
  fi
  case "$cmake_mode" in
    direct) exec "$executable" "$@";;
    loader-argv0) exec "$interpreter" --argv0 "$executable" --library-path "$libpath" "$executable" "$@";;
    # R31 DIAG V2: candidate-only launch variants. Every variant must pass
    # the same actual -P CMAKE_ROOT probe before it can be persisted.
    loader-argv0-no-guest) unset PROROOT_GUEST_EXE; exec "$interpreter" --argv0 "$executable" --library-path "$libpath" "$executable" "$@";;
    loader-plain-no-guest) unset PROROOT_GUEST_EXE; exec "$interpreter" --library-path "$libpath" "$executable" "$@";;
    direct-no-guest) unset PROROOT_GUEST_EXE; exec "$executable" "$@";;
    # Only approved if a real CMAKE_ROOT + -P probe succeeded below.
    private-prefix-argv0) exec "$interpreter" --argv0 "$prefix/bin/cmake" --library-path "$libpath" "$executable" "$@";;
    # Candidate: real authenticated ELF in the private bin directory.
    # A direct exec retains the executable identity for CMake relocation.
    guest-cmake-direct)
      unset PROROOT_GUEST_EXE CMAKE_ROOT
      [ -f /usr/lib/aarch64-linux-gnu/libarchive.so.13 ] &&
      [ ! -L /usr/lib/aarch64-linux-gnu/libarchive.so.13 ] &&
      [ -s /usr/share/cmake-3.28/Modules/CMake.cmake ] || exit 66
      # R34: R33 dropped this search path; PRoot could not load libarchive.
      export LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:$libpath"
      exec /usr/bin/cmake "$@";;
    guest-cmake-loader-argv0)
      unset PROROOT_GUEST_EXE LD_LIBRARY_PATH CMAKE_ROOT
      exec "$interpreter" --argv0 /usr/bin/cmake \
        --library-path "/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:$libpath" \
        /usr/bin/cmake "$@";;
    # R37: CMake-only recovery candidate. The explicitly loaded ELF can
    # bypass PRoot path interception; ask PRoot to patch this guest early.
    # Never change global PRoot arguments or Android app module settings.
    guest-cmake-loader-early-patch)
      unset PROROOT_GUEST_EXE LD_LIBRARY_PATH CMAKE_ROOT
      export PROROOT_FORCE_EARLY_PATCH=1
      exec "$interpreter" --argv0 /usr/bin/cmake \
        --library-path "/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:$libpath" \
        /usr/bin/cmake "$@";;
    private-elf-direct)
      unset PROROOT_GUEST_EXE
      exec "$prefix/bin/cmake-private-elf" "$@";;
    private-elf-loader-argv0)
      unset PROROOT_GUEST_EXE
      exec "$interpreter" --argv0 "$prefix/bin/cmake-private-elf" \
        --library-path "$libpath" "$prefix/bin/cmake-private-elf" "$@";;
    *) echo 'APPFORGE_NDK_HOST_CMAKE_LAUNCH_DENIED' >&2;exit 66;;
  esac
fi
exec "$interpreter" --library-path "$libpath" "$executable" "$@"
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
# Select a safe launcher on THIS device. PRoot can expose a different
# /proc/self/exe to CMake's relocation logic. Do not guess from --version.
# Keep the CMake probe inside the very same isolated candidate prefix;
# PRoot may not resolve /tmp from the explicitly loaded ELF namespace.
cmake_probe_dir=$(mktemp -d "$PREFIX/.cmake-select.XXXXXXXX") || exit 68
[ -d "$cmake_probe_dir" ] && [ ! -L "$cmake_probe_dir" ] || exit 68
echo 'APPFORGE_NDK_CMAKE_PROBE_PRIVATE_PREFIX=PASS'

trap 'rm -rf -- "$cmake_probe_dir"' 0 HUP INT TERM
cat > "$cmake_probe_dir/check.cmake" <<'CMAKE'
if(NOT EXISTS "${CMAKE_ROOT}/Modules/CMakeDetermineSystem.cmake")
  message(FATAL_ERROR "APPFORGE_NDK_HOST_CMAKE_ROOT_INVALID")
endif()
CMAKE
[ -s "$PREFIX/sysroot/usr/share/cmake-3.28/Modules/CMakeDetermineSystem.cmake" ] || exit 68
# Check the pinned ELF loader's private-library resolution first.
# This does not authorize installation; the actual CMAKE_ROOT probe below must pass.
private_libpath="$PREFIX/sysroot/usr/lib/llvm-18/lib:$PREFIX/sysroot/usr/lib/aarch64-linux-gnu:$PREFIX/sysroot/lib/aarch64-linux-gnu"
if ! /lib/ld-linux-aarch64.so.1 --library-path "$private_libpath" --list "$PREFIX/sysroot/usr/bin/cmake" >"$cmake_probe_dir/loader.log" 2>&1;then
  sed -n '1,12p' "$cmake_probe_dir/loader.log" >&2
  echo 'APPFORGE_NDK_HOST_CMAKE_PRIVATE_ELF_DEPENDENCY_FAILED' >&2
  exit 68
fi
[ -f "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/libarchive.so.13" ] || {
  echo 'APPFORGE_NDK_HOST_CMAKE_PRIVATE_LIBARCHIVE_MISSING' >&2;exit 68
}
[ -s "$cmake_probe_dir/check.cmake" ] &&
 [ -s "$PREFIX/sysroot/usr/share/cmake-3.28/Modules/CMakeDetermineSystem.cmake" ] || {
  echo 'APPFORGE_NDK_CMAKE_SCRIPT_SHELL_INVISIBLE' >&2;exit 68;
}
echo 'APPFORGE_NDK_CMAKE_SCRIPT_SHELL_VISIBLE=PASS'
for cmake_mode in loader-argv0 direct loader-argv0-no-guest loader-plain-no-guest direct-no-guest;do
  if APPFORGE_CMAKE_PROBE_MODE="$cmake_mode" "$BIN/cmake" -P "$cmake_probe_dir/check.cmake" >"$cmake_probe_dir/log.$cmake_mode" 2>&1;then
    printf '%s\n' "$cmake_mode" > "$PREFIX/cmake-launch-mode-v9"
    break
  fi
  echo "APPFORGE_NDK_HOST_CMAKE_MODE_REJECTED=$cmake_mode" >&2
done
if [ ! -s "$PREFIX/cmake-launch-mode-v9" ];then
  for cmake_mode in loader-argv0 direct loader-argv0-no-guest loader-plain-no-guest direct-no-guest;do
    if [ -f "$cmake_probe_dir/log.$cmake_mode" ];then
      echo "APPFORGE_NDK_HOST_CMAKE_PROBE_DIAGNOSTIC=$cmake_mode" >&2
      sed -n '1,12p' "$cmake_probe_dir/log.$cmake_mode" >&2
    fi
  done
  # CMake invoked through ld-linux may resolve its installation prefix as
  # / rather than the private sysroot. Provide the verified CMake data in this
  # isolated module generation only; never shadow an existing /share entry.
  signed_cmake_data="$PREFIX/sysroot/usr/share/cmake-3.28"
  if [ -s "$signed_cmake_data/Modules/CMakeDetermineSystem.cmake" ] &&
     [ ! -L /share ] && [ ! -e /share/cmake-3.28 ] && [ ! -L /share/cmake-3.28 ];then
    mkdir -p /share
    ln -s "$signed_cmake_data" /share/cmake-3.28 || exit 68
    echo 'APPFORGE_NDK_HOST_PRIVATE_CMAKE_MODULE_LINK=CREATED'
    if APPFORGE_CMAKE_PROBE_MODE=loader-argv0 "$BIN/cmake" -P "$cmake_probe_dir/check.cmake" >"$cmake_probe_dir/log.private-root" 2>&1;then
      printf '%s\n' loader-argv0 > "$PREFIX/cmake-launch-mode-v9"
      echo 'APPFORGE_NDK_HOST_PRIVATE_CMAKE_MODULE_PROBE=PASS'
    else
      echo 'APPFORGE_NDK_HOST_PRIVATE_CMAKE_MODULE_PROBE=FAIL' >&2
      sed -n '1,12p' "$cmake_probe_dir/log.private-root" >&2
    fi
  fi
  # A third, private-only executable identity and matching signed CMake
  # resource layout. No shared /usr or libc is written.
  if [ ! -s "$PREFIX/cmake-launch-mode-v9" ];then
    private_cmake_share="$PREFIX/share/cmake-3.28"
    if [ ! -e "$private_cmake_share" ] && [ ! -L "$private_cmake_share" ] &&
       [ -s "$PREFIX/sysroot/usr/share/cmake-3.28/Modules/CMakeDetermineSystem.cmake" ];then
      mkdir -p "$PREFIX/share"
      ln -s "$PREFIX/sysroot/usr/share/cmake-3.28" "$private_cmake_share" || exit 68
      echo 'APPFORGE_NDK_CMAKE_PRIVATE_PREFIX_LAYOUT=VERIFIED'
    fi
    if [ -s "$private_cmake_share/Modules/CMakeDetermineSystem.cmake" ] &&
       APPFORGE_CMAKE_PROBE_MODE=private-prefix-argv0 "$BIN/cmake" -P "$cmake_probe_dir/check.cmake" >"$cmake_probe_dir/log.private-prefix" 2>&1;then
      printf '%s\n' private-prefix-argv0 > "$PREFIX/cmake-launch-mode-v9"
      echo 'APPFORGE_NDK_HOST_PRIVATE_PREFIX_CMAKE_PROBE=PASS'
    else
      echo 'APPFORGE_NDK_HOST_PRIVATE_PREFIX_CMAKE_PROBE=FAIL' >&2
      [ ! -f "$cmake_probe_dir/log.private-prefix" ] || sed -n '1,16p' "$cmake_probe_dir/log.private-prefix" >&2
    fi
  fi
  # R32: CMake discovers resources relative to its actual ELF location.
  # Copy only the SHA-256-authenticated package ELF into this private
  # candidate prefix. Never alter the global guest /usr or libc.
  if [ ! -s "$PREFIX/cmake-launch-mode-v9" ];then
    signed_cmake_elf="$PREFIX/sysroot/usr/bin/cmake"
    private_cmake_elf="$PREFIX/bin/cmake-private-elf"
    private_cmake_modules="$PREFIX/share/cmake-3.28/Modules/CMake.cmake"

    [ -f "$signed_cmake_elf" ] &&
    [ ! -L "$signed_cmake_elf" ] &&
    [ -x "$signed_cmake_elf" ] &&
    [ -s "$private_cmake_modules" ] &&
    [ ! -e "$private_cmake_elf" ] &&
    [ ! -L "$private_cmake_elf" ] || {
      echo 'APPFORGE_NDK_CMAKE_PRIVATE_ELF_PREFLIGHT_FAILED' >&2
      exit 68
    }

    file "$signed_cmake_elf" |
      grep -Eqi 'aarch64|ARM aarch64' || exit 68

    cp -p "$signed_cmake_elf" "$private_cmake_elf" || exit 68

    [ -f "$private_cmake_elf" ] &&
    [ ! -L "$private_cmake_elf" ] &&
    [ -x "$private_cmake_elf" ] &&
    cmp -s "$signed_cmake_elf" "$private_cmake_elf" || exit 68

    echo 'APPFORGE_NDK_CMAKE_PRIVATE_ELF_BYTES=VERIFIED'

    for cmake_mode in private-elf-direct private-elf-loader-argv0;do
      if APPFORGE_CMAKE_PROBE_MODE="$cmake_mode" "$BIN/cmake" \
          -P "$cmake_probe_dir/check.cmake" \
          >"$cmake_probe_dir/log.$cmake_mode" 2>&1;then
        printf '%s\n' "$cmake_mode" > "$PREFIX/cmake-launch-mode-v9"
        echo "APPFORGE_NDK_CMAKE_PRIVATE_ELF_PROBE=PASS:$cmake_mode"
        break
      fi
      echo "APPFORGE_NDK_CMAKE_PRIVATE_ELF_MODE_REJECTED=$cmake_mode" >&2
      sed -n '1,12p' "$cmake_probe_dir/log.$cmake_mode" >&2
    done
  fi

  # R33: The explicitly loaded ELF can bypass PRoot's guest /opt mappings.
  # In this single isolated module generation only, install authenticated
  # bytes at standard guest runtime paths. Never overwrite existing names.
  if [ ! -s "$PREFIX/cmake-launch-mode-v9" ];then
    echo 'APPFORGE_R33_CMAKE_GUEST_LAYOUT_PROBE=START'
    guest_cmake=/usr/bin/cmake
    guest_modules=/usr/share/cmake-3.28
    guest_libarchive=/usr/lib/aarch64-linux-gnu/libarchive.so.13
    guest_librhash=/usr/lib/aarch64-linux-gnu/librhash.so.0
    signed_elf="$PREFIX/sysroot/usr/bin/cmake"
    signed_modules="$PREFIX/sysroot/usr/share/cmake-3.28"
    signed_libarchive="$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/libarchive.so.13"
    signed_librhash="$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/librhash.so.0"
    # librhash0 is already in the SHA256-pinned EXPO_REACT_NATIVE/arm64 catalog.
    # Resolve only a single local SONAME link if the Ubuntu payload uses one.
    [ -f "$signed_librhash" ] || { echo "APPFORGE_R38_SIGNED_RHASH_MISSING" >&2; exit 68; }
    if [ -L "$signed_librhash" ];then
      rhash_link=$(readlink "$signed_librhash") || exit 68
      case "$rhash_link" in ''|/*|*/*|.*) echo "APPFORGE_R38_RHASH_LINK_UNSAFE" >&2; exit 68;; esac
      rhash_payload="${signed_librhash%/*}/$rhash_link"
    else
      rhash_payload="$signed_librhash"
    fi
    [ -f "$rhash_payload" ] && [ ! -L "$rhash_payload" ] &&
    file -L "$rhash_payload" | grep -Eqi 'aarch64|ARM aarch64' || {
      echo "APPFORGE_R38_SIGNED_RHASH_ELF_INVALID" >&2; exit 68;
    }
    [ -f "$signed_elf" ] && [ ! -L "$signed_elf" ] && [ -x "$signed_elf" ] &&
    [ -s "$signed_modules/Modules/CMake.cmake" ] &&
    [ -f "$signed_libarchive" ] && [ ! -L "$signed_libarchive" ] || {
      echo 'APPFORGE_R33_CMAKE_SIGNED_PAYLOAD_INCOMPLETE' >&2;exit 68;
    }
    # /usr is a guest-private directory only in these individually isolated
    # rootfs generations. If a path exists, stop rather than replace it.
    for guest_path in "$guest_cmake" "$guest_modules" "$guest_libarchive" "$guest_librhash";do
      if [ -e "$guest_path" ] || [ -L "$guest_path" ];then
        echo "APPFORGE_R33_CMAKE_GUEST_PATH_OCCUPIED=$guest_path" >&2
        exit 68
      fi
    done
    [ -d /usr/bin ] && [ -d /usr/share ] &&
    [ -d /usr/lib/aarch64-linux-gnu ] || {
      echo 'APPFORGE_R33_CMAKE_GUEST_ROOT_UNAVAILABLE' >&2;exit 68;
    }
    # All inputs were validated by signed catalog SHA256 before extraction.
    cp -p "$signed_elf" "$guest_cmake" &&
    cp -p "$signed_libarchive" "$guest_libarchive" &&
    cp -p "$rhash_payload" "$guest_librhash" &&
    cp -a "$signed_modules" "$guest_modules" || {
      echo 'APPFORGE_R33_CMAKE_GUEST_COPY_FAILED' >&2;exit 68;
    }
    cmp -s "$signed_elf" "$guest_cmake" &&
    cmp -s "$signed_libarchive" "$guest_libarchive" &&
    cmp -s "$rhash_payload" "$guest_librhash" &&
    [ -d "$guest_modules" ] && [ ! -L "$guest_modules" ] &&
    cmp -s "$signed_modules/Modules/CMake.cmake" "$guest_modules/Modules/CMake.cmake" &&
    cmp -s "$signed_modules/Modules/CMakeDetermineSystem.cmake" "$guest_modules/Modules/CMakeDetermineSystem.cmake" || {
      echo 'APPFORGE_R33_CMAKE_GUEST_BYTES_UNVERIFIED' >&2;exit 68;
    }
    echo 'APPFORGE_R33_CMAKE_GUEST_BYTES=VERIFIED'
    echo 'APPFORGE_R38_GUEST_RHASH=VERIFIED'
    # R40: the authenticated private loader list is the dependency closure,
    # including transitive libraries. Copy only absent, signed Ubuntu ARM64
    # library bytes to this individually isolated candidate guest generation.
    # Do not modify libc, any pre-existing guest path, or the shared base.
    r40_needed="$cmake_probe_dir/r40-cmake-needed.txt"
    LC_ALL=C awk '$2 == "=>" && $1 ~ /^lib[0-9A-Za-z_.+-]+[.]so([.][0-9]+)*$/ {print $1}' \
      "$cmake_probe_dir/loader.log" | LC_ALL=C sort -u > "$r40_needed"
    [ -s "$r40_needed" ] && grep -qx 'libuv.so.1' "$r40_needed" || {
      echo 'APPFORGE_R40_SIGNED_CMAKE_DEPENDENCY_LIST_INVALID' >&2;exit 68;
    }
    r40_copied=0
    r40_existing=0
    while IFS= read -r soname;do
      case "$soname" in
        libc.so.*|libm.so.*|libdl.so.*|libpthread.so.*|librt.so.*|libresolv.so.*|libanl.so.*|libnss_*.so.*) continue;;
      esac
      case "$soname" in ''|*[!a-zA-Z0-9_.+-]*)
        echo 'APPFORGE_R40_CMAKE_DEPENDENCY_NAME_REJECTED' >&2;exit 68;;
      esac
      r40_guest="/usr/lib/aarch64-linux-gnu/$soname"
      if [ -e "$r40_guest" ] || [ -L "$r40_guest" ];then
        r40_existing=$((r40_existing+1))
        continue
      fi
      r40_signed=''
      for r40_dir in "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu" \
                     "$PREFIX/sysroot/lib/aarch64-linux-gnu" \
                     "$PREFIX/sysroot/usr/lib/llvm-18/lib";do
        if [ -f "$r40_dir/$soname" ];then
          r40_signed="$r40_dir/$soname"
          break
        fi
      done
      [ -n "$r40_signed" ] || {
        echo "APPFORGE_R40_SIGNED_CMAKE_DEPENDENCY_MISSING=$soname" >&2;exit 68;
      }
      if [ -L "$r40_signed" ];then
        r40_link=$(readlink "$r40_signed") || exit 68
        case "$r40_link" in ''|/*|*/*|.*)
          echo "APPFORGE_R40_SIGNED_CMAKE_DEPENDENCY_LINK_REJECTED=$soname" >&2;exit 68;;
        esac
        r40_payload="${r40_signed%/*}/$r40_link"
      else
        r40_payload="$r40_signed"
      fi
      [ -f "$r40_payload" ] && [ ! -L "$r40_payload" ] && \
      file -L "$r40_payload" | grep -Eqi 'aarch64|ARM aarch64' || {
        echo "APPFORGE_R40_SIGNED_CMAKE_DEPENDENCY_ELF_INVALID=$soname" >&2;exit 68;
      }
      [ ! -e "$r40_guest" ] && [ ! -L "$r40_guest" ] || exit 68
      cp -p "$r40_payload" "$r40_guest" && \
      [ -f "$r40_guest" ] && [ ! -L "$r40_guest" ] && \
      cmp -s "$r40_payload" "$r40_guest" || {
        echo "APPFORGE_R40_GUEST_CMAKE_DEPENDENCY_COPY_FAILED=$soname" >&2;exit 68;
      }
      r40_copied=$((r40_copied+1))
      echo "APPFORGE_R40_GUEST_CMAKE_DEPENDENCY_VERIFIED=$soname"
    done < "$r40_needed"
    [ -f /usr/lib/aarch64-linux-gnu/libuv.so.1 ] || {
      echo 'APPFORGE_R40_GUEST_LIBUV_NOT_VISIBLE' >&2;exit 68;
    }
    echo "APPFORGE_R40_GUEST_CMAKE_DEPENDENCY_CLOSURE=VERIFIED:copied=$r40_copied:retained=$r40_existing"
    if APPFORGE_CMAKE_PROBE_MODE=guest-cmake-direct "$BIN/cmake" \
         -P "$cmake_probe_dir/check.cmake" \
         >"$cmake_probe_dir/log.guest-direct" 2>&1;then
      printf '%s\n' guest-cmake-direct > "$PREFIX/cmake-launch-mode-v9"
      echo 'APPFORGE_R33_CMAKE_GUEST_REAL_P_TEST=PASS'
    else
      echo 'APPFORGE_R33_CMAKE_GUEST_REAL_P_TEST=FAILED' >&2
      sed -n '1,22p' "$cmake_probe_dir/log.guest-direct" >&2
      echo 'APPFORGE_R34_CMAKE_EXPLICIT_GUEST_LOADER_TEST=START'
      if APPFORGE_CMAKE_PROBE_MODE=guest-cmake-loader-argv0 "$BIN/cmake" \
          -P "$cmake_probe_dir/check.cmake" \
          >"$cmake_probe_dir/log.guest-loader" 2>&1;then
        printf '%s\n' guest-cmake-loader-argv0 > "$PREFIX/cmake-launch-mode-v9"
        echo 'APPFORGE_R34_CMAKE_EXPLICIT_GUEST_LOADER_TEST=PASS'
      else
        echo 'APPFORGE_R34_CMAKE_EXPLICIT_GUEST_LOADER_TEST=FAILED' >&2
        sed -n '1,24p' "$cmake_probe_dir/log.guest-loader" >&2
        # R37: isolated early-patching candidate, never a fabricated READY.
        echo 'APPFORGE_R37_CMAKE_EARLY_PATCH=START' >&2
        if APPFORGE_CMAKE_PROBE_MODE=guest-cmake-loader-early-patch "$BIN/cmake" \
             -P "$cmake_probe_dir/check.cmake" \
             >"$cmake_probe_dir/log.guest-early-patch" 2>&1;then
          printf '%s\n' guest-cmake-loader-early-patch > "$PREFIX/cmake-launch-mode-v9"
          echo 'APPFORGE_R37_CMAKE_EARLY_PATCH=PASS' >&2
        else
          echo 'APPFORGE_R37_CMAKE_EARLY_PATCH=FAILED' >&2
          sed -n '1,24p' "$cmake_probe_dir/log.guest-early-patch" >&2
        fi
      fi
      # No READY without a real CMAKE_ROOT and -P test.
    fi
  fi

  [ -s "$PREFIX/cmake-launch-mode-v9" ] || {
    # R35 persistent pre-rollback evidence: NO runtime modification or fake READY.
    echo 'APPFORGE_R35_DIAGNOSTIC=BEGIN' >&2
    for diagnostic_path in /usr/bin/cmake /usr/share/cmake-3.28/Modules/CMake.cmake \
        /usr/lib/aarch64-linux-gnu/libarchive.so.13 \
        /lib/ld-linux-aarch64.so.1 \
        "$PREFIX/sysroot/usr/bin/cmake" \
        "$PREFIX/sysroot/usr/share/cmake-3.28/Modules/CMake.cmake" \
        "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/libarchive.so.13";do
      if [ -L "$diagnostic_path" ];then
        echo "APPFORGE_R35_LINK=$diagnostic_path -> $(readlink "$diagnostic_path")" >&2
      elif [ -f "$diagnostic_path" ];then
        echo "APPFORGE_R35_REGULAR=$diagnostic_path" >&2
      elif [ -d "$diagnostic_path" ];then
        echo "APPFORGE_R35_DIRECTORY=$diagnostic_path" >&2
      else
        echo "APPFORGE_R35_MISSING=$diagnostic_path" >&2
      fi
    done
    echo "APPFORGE_R35_LIBPATH=$private_libpath" >&2
    if [ -x /lib/ld-linux-aarch64.so.1 ] && [ -f /usr/bin/cmake ];then
      echo 'APPFORGE_R35_DYNAMIC_LOADER_LIST=BEGIN' >&2
      /lib/ld-linux-aarch64.so.1 --library-path \
        "/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:$private_libpath" \
        --list /usr/bin/cmake 2>&1 | sed -n '1,22p' >&2 || :
      echo 'APPFORGE_R35_DYNAMIC_LOADER_LIST=END' >&2
    fi
    if command -v readelf >/dev/null 2>&1 && [ -f /usr/bin/cmake ];then
      echo 'APPFORGE_R35_ELF_NEEDED=BEGIN' >&2
      readelf -d /usr/bin/cmake 2>&1 | grep -E 'NEEDED|RPATH|RUNPATH' | sed -n '1,16p' >&2 || :
      echo 'APPFORGE_R35_ELF_NEEDED=END' >&2
    fi
    echo 'APPFORGE_R35_DIAGNOSTIC=END' >&2
    echo 'APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER' >&2
    exit 68
  }
fi
printf 'APPFORGE_NDK_HOST_CMAKE_LAUNCH=%s\n' "$(cat "$PREFIX/cmake-launch-mode-v9")"
rm -rf -- "$cmake_probe_dir"
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
probe_dir=$(mktemp -d "$PREFIX/.cmake-functional.XXXXXXXX") || exit 68
[ -d "$probe_dir" ] && [ ! -L "$probe_dir" ] || exit 68
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
# R41: CMake is now guest-native. Clang's explicit glibc-loader path can bypass
# PRoot's guest-path translation, so a real guest C file becomes unreadable.
# Test signed ARM64 guest ELF and the loader-list dependency closure in this
# candidate generation. Never install to shared libc, overwrite guest names,
# infer readiness from --version, or accept stdin-only as a source-read test.
r41_clang_signed="$PREFIX/sysroot/usr/lib/llvm-18/bin/clang"
r41_clang_guest=/usr/lib/llvm-18/bin/clang
r41_clang_data_src="$PREFIX/sysroot/usr/lib/llvm-18/lib/clang"
r41_clang_data_guest=/usr/lib/llvm-18/lib/clang
r41_list="$probe_dir/r41-clang-loader-list.txt"
r41_needed="$probe_dir/r41-clang-needed.txt"
echo 'APPFORGE_R41_GUEST_CLANG_PROBE=START'
[ -f "$r41_clang_signed" ] && [ -x "$r41_clang_signed" ] &&   file -L "$r41_clang_signed" | grep -Eqi 'aarch64|ARM aarch64' || {
  echo 'APPFORGE_R41_CLANG_SIGNED_ELF_MISSING' >&2;exit 69;
}
[ -d "$r41_clang_data_src" ] && [ ! -L "$r41_clang_data_src" ] || {
  echo 'APPFORGE_R41_CLANG_RESOURCE_DIR_MISSING' >&2;exit 69;
}
for r41_path in /usr/lib/llvm-18 /usr/lib/llvm-18/bin /usr/lib/llvm-18/lib;do
  [ ! -L "$r41_path" ] || { echo 'APPFORGE_R41_CLANG_PARENT_SYMLINK_REJECTED' >&2;exit 69; }
done
for r41_path in "$r41_clang_guest" "$r41_clang_data_guest";do
  [ ! -e "$r41_path" ] && [ ! -L "$r41_path" ] || {
    echo 'APPFORGE_R41_CLANG_GUEST_PATH_OCCUPIED' >&2;exit 69;
  }
done
mkdir -p /usr/lib/llvm-18/bin /usr/lib/llvm-18/lib
if ! /lib/ld-linux-aarch64.so.1 --library-path "$private_libpath" --list     "$r41_clang_signed" > "$r41_list" 2>&1;then
  echo 'APPFORGE_R41_CLANG_SIGNED_DEPENDENCY_LIST_FAILED' >&2
  sed -n '1,15p' "$r41_list" >&2;exit 69
fi
LC_ALL=C awk '$2 == "=>" && $1 ~ /^lib[0-9A-Za-z_.+-]+[.]so([.][0-9]+)*$/ {print $1}'   "$r41_list" | LC_ALL=C sort -u > "$r41_needed"
[ -s "$r41_needed" ] || { echo 'APPFORGE_R41_CLANG_NEEDED_EMPTY' >&2;exit 69; }
r41_copy=0
r41_retained=0
while IFS= read -r r41_soname;do
  case "$r41_soname" in
    libc.so.*|libm.so.*|libdl.so.*|libpthread.so.*|librt.so.*|libresolv.so.*|libanl.so.*|libnss_*.so.*) continue;;
    ''|*[!A-Za-z0-9_.+-]*) echo 'APPFORGE_R41_CLANG_LIBNAME_REJECTED' >&2;exit 69;;
  esac
  r41_guest_lib="/usr/lib/aarch64-linux-gnu/$r41_soname"
  if [ -e "$r41_guest_lib" ] || [ -L "$r41_guest_lib" ];then
    r41_retained=$((r41_retained+1))
    continue
  fi
  r41_signed_lib=''
  for r41_libdir in "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu"                     "$PREFIX/sysroot/lib/aarch64-linux-gnu"                     "$PREFIX/sysroot/usr/lib/llvm-18/lib";do
    if [ -f "$r41_libdir/$r41_soname" ];then
      r41_signed_lib="$r41_libdir/$r41_soname"
      break
    fi
  done
  [ -n "$r41_signed_lib" ] || {
    echo "APPFORGE_R41_CLANG_SIGNED_DEPENDENCY_MISSING=$r41_soname" >&2;exit 69;
  }
  # R42: package-authenticated cross-directory SONAME links (e.g. libLLVM.so.18.1
  # -> ../llvm-18/lib/libLLVM.so.1) are legitimate Ubuntu ARM64 layout.
  # Resolve symlink chains fully; accept ONLY regular ELF within these trusted
  # private package-extracted directories. Reject escape, dangling or loops.
  r41_source=$(readlink -f "$r41_signed_lib") || {
    echo "APPFORGE_R42_CLANG_LINK_CANNOT_RESOLVE=$r41_soname" >&2; exit 69;
  }
  case "$r41_source" in
    "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/"*|    "$PREFIX/sysroot/usr/lib/llvm-18/lib/"*|    "$PREFIX/sysroot/lib/aarch64-linux-gnu/"*) ;;
    *) echo "APPFORGE_R42_CLANG_LINK_TARGET_OUTSIDE_SIGNED_SYSROOT=$r41_soname" >&2; exit 69;;
  esac
  [ -f "$r41_source" ] && [ ! -L "$r41_source" ] || {
    echo "APPFORGE_R42_CLANG_LINK_NOT_REGULAR_ELF=$r41_soname" >&2; exit 69;
  }
  echo "APPFORGE_R42_CLANG_LINK_RESOLVED=$r41_soname"
  [ -f "$r41_source" ] && [ ! -L "$r41_source" ] &&   file -L "$r41_source" | grep -Eqi 'aarch64|ARM aarch64' || {
    echo 'APPFORGE_R41_CLANG_DEPENDENCY_ELF_REJECTED' >&2;exit 69;
  }
  [ ! -e "$r41_guest_lib" ] && [ ! -L "$r41_guest_lib" ] || exit 69
  cp -p "$r41_source" "$r41_guest_lib" &&   [ -f "$r41_guest_lib" ] && [ ! -L "$r41_guest_lib" ] &&   cmp -s "$r41_source" "$r41_guest_lib" || {
    echo 'APPFORGE_R41_CLANG_DEPENDENCY_COPY_FAILED' >&2;exit 69;
  }
  r41_copy=$((r41_copy+1))
  echo "APPFORGE_R41_CLANG_LIB_VERIFIED=$r41_soname"
done < "$r41_needed"
cp -pL "$r41_clang_signed" "$r41_clang_guest" && cp -a "$r41_clang_data_src" "$r41_clang_data_guest" && [ -f "$r41_clang_guest" ] && [ ! -L "$r41_clang_guest" ] && [ -d "$r41_clang_data_guest" ] && [ ! -L "$r41_clang_data_guest" ] && cmp -s "$r41_clang_signed" "$r41_clang_guest" || {
  echo 'APPFORGE_R41_CLANG_SIGNED_BYTES_UNVERIFIED' >&2;exit 69;
}
echo "APPFORGE_R41_CLANG_GUEST_CLOSURE=VERIFIED:copied=$r41_copy:retained=$r41_retained"
# Required real file read, no stdin workaround, no forged READY.
if ! LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/usr/lib/llvm-18/lib:/lib/aarch64-linux-gnu"   "$r41_clang_guest" --target=aarch64-unknown-linux-gnu -x c -fsyntax-only   "$probe_dir/readable.c";then
  echo 'APPFORGE_R41_CLANG_GUEST_SOURCE_READ=FAILED' >&2;exit 69;
fi
echo 'APPFORGE_R41_CLANG_GUEST_SOURCE_READ=PASS'
printf '%s\n' guest-clang-direct > "$PREFIX/clang-launch-mode-r41"
# The original $BIN/clang source-file probe must still pass below.
if ! "$BIN/clang" --target=aarch64-unknown-linux-gnu -x c -fsyntax-only "$probe_dir/readable.c";then
  echo 'APPFORGE_NDK_HOST_SOURCE_READ_PROBE_FAILED' >&2;exit 69
fi
printf 'APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS\n'
# R44: `lld-18` provides a signed regular `lld` ELF and `ld.lld -> lld`.
# A glibc-loader invocation bypasses PRoot's guest filesystem mapping; the
# approved LLD must work as a guest ELF. Do not write to shared /usr or libc.
# All paths below exist only inside the rollback-protected candidate rootfs.
r44_signed=/opt/appforge-device/ndk-host-v7/sysroot/usr/lib/llvm-18/bin/lld
r44_signed_link=/opt/appforge-device/ndk-host-v7/sysroot/usr/lib/llvm-18/bin/ld.lld
r44_guest=/usr/lib/llvm-18/bin/lld
r44_guest_link=/usr/lib/llvm-18/bin/ld.lld
r44_deps="$probe_dir/r44-lld-dependencies.txt"
r44_loader_log="$probe_dir/r44-lld-loader.log"
r44_test_log="$probe_dir/r44-lld-direct.log"
echo 'APPFORGE_R44_GUEST_LLD_PROBE=START'
[ -f "$r44_signed" ] && [ ! -L "$r44_signed" ] && [ -x "$r44_signed" ] &&
[ -L "$r44_signed_link" ] && [ "$(readlink "$r44_signed_link")" = lld ] &&
file -L "$r44_signed" | grep -Eqi 'aarch64|ARM aarch64' || {
  echo 'APPFORGE_R44_SIGNED_LLD_LAYOUT_REJECTED' >&2;exit 69;
}
[ ! -e "$r44_guest" ] && [ ! -L "$r44_guest" ] &&
[ ! -e "$r44_guest_link" ] && [ ! -L "$r44_guest_link" ] || {
  echo 'APPFORGE_R44_GUEST_LLD_PATH_OCCUPIED' >&2;exit 69;
}
if ! /lib/ld-linux-aarch64.so.1 --library-path "$private_libpath" --list "$r44_signed" > "$r44_loader_log" 2>&1;then
  echo 'APPFORGE_R44_LLD_SIGNED_LOADER_LIST_FAILED' >&2
  sed -n '1,20p' "$r44_loader_log" >&2;exit 69
fi
LC_ALL=C awk '$2 == "=>" && $1 ~ /^lib[0-9A-Za-z_.+-]+[.]so([.][0-9]+)*$/ {print $1}' \
  "$r44_loader_log" | LC_ALL=C sort -u > "$r44_deps"
[ -s "$r44_deps" ] || { echo 'APPFORGE_R44_LLD_DEPENDENCY_LIST_EMPTY' >&2;exit 69; }
r44_copied=0
r44_retained=0
while IFS= read -r r44_soname;do
  case "$r44_soname" in
    libc.so.*|libm.so.*|libdl.so.*|libpthread.so.*|librt.so.*|libresolv.so.*|libanl.so.*|libnss_*.so.*) continue;;
    ''|*[!A-Za-z0-9_.+-]*) echo 'APPFORGE_R44_LLD_LIBNAME_REJECTED' >&2;exit 69;;
  esac
  r44_dst="/usr/lib/aarch64-linux-gnu/$r44_soname"
  if [ -e "$r44_dst" ] || [ -L "$r44_dst" ];then
    [ -f "$r44_dst" ] && file -L "$r44_dst" | grep -Eqi 'aarch64|ARM aarch64' || {
      echo "APPFORGE_R44_LLD_GUEST_LIB_INVALID=$r44_soname" >&2;exit 69;
    }
    r44_retained=$((r44_retained+1))
    continue
  fi
  r44_src=''
  for r44_dir in "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu" \
                 "$PREFIX/sysroot/lib/aarch64-linux-gnu" \
                 "$PREFIX/sysroot/usr/lib/llvm-18/lib";do
    if [ -f "$r44_dir/$r44_soname" ];then
      r44_src=$(readlink -f "$r44_dir/$r44_soname") || exit 69
      break
    fi
  done
  [ -n "$r44_src" ] || {
    echo "APPFORGE_R44_LLD_SIGNED_LIB_MISSING=$r44_soname" >&2;exit 69;
  }
  case "$r44_src" in
    "$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/"*|\
    "$PREFIX/sysroot/lib/aarch64-linux-gnu/"*|\
    "$PREFIX/sysroot/usr/lib/llvm-18/lib/"*) ;;
    *) echo "APPFORGE_R44_LLD_LIB_ESCAPED_SYSROOT=$r44_soname" >&2;exit 69;;
  esac
  [ -f "$r44_src" ] && [ ! -L "$r44_src" ] &&
  file -L "$r44_src" | grep -Eqi 'aarch64|ARM aarch64' || {
    echo "APPFORGE_R44_LLD_SIGNED_LIB_INVALID=$r44_soname" >&2;exit 69;
  }
  cp -p "$r44_src" "$r44_dst" && [ -f "$r44_dst" ] &&
  [ ! -L "$r44_dst" ] && cmp -s "$r44_src" "$r44_dst" || {
    echo "APPFORGE_R44_LLD_LIB_COPY_FAILED=$r44_soname" >&2;exit 69;
  }
  r44_copied=$((r44_copied+1))
  echo "APPFORGE_R44_LLD_LIB_VERIFIED=$r44_soname"
done < "$r44_deps"
cp -p "$r44_signed" "$r44_guest" &&
[ -f "$r44_guest" ] && [ ! -L "$r44_guest" ] &&
cmp -s "$r44_signed" "$r44_guest" &&
ln -s lld "$r44_guest_link" &&
[ -L "$r44_guest_link" ] &&
[ "$(readlink "$r44_guest_link")" = lld ] || {
  echo 'APPFORGE_R44_GUEST_LLD_BYTES_OR_LINK_FAILED' >&2;exit 69;
}
echo "APPFORGE_R44_LLD_GUEST_CLOSURE=VERIFIED:copied=$r44_copied:retained=$r44_retained"
if ! LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/usr/lib/llvm-18/lib:/lib/aarch64-linux-gnu" \
  "$r44_guest_link" --version > "$r44_test_log" 2>&1;then
  echo 'APPFORGE_R44_LLD_GUEST_EXECUTION=FAILED' >&2
  sed -n '1,30p' "$r44_test_log" >&2;exit 69;
fi
[ -s "$r44_test_log" ] || { echo 'APPFORGE_R44_LLD_EMPTY_VERSION' >&2;exit 69; }
sed -n '1,3p' "$r44_test_log"
echo 'APPFORGE_R44_LLD_GUEST_EXECUTION=PASS'
printf '%s\n' guest-lld-direct > "$PREFIX/lld-launch-mode-r44"
[ -s "$PREFIX/lld-launch-mode-r44" ] &&
[ ! -L "$PREFIX/lld-launch-mode-r44" ] || exit 69
# Test the normal NDK entry that will be called by the app's `-fuse-ld=lld`.
if ! "$BIN/ld.lld" --version > "$r44_test_log" 2>&1;then
  echo 'APPFORGE_R44_LLD_WRAPPER_EXECUTION=FAILED' >&2
  sed -n '1,30p' "$r44_test_log" >&2;exit 69;
fi
echo 'APPFORGE_R44_LLD_WRAPPER_EXECUTION=PASS'
# R48: only this isolated candidate rootfs and signed private sysroot.
r48_signed_ar="$PREFIX/sysroot/usr/lib/llvm-18/bin/llvm-ar"
r48_guest_ar=/usr/lib/llvm-18/bin/llvm-ar
r48_log="$probe_dir/r48-llvm-ar.log"
echo 'APPFORGE_R48_GUEST_AR_PROBE=START'
[ -f "$r48_signed_ar" ] && [ ! -L "$r48_signed_ar" ] &&
[ -x "$r48_signed_ar" ] &&
file -L "$r48_signed_ar" | grep -Eqi 'aarch64|ARM aarch64' || {
  echo 'APPFORGE_R48_SIGNED_AR_INVALID' >&2;exit 69;
}
if [ -e "$r48_guest_ar" ] || [ -L "$r48_guest_ar" ];then
  [ -f "$r48_guest_ar" ] && [ ! -L "$r48_guest_ar" ] &&
  cmp -s "$r48_signed_ar" "$r48_guest_ar" || {
    echo 'APPFORGE_R48_GUEST_AR_COLLISION_OR_IDENTITY_MISMATCH' >&2;exit 69;
  }
  echo 'APPFORGE_R48_GUEST_AR_EXISTING=VERIFIED'
else
  # The source is package-authenticated, staged private bytes only.
  cp -p "$r48_signed_ar" "$r48_guest_ar" &&
  [ -f "$r48_guest_ar" ] && [ ! -L "$r48_guest_ar" ] &&
  cmp -s "$r48_signed_ar" "$r48_guest_ar" || {
    echo 'APPFORGE_R48_GUEST_AR_COPY_FAILED' >&2;exit 69;
  }
  echo 'APPFORGE_R48_GUEST_AR_COPIED=VERIFIED'
fi
printf '%s\n' guest-llvm-ar-direct > "$PREFIX/llvm-ar-launch-mode-r48"
[ -f "$PREFIX/llvm-ar-launch-mode-r48" ] &&
[ ! -L "$PREFIX/llvm-ar-launch-mode-r48" ] || exit 69
if ! LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/usr/lib/llvm-18/lib:/lib/aarch64-linux-gnu" \
  "$r48_guest_ar" --version >"$r48_log" 2>&1;then
  echo 'APPFORGE_R48_GUEST_AR_EXECUTION=FAILED' >&2
  sed -n '1,18p' "$r48_log" | cut -c 1-220 >&2
  exit 69
fi
[ -s "$r48_log" ] || { echo 'APPFORGE_R48_GUEST_AR_VERSION_EMPTY' >&2;exit 69; }
echo 'APPFORGE_R48_GUEST_AR_EXECUTION=PASS'
# Prove this wrapper really reads a file from the translated guest /opt path.
[ -s "$probe_dir/readable.c" ] && [ ! -L "$probe_dir/readable.c" ] || {
  echo 'APPFORGE_R48_REAL_AR_INPUT_MISSING' >&2;exit 69;
}
if ! "$BIN/llvm-ar" rcs "$probe_dir/r48-test.a" "$probe_dir/readable.c" \
  >"$r48_log" 2>&1;then
  echo 'APPFORGE_R48_REAL_AR_ARCHIVE=FAILED' >&2
  sed -n '1,18p' "$r48_log" | cut -c 1-220 >&2;exit 69
fi
[ -s "$probe_dir/r48-test.a" ] && [ ! -L "$probe_dir/r48-test.a" ] || {
  echo 'APPFORGE_R48_REAL_AR_ARCHIVE_EMPTY' >&2;exit 69;
}
if ! "$BIN/llvm-ar" t "$probe_dir/r48-test.a" \
  >"$r48_log" 2>&1;then
  echo 'APPFORGE_R48_REAL_AR_LIST=FAILED' >&2
  sed -n '1,18p' "$r48_log" | cut -c 1-220 >&2;exit 69
fi
[ "$(cat "$r48_log")" = readable.c ] || {
  echo 'APPFORGE_R48_REAL_AR_ARCHIVE_MEMBER_INVALID' >&2;exit 69;
}
echo 'APPFORGE_R48_REAL_AR_ARCHIVE=PASS'
echo 'APPFORGE_R48_AR_WRAPPER_EXECUTION=PASS'
rm -rf -- "$probe_dir"
trap - 0 HUP INT TERM
[ "$(dpkg-query -W -f='${Version}' libc6)" = '2.39-0ubuntu8.7' ] || exit 67
printf 'APPFORGE_NDK_HOST_EXPLICIT_LOADER_WITH_GUEST_EXE=PASS\n'
printf 'APPFORGE_NDK_HOST_PRIVATE_TOOLS=PASS\nAPPFORGE_NDK_HOST_GLIBC_2_39_8_7_PRESERVED=PASS\nAPPFORGE_NDK_HOST_SIGNED_PACKAGES=69\n'
