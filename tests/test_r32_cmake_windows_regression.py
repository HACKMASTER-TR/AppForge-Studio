import re
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / (
    "android-app/app/src/main/assets/device-build"
)

NDK = ASSETS / "install-ndk-host-isolated-v7.sh"
WIN = ASSETS / "install-windows-native-toolchain.sh"


class R32RegressionTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.ndk = NDK.read_text()
        cls.win = WIN.read_text()

    def test_01_shell_syntax(self):
        for path in (NDK, WIN):
            result = subprocess.run(
                ["sh", "-n", str(path)],
                capture_output=True,
                text=True,
            )
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_02_windows_signed_apt_preserved(self):
        self.assertIn("managed_apt update", self.win)
        self.assertIn("APPFORGE_APT_MANAGED_INSTALL", self.win)
        self.assertIn("APPFORGE_R2_RELEASE_AUTHORIZED", self.win)
        self.assertIn("repair_interrupted_dpkg", self.win)

    def test_03_windows_failure_never_retries_same_stage(self):
        match = re.search(
            r"(?ms)^if ! install_packages; then\n"
            r".*?^fi\n(?=\n# Never publish readiness before the original APT transaction)",
            self.win,
        )
        self.assertIsNotNone(match)
        guard = match.group(0)

        command = (
            "install_packages() {\n"
            "  printf 'INSTALL_CALLED\\n'\n"
            "  return 42\n"
            "}\n"
            + guard
            + "\nprintf 'UNEXPECTED_CONTINUE\\n'\n"
        )

        result = subprocess.run(
            ["sh", "-c", command],
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 60)
        self.assertEqual(
            result.stdout.count("INSTALL_CALLED"), 1
        )
        self.assertIn(
            "SAME_GENERATION_RETRY=BLOCKED",
            result.stderr,
        )
        self.assertNotIn("UNEXPECTED_CONTINUE", result.stdout)

    def test_04_windows_success_continues(self):
        match = re.search(
            r"(?ms)^if ! install_packages; then\n"
            r".*?^fi\n(?=\n# Never publish readiness before the original APT transaction)",
            self.win,
        )
        self.assertIsNotNone(match)

        command = (
            "install_packages() {\n"
            "  printf 'INSTALL_CALLED\\n'\n"
            "  return 0\n"
            "}\n"
            + match.group(0)
            + "\nprintf 'CONTINUE_OK\\n'\n"
        )

        result = subprocess.run(
            ["sh", "-c", command],
            capture_output=True,
            text=True,
        )

        self.assertEqual(result.returncode, 0)
        self.assertIn("CONTINUE_OK", result.stdout)
        self.assertEqual(
            result.stdout.count("INSTALL_CALLED"), 1
        )

    def test_05_cmake_signed_private_elf(self):
        for required in (
            'file "$signed_cmake_elf"',
            'cp -p "$signed_cmake_elf" "$private_cmake_elf"',
            'cmp -s "$signed_cmake_elf" "$private_cmake_elf"',
            "APPFORGE_NDK_CMAKE_PRIVATE_ELF_BYTES=VERIFIED",
            "private-elf-direct)",
            "private-elf-loader-argv0)",
        ):
            self.assertIn(required, self.ndk)

    def test_06_cmake_functional_guards(self):
        for required in (
            'APPFORGE_CMAKE_PROBE_MODE="$cmake_mode"',
            '"$BIN/cmake" -P "$cmake_probe_dir/check.cmake"',
            '"$BIN/cmake" -P "$probe_dir/check.cmake"',
            "APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER",
            "APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS",
            "APPFORGE_NDK_HOST_SIGNED_PACKAGES=69",
        ):
            self.assertIn(required, self.ndk)

    def test_07_isolated_receipt_required(self):
        installer = ROOT / (
            "android-app/app/src/main/java/com/appforge/"
            "studio/build/IsolatedModuleInstallerV3.kt"
        )
        source = installer.read_text()

        self.assertIn("generation.commit(", source)
        self.assertIn("require(ready(app, target))", source)
        self.assertIn("functionalTests", source)
        self.assertIn("PASS_DEVICE_LOCAL", source)


if __name__ == "__main__":
    unittest.main(verbosity=2)
