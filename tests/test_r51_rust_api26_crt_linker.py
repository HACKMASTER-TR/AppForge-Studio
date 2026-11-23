"""R51 API26 startup objects and CRT library search contract."""
from pathlib import Path
import importlib.util, subprocess, tempfile, unittest
R=Path(__file__).resolve().parents[1]
PY=R/'android-app/app/src/main/assets/device-build/rust-android.py'
S=PY.read_text()
SH=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r51-unsigned-apk-ci.yml').read_text()
class R51(unittest.TestCase):
    def module(self):
        spec=importlib.util.spec_from_file_location('r51_rust_ndk',PY)
        m=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m)
        return m
    def test_signed_baseline_and_exact_crt_guard(self):
        mod=self.module()
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);sdk=root/'android-sdk'
            pre=sdk/'ndk/27.1.12297006/toolchains/llvm/prebuilt/linux-x86_64'
            bins=pre/'bin';bins.mkdir(parents=True)
            sysroot=pre/'sysroot'
            header=sysroot/'usr/include/unistd.h';header.parent.mkdir(parents=True)
            header.write_text('// stub header for source contract test')
            crt=sysroot/'usr/lib/aarch64-linux-android/26';crt.mkdir(parents=True)
            (sdk.parent/'.expo-arm64-ndk-host-v3').touch()
            (pre.parents[3]/'source.properties').write_text('Pkg.Revision = 27.1.12297006\n')
            clang=bins/'clang'
            clang.write_text('#!/bin/sh\n# APPFORGE_EXPO_ARM64_NDK_CLANG_WRAPPER_V3\n'
                             '# -resource-dir= -B --ld-path=/usr/lib/llvm-18/bin/ld.lld\n')
            clang.chmod(0o755)
            original=bins/'appforge-rust-aarch64-linux-android26-clang'
            original.write_text('#!/bin/sh\n# APPFORGE_R50_RUST_API26_SYSROOT_WRAPPER_V1\n'
                                'set -eu\n'
                                f'exec "{clang}" --target=aarch64-linux-android26 --sysroot="{sysroot}" "$@"\n')
            original.chmod(0o755)
            nxt=bins/'appforge-rust-api26-crt-linker-r51'
            expected=('#!/bin/sh\n# APPFORGE_R51_RUST_API26_CRT_WRAPPER_V1\n'
                      'set -eu\n'
                      f'exec "{original}" -B"{crt}" -L"{crt}" -L"{crt.parent}" "$@"\n')
            nxt.write_text(expected);nxt.chmod(0o755)
            for fname in ('crtbegin_so.o','crtend_so.o','libc.so','libdl.so'):
                (crt/fname).write_text('test file')
            check=lambda:mod.verified_appforge_r51_crt_linker(sdk,'aarch64',trusted_sdk=sdk)
            self.assertEqual(check(),nxt)
            self.assertIsNone(mod.verified_appforge_r51_crt_linker(sdk,'x86_64',trusted_sdk=sdk))
            self.assertIsNone(mod.verified_appforge_r51_crt_linker(sdk,'aarch64'))
            (crt/'crtend_so.o').unlink();self.assertIsNone(check())
            (crt/'crtend_so.o').write_text('test file')
            nxt.write_text(expected.replace('-B"','-X"'));self.assertIsNone(check())
            nxt.write_text(expected);self.assertEqual(check(),nxt)
    def test_real_link_and_ready_order(self):
        p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh')],capture_output=True,text=True)
        self.assertEqual(p.returncode,0,p.stderr)
        for token in ('APPFORGE_R50_RUST_LINKER_WRAPPER=PASS',
                      'APPFORGE_R51_CRT_LIBS=PASS',
                      'APPFORGE_R51_API26_LINKER_ROUTE=VERIFIED',
                      'APPFORGE_R51_API26_CRT_PATHS=VERIFIED',
                      'APPFORGE_R51_RUST_API26_CRT_ELF=PASS',
                      'APPFORGE_R51_RUST_API26_CRT_ELF=FAILED',
                      'crtbegin_so.o','crtend_so.o','libc.so','libdl.so',
                      '-B"$r51_api26_dir"','-L"$r51_api26_dir"',
                      'max-page-size=16384','defined_exports(result)'):
            self.assertIn(token,SH+S)
        self.assertLess(S.index('proof=probe_linker(candidate)'),S.index('APPFORGE_R51_RUST_API26_CRT_ELF=PASS'))
        self.assertLess(SH.index('APPFORGE_R51_CRT_LIBS=PASS'),SH.index('touch "$EXPO_ARM64_NDK_MARKER"'))
        self.assertLess(SH.index('if [ "$ENGINE" = rust-native ];then\n /usr/bin/python3.12'),SH.index('touch "$READY"'))
    def test_ci_and_diagnostics(self):
        self.assertIn('result.output.contains("APPFORGE_R51_")',K)
        self.assertIn('line.startsWith("APPFORGE_R51_")',K)
        self.assertIn('appforgeR51OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(574)',G)
        self.assertIn('VERSION_CODE=574',W)
        self.assertIn('tests/test_r51_rust_api26_crt_linker.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
