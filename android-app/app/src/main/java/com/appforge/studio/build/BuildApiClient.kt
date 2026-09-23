package com.appforge.studio.build

import android.content.Context
import android.net.Uri
import com.appforge.studio.io.ProjectLibrary
import com.appforge.studio.ai.AppForgeAgentSessionStore
import com.appforge.studio.model.ProjectDraft
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile

data class BuildCreateResult(
    val buildId: String,
    val buildNo: Long?,
    val status: String
)

class BuildApiException(
    val statusCode: Int,
    val errorCode: String?,
    val recoveryAction: String?,
    val quota: JSONObject?,
    message: String
) : IllegalStateException(message)

data class BuildCancelResult(val status: String, val immediate: Boolean)

data class BuildStatusResult(
    val buildId: String,
    val buildNo: Long?,
    val status: String,
    val progress: Int,
    val logs: List<String>,
    val preflight: List<String>,
    val apkAvailable: Boolean,
    val aabAvailable: Boolean,
    val exeAvailable: Boolean,
    val queuePosition: Int? = null,
    val queueAhead: Int? = null,
    val queueCompatibleWorkerSlots: Int = 0,
    val queueEstimatedWaitSeconds: Int? = null,
    val queueEstimate: String? = null
)

data class DownloadTicketResult(val url: String, val direct: Boolean, val expiresInSeconds: Int)
data class RemoteBuildHistoryItem(
    val buildId: String,
    val buildNo: Long?,
    val appName: String,
    val packageName: String,
    val status: String,
    val createdAt: Long
)

data class ProjectQuotaResult(
    val plan: String,
    val planKind: String,
    val used: Int,
    val reserved: Int,
    val limit: Int?,
    val remaining: Int?,
    val availableToStart: Int?,
    val unlimited: Boolean,
    val customLimit: Int?,
    val successOnly: Boolean,
    val failedBuildsConsumeQuota: Boolean,
    val periodEndsAt: String?
)

data class ArtifactTopFile(val path: String, val category: String, val sizeBytes: Long)
data class ArtifactSizeReport(
    val kind: String,
    val fileSizeBytes: Long,
    val uncompressedBytes: Long,
    val entryCount: Int,
    val topFiles: List<ArtifactTopFile>,
    val groups: Map<String, Long>
)
data class SecurityInsight(val severity: String, val title: String, val detail: String)
data class TestLabResult(
    val buildId: String,
    val appName: String,
    val packageName: String,
    val apk: ArtifactSizeReport?,
    val aab: ArtifactSizeReport?,
    val security: List<SecurityInsight>
)
data class BuildCompareResult(
    val leftBuildId: String,
    val rightBuildId: String,
    val apkDeltaBytes: Long,
    val aabDeltaBytes: Long,
    val changeCount: Int,
    val changes: List<String>,
    val releaseNotes: List<String>
)

