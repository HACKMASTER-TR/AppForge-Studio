"""R39 must preserve original app, R38 repair, and real readiness gates."""
import pathlib
import unittest
P=pathlib.Path(__file__).resolve().parents[1]
K=(P/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(P/'android-app/app/build.gradle.kts').read_text()
W=(P/'.github/workflows/r39-unsigned-apk-ci.yml').read_text()
S=(P/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
class R39ComparatorTrace(unittest.TestCase):
    def test_01_single_guarded_exception(self):
        self.assertEqual(K.count('"r39-comparator-diagnostics"'),1)
        self.assertIn('e is IllegalArgumentException',K)
        self.assertIn('Comparison method violates its general contract',K)
        self.assertIn('e.stackTrace.take(50)',K)
        self.assertIn('R39_FRAME=',K)
        self.assertIn('target.writeText(text.take(10000))',K)
        self.assertIn('R39_COMPARATOR_STACK_SAVED=PASS',K)
    def test_02_no_fabricated_ready_or_stage_changes(self):
        self.assertIn('generation.commit(verify={ candidate ->',K)
        self.assertIn('APPFORGE_R38_GUEST_RHASH=VERIFIED',S)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',S)
        self.assertIn('"$BIN/cmake" -P "$probe_dir/check.cmake"',S)
    def test_03_original_identity_and_unsigned(self):
        self.assertIn('appforgeR39OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(562)',G)
        self.assertIn('output.versionName.set("5.0.60-r39-comparator-trace")',G)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
        self.assertIn('-PappforgeR39OriginalIdentityUpgrade=true',W)
if __name__=='__main__':unittest.main(verbosity=2)
