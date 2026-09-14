package com.appforge.studio.ai

internal enum class AppForgeAgentBackendKind {
    NODE_EXPRESS,
    FASTAPI
}

internal enum class AppForgeAgentAuthKind {
    NONE,
    EMAIL_PASSWORD,
    MAGIC_LINK,
    OAUTH
}

internal enum class AppForgeAgentDatabaseKind {
    NONE,
    POSTGRES
}

internal enum class AppForgeAgentFieldType {
    STRING,
    INTEGER,
    LONG,
    BOOLEAN,
    DECIMAL,
    UUID,
    DATETIME
}

internal enum class AppForgeAgentApiMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE
}

internal data class AppForgeAgentDataFieldSpec(
    val id: String,
    val type: AppForgeAgentFieldType,
    val required: Boolean = true,
    val primaryKey: Boolean = false,
    val unique: Boolean = false
)

internal data class AppForgeAgentDataEntitySpec(
    val id: String,
    val tableName: String,
    val fields: List<AppForgeAgentDataFieldSpec>
)

internal data class AppForgeAgentApiEndpointSpec(
    val id: String,
    val method: AppForgeAgentApiMethod,
    val path: String,
    val authRequired: Boolean = false,
    val requestEntity: String? = null,
    val responseEntity: String? = null
)

internal data class AppForgeAgentFullStackContract(
    val schemaVersion: Int = 1,
    val appName: String,
    val frontendPlatform: AppForgeAgentPlatform,
    val backend: AppForgeAgentBackendKind,
    val auth: AppForgeAgentAuthKind = AppForgeAgentAuthKind.NONE,
    val database: AppForgeAgentDatabaseKind = AppForgeAgentDatabaseKind.NONE,
    val entities: List<AppForgeAgentDataEntitySpec> = emptyList(),
    val endpoints: List<AppForgeAgentApiEndpointSpec> = emptyList(),
    val environmentKeys: List<String> = emptyList()
)

internal enum class AppForgeAgentFullStackIssueLevel {
    WARNING,
    ERROR
}

internal data class AppForgeAgentFullStackIssue(
    val level: AppForgeAgentFullStackIssueLevel,
    val field: String,
    val message: String
)

internal data class AppForgeAgentFullStackValidation(
    val issues: List<AppForgeAgentFullStackIssue>
) {
    val valid: Boolean
        get() = issues.none { it.level == AppForgeAgentFullStackIssueLevel.ERROR }
}

internal object AppForgeAgentFullStackValidator {
    private const val MAX_ENTITIES = 32
    private const val MAX_FIELDS_PER_ENTITY = 64
    private const val MAX_ENDPOINTS = 64
    private const val MAX_ENV_KEYS = 32
    private const val MAX_PACKET_CHARS = 24 * 1024

    private val idPattern = Regex("^[A-Za-z][A-Za-z0-9_-]{0,63}$")
    private val tablePattern = Regex("^[a-z][a-z0-9_]{0,62}$")
    private val apiPathPattern = Regex("^/api(?:/[A-Za-z0-9_.{}-]+)+$")
    private val envKeyPattern = Regex("^APPFORGE_[A-Z][A-Z0-9_]{0,62}$")

