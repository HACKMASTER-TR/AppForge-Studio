"""Execute production Kotlin policy, sink and in-repo tests with inert host adapters.
No Android/device acceptance; HTTPS is intercepted in-process, never sent to a network.
Generated compiler files are confined to the allowed terminal test source tree.
"""
import re
import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACKAGE = ROOT / 'android-app/app/src/main/java/com/appforge/studio/terminal'
TESTS = ROOT / 'android-app/app/src/test/java/com/appforge/studio/terminal'
BASE = '89d1d156614714c47d2b3337a2bf0bfba59c63cd'

STUBS = {
    'JUnit.kt': '''package org.junit
annotation class Test
object Assert {
    @JvmStatic fun assertTrue(value: Boolean) { check(value) }
    @JvmStatic fun assertFalse(value: Boolean) { check(!value) }
}
''',
    'Android.kt': '''package android.content
class ClipData(val text: CharSequence) {
    companion object { fun newPlainText(label: String, text: CharSequence) = ClipData(text) }
}
class ClipboardManager {
    val writes = mutableListOf<String>()
    fun setPrimaryClip(clip: ClipData) { writes.add(clip.text.toString()) }
}
open class Context {
    val clipboard = ClipboardManager()
    var onLookup: () -> Unit = {}
    fun getSystemService(name: String): Any { onLookup(); return clipboard }
    companion object { const val CLIPBOARD_SERVICE = "clipboard" }
}
''',
    'Clock.kt': '''package android.os
object SystemClock {
    var offset = 0L
    fun elapsedRealtime(): Long = System.nanoTime() / 1_000_000 + offset
}
''',
    'Model.kt': '''package com.appforge.studio.model
val DEFAULT_CONTROL_PLANE_URL = "https://inert-autocopy.example.invalid"
''',
    'Session.kt': '''package com.appforge.studio.net
data class Session(val userId: String, val token: String, val email: String)
''',
    'Security.kt': '''package com.appforge.studio.security
import android.content.Context
object OwnerAccessPolicy {
    var token: String? = "INERT_GOOGLE_ID_TOKEN_" + "X".repeat(64)
    var active: Boolean = true
    fun isActiveOwner(context: Context): Boolean = active && token != null
    fun currentGoogleIdToken(): String? = token
}
''',
    'Json.kt': '''package org.json
class JSONObject(private val text: String) {
    fun optBoolean(name: String, fallback: Boolean): Boolean {
        val value = text.substringAfter(name, "").substringAfter(":", "").trimStart()
        return when {
            value.startsWith("true") -> true
            value.startsWith("false") -> false
            else -> fallback
        }
    }
    fun optLong(name: String, fallback: Long): Long =
        text.substringAfter(name, "").substringAfter(":", "")
            .takeWhile { it.isDigit() }.toLongOrNull() ?: fallback
}
''',
}

