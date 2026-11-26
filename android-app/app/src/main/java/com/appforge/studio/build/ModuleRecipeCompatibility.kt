package com.appforge.studio.build

/** Reviewed previous recipes only. This never replaces version, witness,
 * ownership or signed APT validation in recordMatches. Rust is deliberately
 * excluded: its new Git prerequisite requires a real installation refresh.
 * Provenance: c939423133ea196b794ad47c5e82a02966ee8b96 APK SHA-256
 * 91ccd6cdd6438559bc3239820ea416d414c55a77d6658ac1ad6ede88c9110f31.
 */
internal object ModuleRecipeCompatibility {
    private val previous = mapOf(
        OfflineModuleId.ANDROID_CORE to "9d41ff77014a5519992df42e15a8d3d5b80633300831997ecec141d4c9373d3c",
        OfflineModuleId.NODE_WEB to "fcb6a2e1d7ddc6b885d72412e4014b9495a2ecaf1a3a20c35ad3c98ebd04db22",
        OfflineModuleId.PYTHON_ANDROID to "9d41ff77014a5519992df42e15a8d3d5b80633300831997ecec141d4c9373d3c",
        OfflineModuleId.EXPO_REACT_NATIVE to "9d41ff77014a5519992df42e15a8d3d5b80633300831997ecec141d4c9373d3c",
        OfflineModuleId.WINDOWS_NATIVE to "3ee6d73744812db277abd74275176fa0204bcae3d2521d1aabce2dbe87f443b5"
    )
    fun accepts(id: OfflineModuleId, recorded: String, current: String): Boolean =
        recorded.matches(Regex("[a-f0-9]{64}")) &&
            (recorded == current || previous[id] == recorded)
}
