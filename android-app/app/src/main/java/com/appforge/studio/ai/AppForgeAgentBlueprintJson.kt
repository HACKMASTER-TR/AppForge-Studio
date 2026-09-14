package com.appforge.studio.ai

internal class AppForgeAgentBlueprintJsonException(
    message: String
) : IllegalArgumentException(message)

internal object AppForgeAgentBlueprintJson {
    private const val SCHEMA_VERSION = 1
    private const val MAX_JSON_CHARS = 32 * 1024
    private const val MAX_DEPTH = 12
    private const val MAX_STRING_CHARS = 8 * 1024
    private const val MAX_ARRAY_ITEMS = 256
    private const val MAX_OBJECT_FIELDS = 96

    fun parse(raw: String): AppForgeAgentBlueprint {
        val extracted = extractStructuredPayload(raw)

        if (extracted.length > MAX_JSON_CHARS) {
            fail("Blueprint JSON $MAX_JSON_CHARS karakteri aşamaz.")
        }

        val json = normalizeRawStringControlCharacters(extracted)

        if (json.length > MAX_JSON_CHARS) {
            fail("Normalize edilmiş Blueprint JSON $MAX_JSON_CHARS karakteri aşamaz.")
        }

        val root = Parser(json).parseDocument().asObject("root")
        root.requireOnly(
            path = "root",
            allowed = setOf(
                "schemaVersion",
                "appName",
                "prompt",
                "platform",
                "tokens",
                "startRoute",
                "screens",
                "maxRepairAttempts"
            )
        )

        val schemaVersion = root.requiredInt("schemaVersion", "root.schemaVersion")
        if (schemaVersion != SCHEMA_VERSION) {
            fail("Desteklenmeyen blueprint schemaVersion: $schemaVersion.")
        }

        val platform = parsePlatform(
            root.requiredString("platform", "root.platform")
        )

        val tokens = root.optionalObject("tokens", "root.tokens")
            ?.let(::parseTokens)
            ?: AppForgeAgentDesignTokens()

        val screens = root.requiredArray("screens", "root.screens")
            .mapIndexed { index, value ->
                parseScreen(value.asObject("root.screens[$index]"), index)
            }

        val blueprint = AppForgeAgentBlueprint(
            appName = root.requiredString("appName", "root.appName"),
            prompt = root.requiredString("prompt", "root.prompt"),
            platform = platform,
            tokens = tokens,
            startRoute = root.requiredString("startRoute", "root.startRoute"),
            screens = screens,
            maxRepairAttempts = root.optionalInt(
                "maxRepairAttempts",
                "root.maxRepairAttempts"
            ) ?: 2
        )

        val validation = AppForgeAgentBlueprintValidator.validate(blueprint)
        if (!validation.valid) {
            val errors = validation.issues
                .filter { it.level == AgentBlueprintIssueLevel.ERROR }
                .joinToString(" | ") { "${it.field}: ${it.message}" }
            fail("Blueprint doğrulaması başarısız: $errors")
        }

        return blueprint
    }

    fun encode(blueprint: AppForgeAgentBlueprint): String {
        val validation = AppForgeAgentBlueprintValidator.validate(blueprint)
        if (!validation.valid) {
            val errors = validation.issues
                .filter { it.level == AgentBlueprintIssueLevel.ERROR }
                .joinToString(" | ") { "${it.field}: ${it.message}" }
            fail("Geçersiz blueprint encode edilemez: $errors")
        }

        return buildString {
            append('{')
            append("\"schemaVersion\":1,")
            append("\"appName\":")
            appendJsonString(blueprint.appName)
            append(',')
            append("\"prompt\":")
            appendJsonString(blueprint.prompt)
            append(',')
            append("\"platform\":")
            appendJsonString(blueprint.platform.name)
            append(',')
            append("\"tokens\":{")
            append("\"primary\":")
            appendJsonString(blueprint.tokens.primary)
            append(',')
            append("\"secondary\":")
            appendJsonString(blueprint.tokens.secondary)
            append(',')
            append("\"background\":")
            appendJsonString(blueprint.tokens.background)
            append(',')
            append("\"surface\":")
            appendJsonString(blueprint.tokens.surface)
            append(',')
            append("\"text\":")
            appendJsonString(blueprint.tokens.text)
            append(',')
            append("\"spacingUnitDp\":${blueprint.tokens.spacingUnitDp},")
            append("\"cornerRadiusDp\":${blueprint.tokens.cornerRadiusDp}")
            append("},")
            append("\"startRoute\":")
            appendJsonString(blueprint.startRoute)
            append(',')
            append("\"screens\":[")
            blueprint.screens.forEachIndexed { index, screen ->
                if (index > 0) append(',')
                append('{')
                append("\"id\":")
                appendJsonString(screen.id)
                append(',')
                append("\"title\":")
                appendJsonString(screen.title)
                append(',')
                append("\"route\":")
                appendJsonString(screen.route)
                append(',')
                append("\"purpose\":")
                appendJsonString(screen.purpose)
                append(',')
                append("\"components\":[")
                screen.components.forEachIndexed { componentIndex, component ->
                    if (componentIndex > 0) append(',')
                    appendJsonString(component)
                }
                append("],")
                append("\"actions\":[")
                screen.actions.forEachIndexed { actionIndex, action ->
                    if (actionIndex > 0) append(',')
                    append('{')
                    append("\"id\":")
                    appendJsonString(action.id)
                    append(',')
                    append("\"label\":")
                    appendJsonString(action.label)
                    action.targetRoute?.let { target ->
                        append(',')
                        append("\"targetRoute\":")
                        appendJsonString(target)
                    }
                    append('}')
                }
                append("]}")
            }
            append("],")
            append("\"maxRepairAttempts\":${blueprint.maxRepairAttempts}")
            append('}')
        }
    }

