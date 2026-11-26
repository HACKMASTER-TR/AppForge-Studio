"""R43: direct, private LLD discovery by real Android ARM64 C/C++ linker tests."""
from pathlib import Path
import subprocess
import unittest
R=Path(__file__).resolve().parents[1]
S=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
HOST=(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r43-unsigned-apk-ci.yml').read_text()
class R43(unittest.TestCase):
    def test_shell_syntax(self):
        for file in ['install-toolchain.sh','install-ndk-host-isolated-v7.sh']:
            p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build'/file)],capture_output=True,text=True)
            self.assertEqual(0,p.returncode,p.stderr)
    def test_explicit_private_link_search_only(self):
        self.assertEqual(S.count('    -B"$bin" ' + chr(92) + chr(10) + '    --rtlib=compiler-rt'),1)
        self.assertIn('APPFORGE_R43_NDK_LLD_SEARCH_PATH=EXPLICIT_PRIVATE_BIN',S)
        self.assertIn('APPFORGE_R43_NDK_LLD_WRAPPER_MISSING',S)
        self.assertIn('"$bin/ld.lld" --version',S)
        self.assertIn('APPFORGE_EXPO_ARM64_NDK_LLD_WRAPPER_V2',S)
        self.assertIn('exec /usr/lib/llvm-18/bin/clang "$@"',HOST)
    def test_real_link_gates(self):
        for s in ('APPFORGE_R43_NDK_C_LLD_LINK=FAILED','APPFORGE_R43_NDK_C_LLD_LINK=PASS',
                  'APPFORGE_R43_NDK_CPP_LLD_LINK=FAILED','APPFORGE_R43_NDK_CPP_LLD_LINK=PASS'):
            self.assertIn(s,S)
        self.assertGreaterEqual(S.count('-fuse-ld=lld'),2)
        self.assertIn('-stdlib=libc++         -fuse-ld=lld',S)
        self.assertLess(S.index('APPFORGE_R43_NDK_C_LLD_LINK=PASS'),S.index('APPFORGE_R43_NDK_CPP_LLD_LINK=PASS'))
        self.assertLess(S.index('APPFORGE_R43_NDK_CPP_LLD_LINK=PASS'),S.index('touch "$EXPO_ARM64_NDK_MARKER"'))
    def test_diagnostics_and_identity(self):
        self.assertIn('result.output.contains("APPFORGE_R43_")',K)
        self.assertIn('line.startsWith("APPFORGE_R43_")',K)
        self.assertIn('appforgeR43OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(566)',G)
        self.assertIn('VERSION_CODE=566',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
        self.assertIn('tests/test_r43_ndk_lld_search.py',W)
if __name__=='__main__':unittest.main(verbosity=2)
