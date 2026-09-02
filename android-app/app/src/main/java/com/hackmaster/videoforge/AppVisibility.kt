package com.hackmaster.videoforge

import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicInteger

object AppVisibility {
    interface Listener {
        fun onAppForegroundChanged(isForeground: Boolean)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val startedActivities = AtomicInteger(0)

    @Volatile
    var isForeground: Boolean = false
        private set

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun activityStarted() {
        val count = startedActivities.incrementAndGet()
        if (count == 1) setForeground(true)
    }

    fun activityStopped() {
        val count = startedActivities.updateAndGet { current ->
            if (current <= 0) 0 else current - 1
        }
        if (count == 0) setForeground(false)
    }

    private fun setForeground(value: Boolean) {
        if (isForeground == value) return
        isForeground = value
        listeners.forEach { listener ->
            runCatching {
                listener.onAppForegroundChanged(value)
            }
        }
    }
}