    fun validate(contract: AppForgeAgentFullStackContract): AppForgeAgentFullStackValidation {
        val issues = mutableListOf<AppForgeAgentFullStackIssue>()

        fun error(field: String, message: String) {
            issues += AppForgeAgentFullStackIssue(
                level = AppForgeAgentFullStackIssueLevel.ERROR,
                field = field,
                message = message
            )
        }

        fun warning(field: String, message: String) {
            issues += AppForgeAgentFullStackIssue(
                level = AppForgeAgentFullStackIssueLevel.WARNING,
                field = field,
                message = message
            )
        }

        if (contract.schemaVersion != 1) {
            error("schemaVersion", "Full-stack contract schemaVersion yalnız 1 olabilir.")
        }

        val appName = contract.appName.trim()
        if (appName.isBlank()) {
            error("appName", "Uygulama adı boş olamaz.")
        } else if (appName.length > 120 || appName.any { it == '\u0000' || it == '\r' || it == '\n' }) {
            error("appName", "Uygulama adı güvenli ve en fazla 120 karakter olmalı.")
        }

        if (contract.entities.size > MAX_ENTITIES) {
            error("entities", "En fazla $MAX_ENTITIES veri varlığı desteklenir.")
        }
        if (contract.endpoints.size > MAX_ENDPOINTS) {
            error("endpoints", "En fazla $MAX_ENDPOINTS API endpoint'i desteklenir.")
        }
        if (contract.environmentKeys.size > MAX_ENV_KEYS) {
            error("environmentKeys", "En fazla $MAX_ENV_KEYS ortam anahtarı referansı desteklenir.")
        }

        if (contract.database == AppForgeAgentDatabaseKind.NONE && contract.entities.isNotEmpty()) {
            error("entities", "Database NONE iken kalıcı veri varlığı tanımlanamaz.")
        }

        if (contract.database == AppForgeAgentDatabaseKind.POSTGRES && contract.entities.isEmpty()) {
            warning("entities", "Postgres seçildi ancak veri varlığı tanımlanmadı.")
        }

        val entityIds = mutableSetOf<String>()
        val tableNames = mutableSetOf<String>()

        contract.entities.forEachIndexed { entityIndex, entity ->
            val path = "entities[$entityIndex]"
            val id = entity.id.trim()
            val table = entity.tableName.trim()

            if (!idPattern.matches(id)) {
                error("$path.id", "Veri varlığı kimliği geçersiz: '$id'.")
            }
            if (!entityIds.add(id.lowercase())) {
                error("$path.id", "Veri varlığı kimliği benzersiz olmalı: '$id'.")
            }
            if (!tablePattern.matches(table)) {
                error("$path.tableName", "Tablo adı lower_snake_case biçiminde olmalı.")
            }
            if (!tableNames.add(table.lowercase())) {
                error("$path.tableName", "Tablo adı benzersiz olmalı: '$table'.")
            }
            if (entity.fields.isEmpty()) {
                error("$path.fields", "Her veri varlığında en az bir alan olmalı.")
            }
            if (entity.fields.size > MAX_FIELDS_PER_ENTITY) {
                error("$path.fields", "Bir veri varlığında en fazla $MAX_FIELDS_PER_ENTITY alan olabilir.")
            }

            val fieldIds = mutableSetOf<String>()
            var primaryKeyCount = 0
            entity.fields.forEachIndexed { fieldIndex, field ->
                val fieldPath = "$path.fields[$fieldIndex]"
                val fieldId = field.id.trim()
                if (!tablePattern.matches(fieldId)) {
                    error("$fieldPath.id", "Alan adı lower_snake_case biçiminde olmalı.")
                }
                if (!fieldIds.add(fieldId.lowercase())) {
                    error("$fieldPath.id", "Alan adı aynı veri varlığında benzersiz olmalı: '$fieldId'.")
                }
                if (field.primaryKey) {
                    primaryKeyCount += 1
                    if (!field.required) {
                        error("$fieldPath.required", "Primary key alanı required olmalı.")
                    }
                }
            }

            if (primaryKeyCount > 1) {
                error("$path.fields", "V1 contract bir veri varlığında en fazla bir primary key destekler.")
            }
        }

        val normalizedEntityIds = contract.entities.map { it.id.trim().lowercase() }.toSet()
        val endpointKeys = mutableSetOf<String>()
        val endpointIds = mutableSetOf<String>()

        contract.endpoints.forEachIndexed { index, endpoint ->
            val path = "endpoints[$index]"
            val id = endpoint.id.trim()
            val apiPath = endpoint.path.trim()

            if (!idPattern.matches(id)) {
                error("$path.id", "Endpoint kimliği geçersiz: '$id'.")
            }
            if (!endpointIds.add(id.lowercase())) {
                error("$path.id", "Endpoint kimliği benzersiz olmalı: '$id'.")
            }
            if (!apiPathPattern.matches(apiPath) || ".." in apiPath || "//" in apiPath) {
                error("$path.path", "Endpoint yolu güvenli /api/... biçiminde olmalı.")
            }

            val endpointKey = "${endpoint.method.name}:${apiPath.lowercase()}"
            if (!endpointKeys.add(endpointKey)) {
                error("$path.path", "Aynı method/path endpoint'i tekrar tanımlanamaz.")
            }

            if (endpoint.authRequired && contract.auth == AppForgeAgentAuthKind.NONE) {
                error("$path.authRequired", "Auth NONE iken endpoint authRequired olamaz.")
            }

            validateEntityReference(
                field = "$path.requestEntity",
                value = endpoint.requestEntity,
                entityIds = normalizedEntityIds,
                error = ::error
            )
            validateEntityReference(
                field = "$path.responseEntity",
                value = endpoint.responseEntity,
                entityIds = normalizedEntityIds,
                error = ::error
            )
        }

        val envKeys = mutableSetOf<String>()
        contract.environmentKeys.forEachIndexed { index, raw ->
            val key = raw.trim()
            if (!envKeyPattern.matches(key)) {
                error("environmentKeys[$index]", "Ortam referansı APPFORGE_* biçiminde olmalı.")
            }
            if (!envKeys.add(key)) {
                error("environmentKeys[$index]", "Ortam referansları benzersiz olmalı: '$key'.")
            }
            if ('=' in key || ':' in key || '/' in key || '\\' in key) {
                error("environmentKeys[$index]", "Ortam referansı yalnız anahtar adı olabilir; değer içeremez.")
            }
        }

        return AppForgeAgentFullStackValidation(issues)
    }

