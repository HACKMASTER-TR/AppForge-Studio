#!/bin/sh
set -eu
export APPFORGE_MODULE_ID="${APPFORGE_MODULE_ID:-WINDOWS_NATIVE}"
if [ -f /workspace/runtime/module-download-telemetry.sh ]; then
  . /workspace/runtime/module-download-telemetry.sh
fi

ROOT="/opt/appforge-device/windows-native-v1"
READY="$ROOT/.ready"
OFFLINE="${APPFORGE_DEVICE_OFFLINE:-0}"

CC="x86_64-w64-mingw32-gcc-posix"
CXX="x86_64-w64-mingw32-g++-posix"

mkdir -p "$ROOT"

have_tools() {
  command -v cmake >/dev/null 2>&1 &&
  command -v ninja >/dev/null 2>&1 &&
  command -v "$CC" >/dev/null 2>&1 &&
  command -v "$CXX" >/dev/null 2>&1 &&
  command -v x86_64-w64-mingw32-windres >/dev/null 2>&1 &&
  command -v file >/dev/null 2>&1
}

# Ubuntu's g++-mingw POSIX postinst registers the package's own g++ driver
# with update-alternatives. An interrupted unpack can leave that file absent.
# Restore ONLY its signed, version-pinned package file in the disposable rootfs.
# Never substitute the C driver for a missing C++ driver, and never force dpkg.
repair_mingw_links() {
  target=/usr/bin/x86_64-w64-mingw32-g++-posix
  [ -x "$target" ] && return 0
  [ ! -e "$target" ] && [ ! -L "$target" ] || {
    echo 'APPFORGE_WINDOWS_GXX_EXISTING_PATH_UNTRUSTED' >&2;return 43;
  }
  [ "$(dpkg --print-architecture)" = arm64 ] || return 43
  [ "${APPFORGE_R2_RELEASE_AUTHORIZED:-0}" = 1 ] || return 43
  archive=/opt/appforge-device/apt-snapshot-v1/arm64-20261018T000000Z/archives/8314d76080dc9979f8ebcfb2d70852804a6b765e38028990d8ff67ced712c99d.deb
  [ -f "$archive" ] && [ ! -L "$archive" ] || {
    echo 'APPFORGE_WINDOWS_GXX_SIGNED_ARCHIVE_MISSING' >&2;return 44;
  }
  printf '%s  %s\n' 8314d76080dc9979f8ebcfb2d70852804a6b765e38028990d8ff67ced712c99d "$archive" | sha256sum -c - >/dev/null || return 44
  [ "$(dpkg-deb -f "$archive" Package)" = g++-mingw-w64-x86-64-posix ] || return 44
  [ "$(dpkg-deb -f "$archive" Version)" = '13.2.0-6ubuntu1+26.1' ] || return 44
  [ "$(dpkg-deb -f "$archive" Architecture)" = arm64 ] || return 44
  recover=$(mktemp -d /tmp/appforge-signed-gxx.XXXXXXXX) || return 44
  dpkg-deb -x "$archive" "$recover" || { rm -rf -- "$recover";return 44; }
  source_driver="$recover/usr/bin/x86_64-w64-mingw32-g++-posix"
  if [ -L "$source_driver" ];then
    link=$(readlink "$source_driver") || { rm -rf -- "$recover";return 44; }
    case "$link" in x86_64-w64-mingw32-g++-[0-9]*-posix) ;; *) rm -rf -- "$recover";return 44;;esac
    [ -x "/usr/bin/$link" ] || { echo 'APPFORGE_WINDOWS_GXX_TARGET_MISSING' >&2;rm -rf -- "$recover";return 44; }
    ln -s "$link" "/usr/bin/.appforge-gxx-v540.$$" || { rm -rf -- "$recover";return 44; }
    mv -T "/usr/bin/.appforge-gxx-v540.$$" "$target" || { rm -rf -- "$recover";return 44; }
  elif [ -f "$source_driver" ] && [ ! -L "$source_driver" ] && [ -x "$source_driver" ];then
    file -L "$source_driver" | grep -Eqi 'aarch64|ARM aarch64' || { rm -rf -- "$recover";return 44; }
    cp -p "$source_driver" "/usr/bin/.appforge-gxx-v540.$$" || { rm -rf -- "$recover";return 44; }
    cmp -s "$source_driver" "/usr/bin/.appforge-gxx-v540.$$" || { rm -rf -- "$recover";return 44; }
    mv -T "/usr/bin/.appforge-gxx-v540.$$" "$target" || { rm -rf -- "$recover";return 44; }
  else
    echo 'APPFORGE_WINDOWS_GXX_AUTHENTIC_FILE_NOT_FOUND' >&2
    rm -rf -- "$recover";return 44
  fi
  rm -rf -- "$recover"
  [ -x "$target" ] || return 44
  echo 'APPFORGE_WINDOWS_GXX_SIGNED_DRIVER_RESTORED=PASS'
}

