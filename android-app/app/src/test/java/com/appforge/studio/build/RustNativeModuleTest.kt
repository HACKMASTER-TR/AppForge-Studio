package com.appforge.studio.build
import com.appforge.studio.model.ProjectDraft
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
class RustNativeModuleTest {
 @Test fun nativeRustToolsAreIndependentOfNodeAndExpo() {
  val plan=OfflineModules.resolve(setOf(OfflineModuleId.RUST_ANDROID_NDK))
  assertTrue(plan.containsAll(listOf(OfflineModuleId.RUST,OfflineModuleId.ANDROID_CORE,OfflineModuleId.LINUX_RUNTIME)))
  assertEquals(1,plan.count{it==OfflineModuleId.LINUX_RUNTIME});assertFalse(plan.contains(OfflineModuleId.NODE_WEB));assertFalse(plan.contains(OfflineModuleId.EXPO_REACT_NATIVE))
 }
 @Test fun unpreparedNativeSourceRequestsActualLinkerModule() {
  val root=Files.createTempDirectory("rust-native-plan").toFile()
  try {File(root,"Cargo.toml").writeText("[package]\nname=\"example\"\nversion=\"0.1.0\"");File(root,"src").mkdir()
   val plan=OfflineModules.forProject(ProjectDraft(importedFolder=root.path,sourceBuildEngine="android-gradle",buildOutput="both"))
   assertNotNull(plan.error);assertTrue(plan.modules.contains(OfflineModuleId.RUST_ANDROID_NDK));assertFalse(plan.modules.contains(OfflineModuleId.NODE_WEB))
  }finally{root.deleteRecursively()}
 }
 @Test fun standalonePublicPhysicalGatesArePreserved() {
  assertTrue(DeviceBuildCapabilities.forEngine("react-native")!!.readyOutputs.isEmpty())
  assertTrue(DeviceBuildCapabilities.forEngine("android-ndk")!!.readyOutputs.isEmpty())
 }
 @Test fun unchangedExpoUseStillProtectsSharedNdkFiles() {
  val base=Files.createTempDirectory("rust-ndk-shared").toFile()
  try {
   val root=File(base,"root").apply{mkdir()}
   val ndk="opt/appforge-device/android-sdk/ndk/27.1.12297006/compiler"
   val expo="opt/appforge-device/expo-sdk-36/platforms/android-36/android.jar"
   File(root,ndk).parentFile!!.mkdirs();File(root,expo).parentFile!!.mkdirs();File(root,"user-project.txt").writeText("preserve")
   BaseRuntimeOwnership.captureFresh(root)
   fun install(id:OfflineModuleId,path:String) {
    RuntimeGenerationStore(root).prepare(id).use{tx->
     File(tx.root,path).writeText(id.name)
     ModuleOwnershipStore(tx.root).record(id,tx.previousRootName,RuntimeGenerationStore(root).snapshot(),RuntimeGenerationStore(tx.root).snapshot())
     tx.commit(verify={})
    }
   }
   install(OfflineModuleId.RUST_ANDROID_NDK,ndk);install(OfflineModuleId.EXPO_REACT_NATIVE,expo)
   assertNotNull(ModuleOwnershipStore(root).safeRemovalReason(OfflineModuleId.RUST_ANDROID_NDK))
   assertEquals("RUST_ANDROID_NDK",File(root,ndk).readText());assertEquals("preserve",File(root,"user-project.txt").readText())
  }finally{base.deleteRecursively()}
 }
}
