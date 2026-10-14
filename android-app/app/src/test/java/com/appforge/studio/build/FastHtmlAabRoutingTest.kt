package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import org.junit.Assert.*
import org.junit.Test

class FastHtmlAabRoutingTest {
    private fun local() = ProjectDraft().apply {
        appName = "Static Test"
        sourceMode = SourceMode.LOCAL
        sourceBuildEngine = "webview-static"
        sourceTechnology = "html"
        importedFolder = "/test/static"
        startPage = "/test/static/index.html"
        notifications = false
    }

    @Test fun apkRemainsDefaultAndSupported() {
        val draft = local()
        assertEquals("apk", draft.buildOutput)
        assertTrue(FastHtmlApkPolicy.isCandidate(draft))
        FastHtmlApkPolicy.requireSupported(draft)
    }

    @Test fun aabAndBothRemainFastCandidatesButFailClosed() {
        listOf("aab", "both", "apk+aab").forEach { output ->
            val draft = local().apply { buildOutput = output }
            assertTrue(FastHtmlApkPolicy.isCandidate(draft))
            assertThrows(IllegalArgumentException::class.java) {
                FastHtmlApkPolicy.requireSupported(draft)
            }
        }
    }

    @Test fun explicitLocalStaticAabAndBothEligibleForIndependentPreflight() {
        val root = kotlin.io.path.createTempDirectory("appforge-v15-fast-static-").toFile()
        try {
            val start = java.io.File(root, "index.html").apply { writeText("<html>ok</html>") }
            listOf("aab", "both", "apk+aab").forEach { output ->
                val draft = local().copy(importedFolder = root.path, startPage = start.path,
                    buildOutput = output)
                FastHtmlAabUnsignedPackager.requireSource(draft)
                assertTrue(FastHtmlApkPolicy.isCandidate(draft))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun allOutputChoicesSelectAndroidFastOrNormalDeviceRoute() {
        val fastOutputs = listOf("apk", "aab", "both", "apk+aab", "android", "")
        for (output in fastOutputs) {
            val draft = local().apply { buildOutput = output }
            assertTrue("Android-only $output should remain fast", FastHtmlApkPolicy.isCandidate(draft))
        }

        val normalOutputs = listOf(
            "exe", "windows-exe", "portable-exe", "windows-portable-exe",
            "all", "apk+aab+exe", "native-exe", "windows-native-exe"
        )
        for (output in normalOutputs) {
            for (mode in listOf(SourceMode.LOCAL, SourceMode.URL)) {
                val draft = local().apply {
                    buildOutput = output
                    sourceMode = mode
                    if (mode == SourceMode.URL) webUrl = "https://example.com"
                }
                assertFalse("Windows/mixed $output must avoid Android fast", FastHtmlApkPolicy.isCandidate(draft))
            }
        }

        // Invalid output must still fail closed, not silently fall into an arbitrary engine.
        assertThrows(IllegalStateException::class.java) {
            FastHtmlApkPolicy.isCandidate(local().apply { buildOutput = "invalid-output" })
        }
    }

    @Test fun foreignEnginesNeverEnterFastRoute() {
        listOf("node-web", "expo", "react-native", "android-gradle", "android-ndk").forEach { engine ->
            assertFalse(FastHtmlApkPolicy.isCandidate(local().apply { sourceBuildEngine = engine }))
        }
        listOf("react", "vite", "node-web", "expo", "kotlin").forEach { tech ->
            assertFalse(FastHtmlApkPolicy.isCandidate(local().apply { sourceTechnology = tech }))
        }
    }

    @Test fun httpsUrlApkPreservedAndUrlAabRejected() {
        val draft = local().apply { sourceMode = SourceMode.URL; webUrl = "https://example.com" }
        FastHtmlApkPolicy.requireSupported(draft)
        draft.buildOutput = "aab"
        assertThrows(IllegalArgumentException::class.java) { FastHtmlApkPolicy.requireSupported(draft) }
        draft.buildOutput = "apk"
        draft.webUrl = "http://example.com"
        assertThrows(IllegalArgumentException::class.java) { FastHtmlApkPolicy.requireSupported(draft) }
    }
}
