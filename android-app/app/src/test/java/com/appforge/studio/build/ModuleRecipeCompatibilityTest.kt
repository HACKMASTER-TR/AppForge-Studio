package com.appforge.studio.build
import org.junit.Assert.*
import org.junit.Test
class ModuleRecipeCompatibilityTest {
 @Test fun currentRecipeStillRequiresValidDigest() {
  assertTrue(ModuleRecipeCompatibility.accepts(OfflineModuleId.RUST,"a".repeat(64),"a".repeat(64)))
  assertFalse(ModuleRecipeCompatibility.accepts(OfflineModuleId.RUST,"invalid","invalid"))
 }
 @Test fun knownPreviousRecipeIsScopedToUnchangedModule() {
  val old="9d41ff77014a5519992df42e15a8d3d5b80633300831997ecec141d4c9373d3c"
  assertTrue(ModuleRecipeCompatibility.accepts(OfflineModuleId.ANDROID_CORE,old,"b".repeat(64)))
  assertFalse(ModuleRecipeCompatibility.accepts(OfflineModuleId.RUST,old,"b".repeat(64)))
  assertFalse(ModuleRecipeCompatibility.accepts(OfflineModuleId.RUST_ANDROID_NDK,old,"b".repeat(64)))
  assertFalse(ModuleRecipeCompatibility.accepts(OfflineModuleId.ANDROID_CORE,"c".repeat(64),"b".repeat(64)))
 }
}
