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
