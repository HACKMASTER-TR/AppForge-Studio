package com.appforge.studio.ai

import java.security.MessageDigest

internal object AppForgeAgentFullStackCodegen {
    fun generate(
        blueprint: AppForgeAgentBlueprint,
        contract: AppForgeAgentFullStackContract
    ): AppForgeGeneratedProject {
        val blueprintValidation = AppForgeAgentBlueprintValidator.validate(blueprint)
        require(blueprintValidation.valid) { "Full-stack codegen için blueprint geçersiz." }

        val contractValidation = AppForgeAgentFullStackValidator.validate(contract)
        require(contractValidation.valid) {
            contractValidation.issues
                .filter { it.level == AppForgeAgentFullStackIssueLevel.ERROR }
                .joinToString(
                    prefix = "Full-stack contract geçersiz: ",
                    separator = " | "
                ) { "${it.field}: ${it.message}" }
        }

        require(contract.appName.trim() == blueprint.appName.trim()) {
            "Blueprint ve full-stack contract appName eşleşmeli."
        }
        require(contract.frontendPlatform == blueprint.platform) {
            "Blueprint platformu ile full-stack frontendPlatform eşleşmeli."
        }

        val frontend = AppForgeAgentCodegen.generate(blueprint)
        val contractFiles = buildList {
            add(
                AppForgeGeneratedFile(
                    path = "backend/contracts/appforge-fullstack.json",
                    content = renderContractJson(contract)
                )
            )
            add(
                AppForgeGeneratedFile(
                    path = "backend/contracts/openapi.json",
                    content = renderOpenApi(contract)
                )
            )
            add(
                AppForgeGeneratedFile(
                    path = "backend/contracts/environment.txt",
                    content = renderEnvironment(contract)
                )
            )
            if (contract.database == AppForgeAgentDatabaseKind.POSTGRES) {
                add(
                    AppForgeGeneratedFile(
                        path = "backend/database/schema.sql",
                        content = renderPostgresSchema(contract)
                    )
                )
            }
        }

        val files = (frontend.files + contractFiles)
            .map { file ->
                file.copy(
                    content = file.content
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .let { if (it.endsWith('\n')) it else "$it\n" }
                )
            }

        val duplicate = files.groupBy { it.path.lowercase() }.entries.firstOrNull { it.value.size > 1 }
        require(duplicate == null) {
            "Full-stack codegen yinelenen dosya yolu üretti: ${duplicate?.key}"
        }

        val digestInput = buildString {
            append(frontend.digestSha256)
            append('\n')
            files.sortedBy { it.path }.forEach { file ->
                append(file.path)
                append('\n')
                append(file.content)
                append('\n')
            }
        }

        return AppForgeGeneratedProject(
            platform = frontend.platform,
            entryPoint = frontend.entryPoint,
            files = files,
            digestSha256 = sha256(digestInput)
        )
    }

    private fun renderContractJson(contract: AppForgeAgentFullStackContract): String =
        buildString {
            append("{\n")
            append("  \"schemaVersion\": 1,\n")
            append("  \"appName\": ").appendJson(contract.appName.trim()).append(",\n")
            append("  \"frontendPlatform\": ").appendJson(contract.frontendPlatform.name).append(",\n")
            append("  \"backend\": ").appendJson(contract.backend.name).append(",\n")
            append("  \"auth\": ").appendJson(contract.auth.name).append(",\n")
            append("  \"database\": ").appendJson(contract.database.name).append(",\n")
            append("  \"environmentKeys\": [")
            AppForgeAgentFullStackValidator.requiredEnvironmentKeys(contract).forEachIndexed { index, key ->
                if (index > 0) append(", ")
                appendJson(key)
            }
            append("],\n")
            append("  \"entities\": [\n")
            contract.entities.forEachIndexed { entityIndex, entity ->
                append("    {\"id\": ").appendJson(entity.id.trim())
                append(", \"tableName\": ").appendJson(entity.tableName.trim())
                append(", \"fields\": [")
                entity.fields.forEachIndexed { fieldIndex, field ->
                    if (fieldIndex > 0) append(", ")
                    append("{\"id\": ").appendJson(field.id.trim())
                    append(", \"type\": ").appendJson(field.type.name)
                    append(", \"required\": ${field.required}")
                    append(", \"primaryKey\": ${field.primaryKey}")
                    append(", \"unique\": ${field.unique}}")
                }
                append("]}")
                if (entityIndex < contract.entities.lastIndex) append(',')
                append('\n')
            }
            append("  ],\n")
            append("  \"endpoints\": [\n")
            contract.endpoints.forEachIndexed { index, endpoint ->
                append("    {\"id\": ").appendJson(endpoint.id.trim())
                append(", \"method\": ").appendJson(endpoint.method.name)
                append(", \"path\": ").appendJson(endpoint.path.trim())
                append(", \"authRequired\": ${endpoint.authRequired}")
                append(", \"requestEntity\": ")
                endpoint.requestEntity?.trim()?.takeIf { it.isNotBlank() }?.let { appendJson(it) } ?: append("null")
                append(", \"responseEntity\": ")
                endpoint.responseEntity?.trim()?.takeIf { it.isNotBlank() }?.let { appendJson(it) } ?: append("null")
                append('}')
                if (index < contract.endpoints.lastIndex) append(',')
                append('\n')
            }
            append("  ]\n")
            append("}")
        }

