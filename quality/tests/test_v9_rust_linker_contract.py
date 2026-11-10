"""Fail-closed V9 R2 ARM64 Rust Clang/Cargo integration contract."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
RT = ROOT / 'android-app/app/src/main/assets/device-build'
BUILD = ROOT / 'android-app/app/src/main/java/com/appforge/studio/build'

class RustLinkerContract(unittest.TestCase):
    def test_pinned_arm64_clang_and_dev_packages_unchanged(self):
        catalog = (RT/'apt-module-packages-v1.tsv').read_text()
        names = {r[2]: r[3] for line in catalog.splitlines() if (r := line.split('\t')) and len(r) >= 8 and r[0] == 'EXPO_REACT_NATIVE' and r[1] == 'arm64'}
        self.assertEqual(names['clang'], '1:18.0-59~exp2')
        self.assertEqual(names['lld'], '1:18.0-59~exp2')
        self.assertEqual(names['libc6-dev'], '2.39-0ubuntu8.9')

    def test_rust_apt_stays_signed_no_direct_network(self):
        s = (RT/'install-rust-prerequisites.sh').read_text()
        self.assertIn('APPFORGE_APT_MANAGED_INSTALL', s)
        self.assertIn('appforge_snapshot_apt_get install', s)
        self.assertIn('EXPO_REACT_NATIVE', s)
        self.assertIn('APPFORGE_RUST_LINKER_NOT_INSTALLED', s)
        self.assertNotIn('apt-get --allow-unauthenticated', s)
        self.assertNotIn('wget ', s)
        self.assertNotIn('curl ', s)
        self.assertIn("'libc6-dev=2.39-0ubuntu8.9'", s)

    def test_r2_prefetch_and_staging_require_pinned_packages(self):
        prefetch = (BUILD/'OfflineBuildPackManager.kt').read_text()
        reuse = (BUILD/'V9R2AptInstallReuse.kt').read_text()
        self.assertIn('OfflineModuleId.RUST->if(architecture=="arm64") listOf(OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.EXPO_REACT_NATIVE)', prefetch)
        self.assertIn('OfflineModuleId.RUST->if(architecture=="arm64") setOf(OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.EXPO_REACT_NATIVE)', reuse)
        self.assertIn('V9SelectiveR2ModuleCache.preview(', reuse)
        self.assertIn('V9SelectiveR2ModuleCache.fetchSelectedWithConsent(', prefetch)

    def test_rust_cargo_keeps_native_offline_compile_and_execution(self):
        s = (RT/'module-functional-test.sh').read_text()
        self.assertIn('RUSTC="$RUST_BIN/rustc"', s)
        self.assertIn('CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER="$linker"', s)
        self.assertIn('cargo" build --offline --target "$rust_host"', s)
        self.assertIn('target/$rust_host/debug/offline_probe', s)
        self.assertIn('APPFORGE_RUST_LINKER_MISSING', s)
        self.assertNotIn('APPFORGE_FUNCTIONAL_PASS:RUST\n', s)

if __name__ == '__main__':
    unittest.main()
