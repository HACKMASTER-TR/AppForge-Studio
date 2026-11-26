"""R56 real PRoot and Ubuntu Clang A/B checks stay isolated and fail-closed."""
from pathlib import Path
import subprocess
import unittest

ROOT=Path(__file__).resolve().parents[1]
S=(ROOT/'tests/r55_arm64_reference_link_smoke.sh').read_text()
W=(ROOT/'.github/workflows/r56-arm64-proot-parity-ci.yml').read_text()

class R56ProotParity(unittest.TestCase):
    def test_shell_syntax(self):
        p=subprocess.run(['bash','-n',str(ROOT/'tests/r55_arm64_reference_link_smoke.sh')], capture_output=True, text=True)
        self.assertEqual(p.returncode,0,p.stderr)
    def test_ubuntu_legacy_routing_is_diagnostic_only(self):
        for name in ('R56_UBUNTU_R50_API26','R56_UBUNTU_R51_API26','R56_UBUNTU_R52_API26'):
            self.assertIn('r56_legacy '+name,S)
        self.assertIn('log R56_UBUNTU_CLANG=AVAILABLE',S)
        self.assertIn('-resource-dir="$RESOURCE"',S)
        self.assertIn('R56_UBUNTU_R52_API26 -B',S)
    def test_proot_executes_real_fixed_source_link(self):
        for label in ('R56_PROOT_C_API26_LINK','R56_PROOT_CPP_API26_LINK','R56_PROOT_RUST_API26_LINK'):
            self.assertIn(label,S)
        self.assertIn('proot -0 -r / -w "$WORK" "$CC"',S)
        self.assertIn('proot -0 -r / -w "$WORK" "$CXX"',S)
        self.assertIn('proot -0 -r / -w "$WORK" "$(command -v rustc)"',S)
        self.assertIn('R56_PROOT_ANDROID_DEVICE=NOT_TESTED',S)
        self.assertIn('R56_PROOT_RUNNER=UNAVAILABLE_PTRACE_OR_LAUNCH',S)
    def test_ci_is_runner_only_and_no_apk_or_install(self):
        self.assertIn('runs-on: ubuntu-24.04-arm',W)
        self.assertIn('sudo apt-get install -y -qq cmake ninja-build file binutils unzip curl proot clang-18',W)
        self.assertIn('R56_NO_DEVICE_INSTALL=YES',W)
        self.assertIn('R56_NO_SIGNING=YES',W)
        self.assertIn('R56_NO_APK_BUILD=YES',W)
        self.assertNotIn('adb install',W)
        self.assertNotIn('gradle -p android-app',W)
        self.assertIn('python3 tests/test_r56_arm64_proot_comparison.py',W)
    def test_reference_resource_controls_preserved(self):
        for s in ('R55_OFFICIAL_CLANG_RESOURCES=PASS_PINNED_R27B',
                  'R55_NATIVE_ARM64_CLANG=EXECUTABLE_ON_ARM64',
                  'R55_C_API26_LINK','R55_CPP_API26_LINK',
                  'R55_RUST_API26_LINK','R55_HERMES_BYTECODE',
                  'R55_EXPO_REACT_NATIVE_FULL_BUILD=NOT_TESTED'):
            self.assertIn(s,S)

if __name__=='__main__': unittest.main(verbosity=2)
