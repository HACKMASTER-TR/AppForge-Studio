"""R44: guest-native signed LLD, dependencies, real execution gates and no false ready."""
from pathlib import Path
import subprocess, unittest
R=Path(__file__).resolve().parents[1]
H=(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
S=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r44-unsigned-apk-ci.yml').read_text()
class R44(unittest.TestCase):
    def test_shell_syntax(self):
        for script in ('install-toolchain.sh','install-ndk-host-isolated-v7.sh'):
            proc=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build'/script)],capture_output=True,text=True)
            self.assertEqual(proc.returncode,0,proc.stderr)
    def test_authenticated_lld_payload_and_link(self):
        for text in ('APPFORGE_R44_SIGNED_LLD_LAYOUT_REJECTED',
                     '[ "$(readlink "$r44_signed_link")" = lld ]',
                     'cmp -s "$r44_signed" "$r44_guest"',
                     'ln -s lld "$r44_guest_link"',
                     'APPFORGE_R44_GUEST_LLD_BYTES_OR_LINK_FAILED'):
            self.assertIn(text,H)
    def test_no_global_modification_and_guest_scope(self):
        self.assertEqual(H.count('if [ "$name" = ld.lld ] && [ -f "$prefix/lld-launch-mode-r44" ];then'),1)
        self.assertIn('exec /usr/lib/llvm-18/bin/ld.lld "$@"',H)
        self.assertIn('APPFORGE_R44_LLD_LIB_ESCAPED_SYSROOT',H)
        self.assertIn('APPFORGE_R44_LLD_GUEST_CLOSURE=VERIFIED',H)
        self.assertIn('APPFORGE_R44_LLD_SIGNED_LOADER_LIST_FAILED',H)
        self.assertIn('APPFORGE_R44_LLD_GUEST_EXECUTION=FAILED',H)
        self.assertIn('APPFORGE_R44_LLD_WRAPPER_EXECUTION=PASS',H)
    def test_no_false_ready(self):
        self.assertLess(H.index('APPFORGE_R44_LLD_WRAPPER_EXECUTION=PASS'),
                        H.index('rm -rf -- "$probe_dir"\ntrap - 0 HUP INT TERM'))
        self.assertIn('APPFORGE_R43_NDK_C_LLD_LINK=PASS',S)
        self.assertIn('APPFORGE_R43_NDK_CPP_LLD_LINK=PASS',S)
        self.assertLess(S.index('APPFORGE_R43_NDK_CPP_LLD_LINK=PASS'),S.index('touch "$EXPO_ARM64_NDK_MARKER"'))
        self.assertIn('APPFORGE_R33_CMAKE_GUEST_REAL_P_TEST=PASS',H)
        self.assertIn('APPFORGE_R41_CLANG_GUEST_SOURCE_READ=PASS',H)
    def test_diagnostics_identity_ci(self):
        self.assertIn('result.output.contains("APPFORGE_R44_")',K)
        self.assertIn('line.startsWith("APPFORGE_R44_")',K)
        self.assertIn('appforgeR44OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(567)',G)
        self.assertIn('VERSION_CODE=567',W)
        self.assertIn('tests/test_r44_guest_lld_direct.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
