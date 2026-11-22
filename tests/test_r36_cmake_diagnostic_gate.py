"""R36 regression: nested NDK failure must persist diagnostic before rollback."""
import pathlib
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
K=(ROOT/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
N=(ROOT/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
class R36Tests(unittest.TestCase):
    def test_01_nested_installer_marker(self):
        self.assertIn('result.output.contains("APPFORGE_R35_DIAGNOSTIC=BEGIN")',K)
        self.assertNotIn('command.contains("install-ndk-host-isolated-v7.sh")',K)
    def test_02_both_target_modules(self):
        self.assertIn('OfflineModuleId.RUST_ANDROID_NDK, OfflineModuleId.EXPO_REACT_NATIVE',K)
        self.assertIn('result.exitCode != 0',K)
    def test_03_preserves_failure_details(self):
        self.assertIn('storedAttempt.exceptionOrNull()?.javaClass?.simpleName',K)
        self.assertIn('R35_CMAKE_DIAGNOSTIC_SAVED=$stored;R36_WRITE_FAILURE=$failureKind',K)
        self.assertIn('r35-cmake-diagnostics',K)
    def test_04_bounded_and_before_throw(self):
        self.assertIn('text.takeLast(25000)',K)
        self.assertLess(K.index('R35_CMAKE_DIAGNOSTIC_SAVED='), K.index('result.exitCode ==\n                0'))
    def test_05_same_physical_probes(self):
        self.assertIn('APPFORGE_R35_DIAGNOSTIC=BEGIN',N)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',N)
    def test_06_no_shell_changes(self):
        self.assertIn('APPFORGE_R34_CMAKE_EXPLICIT_GUEST_LOADER_TEST=START',N)
if __name__=='__main__': unittest.main(verbosity=2)
