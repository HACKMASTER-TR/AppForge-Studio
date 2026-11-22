"""R40 CMake: signed dependency closure in guest only, no fabricated READY."""
import pathlib
import subprocess
import unittest
P=pathlib.Path(__file__).resolve().parents[1]
S=(P/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
G=(P/'android-app/app/build.gradle.kts').read_text()
K=(P/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
W=(P/'.github/workflows/r40-unsigned-apk-ci.yml').read_text()
CAT=(P/'android-app/app/src/main/assets/device-build/apt-module-packages-v1.tsv').read_text()
class R40GuestDependencyClosure(unittest.TestCase):
    def test_01_valid_shell(self):
        p=subprocess.run(['sh','-n',str(P/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh')],capture_output=True,text=True)
        self.assertEqual(p.returncode,0,p.stderr)
    def test_02_all_inputs_authenticated(self):
        self.assertIn('EXPECTED_CATALOG=77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f',S)
        self.assertIn('sha256sum -c - >/dev/null || exit 64',S)
        self.assertEqual(len([x for x in CAT.splitlines() if x.startswith('EXPO_REACT_NATIVE\tarm64\tlibuv1t64\t')]),1)
        self.assertIn('28bd1e33e5ff44f1878e757c42951bfafd26e92a5865b72ad33622e1251f45c2',CAT)
    def test_03_closed_signed_only_and_no_overwrite(self):
        self.assertIn('"$cmake_probe_dir/loader.log" | LC_ALL=C sort -u',S)
        self.assertIn("grep -qx 'libuv.so.1'",S)
        self.assertIn('libc.so.*|libm.so.*|libdl.so.*',S)
        self.assertIn('if [ -e "$r40_guest" ] || [ -L "$r40_guest" ];then',S)
        self.assertIn('APPFORGE_R40_SIGNED_CMAKE_DEPENDENCY_MISSING=',S)
        self.assertIn('APPFORGE_R40_SIGNED_CMAKE_DEPENDENCY_ELF_INVALID=',S)
        self.assertIn('cmp -s "$r40_payload" "$r40_guest"',S)
        self.assertIn('APPFORGE_R40_GUEST_CMAKE_DEPENDENCY_CLOSURE=VERIFIED:',S)
        self.assertLess(S.index('APPFORGE_R40_GUEST_CMAKE_DEPENDENCY_CLOSURE=VERIFIED'),S.index('APPFORGE_R33_CMAKE_GUEST_REAL_P_TEST=PASS'))
    def test_04_actual_cmake_and_clang_readiness_gates_remain(self):
        self.assertIn('APPFORGE_CMAKE_PROBE_MODE=guest-cmake-direct "$BIN/cmake"',S)
        self.assertIn('-P "$cmake_probe_dir/check.cmake"',S)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',S)
        self.assertIn('"$BIN/cmake" -P "$probe_dir/check.cmake"',S)
        self.assertIn('"$BIN/clang" --target=aarch64-unknown-linux-gnu',S)
        self.assertIn('APPFORGE_NDK_HOST_CLANG_SOURCE_READ=PASS',S)
    def test_05_identity_and_diagnostics(self):
        self.assertIn('line.startsWith("APPFORGE_R40_")',K)
        self.assertIn('appforgeR40OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(563)',G)
        self.assertIn('-PappforgeR40OriginalIdentityUpgrade=true',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
        self.assertIn('R39_COMPARATOR_STACK_SAVED=PASS',K)
if __name__=='__main__':unittest.main(verbosity=2)
