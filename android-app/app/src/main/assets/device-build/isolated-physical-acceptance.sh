#!/bin/sh
# Actual isolated rootfs test, invoked INSIDE the selected module's PRoot.
# Creates only private /tmp files; never inspects user projects or foreign modules.
set -eu
MODULE=${1:?module id required}
ROOT=/opt/appforge-device
WORK=$(mktemp -d /tmp/appforge-isolated-acceptance.XXXXXXXX)
trap 'rm -rf -- "$WORK"' EXIT HUP INT TERM
case "$MODULE" in
    RUST_ANDROID_NDK|EXPO_REACT_NATIVE)
        # Running an actual CMake script validates dynamic ELF linking and CMAKE_ROOT.
        CMAKE="$ROOT/android-sdk/cmake/3.22.1/bin/cmake"
        cat > "$WORK/probe.cmake" <<'CMAKE_PROBE'
cmake_minimum_required(VERSION 3.22)
if (NOT CMAKE_ROOT OR NOT EXISTS "${CMAKE_ROOT}/Modules/CMake.cmake")
    message(FATAL_ERROR "APPFORGE_CMAKE_ROOT_INVALID")
endif()
message("APPFORGE_CMAKE_MODULES=PASS")
CMAKE_PROBE
        "$CMAKE" -P "$WORK/probe.cmake" > "$WORK/cmake.log" 2>&1 || {
            cat "$WORK/cmake.log" >&2
            echo 'APPFORGE_CMAKE_LOADER_OR_MODULES=FAIL' >&2
            exit 68
        }
        grep -Fq 'APPFORGE_CMAKE_MODULES=PASS' "$WORK/cmake.log" || {
            echo 'APPFORGE_CMAKE_MODULES_MISSING' >&2; exit 68;
        }
        echo 'APPFORGE_NDK_HOST_CMAKE_MODULES=PASS'
        ;;
    WINDOWS_NATIVE)
        printf '%s\n' '#include <windows.h>' 'int main() { return sizeof(void*) == 8 ? 0 : 1; }' > "$WORK/main.cpp"
        /usr/bin/x86_64-w64-mingw32-g++-posix "$WORK/main.cpp" -o "$WORK/probe.exe"
        test -s "$WORK/probe.exe"
        file "$WORK/probe.exe" | grep -Fq 'PE32+ executable' || exit 69
        echo 'APPFORGE_WINDOWS_NATIVE_CPP_PE32=PASS'
        ;;
    NODE_WEB|PYTHON_ANDROID|ANDROID_CORE|RUST)
        # Their existing module-functional-test.sh did the real compile/runtime work.
        : ;;
    *) echo "APPFORGE_ISOLATED_UNSUPPORTED:$MODULE" >&2; exit 60 ;;
esac
echo "APPFORGE_ISOLATED_FUNCTIONAL_PASS:$MODULE"
