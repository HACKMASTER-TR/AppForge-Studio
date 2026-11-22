"""R38 regression: only signed catalog guest librhash; no fake READY."""
from pathlib import Path
import subprocess
import unittest
ROOT = Path(__file__).resolve().parents[1]
S = (ROOT / "android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh").read_text()
K = (ROOT / "android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt").read_text()
G = (ROOT / "android-app/app/build.gradle.kts").read_text()
CAT = (ROOT / "android-app/app/src/main/assets/device-build/apt-module-packages-v1.tsv").read_text()
class R38GuestRhash(unittest.TestCase):
    def test_01_valid_shell(self):
        p = subprocess.run(["sh", "-n", str(ROOT / "android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh")], capture_output=True, text=True)
        self.assertEqual(p.returncode, 0, p.stderr)
    def test_02_signed_rhash_catalog(self):
        row = [x for x in CAT.splitlines() if x.startswith("EXPO_REACT_NATIVE\tarm64\tlibrhash0\t")]
        self.assertEqual(len(row), 1)
        self.assertIn("5406df3423dc0ce53a4f6ff0b786a597c7b5ad3b8e3a51a04bec85d5fe1ab82f", row[0])
        self.assertIn('signed_librhash="$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/librhash.so.0"', S)
    def test_03_guest_only_and_no_overwrite(self):
        self.assertIn('guest_librhash=/usr/lib/aarch64-linux-gnu/librhash.so.0', S)
        self.assertIn('"$guest_libarchive" "$guest_librhash";do', S)
        self.assertIn('if [ -e "$guest_path" ] || [ -L "$guest_path" ];then', S)
        self.assertIn('cp -p "$rhash_payload" "$guest_librhash"', S)
        self.assertIn('cmp -s "$rhash_payload" "$guest_librhash"', S)
        self.assertIn('APPFORGE_R38_GUEST_RHASH=VERIFIED', S)
        self.assertIn('APPFORGE_R38_SIGNED_RHASH_ELF_INVALID', S)
    def test_04_proven_guest_path_and_functional(self):
        self.assertIn('APPFORGE_CMAKE_PROBE_MODE=guest-cmake-direct "$BIN/cmake"', S)
        self.assertIn('-P "$cmake_probe_dir/check.cmake"', S)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER', S)
        self.assertIn('"$BIN/cmake" -P "$probe_dir/check.cmake"', S)
        self.assertIn('"$BIN/clang" --target=aarch64-unknown-linux-gnu', S)
    def test_05_diagnostics_and_original_identity(self):
        self.assertIn('line.startsWith("APPFORGE_R38_")', K)
        self.assertIn('appforgeR38OriginalIdentityUpgrade', G)
        self.assertIn('output.versionCode.set(561)', G)
if __name__ == '__main__':
    unittest.main(verbosity=2)
