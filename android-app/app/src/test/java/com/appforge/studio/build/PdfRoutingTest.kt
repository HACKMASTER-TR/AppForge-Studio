package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import org.junit.Assert.*
import org.junit.Test

class PdfRoutingTest {
    private fun pdf() = ProjectDraft(appName = "PDF Reader", sourceTechnology = "pdf",
        sourceBuildEngine = "webview-static", sourceMode = SourceMode.LOCAL,
        importedFolder = "/private/pdf", startPage = "/private/pdf/index.html")

    @Test fun pdfRequiresNormalAndroidEvenWithHtmlFallback() {
        for (output in listOf("apk", "aab", "both", "apk+aab")) {
            val draft = pdf().copy(buildOutput = output)
            assertFalse(FastHtmlApkPolicy.isCandidate(draft))
            WebviewProjectSettings.validate(draft)
            assertEquals(WizardCapabilityClass.NORMAL_SUPPORTED, WizardReadinessEvaluator.evaluate(draft).capability)
        }
        assertEquals("webview-static", DeviceBuildCapabilities.forTechnology("pdf")?.engine)
    }
    @Test fun pdfRejectsUrlForeignEngineAndWindowsOutputs() {
        for (draft in listOf(pdf().copy(sourceMode = SourceMode.URL, webUrl = "https://example.com/document.pdf"),
            pdf().copy(sourceBuildEngine = "node-web"), pdf().copy(buildOutput = "exe"), pdf().copy(buildOutput = "all"))) {
            assertFalse(WizardReadinessEvaluator.evaluate(draft).ready)
            assertThrows(IllegalArgumentException::class.java) { WebviewProjectSettings.validate(draft) }
        }
    }
}
