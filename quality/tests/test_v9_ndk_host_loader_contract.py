"""V8 regression: signed libedit payload, private SONAME, explicit trusted glibc loader."""
import pathlib,unittest
ROOT=pathlib.Path(__file__).resolve().parents[2]
DEV=ROOT/'android-app/app/src/main/assets/device-build'
FLOW=ROOT/'.github/workflows/v9-offline-module-center-r2.yml'

class NdkHostLoaderContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script=(DEV/'install-ndk-host-isolated-v7.sh').read_text()

    def test_signed_libedit_package_and_soname_checked(self):
        s=self.script
        self.assertIn('as_verify_indices',s)
        self.assertIn('EXPECTED_CATALOG=77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f',s)
        self.assertIn('sha256sum -c -',s)
        self.assertIn('LIBEDIT_REAL="$LIBDIR/libedit.so.2.0.72"',s)
        self.assertIn('LIBEDIT_SONAME="$LIBDIR/libedit.so.2"',s)
        self.assertIn('"$(readlink "$LIBEDIT_SONAME")" = libedit.so.2.0.72',s)
        self.assertIn('APPFORGE_NDK_LIBEDIT_ARM64_ABI_MISMATCH',s)
        self.assertIn('APPFORGE_NDK_LIBEDIT_PRIVATE_SONAME=PASS',s)
        self.assertLess(s.index('dpkg-deb -x '),s.index('LIBEDIT_REAL='))

    def test_no_shared_loader_or_apt_mutation(self):
        s=self.script
        for snippet in ('[ "${APPFORGE_R2_RELEASE_AUTHORIZED:-0}" = 1 ]',
                        "'2.39-0ubuntu8.7'",'libc6|libc-bin|libc6-dev|libc-dev-bin',
                        'PREFIX=/opt/appforge-device/ndk-host-v7',
                        'libpath="$sysroot/usr/lib/llvm-18/lib:',
                        'interpreter=/lib/ld-linux-aarch64.so.1',
                        'exec "$interpreter" --library-path "$libpath" "$executable" "$@"'):
            self.assertIn(snippet,s)
        for forbidden in ('apt-get install','dpkg -i','dpkg --force','LD_PRELOAD=',
                          'Acquire::https::Verify-Peer=false'):
            self.assertNotIn(forbidden,s)
        self.assertIn('mv -f "$LIBDIR/.libedit.so.2.v8.$$" "$LIBEDIT_SONAME"',s)

    def test_real_tool_execution_and_link_tests_still_required(self):
        s=self.script
        tools=(DEV/'install-toolchain.sh').read_text()
        self.assertIn('APPFORGE_NDK_HOST_EXECUTION_FAILED',s)
        for cmd in ('clang','ld.lld','cmake','ninja','llvm-ar'):
            self.assertIn('"$BIN/'+cmd+'" --version',s)
        for token in ('APPFORGE_EXPO_ARM64_NDK_COMPILER_RT_LINK=PASS',
                      'APPFORGE_EXPO_ARM64_NDK_CXX_LINK=PASS',
                      'APPFORGE_EXPO_NDK_C_PROBE_ABI_FAIL',
                      'APPFORGE_EXPO_NDK_CPP_LINK_ABI_FAIL'):
            self.assertIn(token,tools)
        self.assertNotIn('touch "$READY"\nexit 0',tools)

    def test_v8_ci_branch_in_both_isolated_apks(self):
        s=FLOW.read_text()
        branch='codex/v9-r2-ndk-libedit-loader-20261110'
        self.assertIn(branch+']',s)
        self.assertEqual(s.count("github.ref == 'refs/heads/"+branch+"'"),2)
        self.assertIn('codex/v9-r2-ndk-isolated-host-20261110',s)

if __name__=='__main__':unittest.main()
