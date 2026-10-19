package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Real host filesystem transactions; fixture payloads are not SDK/device acceptance. */
@RunWith(Parameterized::class)
internal class ModuleLifecycleAcceptanceTest(private val id:OfflineModuleId) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}") fun modules()=listOf(OfflineModuleId.ANDROID_CORE,OfflineModuleId.NODE_WEB,OfflineModuleId.PYTHON_ANDROID,OfflineModuleId.EXPO_REACT_NATIVE,OfflineModuleId.WINDOWS_NATIVE).map{arrayOf<Any>(it)}
    }
    private val relative:String get()=when(id) {
        OfflineModuleId.ANDROID_CORE->"opt/appforge-device/jdk-17/bin/tool"
        OfflineModuleId.NODE_WEB->"opt/appforge-device/node-22.23.3/bin/node"
        OfflineModuleId.PYTHON_ANDROID->"usr/lib/python3.12/tool"
        OfflineModuleId.EXPO_REACT_NATIVE->"opt/appforge-device/android-sdk/ndk/27.1.12297006/tool"
        else->"usr/x86_64-w64-mingw32/tool"
    }
    private inline fun fixture(body:(File,File)->Unit) {
        val base=Files.createTempDirectory("module-lifecycle-").toFile()
        try {
            val root=File(base,"rootfs").apply{mkdir()}
            File(root,"vendor").writeText("vendor")
            BaseRuntimeOwnership.captureFresh(root)
            File(root,"root/signing.key").apply{parentFile.mkdirs();writeText("fixture user data, not a real key")}
            File(base,"projects/source.html").apply{parentFile.mkdirs();writeText("user source")}
            body(base,root)
            assertEquals("user source",File(base,"projects/source.html").readText())
        } finally {base.deleteRecursively()}
    }
    private fun install(root:File,value:String,fault:(String)->Unit={}) {
        RuntimeGenerationStore(root).prepare(id).use{tx->
            File(tx.root,relative).apply{parentFile.mkdirs();writeText(value)}
            ModuleOwnershipStore(tx.root).record(id,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot(),root)
            tx.commit(verify={check(ModuleOwnershipStore(it).verifyNamespace(id))},fault=fault)
        }
    }
    @Test fun installUpdateRollbackAndExactRemovalPreserveUserData()=fixture{_,root->
        install(root,"v1");val first=ModuleOwnershipStore(root).lifecycleInventory(id)
        assertEquals(OfflineModules.spec(id).version,first.version);assertEquals(OfflineModules.spec(id).dependencies,first.dependencies)
        install(root,"v2");val next=ModuleOwnershipStore(root).lifecycleInventory(id)
        assertNotEquals(first.generation,next.generation);assertEquals(first.version,next.previousVersion)
        RuntimeGenerationStore(root).rollback(id);assertEquals("v1",File(root,relative).readText())
        assertNull(ModuleOwnershipStore(root).safeRemovalReason(id))
        RuntimeGenerationStore(root).prepareOwner("REMOVE:${id.name}").use{tx->ModuleOwnershipStore(root).restoreInto(id,tx.root);tx.commit(verify={})}
        assertFalse(File(root,relative).exists());assertEquals("fixture user data, not a real key",File(root,"root/signing.key").readText())
    }
    @Test fun activationFailuresPreservePreviouslyWorkingFiles()=fixture{_,root->
        install(root,"v1")
        for(point in listOf("before-switch","old-moved","new-moved")) {
            assertThrows(IllegalStateException::class.java){install(root,"v2"){if(it==point)error("injected activation failure")}}
            assertEquals("v1",File(root,relative).readText());assertTrue(ModuleOwnershipStore(root).verifyNamespace(id))
        }
    }
    @Test fun diskAndCancellationNeverStartDestructiveTransition()=fixture{_,root->
        install(root,"v1");val store=RuntimeGenerationStore(root)
        assertThrows(IllegalArgumentException::class.java){store.prepare(id,availableBytes={0})}
        assertThrows(java.util.concurrent.CancellationException::class.java){store.prepare(id,canceled={throw java.util.concurrent.CancellationException()})}
        assertThrows(IllegalArgumentException::class.java){store.rollback(id,availableBytes={0})}
        assertEquals("v1",File(root,relative).readText())
    }
    @Test fun foreignFilesAndUserEditsDisableRemovalAndRollback()=fixture{_,root->
        install(root,"v1")
        val foreign=File(File(root,relative).parentFile,"user-source").apply{writeText("preserve")}
        assertNotNull(ModuleOwnershipStore(root).safeRemovalReason(id));assertFalse(RuntimeGenerationStore(root).rollbackAvailable(id))
        assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).rollback(id)}
        assertEquals("preserve",foreign.readText());assertEquals("v1",File(root,relative).readText())
    }
    @Test fun tamperedPreviousGenerationAndManifestCannotEnableLifecycle()=fixture{base,root->
        install(root,"v1")
        val old=base.listFiles()!!.single{it.name.startsWith(".v3-previous-")}
        File(old,"vendor").writeText("corrupt")
        assertFalse(RuntimeGenerationStore(root).rollbackAvailable(id))
        val receipt=File(root,"opt/appforge-device/ownership-v5/${id.name}.json")
        val data=JSONObject(receipt.readText());data.getJSONObject("changes").put("../outside",JSONObject())
        receipt.writeText(data.toString());assertNotNull(ModuleOwnershipStore(root).safeRemovalReason(id))
        assertEquals("v1",File(root,relative).readText())
    }
    @Test fun legacyManifestRemainsReadableButCannotAuthorizeRemoval()=fixture{_,root->
        install(root,"v1")
        val receipt=File(root,"opt/appforge-device/ownership-v5/${id.name}.json")
        val data=JSONObject(receipt.readText());data.remove("lifecycleRevision");receipt.writeText(data.toString())
        assertTrue(ModuleOwnershipStore(root).verifyNamespace(id));assertNotNull(ModuleOwnershipStore(root).safeRemovalReason(id))
        assertFalse(RuntimeGenerationStore(root).rollbackAvailable(id));assertEquals("v1",File(root,relative).readText())
    }
    @Test fun userSettingsCannotBeClaimedByAnInstaller()=fixture{_,root->
        install(root,"v1")
        assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).prepare(id).use{tx->
            File(tx.root,"root/signing.key").writeText("overwritten")
            ModuleOwnershipStore(tx.root).record(id,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot(),root)
        }}
        assertEquals("fixture user data, not a real key",File(root,"root/signing.key").readText())
    }
    @Test fun activeBuildAndDependentsBlockDestructiveActions()=fixture{_,root->
        install(root,"v1")
        val facts=ModuleLifecyclePolicy.Facts(id,true,true,ownershipError=null,removalError=null,rollbackError=null)
        assertTrue(ModuleLifecyclePolicy.evaluate(facts).remove.allowed)
        val dependent=ModuleLifecyclePolicy.evaluate(facts.copy(dependents=setOf(OfflineModuleId.FAST_HTML)))
        assertFalse(dependent.remove.allowed);assertFalse(dependent.rollback.allowed)
        BuildModuleUsage.buildLease().use{
            val active=ModuleLifecyclePolicy.evaluate(facts.copy(busy=BuildModuleUsage.mutationBlockedReason()))
            assertFalse(active.update.allowed);assertFalse(active.remove.allowed);assertFalse(active.rollback.allowed)
            assertThrows(IllegalStateException::class.java){BuildModuleUsage.mutationLease()}
        }
        assertEquals("v1",File(root,relative).readText())
    }
}
