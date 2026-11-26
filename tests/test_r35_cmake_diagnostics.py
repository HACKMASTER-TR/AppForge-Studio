"""R35 diagnostic-only contract: read-safe guest probes and persistent failure log."""
import pathlib
import subprocess
import unittest
ROOT=pathlib.Path(__file__).resolve().parents[1]
N=(ROOT/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K=(ROOT/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
class R35Tests(unittest.TestCase):
    def test_01_shell(self):
        self.assertEqual(subprocess.run(['sh','-n',str(ROOT/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh')]).returncode,0)
    def test_02_no_ready_on_failure(self):
        self.assertIn('APPFORGE_R35_DIAGNOSTIC=BEGIN',N)
        self.assertIn('APPFORGE_NDK_HOST_CMAKE_NO_VERIFIED_LAUNCHER',N)
        self.assertIn('exit 68',N)
    def test_03_no_mutation_by_probe(self):
        s=N[N.index('APPFORGE_R35_DIAGNOSTIC=BEGIN'):N.index('APPFORGE_R35_DIAGNOSTIC=END')]
        for dangerous in ('rm -rf','mv -f','cp -a','apt install','chmod','dpkg --configure'):
            self.assertNotIn(dangerous,s)
    def test_04_store_before_failure(self):
        self.assertIn('r35-cmake-diagnostics',K)
        self.assertIn('R35_CMAKE_DIAGNOSTIC_SAVED=',K)
        self.assertLess(K.index('R35_CMAKE_DIAGNOSTIC_SAVED='),K.index('result.exitCode ==\n                0'))
    def test_05_log_is_bounded(self):
        self.assertIn('text.takeLast(25000)',K)
        self.assertIn('takeLast(130)',K)
        self.assertIn('java.nio.file.Files.isSymbolicLink(target.toPath())',K)
if __name__=='__main__': unittest.main(verbosity=2)
