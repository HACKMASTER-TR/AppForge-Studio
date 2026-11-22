"""R37: no READY without real CMake -P and isolated early-path candidate."""
import pathlib
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
S=(ROOT/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K=(ROOT/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(ROOT/'android-app/app/build.gradle.kts').read_text()
class R37(unittest.TestCase):
    def test_01_private_mode_does_not_change_other_tools(self):
        marker='guest-cmake-loader-early-patch)'
        self.assertEqual(S.count(marker),1)
        self.assertIn('export PROROOT_FORCE_EARLY_PATCH=1',S)
        self.assertLess(S.index(marker),S.index('APPFORGE_R37_CMAKE_EARLY_PATCH=START'))
        self.assertIn('unset PROROOT_GUEST_EXE LD_LIBRARY_PATH CMAKE_ROOT',S)
    def test_02_real_p_probe_is_mandatory(self):
        self.assertIn('APPFORGE_CMAKE_PROBE_MODE=guest-cmake-loader-early-patch "$BIN/cmake"',S)
        self.assertIn('-P "$cmake_probe_dir/check.cmake"',S)
        self.assertIn('APPFORGE_R37_CMAKE_EARLY_PATCH=PASS',S)
        self.assertIn('APPFORGE_R37_CMAKE_EARLY_PATCH=FAILED',S)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',S)
    def test_03_no_false_functional_ready(self):
        self.assertIn('"$BIN/cmake" -P "$probe_dir/check.cmake"',S)
        self.assertIn('"$BIN/clang" --target=aarch64-unknown-linux-gnu',S)
        self.assertIn('APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS',S)
    def test_04_persistent_diagnostic(self):
        self.assertIn('line.startsWith("APPFORGE_R37_")',K)
        self.assertIn('R35_CMAKE_DIAGNOSTIC_SAVED=$stored',K)
    def test_05_original_identity_and_isolation(self):
        self.assertIn('appforgeR37OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(560)',G)
if __name__=='__main__':unittest.main(verbosity=2)
