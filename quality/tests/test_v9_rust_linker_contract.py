"""V5 contract: Rust host ELF objects+Cargo WASM; never conflict with locked libc6."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
RT = ROOT / 'android-app/app/src/main/assets/device-build'
BUILD = ROOT / 'android-app/app/src/main/java/com/appforge/studio/build'

class RustBaselineContract(unittest.TestCase):
    def test_rust_prerequisites_never_upgrade_locked_libc_or_install_expo(self):
        s = (RT/'install-rust-prerequisites.sh').read_text()
        self.assertIn('APPFORGE_APT_MANAGED_INSTALL', s)
        self.assertIn('appforge_snapshot_apt_get install', s)
        self.assertIn('PYTHON_ANDROID', s)
        self.assertNotIn('EXPO_REACT_NATIVE', s)
        self.assertNotIn('libc6-dev=', s)
        self.assertNotIn('clang=', s)
        self.assertNotIn('lld=', s)
        self.assertNotIn('apt-get --allow-unauthenticated', s)
        self.assertNotIn('wget ', s)
        self.assertNotIn('curl ', s)

    def test_rust_prefetch_and_staging_are_python_only(self):
        prefetch = (BUILD/'OfflineBuildPackManager.kt').read_text()
        reuse = (BUILD/'V9R2AptInstallReuse.kt').read_text()
        self.assertIn('OfflineModuleId.RUST->listOf(OfflineModuleId.PYTHON_ANDROID)', prefetch)
        self.assertIn('OfflineModuleId.RUST->setOf(OfflineModuleId.PYTHON_ANDROID)', reuse)
        self.assertNotIn('OfflineModuleId.RUST->if(architecture', prefetch)
        self.assertNotIn('OfflineModuleId.RUST->if(architecture', reuse)

    def test_real_native_object_and_frozen_offline_wasm_link(self):
        s = (RT/'module-functional-test.sh').read_text()
        self.assertIn('APPFORGE_RUSTC_MISSING_OR_NOT_EXECUTABLE', s)
        self.assertIn('--emit=obj', s)
        self.assertIn("'7f454c46'", s)
        self.assertIn('generate-lockfile --offline', s)
        self.assertIn('--frozen --offline --release', s)
        self.assertIn('--target wasm32-unknown-unknown', s)
        self.assertIn("'0061736d01000000'", s)
        self.assertIn('APPFORGE_RUST_HOST_NATIVE_OBJECT=PASS', s)
        self.assertIn('APPFORGE_RUST_CARGO_WASM_LINK=PASS', s)
        self.assertIn('APPFORGE_RUST_HOST_ELF_EXECUTION=NOT_TESTED_LINKER_NOT_BUNDLED', s)
        self.assertNotIn('target/$rust_host/debug/offline_probe', s)
        self.assertNotIn('CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=', s)

    def test_official_pinned_rust_installer_still_compiles_real_wasm(self):
        s = (RT/'install-rust-module.py').read_text()
        self.assertIn("'wasm32-unknown-unknown'", s)
        self.assertIn("'--frozen','--offline','--release'", s)
        self.assertIn("artifact.read_bytes()[:8]!=b'\\0asm", s)

    def test_module_advertises_only_proven_capabilities(self):
        s = (BUILD/'OfflineModulePlan.kt').read_text()
        self.assertIn('host ELF object + WASM std', s)
        self.assertNotIn('host/WASM std', s)

if __name__ == '__main__': unittest.main()
