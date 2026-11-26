"""R46: real C/C++ link gates via authenticated guest LLD and Clang --ld-path."""
from pathlib import Path
import subprocess, unittest
R=Path(__file__).resolve().parents[1]
S=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
H=(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r46-unsigned-apk-ci.yml').read_text()
class R46(unittest.TestCase):
    def test_shell_syntax(self):
        for fn in ('install-toolchain.sh','install-ndk-host-isolated-v7.sh'):
            p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build'/fn)],capture_output=True,text=True)
            self.assertEqual(0,p.returncode,p.stderr)
    def test_signed_guest_identity_preserved(self):
        self.assertIn('APPFORGE_R45_NDK_LLD_GUEST_IDENTITY=PASS',S)
        self.assertIn('cmp -s "$r45_signed_lld" "$r45_guest_lld"',S)
        self.assertIn('APPFORGE_R44_LLD_GUEST_EXECUTION=PASS',H)
        self.assertIn('APPFORGE_R46_VERIFIED_GUEST_LLD=PASS',S)
        self.assertIn('APPFORGE_R46_GUEST_LLD_VERSION_INVALID',S)
        self.assertIn('APPFORGE_R46_NDK_BIN_LLD=REJECTED_BY_PROOT',S)
    def test_clang_real_linker_routing(self):
        self.assertEqual(S.count('    -B"$bin" ' + chr(92) + chr(10) + '    --rtlib=compiler-rt'),1)
        self.assertEqual(S.count('--ld-path=/usr/lib/llvm-18/bin/ld.lld'),4)
        self.assertIn('grep -F -- \'--ld-path=/usr/lib/llvm-18/bin/ld.lld\' "$bin/clang"',S)
        self.assertIn('APPFORGE_R46_CLANG_LINKER_ROUTE=SIGNED_GUEST_ABSOLUTE_PATH',S)
        self.assertIn('"$bin/ld.lld" --version',S)
    def test_real_C_and_CPP_link_before_ready(self):
        for token in ('APPFORGE_R43_NDK_C_LLD_LINK=FAILED','APPFORGE_R43_NDK_C_LLD_LINK=PASS',
                      'APPFORGE_R43_NDK_CPP_LLD_LINK=FAILED','APPFORGE_R43_NDK_CPP_LLD_LINK=PASS',
                      'APPFORGE_R46_REAL_C_GUEST_LLD_LINK=PASS','APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS'):
            self.assertIn(token,S)
        self.assertGreaterEqual(S.count('-fuse-ld=lld'),2)
        self.assertLess(S.index('APPFORGE_R46_REAL_C_GUEST_LLD_LINK=PASS'),
                        S.index('APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS'))
        self.assertLess(S.index('APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS'),
                        S.index('touch "$EXPO_ARM64_NDK_MARKER"'))
    def test_diagnostics_identity_workflow(self):
        self.assertIn('result.output.contains("APPFORGE_R46_")',K)
        self.assertIn('line.startsWith("APPFORGE_R46_")',K)
        self.assertIn('appforgeR46OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(569)',G)
        self.assertIn('VERSION_CODE=569',W)
        self.assertIn('tests/test_r46_clang_guest_ld_path.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