repair_interrupted_dpkg() {
  audit="$(
    dpkg --audit 2>/dev/null ||
    true
  )"

  if [ -z "$audit" ]; then
    return 0
  fi

  if ! printf '%s\n' "$audit" |
       grep -Eq 'mingw-w64|gcc-mingw|g\+\+-mingw'
  then
    echo "APPFORGE_WINDOWS_NATIVE_DPKG_UNRELATED_BROKEN_STATE" >&2
    printf '%s\n' "$audit" >&2
    return 43
  fi

  echo "APPFORGE_WINDOWS_NATIVE_DPKG_REPAIR=START"

  repair_mingw_links
  # Satisfy the C compiler dependency before g++'s postinst alternatives.
  # Already-configured packages must not be needlessly reconfigured.
  gcc_state=$(dpkg-query -W -f='${db:Status-Abbrev}' gcc-mingw-w64-x86-64-posix 2>/dev/null || true)
  case "$gcc_state" in
    ii*) ;;
    iU*|iF*|iH*)
      dpkg --configure gcc-mingw-w64-x86-64-posix || {
        echo 'APPFORGE_WINDOWS_GCC_POSIX_CONFIGURE_BLOCKED' >&2;return 44;
      };;
    *) echo 'APPFORGE_WINDOWS_GCC_POSIX_STATE_UNVERIFIED' >&2;return 44;;
  esac
  gxx_state=$(dpkg-query -W -f='${db:Status-Abbrev}' g++-mingw-w64-x86-64-posix 2>/dev/null || true)
  case "$gxx_state" in
    ii*) ;;
    iU*|iF*|iH*)
      dpkg --configure g++-mingw-w64-x86-64-posix || {
        echo 'APPFORGE_WINDOWS_GXX_POSIX_CONFIGURE_BLOCKED' >&2;return 44;
      };;
    *) echo 'APPFORGE_WINDOWS_GXX_POSIX_STATE_UNVERIFIED' >&2;return 44;;
  esac
  dpkg --configure -a

  echo "APPFORGE_WINDOWS_NATIVE_DPKG_REPAIR=PASS"
}

verify_smoke() {
  smoke="$ROOT/smoke"

  rm -rf "$smoke"
  mkdir -p "$smoke"

  cat > "$smoke/main.cpp" <<'CPP'
#include <windows.h>

int WINAPI WinMain(
    HINSTANCE,
    HINSTANCE,
    LPSTR,
    int
) {
    return 0;
}
CPP

  "$CXX" \
    -O2 \
    -s \
    -static-libgcc \
    -static-libstdc++ \
    "$smoke/main.cpp" \
    -o "$smoke/appforge-windows-native-smoke.exe"

  test -s "$smoke/appforge-windows-native-smoke.exe"

  file "$smoke/appforge-windows-native-smoke.exe" |
    grep -q 'PE32+ executable'

  rm -rf "$smoke"
}

repair_interrupted_dpkg

WINDOWS_LOCK="/opt/appforge-device/apt-locks-v5/WINDOWS_NATIVE/snapshot-v1"
have_windows_lock() {
  [ -d "$WINDOWS_LOCK" ] && [ ! -L "$WINDOWS_LOCK" ] || return 1
  find "$WINDOWS_LOCK" -mindepth 2 -maxdepth 2 -name installed.tsv -type f -print -quit | grep -q .
}

if [ "${APPFORGE_MODULE_REFRESH:-0}" != 1 ] && have_tools && have_windows_lock; then
  verify_smoke

  printf '%s\n' \
    'windows-native-v1' \
    > "$READY"

  echo "APPFORGE_WINDOWS_NATIVE_TOOLCHAIN_READY"
  exit 0
fi

if [ "$OFFLINE" = "1" ]; then
  echo "APPFORGE_WINDOWS_NATIVE_OFFLINE_TOOLCHAIN_MISSING" >&2
  echo "CMake/Ninja/MinGW-w64 POSIX x64 araçları önceden kurulmalı." >&2
  exit 42
fi

export DEBIAN_FRONTEND=noninteractive

apt-get update

install_packages() {
  apt-get install \
    -y \
    --no-install-recommends \
    --reinstall \
    ca-certificates \
    cmake \
    ninja-build \
    file \
    binutils-mingw-w64-x86-64 \
    gcc-mingw-w64-x86-64-posix \
    g++-mingw-w64-x86-64-posix \
    mingw-w64-x86-64-dev \
    mingw-w64-tools
}

if ! install_packages; then
  echo "APPFORGE_WINDOWS_NATIVE_APT_REPAIR=START"

  repair_mingw_links

  dpkg --configure -a

  # Only the requested pinned packages may be retried.
  install_packages

  echo "APPFORGE_WINDOWS_NATIVE_APT_REPAIR=PASS"
fi

repair_mingw_links

have_tools

verify_smoke

printf '%s\n' \
  'windows-native-v1' \
  > "$READY"

echo "APPFORGE_WINDOWS_NATIVE_TOOLCHAIN_INSTALL=PASS"
