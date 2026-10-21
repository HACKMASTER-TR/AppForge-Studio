package com.appforge.studio.build
import org.junit.Assert.*
import org.junit.Test
class RustPreparationIntegrityTest {
    private fun entry(hash:String)=RuntimeGenerationStore.Entry("file",1,hash,false,"OWNER_READ,OWNER_WRITE")
    @Test fun disposableCargoCacheCanChangeWithoutChangingTools() {
        val before=mapOf("usr/bin/python3" to entry("python"),"opt/appforge-device/rust-1.85.1/bin/rustc" to entry("rustc"))
        RustPreparationIntegrity.requireUnchangedRuntime(before,before+("root/.cargo/.package-cache" to entry("cache")))
    }
    @Test fun interpreterOrCompilerMutationRejectsOutput() {
        for(path in listOf("usr/bin/python3","usr/lib/python3.12/tomllib/__init__.py","opt/appforge-device/rust-1.85.1/bin/rustc")) {
            assertThrows(IllegalArgumentException::class.java){RustPreparationIntegrity.requireUnchangedRuntime(mapOf(path to entry("trusted")),mapOf(path to entry("modified")))}
        }
    }
    @Test fun trustConfigurationRemovalOrForeignFileAdditionRejectsOutput() {
        assertThrows(IllegalArgumentException::class.java){RustPreparationIntegrity.requireUnchangedRuntime(mapOf("etc/ssl/certs/ca-certificates.crt" to entry("trusted")),emptyMap())}
        assertThrows(IllegalArgumentException::class.java){RustPreparationIntegrity.requireUnchangedRuntime(emptyMap(),mapOf("root/.cargo-other/key" to entry("foreign")))}
    }
}
