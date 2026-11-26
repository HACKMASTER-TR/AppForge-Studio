package com.appforge.studio.build
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class RuntimeArchiveRemovalTest {
 @Test fun atomicRemovalArchivesAllDataAndFailureRestoresActive() {
  val base=Files.createTempDirectory("runtime-remove").toFile()
  try {
   val root=File(base,"runtime-v3").apply{mkdir()};File(root,"foreign-user-file").writeText("preserve")
   val operation=RuntimeArchiveRemoval(root)
   assertThrows(IllegalStateException::class.java){operation.remove{error("rename boundary")}}
   assertEquals("preserve",File(root,"foreign-user-file").readText())
   operation.remove();assertFalse(root.exists());assertEquals("preserve",File(operation.archivedDirectory()!!,"foreign-user-file").readText())
  }finally{base.deleteRecursively()}
 }
}
