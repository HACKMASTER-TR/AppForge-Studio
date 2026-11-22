"""R41: only signed isolated guest clang and real file-path read may authorize candidate."""
import pathlib, unittest, subprocess
R=pathlib.Path(__file__).resolve().parents[1]
S=(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r41-unsigned-apk-ci.yml').read_text()
class R41(unittest.TestCase):
    def test_shell(self):
        p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh')],capture_output=True,text=True)
        self.assertEqual(p.returncode,0,p.stderr)
    def test_guest_wrapper_scoped(self):
        self.assertEqual(S.count('if [ "$name" = clang ] && [ -f "$prefix/clang-launch-mode-r41" ];then'),1)
        self.assertIn('exec /usr/lib/llvm-18/bin/clang "$@"',S)
        self.assertIn('APPFORGE_R41_CLANG_GUEST_SOURCE_READ=PASS',S)
        self.assertIn('APPFORGE_R41_CLANG_GUEST_SOURCE_READ=FAILED',S)
        self.assertIn('"$probe_dir/readable.c"',S)
        self.assertNotIn('cat "$probe_dir/readable.c" | "$BIN/clang"',S)
    def test_signed_dependency_closure(self):
        for frag in ('APPFORGE_R41_CLANG_SIGNED_ELF_MISSING','APPFORGE_R41_CLANG_RESOURCE_DIR_MISSING',
                     'APPFORGE_R41_CLANG_GUEST_PATH_OCCUPIED','APPFORGE_R41_CLANG_DEPENDENCY_COPY_FAILED',
                     'APPFORGE_R41_CLANG_GUEST_CLOSURE=VERIFIED','cmp -s "$r41_clang_signed" "$r41_clang_guest"'):
            self.assertIn(frag,S)
    def test_no_false_ready(self):
        self.assertLess(S.index('APPFORGE_R41_CLANG_GUEST_SOURCE_READ=PASS'),S.index('"$BIN/clang" --target=aarch64-unknown-linux-gnu -x c -fsyntax-only "$probe_dir/readable.c"'))
        self.assertIn('APPFORGE_NDK_HOST_SOURCE_READ_PROBE_FAILED',S)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_MODULES=PASS',S)
        self.assertIn('APPFORGE_R33_CMAKE_GUEST_REAL_P_TEST=PASS',S)
    def test_diagnostic_and_identity(self):
        self.assertIn('r41-clang-diagnostics',K)
        self.assertIn('appforgeR41OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(564)',G)
        self.assertIn('VERSION_CODE=564',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
