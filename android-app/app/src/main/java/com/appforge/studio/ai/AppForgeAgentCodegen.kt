package com.appforge.studio.ai

import java.security.MessageDigest

internal data class AppForgeGeneratedFile(
    val path: String,
    val content: String
)

internal data class AppForgeGeneratedProject(
    val platform: AppForgeAgentPlatform,
    val entryPoint: String,
    val files: List<AppForgeGeneratedFile>,
    val digestSha256: String
)

internal object AppForgeAgentCodegen {
    private const val MAX_FILES = 128
    private const val MAX_FILE_CHARS = 256 * 1024
    private const val MAX_TOTAL_CHARS = 2 * 1024 * 1024

    fun generate(blueprint: AppForgeAgentBlueprint): AppForgeGeneratedProject =
        generateFor(blueprint, blueprint.platform)

    fun generateFor(
        blueprint: AppForgeAgentBlueprint,
        platform: AppForgeAgentPlatform
    ): AppForgeGeneratedProject {
        validateBlueprint(blueprint)
        val target = blueprint.copy(platform = platform)

        val raw = when (platform) {
            AppForgeAgentPlatform.ANDROID -> AppForgeAgentAndroidRenderer.render(target)
            AppForgeAgentPlatform.FLUTTER -> AppForgeAgentFlutterRenderer.render(target)
            AppForgeAgentPlatform.REACT_NATIVE -> AppForgeAgentReactNativeRenderer.render(target)
            AppForgeAgentPlatform.WEB -> AppForgeAgentWebRenderer.render(target)
        }

        return finalizeProject(platform, raw)
    }

    fun generateAll(
        blueprint: AppForgeAgentBlueprint
    ): Map<AppForgeAgentPlatform, AppForgeGeneratedProject> {
        validateBlueprint(blueprint)
        return AppForgeAgentPlatform.entries.associateWith { platform ->
            generateFor(blueprint, platform)
        }
    }

    private fun validateBlueprint(blueprint: AppForgeAgentBlueprint) {
        val validation = AppForgeAgentBlueprintValidator.validate(blueprint)
        require(validation.valid) {
            validation.issues
                .filter { it.level == AgentBlueprintIssueLevel.ERROR }
                .joinToString(
                    prefix = "Codegen için geçersiz blueprint: ",
                    separator = " | "
                ) { "${it.field}: ${it.message}" }
        }
    }

    private fun finalizeProject(
        platform: AppForgeAgentPlatform,
        rendered: AppForgeRendererOutput
    ): AppForgeGeneratedProject {
        require(rendered.files.isNotEmpty()) { "Codegen boş proje üretemez." }
        require(rendered.files.size <= MAX_FILES) {
            "Codegen tek projede en fazla $MAX_FILES dosya üretebilir."
        }

        val normalized = rendered.files.map { file ->
            val path = normalizePath(file.path)
            val content = file.content
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .let { if (it.endsWith('\n')) it else "$it\n" }

            require(content.length <= MAX_FILE_CHARS) {
                "Üretilen dosya çok büyük: $path"
            }
            AppForgeGeneratedFile(path = path, content = content)
        }

        val duplicate = normalized
            .groupBy { it.path.lowercase() }
            .entries
            .firstOrNull { it.value.size > 1 }
        require(duplicate == null) {
            "Üretilen dosya yolları benzersiz olmalı: ${duplicate?.key}"
        }

        val totalChars = normalized.sumOf { it.content.length }
        require(totalChars <= MAX_TOTAL_CHARS) {
            "Üretilen proje $MAX_TOTAL_CHARS karakter sınırını aşıyor."
        }

        val entry = normalizePath(rendered.entryPoint)
        require(normalized.any { it.path == entry }) {
            "Codegen entry point dosyası bulunamadı: $entry"
        }

        val digestInput = buildString {
            append(platform.name)
            append('\n')
            append(entry)
            append('\n')
            normalized.sortedBy { it.path }.forEach { file ->
                append(file.path)
                append('\n')
                append(file.content.length)
                append('\n')
                append(file.content)
                append('\n')
            }
        }

        return AppForgeGeneratedProject(
            platform = platform,
            entryPoint = entry,
            files = normalized,
            digestSha256 = sha256(digestInput)
        )
    }

    private fun normalizePath(raw: String): String {
        val path = raw.trim().replace('\\', '/')
        require(path.isNotBlank()) { "Üretilen dosya yolu boş olamaz." }
        require(!path.startsWith('/')) { "Mutlak dosya yolu yasak: $path" }
        require(!Regex("^[A-Za-z]:/").containsMatchIn(path)) {
            "Windows mutlak dosya yolu yasak: $path"
        }

        val parts = path.split('/')
        require(parts.none { it.isBlank() || it == "." || it == ".." }) {
            "Güvensiz dosya yolu: $path"
        }
        require(parts.all { part ->
            part.length <= 120 &&
                part.all { ch -> ch.isLetterOrDigit() || ch in "._-" }
        }) {
            "Dosya yolu güvenli karakterler içermeli: $path"
        }
        return parts.joinToString("/")
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}

internal data class AppForgeRendererOutput(
    val entryPoint: String,
    val files: List<AppForgeGeneratedFile>
)
