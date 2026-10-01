#!/bin/sh
set -eu

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

versioned_gcc() {
  variant="$1"
  result=""

  for candidate in \
    /usr/bin/x86_64-w64-mingw32-gcc-[0-9]*-"$variant"
  do
    if [ -x "$candidate" ]; then
      result="$candidate"
    fi
  done

  [ -n "$result" ] || return 1
  printf '%s\n' "$result"
}

repair_variant_links() {
  variant="$1"

  gcc_real="$(
    versioned_gcc "$variant" 2>/dev/null ||
    true
  )"

  [ -n "$gcc_real" ] || return 0

  gcc_link="/usr/bin/x86_64-w64-mingw32-gcc-$variant"
  gxx_link="/usr/bin/x86_64-w64-mingw32-g++-$variant"
  cxx_link="/usr/bin/x86_64-w64-mingw32-c++-$variant"

  if [ ! -e "$gcc_link" ]; then
    rm -f "$gcc_link"
    ln -s "$(basename "$gcc_real")" "$gcc_link"
  fi

  # Debian/Ubuntu MinGW g++ is reached through the g++ driver name.
  # Repair only missing/broken unpacked links; valid package files stay intact.
  if [ ! -e "$gxx_link" ]; then
    rm -f "$gxx_link"
    ln -s "$(basename "$gcc_real")" "$gxx_link"
  fi

  if [ ! -e "$cxx_link" ]; then
    rm -f "$cxx_link"
    ln -s "$(basename "$gcc_real")" "$cxx_link"
  fi
}

repair_mingw_links() {
  repair_variant_links posix
  repair_variant_links win32
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

if have_tools; then
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

  apt-get \
    -f \
    install \
    -y \
    --no-install-recommends

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
