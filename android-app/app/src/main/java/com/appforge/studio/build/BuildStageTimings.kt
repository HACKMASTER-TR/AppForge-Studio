package com.appforge.studio.build

/** Monotonic timings with a fixed vocabulary; never log source URLs or signing exceptions. */
internal class BuildStageTimings(private val report: (String) -> Unit = {}) {
    enum class Stage { TEMPLATE, MANIFEST, RESOURCES, ASSETS, PACKAGING, SIGNING, VERIFICATION }
    fun <T> measure(stage: Stage, action: () -> T): T {
        val start = System.nanoTime()
        try {
            val result = action()
            emit(stage, start, "PASS")
            return result
        } catch (error: Throwable) {
            emit(stage, start, "FAIL")
            throw error
        }
    }
    private fun emit(stage: Stage, start: Long, result: String) {
        // Telemetry must not turn an already signed artifact into a failed build.
        runCatching { report("APPFORGE_BUILD_STAGE ${stage.name} $result ${(System.nanoTime() - start).coerceAtLeast(0) / 1_000_000}ms") }
    }
}
