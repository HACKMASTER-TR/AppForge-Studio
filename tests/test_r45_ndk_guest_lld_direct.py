"""R45 linker direct entry with original-identity, diagnosed real link gates."""
from pathlib import Path
import subprocess
import unittest
R=Path(__file__).resolve().parents[1]
S=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
H=(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r45-unsigned-apk-ci.yml').read_text()
class R45(unittest.TestCase):
    def test_shell_syntax(self):
        for filename in ('install-toolchain.sh','install-ndk-host-isolated-v7.sh'):
            p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build'/filename)],capture_output=True,text=True)
            self.assertEqual(p.returncode,0,p.stderr)
    def test_signed_guest_identity_and_private_scope(self):
        for item in ('APPFORGE_R45_NDK_LLD_GUEST_IDENTITY=PASS',
                     '[ "$(readlink "$r45_guest_link")" = lld ]',
                     'cmp -s "$r45_signed_lld" "$r45_guest_lld"',
                     'preserve_ndk_entry ld.lld',
                     'APPFORGE_EXPO_ARM64_NDK_LLD_GUEST_DIRECT_R45',
                     'exec /usr/lib/llvm-18/bin/ld.lld "$@"',
                     'unset PROROOT_GUEST_EXE CMAKE_ROOT'):
            self.assertIn(item,S)
        self.assertIn('APPFORGE_R44_LLD_WRAPPER_EXECUTION=PASS',H)
    def test_runtime_error_diagnostics_and_gate_order(self):
        for item in ('APPFORGE_R45_NDK_LLD_DIRECT_EXECUTION=FAILED',
                     'APPFORGE_R45_LLD_STDERR=',
                     'APPFORGE_R45_NDK_LLD_DIRECT_EXECUTION=PASS',
                     'APPFORGE_R43_NDK_C_LLD_LINK=PASS',
                     'APPFORGE_R43_NDK_CPP_LLD_LINK=PASS'):
            self.assertIn(item,S)
        self.assertLess(S.index('APPFORGE_R45_NDK_LLD_DIRECT_EXECUTION=PASS'),
                        S.index('APPFORGE_R43_NDK_C_LLD_LINK=PASS'))
        self.assertLess(S.index('APPFORGE_R43_NDK_CPP_LLD_LINK=PASS'),
                        S.index('touch "$EXPO_ARM64_NDK_MARKER"'))
        self.assertGreaterEqual(S.count('-fuse-ld=lld'),2)
    def test_identity_ci_and_diagnostics(self):
        for item in ('result.output.contains("APPFORGE_R45_")',
                     'line.startsWith("APPFORGE_R45_")'):
            self.assertIn(item,K)
        self.assertIn('appforgeR45OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(568)',G)
        self.assertIn('VERSION_CODE=568',W)
        self.assertIn('tests/test_r45_ndk_guest_lld_direct.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
