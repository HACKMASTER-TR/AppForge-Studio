"""R52 API26 absolute CRT & Bionic paths: strictly verified source contract."""
from pathlib import Path
import importlib.util, subprocess, tempfile, unittest
R=Path(__file__).resolve().parents[1]
PY=R/'android-app/app/src/main/assets/device-build/rust-android.py'
S=PY.read_text();SH=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r52-unsigned-apk-ci.yml').read_text()
class R52(unittest.TestCase):
    def module(self):
        spec=importlib.util.spec_from_file_location('r52_android_rust',PY)
        m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
    def test_guarded_exact_wrapper(self):
        mod=self.module()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);sdk=root/'android-sdk';ndk=sdk/'ndk/27.1.12297006'
            pre=ndk/'toolchains/llvm/prebuilt/linux-x86_64';bin_dir=pre/'bin';bin_dir.mkdir(parents=True)
            sysroot=pre/'sysroot';api=sysroot/'usr/lib/aarch64-linux-android/26';api.mkdir(parents=True)
            header=sysroot/'usr/include/unistd.h';header.parent.mkdir(parents=True);header.write_text('stub')
            (root/'.expo-arm64-ndk-host-v3').touch()
            (ndk/'source.properties').write_text('Pkg.Revision = 27.1.12297006\n')
            clang=bin_dir/'clang'
            clang.write_text('#!/bin/sh\n# APPFORGE_EXPO_ARM64_NDK_CLANG_WRAPPER_V3\n# -resource-dir= -B --ld-path=/usr/lib/llvm-18/bin/ld.lld\n');clang.chmod(0o755)
            r50=bin_dir/'appforge-rust-aarch64-linux-android26-clang'
            r50.write_text(f'#!/bin/sh\n# APPFORGE_R50_RUST_API26_SYSROOT_WRAPPER_V1\nset -eu\nexec "{clang}" --target=aarch64-linux-android26 --sysroot="{sysroot}" "$@"\n');r50.chmod(0o755)
            r51=bin_dir/'appforge-rust-api26-crt-linker-r51'
            r51.write_text(f'#!/bin/sh\n# APPFORGE_R51_RUST_API26_CRT_WRAPPER_V1\nset -eu\nexec "{r50}" -B"{api}" -L"{api}" -L"{api.parent}" "$@"\n');r51.chmod(0o755)
            for item in ('crtbegin_so.o','crtend_so.o','libc.so','libdl.so'):(api/item).write_bytes(b'dummy')
            r52=bin_dir/'appforge-rust-api26-crt-absolute-r52'
            expected=('#!/bin/sh\n# APPFORGE_R52_API26_CRT_ABSOLUTE_V1\nset -eu\ncase " $* " in\n'
                      '  *" -shared "*)\n'
                      f'    exec "{r51}" -nostartfiles -Wl,-L,"{api}" -Wl,-L,"{api.parent}" -Xlinker "{api / "crtbegin_so.o"}" "$@" -Xlinker "{api / "crtend_so.o"}"\n'
                      '    ;;\n'
                      f'  *) exec "{r51}" "$@" ;;\n'
                      'esac\n')
            r52.write_text(expected);r52.chmod(0o755)
            check=lambda:mod.verified_appforge_r52_crt_linker(sdk,'aarch64',trusted_sdk=sdk)
            self.assertEqual(check(),r52)
            (api/'crtend_so.o').unlink();self.assertIsNone(check())
            (api/'crtend_so.o').write_bytes(b'dummy')
            r52.write_text(expected.replace('-nostartfiles','-fno-startfiles'));self.assertIsNone(check())
            r52.write_text(expected);self.assertEqual(check(),r52)
    def test_wrapper_arguments(self):
        with tempfile.TemporaryDirectory() as tmp:
            d=Path(tmp);args=d/'args.txt';parent=d/'parent.sh'
            parent.write_text('#!/bin/sh\nprintf "%s\\n" "$@" > "'+str(args)+'"\n');parent.chmod(0o755)
            api=d/'api';api.mkdir();crt1=api/'crtbegin_so.o';crt2=api/'crtend_so.o';crt1.touch();crt2.touch()
            wrapper=d/'r52.sh';wrapper.write_text(
                '#!/bin/sh\nset -eu\ncase " $* " in\n  *" -shared "*)\n'
                f'    exec "{parent}" -nostartfiles -Wl,-L,"{api}" -Wl,-L,"{api.parent}" -Xlinker "{crt1}" "$@" -Xlinker "{crt2}"\n'
                f'    ;;\n  *) exec "{parent}" "$@" ;;\nesac\n');wrapper.chmod(0o755)
            subprocess.run([str(wrapper),'-shared','probe.o','-o','probe.so'],check=True)
            argv=args.read_text().splitlines()
            self.assertIn('-nostartfiles',argv)
            self.assertLess(argv.index(str(crt1)),argv.index('probe.o'))
            self.assertGreater(argv.index(str(crt2)),argv.index('probe.o'))
            self.assertIn('-Wl,-L,'+str(api),argv)
            subprocess.run([str(wrapper),'--version'],check=True)
            self.assertEqual(args.read_text().splitlines(),['--version'])
    def test_ready_and_ci_gate(self):
        p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh')],capture_output=True,text=True)
        self.assertEqual(p.returncode,0,p.stderr)
        for token in ('APPFORGE_R52_CRT_ABSOLUTE_PATHS=VERIFIED',
                      'APPFORGE_R52_LINKER_WRAPPER=PASS',
                      'APPFORGE_R52_RUST_API26_REAL_ELF=PASS',
                      'APPFORGE_R52_RUST_API26_REAL_ELF=FAILED',
                      'probe_linker(candidate)',
                      'APPFORGE_R52_API26_CRT_ABSOLUTE_V1'):
            self.assertIn(token,S+SH)
        self.assertLess(S.index('proof=probe_linker(candidate)'),S.index('APPFORGE_R52_RUST_API26_REAL_ELF=PASS'))
        self.assertLess(SH.index('APPFORGE_R52_LINKER_WRAPPER=PASS'),SH.index('touch "$EXPO_ARM64_NDK_MARKER"'))
        self.assertLess(SH.index('if [ "$ENGINE" = rust-native ];then\n /usr/bin/python3.12'),SH.index('touch "$READY"'))
        self.assertIn('result.output.contains("APPFORGE_R52_")',K)
        self.assertIn('line.startsWith("APPFORGE_R52_")',K)
        self.assertIn('output.versionCode.set(575)',G)
        self.assertIn('VERSION_CODE=575',W)
        self.assertIn('tests/test_r52_rust_crt_absolute.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
