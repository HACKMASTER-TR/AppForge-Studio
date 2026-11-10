"""V9 regression: real ELF exec preserves /proc/self/exe and keeps NDK compile/link tests strict."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'android-app/app/src/main/assets/device-build'
HOST = ASSETS / 'install-ndk-host-isolated-v7.sh'
TOOLS = ASSETS / 'install-toolchain.sh'
WORKFLOW = ROOT / '.github/workflows/v9-offline-module-center-r2.yml'


class NdkDirectExecContract(unittest.TestCase):
    def test_private_signed_glibc_baseline_remains_immutable(self):
        s = HOST.read_text()
        for needle in (
            'as_verify_indices',
            'EXPECTED_CATALOG=77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f',
            'sha256sum -c -',
            'dpkg-deb -x',
            'PREFIX=/opt/appforge-device/ndk-host-v7',
            'libc6|libc-bin|libc6-dev|libc-dev-bin',
            "'2.39-0ubuntu8.7'",
            'APPFORGE_NDK_HOST_PRIVATE_TOOLS=PASS',
        ):
            self.assertIn(needle, s)
        for forbidden in ('apt-get install', 'dpkg -i', 'LD_PRELOAD=',
                          'Acquire::https::Verify-Peer=false'):
            self.assertNotIn(forbidden, s)

    def test_v8_signed_libedit_payload_is_preserved(self):
        s = HOST.read_text()
        self.assertIn('LIBEDIT_REAL="$LIBDIR/libedit.so.2.0.72"', s)
        self.assertIn('LIBEDIT_SONAME="$LIBDIR/libedit.so.2"', s)
        self.assertIn('APPFORGE_NDK_LIBEDIT_PRIVATE_SONAME=PASS', s)
        self.assertIn('mv -f "$LIBDIR/.libedit.so.2.v8.$$" "$LIBEDIT_SONAME"', s)
        self.assertLess(s.index('dpkg-deb -x'), s.index('LIBEDIT_REAL='))

    def test_exec_real_elf_not_loader_as_program(self):
        s = HOST.read_text()
        self.assertIn('interpreter=/lib/ld-linux-aarch64.so.1', s)
        self.assertIn('export LD_LIBRARY_PATH="$libpath"', s)
        self.assertIn('exec "$executable" "$@"', s)
        self.assertNotIn('exec "$interpreter" --library-path', s)
        self.assertNotIn('APPFORGE_NDK_HOST_EXPLICIT_LOADER=PASS', s)
        self.assertIn('APPFORGE_NDK_HOST_DIRECT_ELF_EXEC=PASS', s)

    def test_cmake_modules_and_source_file_are_actually_exercised(self):
        s = HOST.read_text()
        for needle in ('APPFORGE_NDK_HOST_CMAKE_MODULES_MISSING',
                       'CMakeDetermineSystem.cmake',
                       '${CMAKE_ROOT}/Modules/CMakeDetermineSystem.cmake',
                       '"$BIN/cmake" -P "$probe_dir/check.cmake"',
                       'APPFORGE_NDK_HOST_CMAKE_MODULES=PASS',
                       '"$BIN/clang" --target=aarch64-unknown-linux-gnu -x c -fsyntax-only "$probe_dir/readable.c"',
                       'APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS'):
            self.assertIn(needle, s)
        self.assertLess(s.index('APPFORGE_NDK_HOST_CMAKE_MODULES=PASS'),
                        s.index('APPFORGE_NDK_HOST_DIRECT_ELF_EXEC=PASS'))

    def test_real_android_target_c_cpp_link_probes_remain_required(self):
        s = TOOLS.read_text()
        for token in ('APPFORGE_NDK_C_PROBE_SOURCE_MISSING',
                      'APPFORGE_NDK_CPP_PROBE_SOURCE_MISSING',
                      'APPFORGE_EXPO_ARM64_NDK_COMPILER_RT_LINK=PASS',
                      'APPFORGE_EXPO_ARM64_NDK_CXX_LINK=PASS',
                      '-fuse-ld=lld',
                      'APPFORGE_EXPO_NDK_C_PROBE_ABI_FAIL',
                      'APPFORGE_EXPO_NDK_CPP_LINK_ABI_FAIL'):
            self.assertIn(token, s)
        self.assertIn('"$bin/clang"', s)
        self.assertIn('"$bin/clang++"', s)
        self.assertIn('APPFORGE_DEVICE_TOOLCHAIN_READY', s)

    def test_v9_ci_runs_both_isolated_signing_variants(self):
        s = WORKFLOW.read_text()
        branch = 'codex/v9-r2-ndk-direct-exec-20261110'
        self.assertIn(branch, s)
        self.assertEqual(s.count("github.ref == 'refs/heads/" + branch + "'"), 2)
        self.assertIn('codex/v9-r2-ndk-libedit-loader-20261110', s)


if __name__ == '__main__':
    unittest.main()