HARNESS = r'''package com.appforge.studio.terminal
import android.content.Context
import com.appforge.studio.security.OwnerAccessPolicy
import kotlinx.coroutines.*
import java.net.*
import javax.net.ssl.HttpsURLConnection
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private object Wire {
    var adminVerified = true
    var ok = true
    var code = 200
    var fail = false
    var expiresAt = System.currentTimeMillis() / 1000L + 1_800L
    var entered: CountDownLatch? = null
    var release: CountDownLatch? = null
    var requests = 0
}
private class InertConnection(url: URL): HttpsURLConnection(url) {
    override fun connect() {}
    override fun disconnect() {}
    override fun usingProxy() = false
    override fun getCipherSuite() = "INERT"
    override fun getLocalCertificates(): Array<java.security.cert.Certificate>? = null
    override fun getServerCertificates(): Array<java.security.cert.Certificate> = emptyArray()
    override fun getResponseCode(): Int {
        check(!instanceFollowRedirects)
        check(url.host == "inert-autocopy.example.invalid")
        check(url.path == "/api/admin/system-status")
        check(getRequestProperty("Authorization")?.startsWith("Bearer INERT_GOOGLE_") == true)
        check(getRequestProperty("X-AppForge-Device-ID") == null)
        Wire.requests++
        Wire.entered?.countDown()
        check(Wire.release?.await(10, TimeUnit.SECONDS) != false)
        if (Wire.fail) throw java.io.IOException("inert network failure")
        return Wire.code
    }
    override fun getInputStream() = ByteArrayInputStream(
        """{"ok":${Wire.ok},"adminVerified":${Wire.adminVerified},"expiresAt":${Wire.expiresAt}}""".toByteArray())
}

fun main() = runBlocking {
    // Run production Kotlin tests plus intercepted-HTTPS clipboard boundary checks.
    val tests = TerminalSafeAutoCopyPolicyTest()
    var count = 0
    tests.javaClass.declaredMethods.filter { it.getAnnotation(org.junit.Test::class.java) != null }
        .forEach { it.invoke(tests); count++ }
    check(count >= 11)
    URL.setURLStreamHandlerFactory { protocol ->
        if (protocol == "https") object: URLStreamHandler() {
            override fun openConnection(url: URL): URLConnection = InertConnection(url)
        } else null
    }
    var terminal = "one"
    var active = true
    val context = Context()
    val sink = TerminalSafeAutoCopy(context, { terminal }, { active }, Dispatchers.Unconfined)
    val originalToken = OwnerAccessPolicy.token
    fun result(command: String = "runtime") = TerminalCopyResult.execute(command) {}!!
    suspend fun enable() {
        Wire.adminVerified = true; Wire.ok = true; Wire.code = 200; Wire.fail = false
        Wire.expiresAt = System.currentTimeMillis() / 1000L + 1_800L
        active = true; terminal = "one"
        OwnerAccessPolicy.token = originalToken; OwnerAccessPolicy.active = true
        check(sink.enable())
    }
    check(!sink.enabled)
    check(sink.begin("runtime", "one") == null)
    Wire.adminVerified = false
    check(!sink.enable())
    enable()
    val safe = sink.begin("runtime", "one")!!
    val before = Wire.requests
    check(sink.copyCompleted(safe, result()))
    check(Wire.requests == before + 1) // Fresh server check at each copy.
    check(context.clipboard.writes == listOf("Araç durumu ekranı açıldı."))
    check(!sink.copyCompleted(safe, result())) // No replay.
    suspend fun denied(mutate: () -> Unit) {
        enable()
        val ticket = sink.begin("runtime", "one")!!
        val size = context.clipboard.writes.size
        mutate()
        check(!sink.copyCompleted(ticket, result()))
        check(context.clipboard.writes.size == size)
    }
    denied { Wire.adminVerified = false }
    check(!sink.enabled)
    denied { Wire.ok = false }
    denied { Wire.expiresAt = System.currentTimeMillis() / 1000L - 10L }
    denied { Wire.expiresAt = System.currentTimeMillis() / 1000L + 20_000L }
    denied { Wire.code = 401 }
    denied { Wire.code = 403 }
    denied { Wire.code = 302 }
    denied { Wire.fail = true }
    denied { OwnerAccessPolicy.token = "INERT_GOOGLE_CHANGED_" + "X".repeat(64) }
    denied { OwnerAccessPolicy.active = false }
    denied { terminal = "two" }
    denied { active = false }
    denied { sink.disable() }
    denied { sink.begin("pwd", "one") }
    enable()
    val mismatch = sink.begin("appforge help", "one")!!
    check(!sink.copyCompleted(mismatch, result()))

    denied { context.onLookup = { sink.disable() } }
    context.onLookup = {}
    denied { context.onLookup = { android.os.SystemClock.offset = 6_001L } }
    context.onLookup = {}; android.os.SystemClock.offset = 0L

    suspend fun pending(mutate: () -> Unit, cancel: Boolean = false, timeout: Boolean = false,
                        enabling: Boolean = false) {
        enable()
        val ticket = sink.begin("runtime", "one")!!
        Wire.entered = CountDownLatch(1); Wire.release = CountDownLatch(1)
        val size = context.clipboard.writes.size
        val job = async(Dispatchers.Default) {
            if (timeout) withTimeoutOrNull(100) { sink.copyCompleted(ticket, result()) }
            else if (enabling) sink.enable() else sink.copyCompleted(ticket, result())
        }
        check(withContext(Dispatchers.IO) { Wire.entered!!.await(3, TimeUnit.SECONDS) })
        mutate()
        if (cancel) job.cancel()
        if (timeout) delay(200)
        Wire.release!!.countDown()
        try { check(job.await() != true) } catch (_: CancellationException) { }
        check(context.clipboard.writes.size == size)
        if (enabling) check(!sink.enabled)
        Wire.entered = null; Wire.release = null
    }
    pending({ sink.disable() })
    pending({ terminal = "two" })
    pending({ active = false })
    pending({ OwnerAccessPolicy.token = "INERT_GOOGLE_CHANGED_" + "X".repeat(64) })
    pending({ OwnerAccessPolicy.active = false })
    pending({}, cancel = true)
    pending({}, timeout = true)
    pending({ active = false }, enabling = true)
    pending({ terminal = "two" }, enabling = true)
    pending({ sink.disable() }, enabling = true)
    pending({}, cancel = true, enabling = true)
    println("Host Kotlin Google-admin policy and clipboard-boundary checks passed; Android runtime deferred.")
}

'''


