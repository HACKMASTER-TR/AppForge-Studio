package com.appforge.studio.terminal

import android.content.Context
import java.io.BufferedOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

internal enum class LspConnectionState {
    STOPPED,
    STARTING,
    READY,
    ERROR
}

internal data class LspSessionSnapshot(
    val state: LspConnectionState,
    val profileTitle: String? = null,
    val detail: String = "LSP kapalı."
)

internal class LinuxLspSession(
    context: Context,
    private val onState: (LspSessionSnapshot) -> Unit,
    private val onDiagnostics: (
        uri: String,
        diagnostics: List<LspDiagnostic>
    ) -> Unit
) : AutoCloseable {
    private val appContext =
        context.applicationContext

    private val packagedEngine =
        PackagedLinuxEngine(
            appContext
        )

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val nextRequestId =
        AtomicInteger(1)

    private val pendingRequests =
        ConcurrentHashMap<
            Int,
            CompletableDeferred<JSONObject>
        >()

    private val documentVersions =
        ConcurrentHashMap<String, Int>()

    @Volatile
    private var process: Process? =
        null

    @Volatile
    private var writer:
        BufferedOutputStream? =
        null

    @Volatile
    private var currentProfile:
        LspLanguageProfile? =
        null

    private var readerJob: Job? = null
    private var errorJob: Job? = null
    private var waitJob: Job? = null

    val ready: Boolean
        get() =
            process?.isAlive == true &&
                currentProfile != null

    suspend fun start(
        rootfs: File,
        workspace: File,
        profile: LspLanguageProfile
    ) {
        require(
            UltimateLspCatalog.isTrusted(
                profile
            )
        ) {
            "LSP profili AppForge kataloğunda değil."
        }

        stop()

        onState(
            LspSessionSnapshot(
                state =
                    LspConnectionState.STARTING,
                profileTitle =
                    profile.title,
                detail =
                    "${profile.title} LSP başlatılıyor…"
            )
        )

        try {
            withContext(Dispatchers.IO) {
                val launcher =
                    packagedEngine
                        .requireLauncher()

                val command =
                    "exec ${profile.serverCommand}"

                val arguments =
                    ProrootPinnedRuntime
                        .buildShellArguments(
                            rootfs = rootfs,
                            workspace = workspace,
                            command = command
                        )

                val runtimeTemp =
                    File(
                        appContext.filesDir,
                        "terminal/linux/proroot-tmp"
                    ).apply {
                        mkdirs()
                    }.canonicalFile

                val started =
                    ProcessBuilder(
                        listOf(
                            launcher.absolutePath
                        ) + arguments
                    )
                        .directory(
                            appContext.filesDir
                        )
                        .redirectErrorStream(false)
                        .apply {
                            environment()
                                .remove("LD_PRELOAD")

                            environment()
                                .remove("LD_LIBRARY_PATH")

                            environment()[
                                "PROROOT_TMP_DIR"
                            ] =
                                runtimeTemp.absolutePath

                            environment()["HOME"] =
                                "/root"

                            environment()["TERM"] =
                                "dumb"

                            environment()["LANG"] =
                                "C.UTF-8"

                            environment()["PATH"] =
                                "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
                        }
                        .start()

                process = started
                writer =
                    BufferedOutputStream(
                        started.outputStream
                    )
                currentProfile = profile
                documentVersions.clear()

                startReaders(started)
            }

            val initialize =
                request(
                    method = "initialize",
                    params =
                        JSONObject()
                            .put(
                                "processId",
                                JSONObject.NULL
                            )
                            .put(
                                "rootUri",
                                "file:///workspace"
                            )
                            .put(
                                "capabilities",
                                JSONObject()
                                    .put(
                                        "textDocument",
                                        JSONObject()
                                            .put(
                                                "completion",
                                                JSONObject()
                                            )
                                            .put(
                                                "definition",
                                                JSONObject()
                                            )
                                    )
                            )
                )

            if (initialize.has("error")) {
                error(
                    initialize
                        .optJSONObject("error")
                        ?.optString("message")
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "LSP initialize reddedildi."
                )
            }

            notify(
                method = "initialized",
                params = JSONObject()
            )

            onState(
                LspSessionSnapshot(
                    state =
                        LspConnectionState.READY,
                    profileTitle =
                        profile.title,
                    detail =
                        "${profile.title} LSP hazır. JSON-RPC stdio bağlantısı aktif."
                )
            )
        } catch (error: Throwable) {
            stopProcessOnly()

            onState(
                LspSessionSnapshot(
                    state =
                        LspConnectionState.ERROR,
                    profileTitle =
                        profile.title,
                    detail =
                        error.message
                            ?: "LSP başlatılamadı."
                )
            )

            throw error
        }
    }

    suspend fun syncDocument(
        relativePath: String,
        text: String
    ) {
        val profile =
            currentProfile
                ?: return

        val matchedProfile =
            UltimateLspCatalog.forPath(
                relativePath
            )

        if (
            matchedProfile?.id !=
            profile.id
        ) {
            return
        }

        val uri =
            LspDocumentPath
                .toWorkspaceUri(
                    relativePath
                )

        val previousVersion =
            documentVersions[uri]

        if (previousVersion == null) {
            documentVersions[uri] = 1

            notify(
                method =
                    "textDocument/didOpen",
                params =
                    JSONObject()
                        .put(
                            "textDocument",
                            JSONObject()
                                .put("uri", uri)
                                .put(
                                    "languageId",
                                    languageIdFor(
                                        relativePath,
                                        profile
                                    )
                                )
                                .put("version", 1)
                                .put("text", text)
                        )
            )
        } else {
            val nextVersion =
                previousVersion + 1

            documentVersions[uri] =
                nextVersion

            notify(
                method =
                    "textDocument/didChange",
                params =
                    JSONObject()
                        .put(
                            "textDocument",
                            JSONObject()
                                .put("uri", uri)
                                .put(
                                    "version",
                                    nextVersion
                                )
                        )
                        .put(
                            "contentChanges",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put(
                                            "text",
                                            text
                                        )
                                )
                        )
            )
        }
    }

    suspend fun completion(
        relativePath: String,
        position: LspPosition
    ): List<LspCompletionItem> {
        require(ready) {
            "LSP hazır değil."
        }

        val response =
            request(
                method =
                    "textDocument/completion",
                params =
                    textDocumentPositionParams(
                        relativePath,
                        position
                    )
            )

        if (response.has("error")) {
            error(
                response
                    .optJSONObject("error")
                    ?.optString("message")
                    ?: "Completion isteği başarısız."
            )
        }

        val result =
            response.opt("result")

        val items =
            when (result) {
                is JSONArray -> result
                is JSONObject ->
                    result.optJSONArray("items")
                        ?: JSONArray()
                else -> JSONArray()
            }

        return buildList {
            var index = 0

            while (
                index < items.length() &&
                size < MAX_COMPLETIONS
            ) {
                val item =
                    items.optJSONObject(index)

                if (item != null) {
                    val label =
                        item
                            .optString("label")
                            .take(MAX_TEXT_CHARS)

                    if (label.isNotBlank()) {
                        val insertText =
                            item
                                .optString(
                                    "insertText",
                                    label
                                )
                                .take(MAX_TEXT_CHARS)

                        add(
                            LspCompletionItem(
                                label = label,
                                insertText =
                                    insertText,
                                detail =
                                    item
                                        .optString(
                                            "detail"
                                        )
                                        .takeIf {
                                            it.isNotBlank()
                                        }
                                        ?.take(
                                            MAX_TEXT_CHARS
                                        )
                            )
                        )
                    }
                }

                index += 1
            }
        }
    }

    suspend fun definition(
        relativePath: String,
        position: LspPosition
    ): LspLocation? {
        require(ready) {
            "LSP hazır değil."
        }

        val response =
            request(
                method =
                    "textDocument/definition",
                params =
                    textDocumentPositionParams(
                        relativePath,
                        position
                    )
            )

        if (response.has("error")) {
            error(
                response
                    .optJSONObject("error")
                    ?.optString("message")
                    ?: "Definition isteği başarısız."
            )
        }

        val result =
            response.opt("result")

        val location =
            when (result) {
                is JSONObject -> result
                is JSONArray ->
                    result.optJSONObject(0)
                else -> null
            } ?: return null

        return parseLocation(location)
    }

    suspend fun stop() {
        val active =
            process

        if (active != null) {
            try {
                withTimeout(1_500L) {
                    request(
                        method = "shutdown",
                        params = JSONObject.NULL
                    )
                }
            } catch (_: Throwable) {
                // Best-effort graceful shutdown; process destruction follows.
            }

            runCatching {
                notify(
                    method = "exit",
                    params = JSONObject.NULL
                )
            }
        }

        stopProcessOnly()

        onState(
            LspSessionSnapshot(
                state =
                    LspConnectionState.STOPPED,
                detail =
                    "LSP kapalı."
            )
        )
    }

    override fun close() {
        stopProcessOnly()
        scope.cancel()
    }

    private fun startReaders(
        started: Process
    ) {
        readerJob =
            scope.launch {
                val framer =
                    LspContentLengthFramer()

                val buffer =
                    ByteArray(4_096)

                try {
                    while (started.isAlive) {
                        val count =
                            started.inputStream
                                .read(buffer)

                        if (count < 0) {
                            break
                        }

                        framer.feed(
                            buffer,
                            count
                        ).forEach {
                            handleMessage(it)
                        }
                    }
                } catch (error: Throwable) {
                    if (started.isAlive) {
                        onState(
                            LspSessionSnapshot(
                                state =
                                    LspConnectionState.ERROR,
                                profileTitle =
                                    currentProfile
                                        ?.title,
                                detail =
                                    error.message
                                        ?: "LSP çıktı akışı kapandı."
                            )
                        )
                    }
                }
            }

        errorJob =
            scope.launch {
                val buffer =
                    ByteArray(2_048)

                var captured = 0

                while (
                    started.isAlive &&
                    captured < MAX_STDERR_BYTES
                ) {
                    val count =
                        started.errorStream
                            .read(buffer)

                    if (count < 0) {
                        break
                    }

                    captured += count
                }
            }

        waitJob =
            scope.launch {
                val exitCode =
                    runCatching {
                        started.waitFor()
                    }.getOrDefault(-1)

                if (process === started) {
                    pendingRequests
                        .values
                        .forEach {
                            it.completeExceptionally(
                                IllegalStateException(
                                    "LSP işlemi kapandı: $exitCode"
                                )
                            )
                        }
                    pendingRequests.clear()

                    process = null
                    writer = null
                    currentProfile = null
                    documentVersions.clear()

                    onState(
                        LspSessionSnapshot(
                            state =
                                LspConnectionState.STOPPED,
                            detail =
                                "LSP işlemi sonlandı ($exitCode)."
                        )
                    )
                }
            }
    }

    private fun handleMessage(
        raw: String
    ) {
        val message =
            runCatching {
                JSONObject(raw)
            }.getOrNull()
                ?: return

        if (message.has("id")) {
            val id =
                message.optInt(
                    "id",
                    Int.MIN_VALUE
                )

            pendingRequests
                .remove(id)
                ?.complete(message)

            return
        }

        if (
            message.optString("method") ==
            "textDocument/publishDiagnostics"
        ) {
            val params =
                message.optJSONObject("params")
                    ?: return

            val uri =
                params
                    .optString("uri")
                    .takeIf {
                        it.startsWith(
                            "file:///workspace/"
                        )
                    }
                    ?: return

            val array =
                params.optJSONArray(
                    "diagnostics"
                ) ?: JSONArray()

            val diagnostics =
                buildList {
                    var index = 0

                    while (
                        index < array.length() &&
                        size < MAX_DIAGNOSTICS
                    ) {
                        array.optJSONObject(index)
                            ?.let(::parseDiagnostic)
                            ?.let(::add)

                        index += 1
                    }
                }

            onDiagnostics(
                uri,
                diagnostics
            )
        }
    }

    private suspend fun request(
        method: String,
        params: Any?
    ): JSONObject {
        require(ready || method == "initialize") {
            "LSP işlemi hazır değil."
        }

        val id =
            nextRequestId
                .getAndIncrement()

        val deferred =
            CompletableDeferred<JSONObject>()

        pendingRequests[id] = deferred

        try {
            send(
                JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("method", method)
                    .put("params", params)
            )

            return withTimeout(
                REQUEST_TIMEOUT_MS
            ) {
                deferred.await()
            }
        } finally {
            pendingRequests.remove(id)
        }
    }

    private fun notify(
        method: String,
        params: Any?
    ) {
        send(
            JSONObject()
                .put("jsonrpc", "2.0")
                .put("method", method)
                .put("params", params)
        )
    }

    @Synchronized
    private fun send(
        message: JSONObject
    ) {
        val output =
            writer
                ?: error(
                    "LSP yazma kanalı hazır değil."
                )

        val body =
            message
                .toString()
                .toByteArray(
                    Charsets.UTF_8
                )

        require(
            body.size <= MAX_RPC_BYTES
        ) {
            "LSP JSON-RPC mesajı çok büyük."
        }

        val header =
            "Content-Length: ${body.size}\r\n\r\n"
                .toByteArray(
                    Charsets.US_ASCII
                )

        output.write(header)
        output.write(body)
        output.flush()
    }

    private fun textDocumentPositionParams(
        relativePath: String,
        position: LspPosition
    ): JSONObject =
        JSONObject()
            .put(
                "textDocument",
                JSONObject()
                    .put(
                        "uri",
                        LspDocumentPath
                            .toWorkspaceUri(
                                relativePath
                            )
                    )
            )
            .put(
                "position",
                JSONObject()
                    .put("line", position.line)
                    .put(
                        "character",
                        position.character
                    )
            )

    private fun parseDiagnostic(
        value: JSONObject
    ): LspDiagnostic? {
        val range =
            parseRange(
                value.optJSONObject("range")
                    ?: return null
            ) ?: return null

        val message =
            value
                .optString("message")
                .take(MAX_TEXT_CHARS)

        if (message.isBlank()) {
            return null
        }

        return LspDiagnostic(
            range = range,
            severity =
                if (value.has("severity")) {
                    value.optInt("severity")
                } else {
                    null
                },
            message = message,
            source =
                value
                    .optString("source")
                    .takeIf {
                        it.isNotBlank()
                    }
                    ?.take(MAX_TEXT_CHARS)
        )
    }

    private fun parseLocation(
        value: JSONObject
    ): LspLocation? {
        val uri =
            value
                .optString("uri")
                .takeIf {
                    it.isNotBlank()
                }
                ?: return null

        val range =
            parseRange(
                value.optJSONObject("range")
                    ?: return null
            ) ?: return null

        return LspLocation(
            uri = uri,
            range = range
        )
    }

    private fun parseRange(
        value: JSONObject
    ): LspRange? {
        val start =
            parsePosition(
                value.optJSONObject("start")
                    ?: return null
            ) ?: return null

        val end =
            parsePosition(
                value.optJSONObject("end")
                    ?: return null
            ) ?: return null

        return LspRange(
            start = start,
            end = end
        )
    }

    private fun parsePosition(
        value: JSONObject
    ): LspPosition? {
        val line = value.optInt("line", -1)
        val character =
            value.optInt(
                "character",
                -1
            )

        if (line < 0 || character < 0) {
            return null
        }

        return LspPosition(
            line = line,
            character = character
        )
    }

    private fun languageIdFor(
        path: String,
        profile: LspLanguageProfile
    ): String {
        val extension =
            path.substringAfterLast(
                '.',
                ""
            ).lowercase()

        return when (profile.id) {
            "typescript" ->
                if (extension in setOf("ts", "tsx")) {
                    "typescript"
                } else {
                    "javascript"
                }

            "cpp" -> "cpp"
            else -> profile.id
        }
    }

    @Synchronized
    private fun stopProcessOnly() {
        readerJob?.cancel()
        errorJob?.cancel()
        waitJob?.cancel()
        readerJob = null
        errorJob = null
        waitJob = null

        pendingRequests
            .values
            .forEach {
                it.cancel()
            }
        pendingRequests.clear()

        runCatching {
            writer?.close()
        }
        writer = null

        val active = process
        process = null

        if (active != null) {
            runCatching {
                active.outputStream.close()
            }
            runCatching {
                active.inputStream.close()
            }
            runCatching {
                active.errorStream.close()
            }

            if (active.isAlive) {
                active.destroy()
            }
        }

        currentProfile = null
        documentVersions.clear()
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS =
            8_000L

        private const val MAX_RPC_BYTES =
            2 * 1_024 * 1_024

        private const val MAX_STDERR_BYTES =
            64 * 1_024

        private const val MAX_DIAGNOSTICS = 200
        private const val MAX_COMPLETIONS = 100
        private const val MAX_TEXT_CHARS = 1_024
    }
}
