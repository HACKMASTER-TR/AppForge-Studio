package com.appforge.studio.build
import com.appforge.studio.io.ProjectTechnologyDetector
import java.nio.file.Files
import java.io.File
import org.junit.Test
import org.junit.Assert.*
class RustSourceDiscoveryTest {
    @Test fun explicitWindowsBindingWinsWithoutCmakeAndDoesNotClaimReady() {
        val root=Files.createTempDirectory("rust-discovery").toFile()
        try {
            File(root,"Cargo.toml").writeText("[package]\nname=\"demo\"\nversion=\"0.1.0\"")
            File(root,"appforge-rust-windows.json").writeText("""{"schema":1,"target":"x86_64-pc-windows-gnu","executable":"demo.exe"}""")
            val info=ProjectTechnologyDetector.detect(root)
            assertEquals("windows-native",info.buildEngine);assertFalse(info.buildReady)
            File(root,"appforge-rust-windows.json").writeText("""{"schema":1,"target":"x86_64-pc-windows-gnu","executable":"../escape.exe"}""")
            assertEquals("unknown",ProjectTechnologyDetector.detect(root).buildEngine)
        }finally{root.deleteRecursively()}
    }
}
