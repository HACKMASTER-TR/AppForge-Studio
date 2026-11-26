"""R48: real archive object path, byte verified guest llvm-ar, preserved link gates."""
from pathlib import Path
import subprocess, unittest
R=Path(__file__).resolve().parents[1]
H=(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
S=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r48-unsigned-apk-ci.yml').read_text()
class R48(unittest.TestCase):
    def test_real_sh_syntax(self):
        for fn in ('install-toolchain.sh','install-ndk-host-isolated-v7.sh'):
            q=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build'/fn)],capture_output=True,text=True)
            self.assertEqual(q.returncode,0,q.stderr)
    def test_authenticated_guest_llvm_ar(self):
        for marker in ('if [ "$name" = llvm-ar ] && [ -f "$prefix/llvm-ar-launch-mode-r48" ];then',
                       'r48_signed_ar="$sysroot/usr/lib/llvm-18/bin/llvm-ar"',
                       'cmp -s "$r48_signed_ar" "$r48_guest_ar"',
                       'exec "$r48_guest_ar" "$@"',
                       'APPFORGE_R48_GUEST_AR_EXECUTION=PASS',
                       'APPFORGE_R48_GUEST_AR_COLLISION_OR_IDENTITY_MISMATCH'):
            self.assertIn(marker,H)
    def test_real_guest_namespace_archive_probe(self):
        for marker in ('"$BIN/llvm-ar" rcs "$probe_dir/r48-test.a" "$probe_dir/readable.c"',
                       '"$BIN/llvm-ar" t "$probe_dir/r48-test.a"',
                       'APPFORGE_R48_REAL_AR_ARCHIVE=PASS',
                       'APPFORGE_R48_AR_WRAPPER_EXECUTION=PASS'):
            self.assertIn(marker,H)
        self.assertLess(H.index('APPFORGE_R48_REAL_AR_ARCHIVE=PASS'),H.index('rm -rf -- "$probe_dir"\ntrap - 0 HUP INT TERM'))
        self.assertIn('APPFORGE_R44_LLD_GUEST_EXECUTION=PASS',H)
    def test_genuine_object_link_and_no_false_ready(self):
        self.assertIn('APPFORGE_R47_GUEST_CPP_COMPILE=PASS',S)
        self.assertIn('APPFORGE_R48_GUEST_AR_REAL_OBJECT_ARCHIVE=PASS',S)
        self.assertIn('APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS',S)
        self.assertLess(S.index('APPFORGE_R47_GUEST_CPP_COMPILE=PASS'),S.index('APPFORGE_R48_GUEST_AR_REAL_OBJECT_ARCHIVE=PASS'))
        self.assertLess(S.index('APPFORGE_R48_GUEST_AR_REAL_OBJECT_ARCHIVE=PASS'),S.index('APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS'))
        self.assertLess(S.index('APPFORGE_R46_REAL_CPP_GUEST_LLD_LINK=PASS'),S.index('touch "$EXPO_ARM64_NDK_MARKER"'))
    def test_diagnostics_and_unsigned_identity_ci(self):
        self.assertIn('result.output.contains("APPFORGE_R48_")',K)
        self.assertIn('line.startsWith("APPFORGE_R48_")',K)
        self.assertIn('appforgeR48OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(571)',G)
        self.assertIn('VERSION_CODE=571',W)
        self.assertIn('tests/test_r48_guest_llvm_ar.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__': unittest.main(verbosity=2)
