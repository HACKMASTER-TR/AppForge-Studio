package com.appforge.studio.terminal

import android.content.Context
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal enum class LinuxBackgroundJobStatus {
    ACTIVE,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMED_OUT,
    PROCESS_LOST
}

internal data class LinuxBackgroundJobMetadata(
    val sessionId: String,
    val title: String,
    val distribution: LinuxDistribution,
    val workspaceFingerprint: String,
    val status: LinuxBackgroundJobStatus,
    val startedAt: Long,
    val finishedAt: Long?
)

internal object LinuxBackgroundJobStore {
    private val lock = Any()
    private val records = LinkedHashMap<String, LinuxBackgroundJobMetadata>()

    @Volatile
    private var initialized = false

    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (initialized) return

        synchronized(lock) {
            if (initialized) return

            appContext = context.applicationContext
            restoreLocked()

            val now = System.currentTimeMillis()
            records.replaceAll { _, value ->
                if (
                    value.status == LinuxBackgroundJobStatus.ACTIVE ||
                    value.status == LinuxBackgroundJobStatus.CANCELLING
                ) {
                    value.copy(
                        status = LinuxBackgroundJobStatus.PROCESS_LOST,
                        finishedAt = now
                    )
                } else {
                    value
                }
            }

            initialized = true
            pruneLocked()
            persistLocked()
        }
    }

    fun markActive(
        context: Context,
        state: LinuxManagedPtySessionState
    ) {
        initialize(context)

        synchronized(lock) {
            records[state.id] =
                LinuxBackgroundJobMetadata(
                    sessionId = state.id,
                    title = sanitizeTitle(state.title),
                    distribution = state.distribution,
                    workspaceFingerprint = fingerprint(state.workspacePath),
                    status = LinuxBackgroundJobStatus.ACTIVE,
                    startedAt = System.currentTimeMillis(),
                    finishedAt = null
                )
            pruneLocked()
            persistLocked()
        }
    }

    fun markCancelling(
        context: Context,
        sessionId: String
    ) {
        initialize(context)

        synchronized(lock) {
            val current = records[sessionId] ?: return
            records[sessionId] =
                current.copy(
                    status = LinuxBackgroundJobStatus.CANCELLING
                )
            persistLocked()
        }
    }

    fun markFinished(
        context: Context,
        sessionId: String,
        status: LinuxBackgroundJobStatus
    ) {
        require(
            status != LinuxBackgroundJobStatus.ACTIVE &&
                status != LinuxBackgroundJobStatus.CANCELLING
        ) {
            "Arka plan işi son duruma geçirilmedi."
        }

        initialize(context)

        synchronized(lock) {
            val current = records[sessionId] ?: return
            records[sessionId] =
                current.copy(
                    status = status,
                    finishedAt = System.currentTimeMillis()
                )
            pruneLocked()
            persistLocked()
        }
    }

    fun isActiveSession(
        context: Context,
        sessionId: String
    ): Boolean {
        initialize(context)

        return synchronized(lock) {
            when (records[sessionId]?.status) {
                LinuxBackgroundJobStatus.ACTIVE,
                LinuxBackgroundJobStatus.CANCELLING -> true
                else -> false
            }
        }
    }

    fun recent(
        context: Context
    ): List<LinuxBackgroundJobMetadata> {
        initialize(context)

        return synchronized(lock) {
            records.values
                .sortedByDescending { it.startedAt }
                .toList()
        }
    }

    private fun restoreLocked() {
        val raw =
            appContext
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(KEY_JOBS, null)
                ?: return

        val array =
            runCatching { JSONArray(raw) }
                .getOrNull()
                ?: return

        for (index in 0 until minOf(array.length(), MAX_RECORDS)) {
            val item = array.optJSONObject(index) ?: continue
            val sessionId =
                item.optString("sessionId")
                    .takeIf { UUID_PATTERN.matches(it) }
                    ?: continue
            val distribution =
                runCatching {
                    LinuxDistribution.valueOf(
                        item.optString("distribution")
                    )
                }.getOrNull()
                    ?: continue
            val workspaceFingerprint =
                item.optString("workspaceFingerprint")
                    .takeIf { FINGERPRINT_PATTERN.matches(it) }
                    ?: continue
            val status =
                runCatching {
                    LinuxBackgroundJobStatus.valueOf(
                        item.optString("status")
                    )
                }.getOrNull()
                    ?: continue
            val startedAt = item.optLong("startedAt", 0L)
            if (startedAt <= 0L) continue
            val finishedAt =
                item.optLong("finishedAt", 0L)
                    .takeIf { it > 0L }

            records[sessionId] =
                LinuxBackgroundJobMetadata(
                    sessionId = sessionId,
                    title = sanitizeTitle(item.optString("title", "Linux")),
                    distribution = distribution,
                    workspaceFingerprint = workspaceFingerprint,
                    status = status,
                    startedAt = startedAt,
                    finishedAt = finishedAt
                )
        }
    }

    private fun persistLocked() {
        if (!::appContext.isInitialized) return

        val array = JSONArray()
        records.values
            .sortedByDescending { it.startedAt }
            .take(MAX_RECORDS)
            .forEach { job ->
                array.put(
                    JSONObject()
                        .put("sessionId", job.sessionId)
                        .put("title", job.title)
                        .put("distribution", job.distribution.name)
                        .put("workspaceFingerprint", job.workspaceFingerprint)
                        .put("status", job.status.name)
                        .put("startedAt", job.startedAt)
                        .put("finishedAt", job.finishedAt ?: 0L)
                )
            }

        appContext
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(KEY_JOBS, array.toString())
            .apply()
    }

    private fun pruneLocked() {
        val keep =
            records.values
                .sortedByDescending { it.startedAt }
                .take(MAX_RECORDS)
                .map { it.sessionId }
                .toSet()

        records.keys
            .filterNot { it in keep }
            .toList()
            .forEach { records.remove(it) }
    }

    private fun fingerprint(workspacePath: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(workspacePath.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun sanitizeTitle(value: String): String =
        TerminalTextSanitizer.clean(value)
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .trim()
            .take(40)
            .ifBlank { "Linux" }

    private const val MAX_RECORDS = 12
    private const val PREFS_NAME = "appforge_linux_background_jobs"
    private const val KEY_JOBS = "jobs_v1"

    private val UUID_PATTERN = Regex("^[0-9a-fA-F-]{36}$")
    private val FINGERPRINT_PATTERN = Regex("^[0-9a-f]{64}$")
}
