"""R49 Rust JNI Android linker discovery: strict pin and real 16KiB probe."""
from pathlib import Path
import importlib.util, os, subprocess, tempfile, unittest
R=Path(__file__).resolve().parents[1]
PY=R/'android-app/app/src/main/assets/device-build/rust-android.py'
S=PY.read_text()
K=(R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G=(R/'android-app/app/build.gradle.kts').read_text()
W=(R/'.github/workflows/r49-unsigned-apk-ci.yml').read_text()
T=(R/'android-app/app/src/main/assets/device-build/install-toolchain.sh').read_text()
class R49(unittest.TestCase):
    def load(self):
        spec=importlib.util.spec_from_file_location('r49_trusted_rust_android',PY)
        obj=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(obj)
        return obj
    def test_pinned_linker_is_strict_and_never_project_fallback(self):
        code=self.load()
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp); sdk=root/'android-sdk'
            ndk=sdk/'ndk/27.1.12297006'
            bin_dir=ndk/'toolchains/llvm/prebuilt/linux-x86_64/bin'
            bin_dir.mkdir(parents=True)
            marker=root/'.expo-arm64-ndk-host-v3'; marker.touch()
            prop=ndk/'source.properties';prop.write_text('Pkg.Revision = 27.1.12297006\n')
            clang=bin_dir/'clang';clang.write_text('#!/bin/sh\n# APPFORGE_EXPO_ARM64_NDK_CLANG_WRAPPER_V3\n# -resource-dir= -B\n# --ld-path=/usr/lib/llvm-18/bin/ld.lld\n')
            clang.chmod(0o755)
            check=lambda:code.verified_appforge_arm64_linker(sdk,'aarch64',trusted_sdk=sdk)
            self.assertEqual(check(),clang)
            self.assertIsNone(code.verified_appforge_arm64_linker(sdk,'x86_64',trusted_sdk=sdk))
            self.assertIsNone(code.verified_appforge_arm64_linker(sdk,'aarch64'))
            marker.unlink()
            self.assertIsNone(check())
            marker.touch()
            prop.write_text('Pkg.Revision = 29.0.0\n')
            self.assertIsNone(check())
            prop.write_text('Pkg.Revision = 27.1.12297006\n')
            clang.write_text('#!/bin/sh\n')
            self.assertIsNone(check())
    def test_no_fake_ready_real_elf_gate(self):
        for token in ('APPFORGE_R49_RUST_API26_16K_LINK=PASS',
                      'APPFORGE_R49_RUST_API26_16K_LINK=FAILED',
                      'APPFORGE_R49_PINNED_NDK_WRAPPER=NOT_VERIFIED',
                      'probe_linker(candidate)',
                      "defined_exports(result)",
                      "minApi=26",
                      "max-page-size=16384",
                      "no fallback"):
            self.assertIn(token,S)
        self.assertIn('APPFORGE_EXPO_ARM64_NDK_HOST=PASS',T)
        self.assertIn('touch "$READY"',T)
        self.assertLess(T.index(' --probe-linker --sdk "$SDK"',T.index('if [ "$ENGINE" = rust-native ];then\n /usr/bin/python3.12')),T.index('touch "$READY"'))
    def test_identity_ci_and_diagnostics(self):
        self.assertIn('result.output.contains("APPFORGE_R49_")',K)
        self.assertIn('line.startsWith("APPFORGE_R49_")',K)
        self.assertIn('appforgeR49OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(572)',G)
        self.assertIn('VERSION_CODE=572',W)
        self.assertIn('tests/test_r49_verified_rust_ndk_linker.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
if __name__=='__main__':unittest.main(verbosity=2)