    private fun renderOpenApi(contract: AppForgeAgentFullStackContract): String =
        buildString {
            append("{\n")
            append("  \"openapi\": \"3.1.0\",\n")
            append("  \"info\": {\"title\": ").appendJson("${contract.appName.trim()} API")
            append(", \"version\": \"1.0.0\"},\n")
            append("  \"paths\": {\n")
            contract.endpoints.groupBy { it.path.trim() }.entries.forEachIndexed { pathIndex, entry ->
                append("    ").appendJson(entry.key).append(": {\n")
                entry.value.sortedBy { it.method.name }.forEachIndexed { methodIndex, endpoint ->
                    append("      ").appendJson(endpoint.method.name.lowercase()).append(": {")
                    append("\"operationId\": ").appendJson(endpoint.id.trim())
                    append(", \"x-appforge-auth-required\": ${endpoint.authRequired}")
                    endpoint.requestEntity?.trim()?.takeIf { it.isNotBlank() }?.let {
                        append(", \"x-appforge-request-entity\": ").appendJson(it)
                    }
                    endpoint.responseEntity?.trim()?.takeIf { it.isNotBlank() }?.let {
                        append(", \"x-appforge-response-entity\": ").appendJson(it)
                    }
                    append(", \"responses\": {\"200\": {\"description\": \"OK\"}}}")
                    if (methodIndex < entry.value.lastIndex) append(',')
                    append('\n')
                }
                append("    }")
                if (pathIndex < contract.endpoints.groupBy { it.path.trim() }.size - 1) append(',')
                append('\n')
            }
            append("  }\n")
            append("}")
        }

    private fun renderEnvironment(contract: AppForgeAgentFullStackContract): String =
        buildString {
            appendLine("# AppForge environment contract V1")
            appendLine("# Names only. Secret values must be supplied outside generated source.")
            AppForgeAgentFullStackValidator.requiredEnvironmentKeys(contract)
                .forEach { appendLine(it) }
        }.trimEnd()

    private fun renderPostgresSchema(contract: AppForgeAgentFullStackContract): String =
        buildString {
            appendLine("-- AppForge deterministic PostgreSQL schema V1")
            appendLine("-- Credentials are intentionally not stored in generated source.")
            contract.entities.forEachIndexed { entityIndex, entity ->
                appendLine("CREATE TABLE IF NOT EXISTS ${entity.tableName.trim()} (")
                entity.fields.forEachIndexed { fieldIndex, field ->
                    append("  ${field.id.trim()} ${postgresType(field.type)}")
                    if (field.required) append(" NOT NULL")
                    if (field.primaryKey) append(" PRIMARY KEY")
                    if (field.unique) append(" UNIQUE")
                    if (fieldIndex < entity.fields.lastIndex) append(',')
                    appendLine()
                }
                appendLine(");")
                if (entityIndex < contract.entities.lastIndex) appendLine()
            }
        }.trimEnd()

    private fun postgresType(type: AppForgeAgentFieldType): String = when (type) {
        AppForgeAgentFieldType.STRING -> "TEXT"
        AppForgeAgentFieldType.INTEGER -> "INTEGER"
        AppForgeAgentFieldType.LONG -> "BIGINT"
        AppForgeAgentFieldType.BOOLEAN -> "BOOLEAN"
        AppForgeAgentFieldType.DECIMAL -> "DOUBLE PRECISION"
        AppForgeAgentFieldType.UUID -> "UUID"
        AppForgeAgentFieldType.DATETIME -> "TIMESTAMPTZ"
    }

    private fun StringBuilder.appendJson(value: String): StringBuilder {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch.code < 0x20) {
                    append("\\u%04x".format(ch.code))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
        return this
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
