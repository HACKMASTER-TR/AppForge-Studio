package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SourceMode
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FastHtmlAabUnsignedPackagerTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun draft(): ProjectDraft {
        val root = temporary.newFolder()
        root.resolve("index.html").writeText("<html>Static</html>")
        return ProjectDraft(appName = "Static", importedFolder = root.path, buildOutput = "aab")
    }

    @Test fun onlyExplicitLocalStaticRequestsEnterPreflight() {
        val supported = draft()
        FastHtmlAabUnsignedPackager.requireSource(supported)
        listOf(supported.copy(sourceMode = SourceMode.URL), supported.copy(buildOutput = "apk"),
            supported.copy(sourceTechnology = "react"), supported.copy(sourceTechnology = "vite"),
            supported.copy(sourceTechnology = "node"), supported.copy(sourceBuildEngine = "node-web"),
            supported.copy(camera = true)).forEach {
            assertThrows(IllegalArgumentException::class.java) { FastHtmlAabUnsignedPackager.requireSource(it) }
        }
    }

    @Test fun rejectsPackageBuildsDependencyTreesAndEscapedEntry() {
        val packageDraft = draft()
        java.io.File(packageDraft.importedFolder!!, "package.json").writeText("{}")
        assertThrows(IllegalArgumentException::class.java) { FastHtmlAabUnsignedPackager.requireSource(packageDraft) }
        val dependencyDraft = draft()
        java.io.File(dependencyDraft.importedFolder!!, "node_modules").mkdir()
        assertThrows(IllegalArgumentException::class.java) { FastHtmlAabUnsignedPackager.requireSource(dependencyDraft) }
        val escaped = draft().copy(startPage = temporary.newFile("outside.html").apply { writeText("outside") }.path)
        assertThrows(IllegalArgumentException::class.java) { FastHtmlApkPackager.readHtmlProject(escaped) }
    }

    private fun zip(name: String, data: ByteArray): ByteArray = ByteArrayOutputStream().also { memory ->
        ZipOutputStream(memory).use { output ->
            output.putNextEntry(ZipEntry(name))
            output.write(data)
            output.closeEntry()
            // Keep malformed/incomplete fixtures above the minimum archive size.
            output.setComment("x".repeat(1500))
        }
    }.toByteArray()

    @Test fun rejectsUnsafeNamesSignaturesAndIncompleteTemplates() {
        listOf("../escape", "META-INF/MANIFEST.MF", "base/dex/classes.dex").forEach {
            assertThrows(IllegalArgumentException::class.java) {
                FastHtmlAabUnsignedPackager.readTemplate(zip(it, byteArrayOf(1)))
            }
        }
    }

    @Test fun boundsCompressedInputAndInflatedEntry() {
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabUnsignedPackager.readTemplate(ByteArray(40 * 1024 * 1024 + 1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabUnsignedPackager.readTemplate(zip("base/resources.pb", ByteArray(24 * 1024 * 1024 + 1)))
        }
    }
}