    internal fun extractStructuredPayload(raw: String): String {
        var text = raw.trim()
        if (text.isBlank()) fail("Blueprint yanıtı boş.")

        val finalRegex = Regex(
            pattern = "(?is)^\\s*<final_answer>\\s*(.*?)\\s*</final_answer>\\s*$"
        )
        finalRegex.matchEntire(text)?.groupValues?.getOrNull(1)?.let {
            text = it.trim()
        }

        val fenceRegex = Regex(
            pattern = "(?is)^```(?:json)?\\s*(.*?)\\s*```$"
        )
        fenceRegex.matchEntire(text)?.groupValues?.getOrNull(1)?.let {
            text = it.trim()
        }

        if (!text.startsWith('{') || !text.endsWith('}')) {
            fail("AI yalnız tek bir JSON nesnesi döndürmelidir; açıklama/metin kabul edilmez.")
        }
        return text
    }

    private fun parsePlatform(raw: String): AppForgeAgentPlatform =
        runCatching { AppForgeAgentPlatform.valueOf(raw.trim().uppercase()) }
            .getOrElse {
                fail(
                    "platform yalnız ANDROID, FLUTTER, REACT_NATIVE veya WEB olabilir."
                )
            }

    private fun parseTokens(obj: JsonObject): AppForgeAgentDesignTokens {
        obj.requireOnly(
            path = "root.tokens",
            allowed = setOf(
                "primary",
                "secondary",
                "background",
                "surface",
                "text",
                "spacingUnitDp",
                "cornerRadiusDp"
            )
        )
        val defaults = AppForgeAgentDesignTokens()
        return AppForgeAgentDesignTokens(
            primary = obj.optionalString("primary", "root.tokens.primary") ?: defaults.primary,
            secondary = obj.optionalString("secondary", "root.tokens.secondary") ?: defaults.secondary,
            background = obj.optionalString("background", "root.tokens.background") ?: defaults.background,
            surface = obj.optionalString("surface", "root.tokens.surface") ?: defaults.surface,
            text = obj.optionalString("text", "root.tokens.text") ?: defaults.text,
            spacingUnitDp = obj.optionalInt(
                "spacingUnitDp",
                "root.tokens.spacingUnitDp"
            ) ?: defaults.spacingUnitDp,
            cornerRadiusDp = obj.optionalInt(
                "cornerRadiusDp",
                "root.tokens.cornerRadiusDp"
            ) ?: defaults.cornerRadiusDp
        )
    }

    private fun parseScreen(obj: JsonObject, index: Int): AppForgeAgentScreenSpec {
        val path = "root.screens[$index]"
        obj.requireOnly(
            path = path,
            allowed = setOf(
                "id",
                "title",
                "route",
                "purpose",
                "components",
                "actions"
            )
        )

        val components = obj.optionalArray("components", "$path.components")
            ?.mapIndexed { componentIndex, value ->
                value.asString("$path.components[$componentIndex]")
            }
            ?: emptyList()

        val actions = obj.optionalArray("actions", "$path.actions")
            ?.mapIndexed { actionIndex, value ->
                parseAction(
                    value.asObject("$path.actions[$actionIndex]"),
                    path,
                    actionIndex
                )
            }
            ?: emptyList()

        return AppForgeAgentScreenSpec(
            id = obj.requiredString("id", "$path.id"),
            title = obj.requiredString("title", "$path.title"),
            route = obj.requiredString("route", "$path.route"),
            purpose = obj.requiredString("purpose", "$path.purpose"),
            components = components,
            actions = actions
        )
    }

