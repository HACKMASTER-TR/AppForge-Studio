package com.appforge.studio.terminal


/*
 * Render-side backpressure.
 *
 * PTY bytes are NEVER discarded here. The native reader continues
 * feeding AnsiTerminalBuffer synchronously. Only expensive Compose
 * snapshot publication is slowed during very large output bursts.
 */
internal object TerminalOutputBackpressure {

    fun accumulatePendingChars(
        current: Long,
        additional: Int
    ): Long {
        val safeCurrent =
            current
                .coerceAtLeast(0L)
                .coerceAtMost(
                    MAX_TRACKED_PENDING_CHARS
                )

        val safeAdditional =
            additional
                .coerceAtLeast(0)
                .toLong()

        return (
            safeCurrent +
                safeAdditional
            )
            .coerceAtMost(
                MAX_TRACKED_PENDING_CHARS
            )
    }


    fun targetPublishDelayMillis(
        active: Boolean,
        pendingChars: Long
    ): Long {
        val chars =
            pendingChars
                .coerceAtLeast(0L)

        return if (active) {
            when {
                chars >=
                    HEAVY_ACTIVE_THRESHOLD ->
                    ACTIVE_HEAVY_DELAY_MS

                chars >=
                    BURST_ACTIVE_THRESHOLD ->
                    ACTIVE_BURST_DELAY_MS

                chars >=
                    MODERATE_ACTIVE_THRESHOLD ->
                    ACTIVE_MODERATE_DELAY_MS

                else ->
                    ACTIVE_NORMAL_DELAY_MS
            }
        } else {
            when {
                chars >=
                    HEAVY_BACKGROUND_THRESHOLD ->
                    BACKGROUND_HEAVY_DELAY_MS

                chars >=
                    BURST_BACKGROUND_THRESHOLD ->
                    BACKGROUND_BURST_DELAY_MS

                else ->
                    BACKGROUND_NORMAL_DELAY_MS
            }
        }
    }


    const val ACTIVE_NORMAL_DELAY_MS =
        32L

    const val BACKGROUND_NORMAL_DELAY_MS =
        250L


    private const val ACTIVE_MODERATE_DELAY_MS =
        48L

    private const val ACTIVE_BURST_DELAY_MS =
        64L

    private const val ACTIVE_HEAVY_DELAY_MS =
        96L


    private const val BACKGROUND_BURST_DELAY_MS =
        325L

    private const val BACKGROUND_HEAVY_DELAY_MS =
        400L


    private const val MODERATE_ACTIVE_THRESHOLD =
        16L * 1024L

    private const val BURST_ACTIVE_THRESHOLD =
        64L * 1024L

    private const val HEAVY_ACTIVE_THRESHOLD =
        256L * 1024L


    private const val BURST_BACKGROUND_THRESHOLD =
        64L * 1024L

    private const val HEAVY_BACKGROUND_THRESHOLD =
        256L * 1024L


    private const val MAX_TRACKED_PENDING_CHARS =
        1024L * 1024L
}
