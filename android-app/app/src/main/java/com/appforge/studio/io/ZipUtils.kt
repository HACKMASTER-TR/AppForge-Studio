package com.appforge.studio.io

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ZipUtils {
    fun zipDirectory(sourceDir: File, outFile: File): File {
        require(sourceDir.isDirectory) { "Kaynak klasör bulunamadı." }
        outFile.parentFile?.mkdirs()

        ZipOutputStream(outFile.outputStream().buffered()).use { zip ->
            sourceDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val rel = file.relativeTo(sourceDir).invariantSeparatorsPath
                    zip.putNextEntry(ZipEntry(rel))
                    file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        return outFile
    }
}
