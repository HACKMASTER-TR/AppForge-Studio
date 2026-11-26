"""R50 Rust API26 NDK sysroot: strict source and real link gate tests."""
from pathlib import Path
import importlib.util, os, subprocess, tempfile, unittest
R=Path(__file__).resolve().parents[1]
P=R/'android-app/app/src/main/assets/device-build/rust-android.py'
S=P.read_text()
SH=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r50-unsigned-apk-ci.yml').read_text()
class R50(unittest.TestCase):
    def load(self):
        spec=importlib.util.spec_from_file_location('r50_trusted_android',P)
        module=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module
    def test_sysroot_api26_linker_guard(self):
        mod=self.load()
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder); sdk=root/'android-sdk'
            ndk=sdk/'ndk/27.1.12297006'
            prebuilt=ndk/'toolchains/llvm/prebuilt/linux-x86_64'
            bins=prebuilt/'bin'; bins.mkdir(parents=True)
            sysroot=prebuilt/'sysroot'
            header=sysroot/'usr/include/unistd.h'
            header.parent.mkdir(parents=True)
            header.write_text('// test header')
            (sysroot/'usr/lib/aarch64-linux-android/26').mkdir(parents=True)
            marker=root/'.expo-arm64-ndk-host-v3'; marker.touch()
            (ndk/'source.properties').write_text('Pkg.Revision = 27.1.12297006\n')
            clang=bins/'clang'
            clang.write_text('#!/bin/sh\n# APPFORGE_EXPO_ARM64_NDK_CLANG_WRAPPER_V3\n'
                             '# -resource-dir= -B --ld-path=/usr/lib/llvm-18/bin/ld.lld\n')
            clang.chmod(0o755)
            launcher=bins/'appforge-rust-aarch64-linux-android26-clang'
            expected=('#!/bin/sh\n# APPFORGE_R50_RUST_API26_SYSROOT_WRAPPER_V1\n'
                      'set -eu\n'
                      f'exec "{clang}" --target=aarch64-linux-android26 --sysroot="{sysroot}" "$@"\n')
            launcher.write_text(expected);launcher.chmod(0o755)
            verify=lambda:mod.verified_appforge_r50_api26_linker(sdk,'aarch64',trusted_sdk=sdk)
            self.assertEqual(verify(),launcher)
            self.assertIsNone(mod.verified_appforge_r50_api26_linker(sdk,'x86_64',trusted_sdk=sdk))
            self.assertIsNone(mod.verified_appforge_r50_api26_linker(sdk,'aarch64'))
            launcher.write_text(expected.replace('android26','android25'))
            self.assertIsNone(verify())
            launcher.write_text(expected)
            header.unlink();self.assertIsNone(verify())
            header.write_text('// restored')
            marker.unlink();self.assertIsNone(verify())
    def test_shell_and_real_link_ready_gate(self):
        proc=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh')],capture_output=True,text=True)
        self.assertEqual(proc.returncode,0,proc.stderr)
        for item in ('APPFORGE_R50_SYSROOT_UNISTD=PASS','APPFORGE_R50_SYSROOT_API26_LIBS=PASS',
                     'APPFORGE_R50_RUST_LINKER_WRAPPER=PASS',
                     'APPFORGE_R50_RUST_API26_REAL_ELF=PASS',
                     'APPFORGE_R50_RUST_API26_REAL_ELF=FAILED',
                     '--sysroot="$r50_sysroot"',
                     'APPFORGE_R49_RUST_API26_16K_LINK=PASS',
                     'defined_exports(result)',
                     'max-page-size=16384',
                     'no fallback'):
            self.assertIn(item,SH+S)
        self.assertLess(S.index('probe_linker(candidate)'),S.index('APPFORGE_R50_RUST_API26_REAL_ELF=PASS'))
        self.assertLess(SH.index('APPFORGE_R50_RUST_LINKER_WRAPPER=PASS'),SH.index('touch "$EXPO_ARM64_NDK_MARKER"'))
        self.assertLess(SH.index('if [ "$ENGINE" = rust-native ];then\n /usr/bin/python3.12'),SH.index('touch "$READY"'))
    def test_identity_diagnostics_ci(self):
        self.assertIn('result.output.contains("APPFORGE_R50_")',K)
        self.assertIn('line.startsWith("APPFORGE_R50_")',K)
        self.assertIn('appforgeR50OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(573)',G)
        self.assertIn('VERSION_CODE=573',W)
        self.assertIn('tests/test_r50_rust_api26_sysroot.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__': unittest.main(verbosity=2)
