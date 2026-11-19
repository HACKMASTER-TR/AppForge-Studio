package com.appforge.studio.build

/** Bounded device-local install budgets; no extension for unrelated shell commands. */
internal object IsolatedInstallTimeoutPolicy {
    const val DEFAULT_TIMEOUT_MS = 1_800_000L
    const val ANDROID_CORE_TIMEOUT_MS = 4_500_000L

    fun forStep(module: OfflineModuleId?, command: String): Long {
        if (module != OfflineModuleId.ANDROID_CORE) return DEFAULT_TIMEOUT_MS
        return when {
            "/workspace/runtime/install-toolchain.sh webview-static" in command -> ANDROID_CORE_TIMEOUT_MS
            "/workspace/runtime/prepare-offline-pack.sh android" in command -> ANDROID_CORE_TIMEOUT_MS
            else -> DEFAULT_TIMEOUT_MS
        }
    }

    /** Never echo shell output, filesystem paths or remote URLs in timeout errors. */
    private fun stepName(command: String): String = when {
        "install-toolchain.sh webview-static" in command -> "ANDROID_TOOLCHAIN"
        "prepare-offline-pack.sh android" in command -> "ANDROID_OFFLINE_PACK"
        "module-functional-test.sh" in command -> "FUNCTIONAL_TEST"
        "isolated-physical-acceptance.sh" in command -> "PHYSICAL_ACCEPTANCE"
        else -> "OTHER"
    }

    fun timeoutMessage(
        module: OfflineModuleId?, command: String, phase: ModulePhase?, timeoutMs: Long
    ): String {
        val moduleName = module?.name ?: "UNKNOWN"
        val phaseName = phase?.name ?: "UNREPORTED"
        val stepName = stepName(command)
        return "APPFORGE_ISOLATED_INSTALL_TIMEOUT: module=$moduleName, " +
            "step=$stepName, lastAptPhase=$phaseName, limitMinutes=${timeoutMs / 60_000L}. " +
            "Korunan kurulum ve doğrulanmış önbellek silinmedi; işlem READY sayılmadı."
    }
}
