package com.appforge.studio.build

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** The job itself is the monitor. Only in-memory transitions belong under this lock. */
internal open class DeviceBuildTerminalState {
    val logs = CopyOnWriteArrayList<String>()
    val cancelled = AtomicBoolean(false)
    @Volatile var status: String = "Hazırlanıyor"
        private set
    @Volatile var progress: Int = 1

    private fun terminal() = status in setOf("success", "failed", "cancelled")

    fun advance(value: Int, detail: String?) = synchronized(this) {
        if (!terminal()) {
            progress = maxOf(progress, value.coerceIn(0, 99))
            if (detail != null) status = detail
        }
    }

    fun complete(message: String): Boolean = synchronized(this) {
        if (terminal() || cancelled.get()) return false
        progress = 100
        status = "success"
        logs.add(message)
        true
    }

    fun cancelBuild(): Boolean = synchronized(this) {
        if (terminal()) return false
        cancelled.set(true)
        status = "cancelled"
        logs.add("⛔ Derleme iptal edildi.")
        true
    }

    fun fail(message: String): Boolean = synchronized(this) {
        if (terminal()) return false
        status = if (cancelled.get()) "cancelled" else "failed"
        if (status == "failed") logs.add(message)
        true
    }

    fun artifactsVisible(): Boolean = synchronized(this) {
        status == "success" && !cancelled.get()
    }
}
