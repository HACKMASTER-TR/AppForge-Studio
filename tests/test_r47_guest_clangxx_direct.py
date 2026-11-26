"""R47: authenticated guest Clang C++ compiler, real source and archive gates."""
from pathlib import Path
import subprocess, unittest
R=Path(__file__).resolve().parents[1]
S=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
H=(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r47-unsigned-apk-ci.yml').read_text()
class R47(unittest.TestCase):
    def test_shell_syntax(self):
        for fn in ('install-toolchain.sh','install-ndk-host-isolated-v7.sh'):
            p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build'/fn)],capture_output=True,text=True)
            self.assertEqual(0,p.returncode,p.stderr)
    def test_signed_guest_clang_cxx_driver(self):
        self.assertEqual(H.count('if [ "$name" = clang++ ] && [ -f "$prefix/clang-launch-mode-r41" ];then'),1)
        self.assertIn('cmp -s "$r47_clang_guest" "$r47_clang_signed"',H)
        self.assertIn('exec "$r47_clang_guest" --driver-mode=g++ "$@"',H)
        self.assertLess(H.index('if [ "$name" = clang ]'),H.index('if [ "$name" = clang++ ]'))
        self.assertLess(H.index('if [ "$name" = clang++ ]'),H.index('if [ "$name" = ld.lld ]'))
        self.assertIn('APPFORGE_R41_CLANG_GUEST_SOURCE_READ=PASS',H)
        self.assertIn('APPFORGE_R44_LLD_WRAPPER_EXECUTION=PASS',H)
    def test_real_gates_in_order(self):
        for token in ('APPFORGE_R47_CPP_SOURCE_PRESENT=PASS',
                      'APPFORGE_R47_GUEST_CPP_COMPILE=PASS',
                      'APPFORGE_R47_LLVM_AR=PASS',
                      'APPFORGE_R46_REAL_C_GUEST_LLD_LINK=PASS',
                      'APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS',
                      'APPFORGE_R47_CPP_STDERR=', 'APPFORGE_R47_AR_STDERR='):
            self.assertIn(token,S)
        self.assertLess(S.index('APPFORGE_R46_REAL_C_GUEST_LLD_LINK=PASS'),
                        S.index('APPFORGE_R47_GUEST_CPP_COMPILE=PASS'))
        self.assertLess(S.index('APPFORGE_R47_GUEST_CPP_COMPILE=PASS'),
                        S.index('APPFORGE_R47_LLVM_AR=PASS'))
        self.assertLess(S.index('APPFORGE_R47_LLVM_AR=PASS'),
                        S.index('APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS'))
        self.assertLess(S.index('APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS'),
                        S.index('touch "$EXPO_ARM64_NDK_MARKER"'))
    def test_identity_and_ci(self):
        self.assertIn('result.output.contains("APPFORGE_R47_")',K)
        self.assertIn('line.startsWith("APPFORGE_R47_")',K)
        self.assertIn('appforgeR47OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(570)',G)
        self.assertIn("VERSION_CODE=570",W)
        self.assertIn('tests/test_r47_guest_clangxx_direct.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