    private fun parseAction(
        obj: JsonObject,
        screenPath: String,
        actionIndex: Int
    ): AppForgeAgentActionSpec {
        val path = "$screenPath.actions[$actionIndex]"
        obj.requireOnly(
            path = path,
            allowed = setOf("id", "label", "targetRoute")
        )
        return AppForgeAgentActionSpec(
            id = obj.requiredString("id", "$path.id"),
            label = obj.requiredString("label", "$path.label"),
            targetRoute = obj.optionalString("targetRoute", "$path.targetRoute")
        )
    }

    internal fun normalizeRawStringControlCharacters(input: String): String {
        var inString = false
        var escaped = false
        var changed = false
        val output = StringBuilder(input.length)

        input.forEach { char ->
            if (!inString) {
                output.append(char)
                if (char == '"') {
                    inString = true
                }
                return@forEach
            }

            if (escaped) {
                output.append(char)
                escaped = false
                return@forEach
            }

            when {
                char == '\\' -> {
                    output.append(char)
                    escaped = true
                }

                char == '"' -> {
                    output.append(char)
                    inString = false
                }

                char.code < 0x20 -> {
                    changed = true
                    when (char) {
                        '\b' -> output.append("\\b")
                        '\u000C' -> output.append("\\f")
                        '\n' -> output.append("\\n")
                        '\r' -> output.append("\\r")
                        '\t' -> output.append("\\t")
                        else -> {
                            output.append("\\u")
                            output.append(
                                char.code
                                    .toString(16)
                                    .padStart(4, '0')
                            )
                        }
                    }
                }

                else -> output.append(char)
            }
        }

        return if (changed) output.toString() else input
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }

    private sealed interface JsonValue
    private data class JsonObject(val values: Map<String, JsonValue>) : JsonValue
    private data class JsonArray(val values: List<JsonValue>) : JsonValue
    private data class JsonString(val value: String) : JsonValue
    private data class JsonNumber(val raw: String) : JsonValue
    private data class JsonBoolean(val value: Boolean) : JsonValue
    private data object JsonNull : JsonValue

    private fun JsonValue.asObject(path: String): JsonObject =
        this as? JsonObject ?: fail("$path JSON object olmalı.")

    private fun JsonValue.asString(path: String): String =
        (this as? JsonString)?.value ?: fail("$path string olmalı.")

    private fun JsonObject.requiredString(key: String, path: String): String =
        values[key]?.asString(path) ?: fail("$path zorunlu.")

    private fun JsonObject.optionalString(key: String, path: String): String? {
        val value = values[key] ?: return null
        if (value === JsonNull) return null
        return value.asString(path)
    }

    private fun JsonObject.requiredArray(key: String, path: String): List<JsonValue> =
        (values[key] as? JsonArray)?.values ?: fail("$path array olmalı ve zorunlu.")

    private fun JsonObject.optionalArray(key: String, path: String): List<JsonValue>? {
        val value = values[key] ?: return null
        if (value === JsonNull) return null
        return (value as? JsonArray)?.values ?: fail("$path array olmalı.")
    }

    private fun JsonObject.optionalObject(key: String, path: String): JsonObject? {
        val value = values[key] ?: return null
        if (value === JsonNull) return null
        return value as? JsonObject ?: fail("$path object olmalı.")
    }

    private fun JsonObject.requiredInt(key: String, path: String): Int =
        values[key].toStrictInt(path)

    private fun JsonObject.optionalInt(key: String, path: String): Int? {
        val value = values[key] ?: return null
        if (value === JsonNull) return null
        return value.toStrictInt(path)
    }

    private fun JsonValue?.toStrictInt(path: String): Int {
        val number = this as? JsonNumber ?: fail("$path integer olmalı.")
        if ('.' in number.raw || 'e' in number.raw.lowercase()) {
            fail("$path integer olmalı.")
        }
        return number.raw.toIntOrNull() ?: fail("$path integer aralık dışında.")
    }

    private fun JsonObject.requireOnly(path: String, allowed: Set<String>) {
        val unknown = values.keys - allowed
        if (unknown.isNotEmpty()) {
            fail("$path bilinmeyen alan içeriyor: ${unknown.sorted().joinToString()}.")
        }
    }

    private class Parser(private val input: String) {
        private var index = 0

        fun parseDocument(): JsonValue {
            skipWhitespace()
            val value = parseValue(depth = 0)
            skipWhitespace()
            if (index != input.length) {
                fail("JSON sonrasında ek içerik kabul edilmez.")
            }
            return value
        }

        private fun parseValue(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) fail("JSON iç içe derinlik sınırını aşıyor.")
            skipWhitespace()
            if (index >= input.length) fail("JSON beklenmedik şekilde bitti.")

