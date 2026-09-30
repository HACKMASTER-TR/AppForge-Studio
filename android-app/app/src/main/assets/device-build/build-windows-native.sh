#!/bin/sh
set -eu

SOURCE="${APPFORGE_WINDOWS_NATIVE_SOURCE:-/workspace/source}"
BUILD="/workspace/windows-native-build"
OUT="/workspace/windows-native-out"
MARKER="/workspace/.appforge-windows-native-output"
TOOLCHAIN="/workspace/runtime/windows-native-mingw-x64.cmake"

case "$SOURCE" in
  /workspace/*|/workspace) ;;
  *) echo "APPFORGE_WINDOWS_NATIVE_SOURCE_ESCAPE" >&2; exit 71 ;;
esac

test -f "$SOURCE/CMakeLists.txt" || {
  echo "APPFORGE_WINDOWS_NATIVE_CMAKELISTS_MISSING:$SOURCE" >&2
  exit 72
}

command -v cmake >/dev/null
command -v ninja >/dev/null
command -v x86_64-w64-mingw32-g++ >/dev/null
command -v x86_64-w64-mingw32-windres >/dev/null

rm -rf "$BUILD" "$OUT" "$MARKER"
mkdir -p "$BUILD" "$OUT"

cat > "$TOOLCHAIN" <<'EOF'
set(CMAKE_SYSTEM_NAME Windows)
set(CMAKE_SYSTEM_PROCESSOR x86_64)
set(CMAKE_C_COMPILER x86_64-w64-mingw32-gcc)
set(CMAKE_CXX_COMPILER x86_64-w64-mingw32-g++)
set(CMAKE_RC_COMPILER x86_64-w64-mingw32-windres)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
EOF

cmake \
  -S "$SOURCE" \
  -B "$BUILD" \
  -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$TOOLCHAIN" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_RUNTIME_OUTPUT_DIRECTORY="$OUT" \
  -DAPPFORGE_WINDOWS_NATIVE=ON

cmake --build "$BUILD" --parallel 2

LIST="$BUILD/.appforge-native-exes.txt"
find "$OUT" -type f -iname '*.exe' -print | sort > "$LIST"
COUNT="$(wc -l < "$LIST" | tr -d ' ')"

if [ "$COUNT" -eq 0 ]; then
  echo "APPFORGE_WINDOWS_NATIVE_EXE_NOT_FOUND" >&2
  exit 73
fi

if [ "$COUNT" -ne 1 ]; then
  echo "APPFORGE_WINDOWS_NATIVE_EXE_AMBIGUOUS:$COUNT" >&2
  cat "$LIST" >&2
  echo "V1 için tek executable CMake target üret. Çoklu target seçimi sonraki kontratta açılacak." >&2
  exit 74
fi

EXE="$(cat "$LIST")"
test -s "$EXE"
file "$EXE" | grep -q 'PE32+ executable' || {
  echo "APPFORGE_WINDOWS_NATIVE_NOT_X64_PE" >&2
  file "$EXE" >&2 || true
  exit 75
}

REL="${EXE#/workspace/}"
case "$REL" in
  /*|../*|*/../*) echo "APPFORGE_WINDOWS_NATIVE_OUTPUT_ESCAPE" >&2; exit 76 ;;
esac
printf '%s\n' "$REL" > "$MARKER"

echo "APPFORGE_WINDOWS_NATIVE_BUILD=PASS"
echo "APPFORGE_WINDOWS_NATIVE_OUTPUT=$REL"