class TerminalAutoCopyContractTest(unittest.TestCase):
    def test_actual_kotlin_policy(self):
        compiler = pathlib.Path(shutil.which('kotlinc')).resolve()
        coroutines = compiler.parent.parent / 'lib/kotlinx-coroutines-core-jvm.jar'
        self.assertTrue(coroutines.is_file(), 'Real coroutine runtime required')
        with tempfile.TemporaryDirectory(prefix='autocopy-host-', dir=TESTS) as temporary:
            directory = pathlib.Path(temporary)
            for name, source in STUBS.items():
                (directory / name).write_text(source)
            (directory / 'Harness.kt').write_text(HARNESS)
            jar = directory / 'tests.jar'
            # Compile the V3 sink with its REAL dependent production sources, not mocked classes.
            sources = [PACKAGE / 'TerminalSafeAutoCopyPolicy.kt', PACKAGE / 'TerminalSafeAutoCopy.kt',
                       PACKAGE / 'TerminalPtyCopyCapture.kt', PACKAGE / 'AnsiTerminalBuffer.kt',
                       TESTS / 'TerminalSafeAutoCopyPolicyTest.kt', *directory.glob('*.kt')]
            subprocess.run(['kotlinc', *map(str, sources), '-cp', str(coroutines),
                            '-include-runtime', '-d', str(jar)], check=True, timeout=120)
            subprocess.run(['java', '-cp', str(jar) + ':' + str(coroutines),
                            'com.appforge.studio.terminal.HarnessKt'], check=True, timeout=40)

    def test_manual_copy_pty_engines_focus_paste_and_exit_remain_unchanged(self):
        for name in ('LinuxMultiSessionTerminalPanel.kt', 'LocalPtyTerminalPanel.kt',
                     'LocalTerminalPanel.kt', 'TermuxTerminalCoreAdapter.kt',
                     'LinuxShellEngine.kt', 'LocalTerminalEngine.kt'):
            path = PACKAGE / name
            relative = path.relative_to(ROOT).as_posix()
            baseline = subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=ROOT).decode()
            current = path.read_text()
            if name == 'LocalPtyTerminalPanel.kt':
                # Preserve the original full-file regression by stripping ONLY
                # V3-marked additive blocks, then the V2 known callback changes.
                v3_blocks = re.findall(
                    r'(?m)^ *// APPFORGE_V3_BEGIN:([A-Z_]+)\n[\s\S]*?^ *// APPFORGE_V3_END:\1\n',
                    current)
                self.assertGreaterEqual(len(v3_blocks), 10)
                current = re.sub(
                    r'(?m)^ *// APPFORGE_V3_BEGIN:([A-Z_]+)\n[\s\S]*?^ *// APPFORGE_V3_END:\1\n',
                    '', current)
                current = current.replace('    onHistoryIndex: (Int) -> Unit,\n'
                                          '    onCopyContextInvalidated: () -> Unit = {}',
                                          '    onHistoryIndex: (Int) -> Unit')
                current = ''.join(line for line in current.splitlines(keepends=True)
                                  if line.strip() != 'onCopyContextInvalidated()')
            self.assertEqual(baseline, current, name)

    def test_help_text_matches_existing_safe_result(self):
        relative = (PACKAGE / 'TerminalWorkspaceScreen.kt').relative_to(ROOT).as_posix()
        baseline = subprocess.check_output(['git', 'show', BASE + ':' + relative], cwd=ROOT).decode()
        help_text = baseline.split('fun appForgeHelp(): String =', 1)[1].split('fun runCommand(', 1)[0].strip()
        self.assertIn(help_text, (PACKAGE / 'TerminalSafeAutoCopyPolicy.kt').read_text())

    def test_copy_follows_completion_and_does_not_capture_shell_output(self):
        screen = (PACKAGE / 'TerminalWorkspaceScreen.kt').read_text()
        completion = screen.index('commandJobs.remove(sessionId)')
        emission = screen.index('safeAutoCopy.copyCompleted(copyTicket, result)')
        self.assertLess(completion, emission)
        self.assertIn('running = false', screen[:completion])
        boundary = (PACKAGE / 'TerminalSafeAutoCopy.kt').read_text()
        self.assertIn('ClipData.newPlainText("AppForge result", text)', boundary)
        self.assertNotIn('termux-clipboard-set', boundary)
        self.assertNotIn('result.output', boundary)
        self.assertIn('connection.instanceFollowRedirects = false', boundary)
        self.assertIn('URL(DEFAULT_CONTROL_PLANE_URL', boundary)
        self.assertIn('policy.consume(ticket, result, authorized, currentTerminalSession().orEmpty())', ' '.join(boundary.split()))
        self.assertIn('adminVerified = response.optBoolean("adminVerified", false)', boundary)
        self.assertIn('TerminalAdminCopyDecision.permits(', boundary)
        self.assertIn('OwnerAccessPolicy.currentGoogleIdToken()', boundary)
        self.assertNotIn('SecureAccountStore', boundary)
        self.assertNotIn('DEFAULT_BUILD_SERVICE_URL', boundary)
        self.assertIn('event == Lifecycle.Event.ON_STOP', screen)
        self.assertNotIn('LaunchedEffect(showSafeHelp)', screen)


if __name__ == '__main__':
    unittest.main()
