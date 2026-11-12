"""V10: PRoot explicit loader plus correctly emulated guest /proc/self/exe."""
from pathlib import Path
import unittest
R=Path(__file__).resolve().parents[2]
T=R/'android-app/app/src/main/assets/device-build'
HOST=T/'install-ndk-host-isolated-v7.sh'
FLOW=R/'.github/workflows/v9-offline-module-center-r2.yml'

class NdkGuestExeContract(unittest.TestCase):
    def test_private_llvm_is_explicitly_loaded(self):
        s=HOST.read_text()
        self.assertIn('export PROROOT_GUEST_EXE="$executable"',s)
        self.assertIn('exec "$interpreter" --library-path "$libpath" "$executable" "$@"',s)
        self.assertLess(s.index('export PROROOT_GUEST_EXE='),s.index('exec "$interpreter" --library-path'))
        self.assertNotIn('exec "$executable" "$@"',s)
        self.assertIn('APPFORGE_NDK_HOST_EXPLICIT_LOADER_WITH_GUEST_EXE=PASS',s)

    def test_isolation_and_integrity_guards(self):
        s=HOST.read_text()
        for item in ('APPFORGE_R2_RELEASE_AUTHORIZED','as_verify_indices',
                     'sha256sum -c -','libc6|libc-bin|libc6-dev|libc-dev-bin',
                     "'2.39-0ubuntu8.7'",'APPFORGE_NDK_LIBEDIT_PRIVATE_SONAME=PASS',
                     'APPFORGE_NDK_HOST_SIGNED_PACKAGES=69'):
            self.assertIn(item,s)
        for bad in ('apt-get install','dpkg -i','curl -k','LD_PRELOAD=',
                    'Acquire::https::Verify-Peer=false'):
            self.assertNotIn(bad,s)

    def test_pinned_proroot_binary_advertises_guest_exe(self):
        binary=R/'android-app/app/src/main/proroot-pinned/arm64-v8a/libproroot-runtime.so'
        self.assertTrue(binary.is_file())
        self.assertIn(b'PROROOT_GUEST_EXE',binary.read_bytes())

    def test_cmake_clang_and_android_real_probes(self):
        s=HOST.read_text()
        t=(T/'install-toolchain.sh').read_text()
        for item in ('"$BIN/cmake" -P "$probe_dir/check.cmake"',
                     '"$BIN/clang" --target=aarch64-unknown-linux-gnu -x c -fsyntax-only',
                     'APPFORGE_NDK_HOST_CMAKE_MODULES=PASS',
                     'APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS'):
            self.assertIn(item,s)
        for item in ('APPFORGE_EXPO_ARM64_NDK_COMPILER_RT_LINK=PASS',
                     'APPFORGE_EXPO_ARM64_NDK_CXX_LINK=PASS',
                     '-fuse-ld=lld','APPFORGE_NDK_C_PROBE_SOURCE_MISSING',
                     'APPFORGE_NDK_CPP_PROBE_SOURCE_MISSING'):
            self.assertIn(item,t)

    def test_both_isolated_ci_variants(self):
        s=FLOW.read_text()
        b='codex/v9-r2-ndk-guest-exe-20261110'
        self.assertIn(b+']',s)
        self.assertEqual(s.count("github.ref == 'refs/heads/"+b+"'"),2)

if __name__=='__main__':unittest.main()
