"""V7 NDK host-isolation contract; no shared libc upgrades or weakened checks."""
import pathlib, unittest
R=pathlib.Path(__file__).resolve().parents[2]
T=R/'android-app/app/src/main/assets/device-build'
B=R/'android-app/app/src/main/java/com/appforge/studio/build'

class NdkIsolationContract(unittest.TestCase):
    def test_signed_r2_packages_and_arm64_only(self):
        s=(T/'install-ndk-host-isolated-v7.sh').read_text()
        for token in ('APPFORGE_MODULE_ID','RUST_ANDROID_NDK','APPFORGE_APT_MANAGED_INSTALL',
                      'APPFORGE_R2_RELEASE_AUTHORIZED','as_verify_indices','EXPO_REACT_NATIVE',
                      '77efb669105e5c17fda85dbd653b78b22b0614eaf70c4db8c7d00a8bb4df9e2f',
                      'sha256sum -c -', 'dpkg-deb -f', '73', '69'):
            self.assertIn(token,s)
        self.assertIn('libc6|libc-bin|libc6-dev|libc-dev-bin',s)
        self.assertIn("'2.39-0ubuntu8.7'",s)
        for bad in ('apt-get install','dpkg --force','--allow-unauthenticated',
                    'Acquire::https::Verify-Peer=false', 'curl -k','wget --no-check-certificate'):
            self.assertNotIn(bad,s)

    def test_no_shared_apt_mutations_for_rust_ndk(self):
        s=(T/'install-toolchain.sh').read_text()
        self.assertIn('NDK_HOST_ISOLATED=0',s)
        self.assertIn('if [ "$NDK_HOST_ISOLATED" = 1 ];then',s)
        self.assertIn('/bin/sh /workspace/runtime/install-ndk-host-isolated-v7.sh',s)
        self.assertIn('if [ "$NDK_HOST_ISOLATED" != 1 ];then repair_broken_openjdk;fi',s)
        self.assertIn('if [ "$NDK_HOST_ISOLATED" != 1 ];then repair_stale_node_packages;fi',s)
        self.assertIn('fi # end shared APT package install;',s)
        self.assertIn('cmake_host="$ROOT/ndk-host-v7/bin/cmake"',s)
        self.assertIn('ndk_host_bin="$ROOT/ndk-host-v7/bin"',s)
        self.assertIn('APPFORGE_EXPO_ARM64_NDK_COMPILER_RT_LINK=PASS',s)
        self.assertIn('APPFORGE_EXPO_ARM64_NDK_CXX_LINK=PASS',s)

    def test_compiler_execution_is_mandatory(self):
        s=(T/'install-ndk-host-isolated-v7.sh').read_text()
        for tool in ('clang','ld.lld','cmake','ninja','llvm-ar'):
            self.assertIn('"$BIN/'+tool+'" --version',s)
        self.assertIn('APPFORGE_NDK_HOST_EXECUTION_FAILED',s)
        self.assertIn('exec "$executable" "$@"',s)
        self.assertIn('APPFORGE_NDK_HOST_PRIVATE_TOOLS=PASS',s)
        self.assertIn('LD_LIBRARY_PATH=',s)
        self.assertNotIn('LD_PRELOAD=',s)

    def test_owner_and_recipe_witness_require_private_host(self):
        s=(B/'OfflineBuildPackManager.kt').read_text()
        o=(B/'ModuleOwnershipStore.kt').read_text()
        self.assertIn('install-ndk-host-isolated-v7.sh',s)
        self.assertIn('opt/appforge-device/ndk-host-v7/verified.tsv',s)
        self.assertIn('opt/appforge-device/ndk-host-v7/',o)
        self.assertIn('ModuleOwnershipStore(staged).record',s)
        self.assertIn('runtimeProbe(existing)',s)
        self.assertIn('writeAttestation(app,staged,id)',s)

    def test_pinned_ndk_prefetch_and_real_link_probes_retained(self):
        s=(B/'OfflineBuildPackManager.kt').read_text()
        reuse=(B/'V9R2AptInstallReuse.kt').read_text()
        tools=(T/'install-toolchain.sh').read_text()
        self.assertIn('OfflineModuleId.RUST_ANDROID_NDK->listOf(OfflineModuleId.EXPO_REACT_NATIVE)',s)
        self.assertIn('OfflineModuleId.RUST_ANDROID_NDK->setOf(OfflineModuleId.EXPO_REACT_NATIVE)',reuse)
        for token in ('-fuse-ld=lld','APPFORGE_EXPO_NDK_C_PROBE_ABI_FAIL',
                      'APPFORGE_EXPO_NDK_CPP_PROBE_ABI_FAIL','APPFORGE_EXPO_NDK_CPP_LINK_ABI_FAIL'):
            self.assertIn(token,tools)

    def test_v7_ci_runs_both_apks(self):
        s=(R/'.github/workflows/v9-offline-module-center-r2.yml').read_text()
        self.assertIn('codex/v9-r2-ndk-isolated-host-20261110',s)
        self.assertEqual(s.count("github.ref == 'refs/heads/codex/v9-r2-ndk-isolated-host-20261110'"),2)

if __name__=='__main__':unittest.main()
