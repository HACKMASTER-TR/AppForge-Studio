"""Standalone host tests for real V3 Bash/PTY capture, not UI/clipboard assertions."""
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
PKG = ROOT / "android-app/app/src/main/java/com/appforge/studio/terminal"
TEST = ROOT / "android-app/app/src/test/java/com/appforge/studio/terminal/TerminalPtyCopyCaptureTest.kt"

STUB = """package org.junit
annotation class Test
object Assert {
    @JvmStatic fun assertTrue(x: Boolean) { check(x) }
    @JvmStatic fun assertFalse(x: Boolean) { check(!x) }
    @JvmStatic fun assertEquals(x: Any?, y: Any?) { check(x == y) { \"expected $x actual $y\" } }
}
"""
RUNNER = """package com.appforge.studio.terminal
fun main() {
    val tests = TerminalPtyCopyCaptureTest()
    val methods = tests.javaClass.declaredMethods.filter { it.getAnnotation(org.junit.Test::class.java) != null }
    check(methods.size >= 7)
    methods.forEach { it.invoke(tests) }
    println(\"V3_KOTLIN_CAPTURE_TESTS=PASS count=${methods.size}\")
}
"""

class PtyCopyTests(unittest.TestCase):
    def test_real_kotlin_engine_including_ansi_buffer(self):
        compiler = shutil.which("kotlinc")
        self.assertIsNotNone(compiler)
        with tempfile.TemporaryDirectory(prefix="pty-v3-host-") as temp:
            root = pathlib.Path(temp)
            (root / "JUnit.kt").write_text(STUB)
            (root / "Run.kt").write_text(RUNNER)
            jar = root / "v3-tests.jar"
            subprocess.run([compiler, str(PKG / "AnsiTerminalBuffer.kt"),
                str(PKG / "TerminalPtyCopyCapture.kt"), str(TEST),
                str(root / "JUnit.kt"), str(root / "Run.kt"),
                "-include-runtime", "-d", str(jar)], check=True, timeout=120)
            subprocess.run(["java", "-jar", str(jar)], check=True, timeout=40)

    def test_v3_ui_and_history_loss_regression_contract(self):
        screen = (PKG / "TerminalWorkspaceScreen.kt").read_text()
        buffer = (PKG / "AnsiTerminalBuffer.kt").read_text()
        capture = (PKG / "TerminalPtyCopyCapture.kt").read_text()
        self.assertNotIn("Kabuk/PTY: NOT_SUPPORTED", screen)
        self.assertIn("tamamlanan Bash/PTY komutları", screen)
        self.assertIn("hassas bilgiler her zaman tespit edilemeyebilir", screen)
        self.assertIn('"OUTPUT_INCOMPLETE" ->', screen)
        self.assertIn("outputHistoryIncomplete", buffer)
        self.assertIn("scrollback.removeFirst()", buffer)
        self.assertIn("output.outputHistoryIncomplete", capture)
        self.assertIn("historyLimit: Int = 20_000", capture)

    def test_shell_hook_and_scoped_wiring(self):
        panel = (PKG / "LocalPtyTerminalPanel.kt").read_text()
        sink = (PKG / "TerminalSafeAutoCopy.kt").read_text()
        screen = (PKG / "TerminalWorkspaceScreen.kt").read_text()
        self.assertIn('environment["PS0"]', panel)
        self.assertIn('environment["PROMPT_COMMAND"]', panel)
        self.assertIn('APPFORGE_CWD_PROMPT_COMMAND', panel)
        self.assertIn('TerminalPtyCopyCapture(', panel)
        self.assertIn('observeAutoCopyResult(', panel)
        self.assertIn('isAutoCopyResultCurrent(', panel)
        self.assertIn('copyPtyCompleted(', sink)
        self.assertIn('freshAdmin(token)', sink)
        self.assertIn('LocalPtySessionRegistry.isAutoCopyResultCurrent(event)', screen)
        self.assertIn('onPtyAutoCopyCompleted = { event ->', screen)
        self.assertIn('onCopyContextInvalidated()', panel)
        self.assertNotIn('termux-clipboard-set', sink)


    def test_real_interactive_bash_ps0_does_not_inject_soh_stx(self):
        """Empirical Bash PTY contract: no SOH/STX inserted ahead of output."""
        import os
        import pty
        import select
        import signal
        import time
        self.assertIn(r'"\\e]777;afcopy-begin=$copyProtocolToken\\a"',
                      (PKG / "LocalPtyTerminalPanel.kt").read_text())
        self.assertNotIn(r'"\\[\\e]777;afcopy-begin=',
                         (PKG / "LocalPtyTerminalPanel.kt").read_text())
        token='0123456789abcdef0123456789abcdef'
        pid, fd=pty.fork()
        if pid==0:
            os.environ['PS0']=r'\e]777;afcopy-begin='+token+r'\a'
            os.environ['PROMPT_COMMAND']=(
                'printf "\\033]777;afcopy-end='+token+';%s\\007" "$?"')
            os.environ['PS1']='APPFORGE_TEST$ '
            os.execl('/bin/bash','bash','--noprofile','--norc','-i')
        def drain(seconds):
            received=bytearray()
            until=time.monotonic()+seconds
            while time.monotonic()<until:
                if select.select([fd],[],[],0.10)[0]:
                    try:
                        chunk=os.read(fd,8192)
                    except OSError:
                        break
                    if not chunk: break
                    received.extend(chunk)
                    if len(received)>65536: break
            return bytes(received)
        try:
            drain(.3)
            os.write(fd,b'printf "APPFORGE_PTY_OK\\n"\r')
            data=drain(1.0)
            begin=(b'\x1b]777;afcopy-begin='+token.encode()+b'\x07')
            end=(b'\x1b]777;afcopy-end='+token.encode()+b';0\x07')
            self.assertIn(begin,data)
            self.assertIn(end,data)
            captured=data.split(begin,1)[1].split(end,1)[0]
            self.assertEqual(b'APPFORGE_PTY_OK\r\n',captured)
        finally:
            try: os.kill(pid,signal.SIGKILL)
            except ProcessLookupError: pass
            os.close(fd)

if __name__ == "__main__":
    unittest.main()
