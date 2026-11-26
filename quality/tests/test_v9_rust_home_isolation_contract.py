"""V6 regression contract: Cargo functional probe must not change /root."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]
RT=ROOT/'android-app/app/src/main/assets/device-build'
BUILD=ROOT/'android-app/app/src/main/java/com/appforge/studio/build'
FLOW=ROOT/'.github/workflows/v9-offline-module-center-r2.yml'

class RustHomeIsolationContract(unittest.TestCase):
    def test_probe_disposable_home_and_cleanup(self):
        s=(RT/'module-functional-test.sh').read_text()
        self.assertIn('WORK=$(mktemp -d /tmp/appforge-functional.XXXXXXXX)',s)
        self.assertIn('trap \'rm -rf "$WORK"\' EXIT HUP INT TERM',s)
        self.assertIn('mkdir -p "$WORK/home" "$WORK/cargo-home" "$WORK/rustup-home"',s)
        self.assertIn('export HOME="$WORK/home" CARGO_HOME="$WORK/cargo-home" RUSTUP_HOME="$WORK/rustup-home"',s)
        self.assertLess(s.index('export HOME="$WORK/home"'),s.index('"$RUST_BIN/rustc" -vV'))
        self.assertIn('"$RUST_BIN/cargo" build --frozen --offline --release',s)
        self.assertIn('APPFORGE_RUST_CARGO_WASM_LINK=PASS',s)

    def test_rust_installer_probe_already_isolated(self):
        s=(RT/'install-rust-module.py').read_text()
        self.assertIn("CARGO_HOME=str(root/'cargo-home')",s)
        self.assertIn("CARGO_NET_OFFLINE='true'",s)
        self.assertIn("'--frozen','--offline','--release'",s)

    def test_user_paths_still_protected(self):
        s=(BUILD/'ModuleOwnershipStore.kt').read_text()
        self.assertIn('(key.startsWith("home/") || key.startsWith("root/"))',s)
        self.assertIn('require(protectedUserChanges.isEmpty())',s)
        self.assertIn('protectedUserChanges.take(3).joinToString',s)
        self.assertIn('unknownRemoved.isEmpty()',s)
        self.assertIn('fun verifyNamespace(id:OfflineModuleId)',s)

    def test_apt_versions_unchanged(self):
        s=(RT/'install-rust-prerequisites.sh').read_text()
        self.assertIn('appforge_snapshot_apt_get install',s)
        self.assertNotIn('libc6-dev=',s)
        self.assertNotIn('clang=',s)
        self.assertIn('APPFORGE_APT_MANAGED_INSTALL',s)

    def test_ci_jobs_both_included(self):
        s=FLOW.read_text()
        self.assertIn('codex/v9-r2-rust-home-isolation-20261110',s)
        self.assertEqual(s.count("github.ref == 'refs/heads/codex/v9-r2-rust-home-isolation-20261110'"),2)
        self.assertIn("github.ref == 'refs/heads/codex/v9-r2-rust-wasm-baseline-20261110'",s)

if __name__=='__main__':unittest.main()
