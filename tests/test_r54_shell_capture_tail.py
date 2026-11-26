"""R54 output capture must keep late Rust failures in a strict 512 KiB budget."""
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[1]
L = (ROOT/'android-app/app/src/main/java/com/appforge/studio/terminal/LinuxShellEngine.kt').read_text()
K = (ROOT/'android-app/app/src/main/java/com/appforge/studio/build/OfflineBuildPackManager.kt').read_text()
G = (ROOT/'android-app/app/build.gradle.kts').read_text()
W = (ROOT/'.github/workflows/r54-unsigned-apk-ci.yml').read_text()

class TestR54(unittest.TestCase):
    def test_bounded_capture_implementation(self):
        for s in ('MAX_CAPTURE_CHARS =', 'MAX_CAPTURE_TAIL_CHARS = 64 * 1_024',
                  'outputCharsSeen += count.toLong()',
                  'outputTail.append(buffer, 0, count)',
                  'outputTail.delete(',
                  'outputCharsSeen <= MAX_CAPTURE_CHARS.toLong()',
                  'APPFORGE_R54_CAPTURE_TAIL=TRUNCATED_HEAD_AND_TAIL'):
            self.assertIn(s, L)
        self.assertIn('MAX_CAPTURE_CHARS - MAX_CAPTURE_TAIL_CHARS - 128',L)
        self.assertLess(L.index('MAX_CAPTURE_TAIL_CHARS ='), len(L))
    def test_head_and_tail_contract(self):
        cap = 512*1024
        tail_size=64*1024
        marker='\nAPPFORGE_R54_CAPTURE_TAIL=TRUNCATED_HEAD_AND_TAIL\n'
        def collect(chunks):
            head='';tail='';total=0
            for chunk in chunks:
                total+=len(chunk)
                head=(head+chunk)[:cap]
                tail=(tail+chunk)[-tail_size:]
            if total<=cap:return head
            return head[:cap-tail_size-128]+marker+tail
        self.assertEqual(collect(['small\n','output\n']),'small\noutput\n')
        early='E'*500000
        later='APPFORGE_R53_LINKER_ENTRY=appforge-rust-api26-crt-absolute-r52\n'
        big=collect([early,'f'*100000,later])
        self.assertIn(marker.strip(),big)
        self.assertIn(later,big)
        self.assertTrue(big.startswith('E'*500))
        self.assertLessEqual(len(big),cap)
    def test_diagnostics_and_unsigned_ci(self):
        self.assertIn('line.startsWith("APPFORGE_R54_")',K)
        self.assertIn('File(diagnosticBase, "r53-rust-link-diagnostics")',K)
        self.assertIn('output.versionCode.set(577)',G)
        self.assertIn('VERSION_CODE=577',W)
        self.assertIn('tests/test_r54_shell_capture_tail.py',W)
        self.assertIn('DO_NOT_INSTALL_UNTIL_LOCAL_SIGNING=YES',W)
        self.assertNotIn('adb install',W)
if __name__=='__main__':unittest.main(verbosity=2)
