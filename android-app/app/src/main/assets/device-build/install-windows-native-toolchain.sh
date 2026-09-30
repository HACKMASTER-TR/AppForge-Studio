#!/bin/sh
set -eu

ROOT="/opt/appforge-device/windows-native-v1"
READY="$ROOT/.ready"
OFFLINE="${APPFORGE_DEVICE_OFFLINE:-0}"

mkdir -p "$ROOT"

have_tools() {
  command -v cmake >/dev/null 2>&1 &&
  command -v ninja >/dev/null 2>&1 &&
  command -v x86_64-w64-mingw32-gcc >/dev/null 2>&1 &&
  command -v x86_64-w64-mingw32-g++ >/dev/null 2>&1 &&
  command -v x86_64-w64-mingw32-windres >/dev/null 2>&1 &&
  command -v file >/dev/null 2>&1
}

verify_smoke() {
  smoke="$ROOT/smoke"
  rm -rf "$smoke"
  mkdir -p "$smoke"
  cat > "$smoke/main.cpp" <<'EOF'
#include <windows.h>
int WINAPI WinMain(HINSTANCE, HINSTANCE, LPSTR, int) { return 0; }
EOF
  x86_64-w64-mingw32-g++ -O2 -s -static-libgcc -static-libstdc++ \
    "$smoke/main.cpp" -o "$smoke/appforge-windows-native-smoke.exe"
  test -s "$smoke/appforge-windows-native-smoke.exe"
  file "$smoke/appforge-windows-native-smoke.exe" | grep -q 'PE32+ executable'
  rm -rf "$smoke"
}

if have_tools; then
  verify_smoke
  printf '%s\n' 'windows-native-v1' > "$READY"
  echo "APPFORGE_WINDOWS_NATIVE_TOOLCHAIN_READY"
  exit 0
fi

if [ "$OFFLINE" = "1" ]; then
  echo "APPFORGE_WINDOWS_NATIVE_OFFLINE_TOOLCHAIN_MISSING" >&2
  echo "CMake/Ninja/MinGW-w64 x64 araçları önceden kurulmalı." >&2
  exit 42
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates \
  cmake \
  ninja-build \
  file \
  binutils-mingw-w64-x86-64 \
  gcc-mingw-w64-x86-64 \
  g++-mingw-w64-x86-64 \
  mingw-w64-tools

have_tools
verify_smoke
printf '%s\n' 'windows-native-v1' > "$READY"

echo "APPFORGE_WINDOWS_NATIVE_TOOLCHAIN_INSTALL=PASS"