            return when (val current = input[index]) {
                '{' -> parseObject(depth + 1)
                '[' -> parseArray(depth + 1)
                '"' -> JsonString(parseString())
                't' -> parseLiteral("true", JsonBoolean(true))
                'f' -> parseLiteral("false", JsonBoolean(false))
                'n' -> parseLiteral("null", JsonNull)
                '-', in '0'..'9' -> JsonNumber(parseNumber())
                else -> fail("Geçersiz JSON karakteri '$current' (konum $index).")
            }
        }

        private fun parseObject(depth: Int): JsonObject {
            expect('{')
            skipWhitespace()
            val values = linkedMapOf<String, JsonValue>()
            if (consumeIf('}')) return JsonObject(values)

            while (true) {
                if (values.size >= MAX_OBJECT_FIELDS) {
                    fail("JSON object alan sayısı sınırı aşıldı.")
                }
                skipWhitespace()
                if (peek() != '"') fail("JSON object anahtarı string olmalı.")
                val key = parseString()
                if (key in values) fail("Aynı JSON alanı tekrar edemez: $key.")
                skipWhitespace()
                expect(':')
                values[key] = parseValue(depth)
                skipWhitespace()
                when {
                    consumeIf('}') -> return JsonObject(values)
                    consumeIf(',') -> Unit
                    else -> fail("JSON object içinde ',' veya '}' bekleniyordu.")
                }
            }
        }

        private fun parseArray(depth: Int): JsonArray {
            expect('[')
            skipWhitespace()
            val values = mutableListOf<JsonValue>()
            if (consumeIf(']')) return JsonArray(values)

            while (true) {
                if (values.size >= MAX_ARRAY_ITEMS) {
                    fail("JSON array eleman sınırı aşıldı.")
                }
                values += parseValue(depth)
                skipWhitespace()
                when {
                    consumeIf(']') -> return JsonArray(values)
                    consumeIf(',') -> Unit
                    else -> fail("JSON array içinde ',' veya ']' bekleniyordu.")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()

            while (index < input.length) {
                val char = input[index++]
                when (char) {
                    '"' -> return result.toString()
                    '\\' -> {
                        if (index >= input.length) fail("JSON escape tamamlanmamış.")
                        val escaped = input[index++]
                        when (escaped) {
                            '"' -> result.append('"')
                            '\\' -> result.append('\\')
                            '/' -> result.append('/')
                            'b' -> result.append('\b')
                            'f' -> result.append('\u000C')
                            'n' -> result.append('\n')
                            'r' -> result.append('\r')
                            't' -> result.append('\t')
                            'u' -> result.append(parseUnicodeEscape())
                            else -> fail("Geçersiz JSON escape: \\$escaped.")
                        }
                    }
                    else -> {
                        if (char.code < 0x20) fail("JSON string kontrol karakteri içeremez.")
                        result.append(char)
                    }
                }

                if (result.length > MAX_STRING_CHARS) {
                    fail("JSON string uzunluk sınırı aşıldı.")
                }
            }
            fail("JSON string kapanmadı.")
        }

        private fun parseUnicodeEscape(): Char {
            if (index + 4 > input.length) fail("Geçersiz unicode escape.")
            val hex = input.substring(index, index + 4)
            if (!hex.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) {
                fail("Geçersiz unicode escape: $hex.")
            }
            index += 4
            return hex.toInt(16).toChar()
        }

        private fun parseNumber(): String {
            val start = index
            consumeIf('-')

            if (consumeIf('0')) {
                // Leading zero consumed.
            } else {
                if (peek() !in '1'..'9') fail("Geçersiz JSON number.")
                while (peek() in '0'..'9') index += 1
            }

            if (consumeIf('.')) {
                if (peek() !in '0'..'9') fail("Geçersiz JSON decimal number.")
                while (peek() in '0'..'9') index += 1
            }

            val exponent = peek()
            if (exponent == 'e' || exponent == 'E') {
                index += 1
                if (peek() == '+' || peek() == '-') index += 1
                if (peek() !in '0'..'9') fail("Geçersiz JSON exponent.")
                while (peek() in '0'..'9') index += 1
            }

            return input.substring(start, index)
        }

        private fun <T : JsonValue> parseLiteral(expected: String, value: T): T {
            if (!input.startsWith(expected, index)) {
                fail("Geçersiz JSON literal (konum $index).")
            }
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index].isWhitespace()) index += 1
        }

        private fun peek(): Char? = input.getOrNull(index)

        private fun expect(expected: Char) {
            if (index >= input.length || input[index] != expected) {
                fail("JSON '$expected' bekliyordu (konum $index).")
            }
            index += 1
        }

        private fun consumeIf(expected: Char): Boolean {
            if (index < input.length && input[index] == expected) {
                index += 1
                return true
            }
            return false
        }
    }

    private fun fail(message: String): Nothing =
        throw AppForgeAgentBlueprintJsonException(message)
}