class BuildApiClient(
    private val context: Context,
    @Suppress("UNUSED_PARAMETER") private val baseUrl: String,
    @Suppress("UNUSED_PARAMETER") private val apiKey: String
) {
    fun createBuild(
        draft: ProjectDraft,
        projectZip: File?,
        @Suppress("UNUSED_PARAMETER") idempotencyKey: String? = null,
        @Suppress("UNUSED_PARAMETER") cacheIdentityNonce: String? = null
    ): BuildCreateResult {
        val started = DeviceBuildEngine.start(context, draft, projectZip)
        return BuildCreateResult(started.id, started.buildNo, "Hazırlanıyor")
    }

    fun getBuild(buildId: String): BuildStatusResult {
        val state = DeviceBuildEngine.snapshot(buildId)
            ?: throw BuildApiException(404, "LOCAL_BUILD_NOT_FOUND", null, null, "Yerel derleme bulunamadı.")
        return BuildStatusResult(
            buildId = state.id,
            buildNo = state.buildNo,
            status = state.status,
            progress = state.progress,
            logs = state.logs,
            preflight = state.preflight,
            apkAvailable = state.apk?.isFile == true,
            aabAvailable = state.aab?.isFile == true,
            exeAvailable = state.exe?.isFile == true
        )
    }

    fun cancelBuild(buildId: String): BuildCancelResult {
        val ok = DeviceBuildEngine.cancel(buildId)
        return BuildCancelResult(if (ok) "cancelled" else "unknown", ok)
    }

    fun createDownloadTicket(buildId: String, kind: String): DownloadTicketResult {
        val artifact = DeviceBuildEngine.artifact(buildId, kind)
            ?: persistedDeviceArtifact(buildId, kind)
            ?: persistedUnifiedAgentArtifact(buildId, kind)
            ?: error("${kind.uppercase()} çıktısı hazır değil.")

        return DownloadTicketResult(
            Uri.fromFile(artifact).toString(),
            true,
            Int.MAX_VALUE
        )
    }

    /**
     * A process restart clears DeviceBuildEngine.jobs but not the successful
     * history record or its canonical artifact. Resolve only that exact build
     * directory; never pick a file by project name or from another account.
     */
    private fun persistedDeviceArtifact(buildId: String, kind: String): File? =
        runCatching {
            if (!Regex("^local-[0-9a-f]{20}$").matches(buildId)) {
                return@runCatching null
            }

            val extension = when (kind.trim().lowercase()) {
                "apk" -> "apk"
                "aab" -> "aab"
                "exe", "windows-exe" -> "exe"
                else -> return@runCatching null
            }

            val saved = ProjectLibrary.loadBuilds(context)
                .firstOrNull { it.id == buildId }
                ?: return@runCatching null

            if (!saved.status.equals("success", ignoreCase = true)) {
                return@runCatching null
            }

            val advertised = when (extension) {
                "apk" -> saved.apkUrl
                "aab" -> saved.aabUrl
                else -> saved.exeUrl
            }
            if (advertised.isNullOrBlank()) {
                return@runCatching null
            }

            val root = File(context.filesDir, "device-build/artifacts").canonicalFile
            val directory = File(root, buildId).canonicalFile
            if (directory.parentFile != root || directory.name != buildId ||
                !directory.isDirectory
            ) {
                return@runCatching null
            }

            val matches = directory.listFiles().orEmpty().filter { candidate ->
                val file = candidate.canonicalFile
                file.parentFile == directory && file.isFile && file.length() > 0L &&
                    file.extension.equals(extension, ignoreCase = true) &&
                    (saved.buildNo == null ||
                        file.name.endsWith("-${saved.buildNo}.$extension", ignoreCase = true))
            }
            matches.singleOrNull()
        }.getOrNull()

    /** Legacy Unified Agent builds have session history, not ProjectLibrary rows. */
    private fun persistedUnifiedAgentArtifact(buildId: String, kind: String): File? =
        runCatching {
            if (!Regex("^local-[0-9a-f]{20}$").matches(buildId)) {
                return@runCatching null
            }
            val extension = when (kind.trim().lowercase()) {
                "apk" -> "apk"
                "aab" -> "aab"
                "exe", "windows-exe" -> "exe"
                else -> return@runCatching null
            }
            val store = AppForgeAgentSessionStore(
                File(context.filesDir, "unified-agent-session")
            )
            val candidates = (store.listRecent(12) + store.listArchived(12))
                .mapNotNull { it.state.remoteBuild }
                .filter { remote ->
                    remote.buildId == buildId &&
                        remote.status.equals("success", ignoreCase = true) &&
                        when (extension) {
                            "apk" -> remote.apkAvailable
                            "aab" -> remote.aabAvailable
                            else -> remote.exeAvailable
                        }
                }
            val buildNo = candidates.mapNotNull { it.buildNo }
                .distinct().singleOrNull() ?: return@runCatching null
            if (candidates.any { it.buildNo != buildNo }) {
                return@runCatching null
            }
            val root = File(context.filesDir, "device-build/artifacts").canonicalFile
            val directory = File(root, buildId).canonicalFile
            if (directory.parentFile != root || directory.name != buildId ||
                !directory.isDirectory
            ) {
                return@runCatching null
            }
            directory.listFiles().orEmpty().filter { candidate ->
                val file = candidate.canonicalFile
                file.parentFile == directory && file.isFile && file.length() > 0L &&
                    file.extension.equals(extension, ignoreCase = true) &&
                    file.name.endsWith("-$buildNo.$extension", ignoreCase = true)
            }.singleOrNull()
        }.getOrNull()

    fun projectQuota(): ProjectQuotaResult = ProjectQuotaResult(
        plan = "device",
        planKind = "device",
        used = ProjectLibrary.freeProjectSlotsUsed(context),
        reserved = 0,
        limit = null,
        remaining = null,
        availableToStart = null,
        unlimited = true,
        customLimit = null,
        successOnly = true,
        failedBuildsConsumeQuota = false,
        periodEndsAt = null
    )

    fun history(): List<RemoteBuildHistoryItem> = ProjectLibrary.loadBuilds(context).map {
        RemoteBuildHistoryItem(
            buildId = it.id,
            buildNo = it.buildNo,
            appName = it.projectName,
            packageName = it.packageName,
            status = it.status,
            createdAt = it.createdAt
        )
    }

    fun testLab(buildId: String): TestLabResult {
        DeviceBuildEngine.snapshot(buildId) ?: error("Yerel derleme bulunamadı.")
        return TestLabResult(
            buildId = buildId,
            appName = "",
            packageName = "",
            apk = artifactReport(DeviceBuildEngine.artifact(buildId, "apk"), "apk"),
            aab = artifactReport(DeviceBuildEngine.artifact(buildId, "aab"), "aab"),
            security = listOf(
                SecurityInsight(
                    severity = "info",
                    title = "Cihaz üzerinde derleme",
                    detail = "Kaynak proje ve build çıktısı AppForge build sunucusuna gönderilmedi."
                )
            )
        )
    }

    fun compareBuilds(leftBuildId: String, rightBuildId: String): BuildCompareResult {
        fun size(id: String, kind: String) = DeviceBuildEngine.artifact(id, kind)?.length() ?: 0L
        return BuildCompareResult(
            leftBuildId = leftBuildId,
            rightBuildId = rightBuildId,
            apkDeltaBytes = size(rightBuildId, "apk") - size(leftBuildId, "apk"),
            aabDeltaBytes = size(rightBuildId, "aab") - size(leftBuildId, "aab"),
            changeCount = 0,
            changes = emptyList(),
            releaseNotes = listOf("Yerel cihaz derlemeleri karşılaştırıldı.")
        )
    }

    fun releaseNotes(buildId: String): List<String> = listOf("Build $buildId cihaz üzerinde oluşturuldu.")

    fun getLogs(buildId: String, afterId: Long = 0): List<Pair<Long, String>> =
        DeviceBuildEngine.snapshot(buildId)?.logs.orEmpty().mapIndexedNotNull { index, line ->
            val id = index.toLong() + 1L
            if (id > afterId) id to line else null
        }

    private fun artifactReport(file: File?, kind: String): ArtifactSizeReport? {
        val source = file?.takeIf { it.isFile } ?: return null
        return runCatching {
            ZipFile(source).use { zip ->
                val entries = zip.entries().asSequence().filterNot { it.isDirectory }.toList()
                val top = entries.sortedByDescending { it.size }.take(12).map {
                    ArtifactTopFile(it.name, it.name.substringBefore('/'), it.size.coerceAtLeast(0L))
                }
                ArtifactSizeReport(
                    kind = kind,
                    fileSizeBytes = source.length(),
                    uncompressedBytes = entries.sumOf { it.size.coerceAtLeast(0L) },
                    entryCount = entries.size,
                    topFiles = top,
                    groups = emptyMap()
                )
            }
        }.getOrNull()
    }
}
