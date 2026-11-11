package com.appforge.studio.io

import android.graphics.pdf.PdfDocument
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Actual Android PdfRenderer acceptance, separate from host parser checks. */
class PdfProjectSourceInstrumentedTest {
    @Test fun validMultipageAndTamperedSource() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pdf-test-${System.nanoTime()}").apply { mkdirs() }
        try {
            val file = File(root, PdfProjectSource.DOCUMENT)
            val document = PdfDocument()
            try {
                repeat(3) { index ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(200, 300, index + 1).create())
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    document.finishPage(page)
                }
                file.outputStream().use(document::writeTo)
            } finally { document.close() }
            assertEquals(3, PdfProjectSource.validate(file))
            PdfProjectSource.prepare(root)
            assertTrue(PdfProjectSource.isProject(root))
            assertEquals("pdf", ProjectTechnologyDetector.detect(root).id)
            file.appendText("tampered")
            assertFalse(PdfProjectSource.isProject(root))
            assertEquals("unknown", ProjectTechnologyDetector.detect(root).id)
        } finally { root.deleteRecursively() }
    }
    @Test fun invalidAndOversizedSourcesFailClosed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pdf-invalid-${System.nanoTime()}").apply { mkdirs() }
        try {
            for (text in listOf("not a PDF", "%PDF-1.4\ninvalid object\n%%EOF")) {
                val file = File(root, PdfProjectSource.DOCUMENT).apply { writeText(text) }
                assertTrue(runCatching { PdfProjectSource.validate(file) }.isFailure)
            }
            val file = File(root, PdfProjectSource.DOCUMENT)
            java.io.RandomAccessFile(file, "rw").use { it.setLength(PdfProjectSource.MAX_BYTES + 1) }
            assertTrue(runCatching { PdfProjectSource.validate(file) }.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun passwordProtectedDocumentIsRejected() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File.createTempFile("encrypted-pdf-", ".pdf", instrumentation.targetContext.cacheDir)
        try {
            instrumentation.context.assets.open("pdf/encrypted.pdf").use { input -> file.outputStream().use { input.copyTo(it) } }
            val failure = runCatching { PdfProjectSource.validate(file) }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message?.contains("Parola") == true)
        } finally { file.delete() }
    }

}
