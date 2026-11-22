"""R33 fail-closed physical-repair contracts; not a substitute for device tests."""
import subprocess
import unittest
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "android-app/app/src/main/assets/device-build"
class R33Tests(unittest.TestCase):
    def test_scripts_parse(self):
        for filename in ("install-ndk-host-isolated-v7.sh", "install-windows-native-toolchain.sh"):
            result = subprocess.run(["sh", "-n", str(BASE / filename)], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)
    def test_authenticated_private_cmake_root(self):
        text = (BASE / "install-ndk-host-isolated-v7.sh").read_text()
        for token in ("APPFORGE_R33_CMAKE_GUEST_BYTES=VERIFIED",
                      "APPFORGE_R33_CMAKE_GUEST_REAL_P_TEST=PASS",
                      'cmp -s "$signed_elf" "$guest_cmake"',
                      'cmp -s "$signed_libarchive" "$guest_libarchive"',
                      'APPFORGE_CMAKE_PROBE_MODE=guest-cmake-direct',
                      '"$BIN/cmake" -P "$probe_dir/check.cmake"'):
            self.assertIn(token,text)
        self.assertIn('printf \'%s\\n\' "$cmake_mode" > "$PREFIX/cmake-launch-mode-v9"', text)
        self.assertNotIn('printf \'%s\\\\n\' "$cmake_mode" > "$PREFIX/cmake-launch-mode-v9"', text)
    def test_windows_signed_postinst_repair_before_retry(self):
        text = (BASE / "install-windows-native-toolchain.sh").read_text()
        for token in ("APPFORGE_R33_WINDOWS_APT_POSTINST_REPAIR=START",
                      "if ! repair_interrupted_dpkg; then",
                      "have_tools && have_windows_lock",
                      "APPFORGE_WINDOWS_NATIVE_SAME_GENERATION_RETRY=BLOCKED"):
            self.assertIn(token,text)
        self.assertEqual(text.count("managed_apt install \\"),1)
    def test_identity_for_r33(self):
        text = (ROOT / "android-app/app/build.gradle.kts").read_text()
        self.assertIn('output.versionCode.set(556)',text)
        self.assertIn('appforgeR33OriginalIdentityUpgrade',text)
        self.assertIn('5.0.54-r33-original-id-test',text)
if __name__ == '__main__':
    unittest.main(verbosity=2)
