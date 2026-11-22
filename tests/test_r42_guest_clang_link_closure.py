"""R42 source security and real `readlink -f` behavior on fixtures."""
from pathlib import Path
import subprocess
import tempfile
import unittest
R = Path(__file__).resolve().parents[1]
S = (R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh').read_text()
K = (R/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G = (R/'android-app/app/build.gradle.kts').read_text()
W = (R/'.github/workflows/r42-unsigned-apk-ci.yml').read_text()
PREFIX = '  # R42: package-authenticated cross-directory SONAME links'
END = '  [ -f "$r41_source" ] && [ ! -L "$r41_source" ] &&'
class R42LinkClosure(unittest.TestCase):
    def test_01_shell_syntax(self):
        p=subprocess.run(['sh','-n',str(R/'android-app/app/src/main/assets/device-build/install-ndk-host-isolated-v7.sh')],capture_output=True,text=True)
        self.assertEqual(0,p.returncode,p.stderr)
    def test_02_guarded_resolution_once(self):
        self.assertEqual(S.count(PREFIX),1)
        self.assertIn('r41_source=$(readlink -f "$r41_signed_lib")',S)
        self.assertIn('APPFORGE_R42_CLANG_LINK_TARGET_OUTSIDE_SIGNED_SYSROOT',S)
        self.assertIn('APPFORGE_R42_CLANG_LINK_RESOLVED=',S)
        self.assertIn('cmp -s "$r41_source" "$r41_guest_lib"',S)
        self.assertIn('APPFORGE_R41_CLANG_GUEST_SOURCE_READ=PASS',S)
        self.assertIn('APPFORGE_R33_CMAKE_GUEST_REAL_P_TEST=PASS',S)
        self.assertNotIn('cat "$probe_dir/readable.c" | "$BIN/clang"',S)
    def _try_link(self,target,layout):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp); base=root/'sysroot/usr/lib/aarch64-linux-gnu'; llvm=root/'sysroot/usr/lib/llvm-18/lib'
            base.mkdir(parents=True);llvm.mkdir(parents=True)
            (llvm/'libLLVM.so.1').write_bytes(b'fixture')
            if layout=='chain':
                (llvm/'libLLVM.so.18.1').symlink_to('libLLVM.so.1')
                (base/'libLLVM.so.18.1').symlink_to('../llvm-18/lib/libLLVM.so.18.1')
            elif layout=='outside':
                (base/'libLLVM.so.18.1').symlink_to('/etc/passwd')
            elif layout=='loop':
                (base/'libLLVM.so.18.1').symlink_to('libLLVM.so.18.1')
            elif layout=='direct':
                (base/'libLLVM.so.18.1').write_bytes(b'fixture')
            else:
                raise AssertionError(layout)
            # Run the exact R42 source block; no package extraction or APK mutation.
            fragment=S.split(PREFIX,1)[1].split(END,1)[0]
            script='set -eu\n'+"PREFIX='"+str(root)+"'\n"+"r41_soname=libLLVM.so.18.1\n"+"r41_signed_lib=\"$PREFIX/sysroot/usr/lib/aarch64-linux-gnu/$r41_soname\"\n"+PREFIX+fragment+"printf 'RESOLVED=%s\\n' \"$r41_source\"\n"
            p=subprocess.run(['sh','-c',script],capture_output=True,text=True)
            return p.returncode,p.stdout+p.stderr
    def test_03_ubuntu_parent_link_accepted(self):
        code,out=self._try_link('','chain')
        self.assertEqual(code,0,out)
        self.assertIn('/usr/lib/llvm-18/lib/libLLVM.so.1',out)
    def test_04_direct_file_accepted(self):
        code,out=self._try_link('','direct')
        self.assertEqual(code,0,out)
    def test_05_absolute_escape_rejected(self):
        code,out=self._try_link('','outside')
        self.assertNotEqual(code,0,out)
        self.assertIn('OUTSIDE_SIGNED_SYSROOT',out)
    def test_06_symlink_loop_rejected(self):
        code,out=self._try_link('','loop')
        self.assertNotEqual(code,0,out)
    def test_07_apk_identity_snapshot_diagnostics(self):
        self.assertIn('appforgeR42OriginalIdentityUpgrade',G)
        self.assertIn('output.versionCode.set(565)',G)
        self.assertIn('VERSION_CODE=565',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
        self.assertIn('line.startsWith("APPFORGE_R42_")',K)
        self.assertIn('r41-clang-diagnostics',K)
if __name__=='__main__': unittest.main(verbosity=2)