    fun contractPacket(contract: AppForgeAgentFullStackContract): String {
        val validation = validate(contract)
        require(validation.valid) {
            validation.issues
                .filter { it.level == AppForgeAgentFullStackIssueLevel.ERROR }
                .joinToString(
                    prefix = "Geçersiz full-stack contract: ",
                    separator = " | "
                ) { "${it.field}: ${it.message}" }
        }

        return buildString {
            appendLine("APPFORGE FULL-STACK CONTRACT V1")
            appendLine("App: ${contract.appName.trim().take(120)}")
            appendLine("Frontend: ${contract.frontendPlatform.name}")
            appendLine("Backend: ${contract.backend.name}")
            appendLine("Auth: ${contract.auth.name}")
            appendLine("Database: ${contract.database.name}")
            appendLine()
            appendLine("ENTITIES:")
            if (contract.entities.isEmpty()) appendLine("- none")
            contract.entities.forEach { entity ->
                appendLine("- ${entity.id} -> ${entity.tableName}")
                entity.fields.forEach { field ->
                    appendLine(
                        "  - ${field.id}:${field.type.name}; required=${field.required}; pk=${field.primaryKey}; unique=${field.unique}"
                    )
                }
            }
            appendLine()
            appendLine("API:")
            if (contract.endpoints.isEmpty()) appendLine("- none")
            contract.endpoints.forEach { endpoint ->
                appendLine(
                    "- ${endpoint.method.name} ${endpoint.path} id=${endpoint.id}; auth=${endpoint.authRequired}; request=${endpoint.requestEntity ?: "none"}; response=${endpoint.responseEntity ?: "none"}"
                )
            }
            appendLine()
            appendLine("ENV REFERENCES (names only, values forbidden):")
            requiredEnvironmentKeys(contract).forEach { appendLine("- $it") }
        }.take(MAX_PACKET_CHARS)
    }

    fun requiredEnvironmentKeys(contract: AppForgeAgentFullStackContract): List<String> {
        val keys = linkedSetOf<String>()
        keys += contract.environmentKeys.map(String::trim)
        if (contract.database == AppForgeAgentDatabaseKind.POSTGRES) {
            keys += "APPFORGE_DATABASE_URL"
        }
        if (contract.auth != AppForgeAgentAuthKind.NONE) {
            keys += "APPFORGE_AUTH_PROVIDER"
        }
        return keys.toList().sorted()
    }

    private fun validateEntityReference(
        field: String,
        value: String?,
        entityIds: Set<String>,
        error: (String, String) -> Unit
    ) {
        val normalized = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (normalized.lowercase() !in entityIds) {
            error(field, "Endpoint veri varlığı bulunamadı: '$normalized'.")
        }
    }
}
