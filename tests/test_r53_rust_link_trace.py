"""R53 persistent bounded Rust NDK linker diagnostic contract."""
from pathlib import Path
import ast, contextlib, importlib.util, io, subprocess, tempfile, unittest
from unittest import mock
R=Path(__file__).resolve().parents[1]
PY=R/'android-app/app/src/main/assets/device-build/rust-android.py'
K=R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt'
G=R/'android-app/app/build.gradle.kts'
W=R/'.github/workflows/r53-unsigned-apk-ci.yml'
S=PY.read_text();KT=K.read_text();GR=G.read_text();WF=W.read_text()
class R53(unittest.TestCase):
    def test_instrumented_probe_only_on_link_failure(self):
        ast.parse(S)
        spec=importlib.util.spec_from_file_location('rust_r53_probe',PY)
        mod=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(mod)
        error=io.StringIO()
        first=subprocess.CompletedProcess([],1,b'',b'ld.lld: error: cannot open crtbegin_so.o\n')
        second=subprocess.CompletedProcess([],0,b'',b' "/usr/lib/llvm-18/bin/ld.lld" "-L/tmp"\n')
        with mock.patch.object(mod.subprocess,'run',side_effect=[first,second]) as run:
            with contextlib.redirect_stderr(error):
                with self.assertRaisesRegex(ValueError,'NDK host cannot link'):
                    mod.probe_linker(Path('/opt/appforge-device/android-sdk/ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64/bin/appforge-rust-api26-crt-absolute-r52'))
        self.assertEqual(run.call_count,2)
        self.assertIn('-###',run.call_args_list[1].args[0])
        self.assertIn('APPFORGE_R53_LINKER_EXIT=1',error.getvalue())
        self.assertIn('APPFORGE_R53_LINKER_ENTRY=appforge-rust-api26-crt-absolute-r52',error.getvalue())
        self.assertIn('APPFORGE_R53_API26_crtbegin_so.o=',error.getvalue())
        self.assertIn('APPFORGE_R53_CLANG_DRIVER=',error.getvalue())
    def test_persist_unconditionally_and_bounded(self):
        self.assertIn('module == OfflineModuleId.RUST_ANDROID_NDK)',KT)
        self.assertIn('File(diagnosticBase, "r53-rust-link-diagnostics")',KT)
        self.assertIn('R53_DIAGNOSTIC=START',KT)
        self.assertIn('line.startsWith("ld.lld: error:")',KT)
        self.assertIn('line.startsWith("APPFORGE_R53_")',KT)
        self.assertIn('.takeLast(65)',KT)
        self.assertIn('selected.take(11000)',KT)
        self.assertLess(KT.index('File(diagnosticBase, "r53-rust-link-diagnostics")'),
                        KT.index('// R35: Persist only allowlisted'))
    def test_ci_only_original_chain(self):
        self.assertIn('val appforgeR53OriginalIdentityUpgrade =',GR)
        self.assertIn('output.versionCode.set(576)',GR)
        self.assertIn('VERSION_CODE=576',WF)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',WF)
        self.assertIn('tests/test_r52_rust_crt_absolute.py',WF)
        self.assertIn('tests/test_r53_rust_link_trace.py',WF)
        self.assertNotIn('adb install',WF)
if __name__=='__main__':unittest.main(verbosity=2)
