package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class FastHtmlV2AssetsTest {
    @Test fun staticMediaAndDocumentsKeepExactOfflineBytes() {
        val root=Files.createTempDirectory("fast-html-v2-assets-").toFile()
        try {
            File(root,"index.html").writeText("<video src='clip.mp4'></video>")
            val assets=listOf("mp3","mp4","webm","ogg","wav","m4a","pdf","csv")
                .associate { "fixture.$it" to byteArrayOf(0,1,2,127,-1) }
            assets.forEach { (name,bytes)->File(root,name).writeBytes(bytes) }
            File(root,"unsafe.exe").writeText("not a static asset")
            val draft=ProjectDraft(appName="Offline",importedFolder=root.path,startPage=File(root,"index.html").path)
            val (entry,packed)=FastHtmlApkPackager.readHtmlProject(draft)
            assertEquals("index.html",entry)
            assets.forEach { (name,bytes)->assertArrayEquals(bytes,packed.getValue("assets/site/$name")) }
            assertFalse(packed.containsKey("assets/site/unsafe.exe"))
            val config=org.json.JSONObject(FastHtmlApkPackager.runtimeConfig(draft,"index.html").toString(Charsets.UTF_8))
            assertTrue(config.getBoolean("fileUpload"))
            assertTrue(config.getBoolean("splashEnabled"))
            assertTrue(config.getBoolean("clipboardBridge"))
            assertTrue(config.getBoolean("vibrationBridge"))
        } finally { root.deleteRecursively() }
    }
    @Test fun sourceEscapeAndFileCountStillFailClosed() {
        val root=Files.createTempDirectory("fast-html-v2-boundary-").toFile()
        val outside=Files.createTempFile("fast-html-v2-outside-",".html").toFile()
        try {
            File(root,"index.html").writeText("<html>safe</html>")
            outside.writeText("<html>outside</html>")
            val draft=ProjectDraft(importedFolder=root.path,startPage=outside.path)
            assertThrows(IllegalArgumentException::class.java) { FastHtmlApkPackager.readHtmlProject(draft) }
            draft.startPage=File(root,"index.html").path
            val link=File(root,"linked.csv")
            Files.createSymbolicLink(link.toPath(),outside.toPath())
            assertThrows(IllegalArgumentException::class.java) { FastHtmlApkPackager.readHtmlProject(draft) }
            assertTrue(link.delete())
            repeat(250) { File(root,"asset-$it.csv").writeText("a,b\n") }
            assertThrows(IllegalArgumentException::class.java) { FastHtmlApkPackager.readHtmlProject(draft) }
        } finally { root.deleteRecursively();outside.delete() }
    }
}
