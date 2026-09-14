package com.appforge.studio.ai

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

internal class AppForgeAgentLocalPatchProvider(
    private val assistant: AppForgeLocalAssistant,
    private val workspace: File
) : AppForgeAgentPatchProviderV8 {
    override fun createPatch(
        request: AppForgeAgentPatchRequestV8
    ): AppForgeAgentPatchPlan {
        require(assistant.isReady) {
            "Structured repair için yerel AI modeli hazır değil."
        }

        val snapshot = buildSnapshot(request)
        require(snapshot.isNotBlank()) {
            "Structured repair için güvenli kaynak snapshot'ı oluşturulamadı."
        }

        val prompt = buildString {
            appendLine("APPFORGE UNIFIED AGENT STRUCTURED PATCH V11")
            appendLine()
            appendLine("HATA FINGERPRINT:")
            appendLine(request.failureFingerprint)
            appendLine()
            appendLine("AŞAMA: ${request.phase}")
            appendLine("DENEME: ${request.attempt}")
            appendLine()
            appendLine("NORMALIZE HATA:")
            appendLine(request.normalizedFailureOutput.take(8_000))
            appendLine()
            appendLine(
                "İZİNLİ ROOT'LAR: " +
                    request.allowedRootPrefixes.sorted().joinToString(", ")
            )
            appendLine()
            appendLine("KAYNAK SNAPSHOT:")
            appendLine(snapshot)
            appendLine()
            appendLine("Yalnız aşağıdaki JSON şemasını döndür:")
            appendLine(
                """{"failureFingerprint":"${request.failureFingerprint}","operations":[{"path":"android/...","baseSha256":"64-hex-veya-null","replacementContent":"dosyanın TAM yeni içeriği"}]}"""
            )
            appendLine()
            appendLine("Kurallar:")
            appendLine("- failureFingerprint birebir aynı kalmalı.")
            appendLine("- Yalnız izinli root altındaki dosyaları değiştir.")
            appendLine("- Mevcut dosyada snapshot'taki SHA-256 değerini baseSha256 olarak kullan.")
            appendLine("- Yeni dosyada baseSha256 null olmalı.")
            appendLine("- replacementContent parçalı diff değil, dosyanın tam içeriği olmalı.")
            appendLine("- .env, token, parola, keystore, private key veya credential üretme.")
            appendLine("- En fazla 8 operasyon üret; gereksiz dosya değiştirme.")
            appendLine("- Shell komutu üretme.")
        }.take(MAX_PROMPT_CHARS)

        val raw = runBlocking {
            assistant.generateStructuredJson(prompt)
        }

        val plan = parsePlan(raw)

        require(plan.failureFingerprint == request.failureFingerprint) {
            "Structured repair fingerprint uyuşmuyor."
        }

        AppForgeAgentStructuredPatch.validate(plan)
        return plan
    }

    private fun buildSnapshot(
        request: AppForgeAgentPatchRequestV8
    ): String {
        val root = workspace.canonicalFile
        require(root.isDirectory) {
            "Repair workspace bulunamadı."
        }

        val failure = request.normalizedFailureOutput.lowercase()
        val candidates = mutableListOf<Pair<Int, File>>()

        request.allowedRootPrefixes
            .sorted()
            .forEach { allowedRoot ->
                val allowed = File(root, allowedRoot).canonicalFile
                if (
                    !allowed.exists() ||
                    !inside(root, allowed)
                ) {
                    return@forEach
                }

                allowed.walkTopDown()
                    .maxDepth(12)
                    .onEnter { directory ->
                        !Files.isSymbolicLink(directory.toPath()) &&
                            inside(root, directory.canonicalFile)
                    }
                    .filter {
                        it.isFile &&
                            inside(root, it.canonicalFile)
                    }
                    .take(1_000)
                    .forEach fileLoop@{ file ->
                        if (
                            Files.isSymbolicLink(file.toPath()) ||
                            file.length() >
                            MAX_SNAPSHOT_FILE_BYTES ||
                            file.extension.lowercase() !in TEXT_EXTENSIONS
                        ) {
                            return@fileLoop
                        }

                        val relative =
                            file.relativeTo(root).invariantSeparatorsPath
                        val score =
                            when {
                                failure.contains(relative.lowercase()) -> 100
                                failure.contains(file.name.lowercase()) -> 80
                                file.extension.lowercase() in
                                    setOf(
                                        "kt",
                                        "kts",
                                        "dart",
                                        "tsx",
                                        "ts",
                                        "js",
                                        "json",
                                        "xml"
                                    ) -> 30
                                else -> 10
                            }

                        candidates += score to file
                    }
            }

        var chars = 0
        val selected = candidates
            .distinctBy { it.second.canonicalPath }
            .sortedWith(
                compareByDescending<Pair<Int, File>> { it.first }
                    .thenBy { it.second.path }
            )
            .take(MAX_SNAPSHOT_FILES)

        return buildString {
            selected.forEach { (_, file) ->
                val content = runCatching {
                    file.readText(Charsets.UTF_8)
                }.getOrNull()
                    ?: return@forEach

                if (
                    chars + content.length >
                    MAX_SNAPSHOT_TOTAL_CHARS
                ) {
                    return@forEach
                }

                chars += content.length
                val relative =
                    file.relativeTo(root).invariantSeparatorsPath

                appendLine("=== FILE $relative ===")
                appendLine("SHA256 ${sha256(file.readBytes())}")
                appendLine(content)
                appendLine("=== END FILE ===")
            }
        }.take(MAX_SNAPSHOT_TOTAL_CHARS + 4_000)
    }

    private fun parsePlan(
        raw: String
    ): AppForgeAgentPatchPlan {
        val json = JSONObject(raw)
        val fingerprint =
            json.getString("failureFingerprint")
        val operationsArray =
            json.getJSONArray("operations")

        require(operationsArray.length() in 1..8) {
            "Structured repair 1..8 operasyon içermeli."
        }

        val operations = buildList {
            for (index in 0 until operationsArray.length()) {
                val item =
                    operationsArray.getJSONObject(index)

                val base =
                    if (
                        item.has("baseSha256") &&
                        !item.isNull("baseSha256")
                    ) {
                        item.getString("baseSha256")
                    } else {
                        null
                    }

                add(
                    AppForgeAgentPatchOperation(
                        path = item.getString("path"),
                        baseSha256 = base,
                        replacementContent =
                            item.getString("replacementContent")
                    )
                )
            }
        }

        return AppForgeAgentPatchPlan(
            failureFingerprint = fingerprint,
            operations = operations
        )
    }

    private fun inside(
        root: File,
        candidate: File
    ): Boolean {
        val prefix =
            root.canonicalPath.trimEnd(File.separatorChar) +
                File.separator

        return candidate.canonicalFile == root.canonicalFile ||
            candidate.canonicalPath.startsWith(prefix)
    }

    private fun sha256(
        bytes: ByteArray
    ): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte ->
                "%02x".format(byte)
            }

    private companion object {
        const val MAX_PROMPT_CHARS = 30 * 1024
        const val MAX_SNAPSHOT_FILES = 8
        const val MAX_SNAPSHOT_FILE_BYTES = 12L * 1024L
        const val MAX_SNAPSHOT_TOTAL_CHARS = 18 * 1024

        val TEXT_EXTENSIONS = setOf(
            "kt",
            "kts",
            "java",
            "xml",
            "gradle",
            "dart",
            "tsx",
            "ts",
            "jsx",
            "js",
            "json",
            "html",
            "css",
            "yaml",
            "yml",
            "toml",
            "properties"
        )
    }
}
