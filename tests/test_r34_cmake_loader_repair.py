"""R34 CMake physical dependency-resolution guards (no fake READY)."""
import subprocess
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
NDK = ROOT / "android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh"
class R34CMakeLoaderTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.s = NDK.read_text()
    def test_01_shell_parses(self):
        p = subprocess.run(["sh", "-n", str(NDK)], capture_output=True, text=True)
        self.assertEqual(p.returncode, 0, p.stderr)
    def test_02_do_not_clear_guest_library_path(self):
        s = self.s
        self.assertNotIn("unset PROROOT_GUEST_EXE LD_LIBRARY_PATH CMAKE_ROOT\n      exec /usr/bin/cmake", s)
        self.assertIn('export LD_LIBRARY_PATH="/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:$libpath"',s)
        self.assertIn('guest-cmake-loader-argv0)',s)
        self.assertIn('--library-path "/usr/lib/aarch64-linux-gnu:/lib/aarch64-linux-gnu:$libpath"',s)
    def test_03_real_guest_resource_tree_not_symlink(self):
        s = self.s
        self.assertIn('cp -a "$signed_modules" "$guest_modules"',s)
        self.assertIn('cmp -s "$signed_modules/Modules/CMake.cmake" "$guest_modules/Modules/CMake.cmake"',s)
        self.assertIn('cmp -s "$signed_libarchive" "$guest_libarchive"',s)
    def test_04_physical_cmake_probe_required(self):
        s=self.s
        self.assertIn('APPFORGE_CMAKE_PROBE_MODE=guest-cmake-direct "$BIN/cmake"',s)
        self.assertIn('APPFORGE_CMAKE_PROBE_MODE=guest-cmake-loader-argv0 "$BIN/cmake"',s)
        self.assertIn('"$BIN/cmake" -P "$probe_dir/check.cmake"',s)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',s)
        self.assertIn('APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS',s)
    def test_05_windows_unchanged_by_patch(self):
        w=(ROOT/"android-app/app/src/main/assets/device-build/install-windows-native-toolchain.sh").read_text()
        self.assertIn("APPFORGE_R33_WINDOWS_APT_POSTINST_REPAIR=START",w)
if __name__ == '__main__':
    unittest.main(verbosity=2)
