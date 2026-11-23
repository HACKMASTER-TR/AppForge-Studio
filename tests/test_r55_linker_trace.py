"""R55: bounded failure evidence is persisted before generation rollback."""
from pathlib import Path
import ast
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[1]
SHELL = (ROOT / 'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
RUST = (ROOT / 'android-app/app/src/main/assets/device-build/rust-android.py').read_text()
KOTLIN = (ROOT / 'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()


class R55LinkerEvidence(unittest.TestCase):
    def test_shell_syntax_and_fixed_stage_traces(self):
        p = subprocess.run(['sh', '-n', str(ROOT / 'android-app/app/src/main/assets/device-build/install-toolchain.sh')],capture_output=True,text=True)
        self.assertEqual(p.returncode, 0, p.stderr)
        self.assertEqual(SHELL.count('r55_link_failure C_API24'),1)
        self.assertEqual(SHELL.count('r55_link_failure CPP_API24'),1)
        self.assertIn('APPFORGE_R55_FAILED_STAGE=$r55_phase',SHELL)
        self.assertIn('"$r55_driver" -### "$@"',SHELL)
        self.assertIn('APPFORGE_R55_CLANG_DRIVER=',SHELL)
        self.assertIn('tail -n 5 | cut -c 1-500',SHELL)

    def test_sysroot_paths_and_nonmutating_evidence(self):
        for marker in ('API26_HEADER','API26_CRTBEGIN','API26_CRTEND','API26_LIBC','API26_LIBDL'):
            self.assertIn('r55_file_state '+marker,SHELL)
        self.assertIn('r55_lib="$host/sysroot/usr/lib/aarch64-linux-android/26"',SHELL)
        self.assertIn('SYMLINK_REJECTED',SHELL)
        self.assertLess(SHELL.index('r55_link_failure C_API24'),SHELL.index('APPFORGE_R43_NDK_C_LLD_LINK=FAILED'))
        self.assertLess(SHELL.index('r55_link_failure CPP_API24'),SHELL.index('APPFORGE_R43_NDK_CPP_LLD_LINK=FAILED'))

    def test_rust_probe_stage_precedes_existing_diagnostics(self):
        ast.parse(RUST)
        self.assertIn('APPFORGE_R55_FAILED_STAGE=RUST_API26_SHARED',RUST)
        self.assertIn('APPFORGE_R55_PROBE_TARGET=aarch64-linux-android26',RUST)
        self.assertLess(RUST.index('APPFORGE_R55_FAILED_STAGE=RUST_API26_SHARED'), RUST.index('APPFORGE_R53_LINKER_EXIT='))
        self.assertIn('APPFORGE_R53_CLANG_DRIVER=',RUST)
        self.assertIn('probe_linker(candidate)',RUST)

    def test_persistent_filtered_evidence_before_rollback(self):
        self.assertIn('File(diagnosticBase, "r53-rust-link-diagnostics")',KOTLIN)
        self.assertIn('line.startsWith("APPFORGE_R55_")',KOTLIN)
        self.assertIn('APPFORGE_R55_EXEC_STAGE=',KOTLIN)
        self.assertIn('APPFORGE_R55_CAPTURE_TAIL=',KOTLIN)
        self.assertIn('APPFORGE_R55_R53_PROBE_TRACE=',KOTLIN)
        self.assertIn('r55Metadata +',KOTLIN)
        self.assertIn('.takeLast(65)',KOTLIN)
        self.assertIn('selected.take(11000)',KOTLIN)
        self.assertLess(KOTLIN.index('File(diagnosticBase, "r53-rust-link-diagnostics")'),KOTLIN.index('// R35: Persist only allowlisted'))
        self.assertIn('module == OfflineModuleId.RUST_ANDROID_NDK)',KOTLIN)


if __name__ == '__main__':
    unittest.main(verbosity=2)
