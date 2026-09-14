package com.appforge.studio.ai

import org.json.JSONArray
import org.json.JSONObject

internal object AppForgeAgentSessionCodec {
    private const val MAX_LOG_LINES = 60
    private const val MAX_HISTORY = 10
    private const val MAX_RELEASE_NOTES = 20
    private const val MAX_SECURITY = 30
    private const val MAX_CHANGES = 30

    fun encode(
        session: AppForgeAgentPersistentSession
    ): String {
        val clean = sanitize(session)
        val root = JSONObject()

        root.put(
            "schemaVersion",
            clean.schemaVersion
        )
        root.put(
            "sessionId",
            clean.sessionId
        )
        root.put(
            "updatedAtEpochMs",
            clean.updatedAtEpochMs
        )

        clean.customName?.let {
            root.put(
                "customName",
                it
            )
        }

        root.put(
            "pinned",
            clean.pinned
        )
        root.put(
            "archived",
            clean.archived
        )

        root.put(
            "step",
            clean.state.step.name
        )
        root.put(
            "prompt",
            clean.state.prompt
        )
        root.put(
            "platform",
            clean.state.platform.name
        )
        root.put(
            "message",
            clean.state.message
        )

        clean.state.blueprint?.let { blueprint ->
            root.put(
                "blueprint",
                sanitizeJsonObject(
                    JSONObject(
                        AppForgeAgentBlueprintJson.encode(
                            blueprint
                        )
                    )
                )
            )
        }

        clean.workspacePath?.let {
            root.put(
                "workspacePath",
                it
            )
        }

        clean.state.remoteBuild?.let {
            root.put(
                "remoteBuild",
                remoteBuildToJson(it)
            )
        }

        root.put(
            "artifact",
            artifactToJson(
                clean.artifactState
            )
        )

        root.put(
            "releaseReview",
            releaseReviewToJson(
                clean.releaseReviewState
            )
        )

        return root.toString()
    }

    fun decode(
        payload: String
    ): AppForgeAgentPersistentSession {
        require(payload.length <= 512 * 1024) {
            "Session payload sınırı aşıldı."
        }

        val root = JSONObject(payload)
        val schema =
            root.getInt("schemaVersion")

        require(
            schema ==
                AppForgeAgentPersistentSession
                    .CURRENT_SCHEMA_VERSION
        ) {
            "Desteklenmeyen session schema: $schema"
        }

        val blueprint =
            root.optJSONObject("blueprint")
                ?.toString()
                ?.let(
                    AppForgeAgentBlueprintJson::parse
                )

        val validation =
            blueprint?.let(
                AppForgeAgentBlueprintValidator::validate
            )

        if (validation != null) {
            require(validation.valid) {
                "Persisted Blueprint doğrulaması başarısız."
            }
        }

        val step =
            enumValueOf<AppForgeAgentStudioStep>(
                root.getString("step")
            )

        val platform =
            enumValueOf<AppForgeAgentPlatform>(
                root.getString("platform")
            )

        require(
            blueprint == null ||
                blueprint.platform == platform
        ) {
            "Persisted Blueprint platformu uyuşmuyor."
        }

        val remote =
            root.optJSONObject("remoteBuild")
                ?.let(::remoteBuildFromJson)

        val safeStep =
            if (
                step == AppForgeAgentStudioStep.BUILD &&
                remote == null
            ) {
                AppForgeAgentStudioStep.BLOCKED
            } else {
                step
            }

        val safeMessage =
            if (
                step == AppForgeAgentStudioStep.BUILD &&
                remote == null
            ) {
                "Yarım build session'ı bulundu; uzaktaki build kimliği olmadığı için güvenli şekilde durduruldu."
            } else {
                root.optString(
                    "message",
                    ""
                )
            }

        return sanitize(
            AppForgeAgentPersistentSession(
                schemaVersion = schema,
                sessionId =
                    root.getString(
                        "sessionId"
                    ),
                updatedAtEpochMs =
                    root.getLong(
                        "updatedAtEpochMs"
                    ),
                customName =
                    root.optStringOrNull(
                        "customName"
                    ),
                pinned =
                    root.optBoolean(
                        "pinned",
                        false
                    ),
                archived =
                    root.optBoolean(
                        "archived",
                        false
                    ),
                state =
                    AppForgeAgentStudioState(
                        step = safeStep,
                        prompt =
                            root.optString(
                                "prompt",
                                ""
                            ),
                        platform = platform,
                        blueprint = blueprint,
                        validation = validation,
                        autonomousResult = null,
                        message = safeMessage,
                        busy = false,
                        remoteBuild = remote
                    ),
                artifactState =
                    root.optJSONObject(
                        "artifact"
                    )
                        ?.let(
                            ::artifactFromJson
                        )
                        ?: AppForgeAgentArtifactState(),
                releaseReviewState =
                    root.optJSONObject(
                        "releaseReview"
                    )
                        ?.let(
                            ::releaseReviewFromJson
                        )
                        ?: AppForgeAgentReleaseReviewState(),
                workspacePath =
                    root.optStringOrNull(
                        "workspacePath"
                    )
            )
        )
    }

    fun sanitize(
        session: AppForgeAgentPersistentSession
    ): AppForgeAgentPersistentSession {
        val cleanBlueprint =
            session.state.blueprint
                ?.let {
                    val json =
                        JSONObject(
                            AppForgeAgentBlueprintJson.encode(
                                it
                            )
                        )
                    AppForgeAgentBlueprintJson.parse(
                        sanitizeJsonObject(
                            json
                        ).toString()
                    )
                }

        val cleanValidation =
            cleanBlueprint?.let(
                AppForgeAgentBlueprintValidator::validate
            )

        val cleanRemote =
            session.state.remoteBuild?.copy(
                buildId =
                    cleanText(
                        session.state.remoteBuild.buildId,
                        160
                    ),
                status =
                    cleanText(
                        session.state.remoteBuild.status,
                        80
                    ),
                logs =
                    session.state.remoteBuild.logs
                        .takeLast(MAX_LOG_LINES)
                        .map {
                            cleanText(
                                it,
                                1_200
                            )
                        },
                preflight =
                    session.state.remoteBuild.preflight
                        .takeLast(MAX_LOG_LINES)
                        .map {
                            cleanText(
                                it,
                                1_200
                            )
                        }
            )

        val cleanArtifact =
            session.artifactState.copy(
                busy = false,
                message =
                    cleanText(
                        session.artifactState.message,
                        2_000
                    ),
                buildId =
                    session.artifactState.buildId
                        ?.let {
                            cleanText(
                                it,
                                160
                            )
                        },
                logs =
                    session.artifactState.logs
                        .takeLast(MAX_LOG_LINES)
                        .map {
                            cleanText(
                                it,
                                1_200
                            )
                        },
                security =
                    session.artifactState.security
                        .take(MAX_SECURITY)
                        .map {
                            it.copy(
                                severity =
                                    cleanText(
                                        it.severity,
                                        80
                                    ),
                                title =
                                    cleanText(
                                        it.title,
                                        300
                                    ),
                                detail =
                                    cleanText(
                                        it.detail,
                                        1_500
                                    )
                            )
                        },
                lastDownloadId = null
            )

        val cleanRelease =
            session.releaseReviewState.copy(
                busy = false,
                message =
                    cleanText(
                        session.releaseReviewState.message,
                        2_000
                    ),
                history =
                    session.releaseReviewState.history
                        .take(MAX_HISTORY)
                        .map {
                            it.copy(
                                buildId =
                                    cleanText(
                                        it.buildId,
                                        160
                                    ),
                                status =
                                    cleanText(
                                        it.status,
                                        80
                                    )
                            )
                        },
                comparison =
                    session.releaseReviewState.comparison
                        ?.copy(
                            previousBuildId =
                                cleanText(
                                    session.releaseReviewState
                                        .comparison
                                        .previousBuildId,
                                    160
                                ),
                            currentBuildId =
                                cleanText(
                                    session.releaseReviewState
                                        .comparison
                                        .currentBuildId,
                                    160
                                ),
                            changes =
                                session.releaseReviewState
                                    .comparison
                                    .changes
                                    .take(MAX_CHANGES)
                                    .map {
                                        cleanText(
                                            it,
                                            1_200
                                        )
                                    }
                        ),
                releaseNotes =
                    session.releaseReviewState.releaseNotes
                        .take(MAX_RELEASE_NOTES)
                        .map {
                            cleanText(
                                it,
                                1_000
                            )
                        },
                readiness =
                    session.releaseReviewState.readiness.copy(
                        reviewRequired = true,
                        checks =
                            session.releaseReviewState
                                .readiness
                                .checks
                                .take(30)
                                .map {
                                    cleanText(
                                        it,
                                        800
                                    )
                                },
                        blockers =
                            session.releaseReviewState
                                .readiness
                                .blockers
                                .take(30)
                                .map {
                                    cleanText(
                                        it,
                                        800
                                    )
                                }
                    )
            )

        return session.copy(
            sessionId =
                cleanSessionId(
                    session.sessionId
                ),
            customName =
                session.customName
                    ?.trim()
                    ?.take(80)
                    ?.let {
                        cleanText(
                            it,
                            80
                        )
                    }
                    ?.takeIf {
                        it.isNotBlank()
                    },
            state =
                session.state.copy(
                    prompt =
                        cleanText(
                            session.state.prompt,
                            4_000
                        ),
                    blueprint = cleanBlueprint,
                    validation = cleanValidation,
                    autonomousResult = null,
                    message =
                        cleanText(
                            session.state.message,
                            2_000
                        ),
                    busy = false,
                    remoteBuild = cleanRemote
                ),
            artifactState = cleanArtifact,
            releaseReviewState = cleanRelease,
            workspacePath =
                session.workspacePath
                    ?.take(1_024)
        )
    }

    private fun remoteBuildToJson(
        remote: AppForgeAgentRemoteBuildInfo
    ) = JSONObject().apply {
        put(
            "buildId",
            remote.buildId
        )
        remote.buildNo?.let {
            put(
                "buildNo",
                it
            )
        }
        put(
            "status",
            remote.status
        )
        put(
            "progress",
            remote.progress
        )
        put(
            "apkAvailable",
            remote.apkAvailable
        )
        put(
            "aabAvailable",
            remote.aabAvailable
        )
        put(
            "exeAvailable",
            remote.exeAvailable
        )
        put(
            "logs",
            stringsToJson(
                remote.logs
            )
        )
        put(
            "preflight",
            stringsToJson(
                remote.preflight
            )
        )
    }

    private fun remoteBuildFromJson(
        json: JSONObject
    ) = AppForgeAgentRemoteBuildInfo(
        buildId =
            json.getString(
                "buildId"
            ),
        buildNo =
            json.optLongOrNull(
                "buildNo"
            ),
        status =
            json.optString(
                "status",
                ""
            ),
        progress =
            json.optInt(
                "progress",
                0
            )
                .coerceIn(
                    0,
                    100
                ),
        apkAvailable =
            json.optBoolean(
                "apkAvailable",
                false
            ),
        aabAvailable =
            json.optBoolean(
                "aabAvailable",
                false
            ),
        exeAvailable =
            json.optBoolean(
                "exeAvailable",
                false
            ),
        logs =
            json.optJSONArray(
                "logs"
            ).toStrings(),
        preflight =
            json.optJSONArray(
                "preflight"
            ).toStrings()
    )

    private fun artifactToJson(
        artifact: AppForgeAgentArtifactState
    ) = JSONObject().apply {
        put(
            "message",
            artifact.message
        )
        artifact.buildId?.let {
            put(
                "buildId",
                it
            )
        }
        put(
            "logsLoaded",
            artifact.logsLoaded
        )
        put(
            "testLabAvailable",
            artifact.testLabAvailable
        )
        put(
            "logs",
            stringsToJson(
                artifact.logs
            )
        )
        artifact.apk?.let {
            put(
                "apk",
                artifactReportToJson(
                    it
                )
            )
        }
        artifact.aab?.let {
            put(
                "aab",
                artifactReportToJson(
                    it
                )
            )
        }

        val securityJson =
            JSONArray()

        artifact.security.forEach {
            securityJson.put(
                JSONObject().apply {
                    put(
                        "severity",
                        it.severity
                    )
                    put(
                        "title",
                        it.title
                    )
                    put(
                        "detail",
                        it.detail
                    )
                }
            )
        }

        put(
            "security",
            securityJson
        )
    }

    private fun artifactFromJson(
        json: JSONObject
    ): AppForgeAgentArtifactState {
        val security =
            buildList {
                val array =
                    json.optJSONArray(
                        "security"
                    )
                        ?: JSONArray()

                for (
                    index in
                    0 until array.length()
                ) {
                    val item =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    add(
                        AppForgeAgentSecurityFinding(
                            severity =
                                item.optString(
                                    "severity",
                                    ""
                                ),
                            title =
                                item.optString(
                                    "title",
                                    ""
                                ),
                            detail =
                                item.optString(
                                    "detail",
                                    ""
                                )
                        )
                    )
                }
            }

        return AppForgeAgentArtifactState(
            busy = false,
            message =
                json.optString(
                    "message",
                    ""
                ),
            buildId =
                json.optStringOrNull(
                    "buildId"
                ),
            logsLoaded =
                json.optBoolean(
                    "logsLoaded",
                    false
                ),
            testLabAvailable =
                json.optBoolean(
                    "testLabAvailable",
                    false
                ),
            logs =
                json.optJSONArray(
                    "logs"
                ).toStrings(),
            apk =
                json.optJSONObject(
                    "apk"
                )
                    ?.let(
                        ::artifactReportFromJson
                    ),
            aab =
                json.optJSONObject(
                    "aab"
                )
                    ?.let(
                        ::artifactReportFromJson
                    ),
            security =
                security
                    .take(
                        MAX_SECURITY
                    ),
            lastDownloadId = null
        )
    }

    private fun artifactReportToJson(
        report: AppForgeAgentArtifactReport
    ) = JSONObject().apply {
        put(
            "kind",
            report.kind
        )
        put(
            "fileSizeBytes",
            report.fileSizeBytes
        )
        put(
            "uncompressedBytes",
            report.uncompressedBytes
        )
        put(
            "entryCount",
            report.entryCount
        )
    }

    private fun artifactReportFromJson(
        json: JSONObject
    ) = AppForgeAgentArtifactReport(
        kind =
            json.optString(
                "kind",
                ""
            ),
        fileSizeBytes =
            json.optLong(
                "fileSizeBytes",
                0L
            ),
        uncompressedBytes =
            json.optLong(
                "uncompressedBytes",
                0L
            ),
        entryCount =
            json.optInt(
                "entryCount",
                0
            )
    )

    private fun releaseReviewToJson(
        review: AppForgeAgentReleaseReviewState
    ) = JSONObject().apply {
        put(
            "message",
            review.message
        )

        val history =
            JSONArray()

        review.history.forEach {
            history.put(
                JSONObject().apply {
                    put(
                        "buildId",
                        it.buildId
                    )
                    it.buildNo?.let {
                        buildNo ->
                            put(
                                "buildNo",
                                buildNo
                            )
                    }
                    put(
                        "status",
                        it.status
                    )
                    put(
                        "createdAt",
                        it.createdAt
                    )
                }
            )
        }

        put(
            "history",
            history
        )

        review.comparison?.let {
            comparison ->
                put(
                    "comparison",
                    JSONObject().apply {
                        put(
                            "previousBuildId",
                            comparison.previousBuildId
                        )
                        put(
                            "currentBuildId",
                            comparison.currentBuildId
                        )
                        put(
                            "apkDeltaBytes",
                            comparison.apkDeltaBytes
                        )
                        put(
                            "aabDeltaBytes",
                            comparison.aabDeltaBytes
                        )
                        put(
                            "changeCount",
                            comparison.changeCount
                        )
                        put(
                            "changes",
                            stringsToJson(
                                comparison.changes
                            )
                        )
                    }
                )
        }

        put(
            "releaseNotes",
            stringsToJson(
                review.releaseNotes
            )
        )

        put(
            "readiness",
            JSONObject().apply {
                put(
                    "ready",
                    review.readiness.ready
                )
                put(
                    "reviewRequired",
                    true
                )
                put(
                    "checks",
                    stringsToJson(
                        review.readiness.checks
                    )
                )
                put(
                    "blockers",
                    stringsToJson(
                        review.readiness.blockers
                    )
                )
            }
        )
    }

    private fun releaseReviewFromJson(
        json: JSONObject
    ): AppForgeAgentReleaseReviewState {
        val history =
            buildList {
                val array =
                    json.optJSONArray(
                        "history"
                    )
                        ?: JSONArray()

                for (
                    index in
                    0 until array.length()
                ) {
                    val item =
                        array.optJSONObject(
                            index
                        )
                            ?: continue

                    add(
                        AppForgeAgentBuildHistoryItem(
                            buildId =
                                item.optString(
                                    "buildId",
                                    ""
                                ),
                            buildNo =
                                item.optLongOrNull(
                                    "buildNo"
                                ),
                            status =
                                item.optString(
                                    "status",
                                    ""
                                ),
                            createdAt =
                                item.optLong(
                                    "createdAt",
                                    0L
                                )
                        )
                    )
                }
            }

        val comparison =
            json.optJSONObject(
                "comparison"
            )
                ?.let {
                    AppForgeAgentBuildComparison(
                        previousBuildId =
                            it.optString(
                                "previousBuildId",
                                ""
                            ),
                        currentBuildId =
                            it.optString(
                                "currentBuildId",
                                ""
                            ),
                        apkDeltaBytes =
                            it.optLong(
                                "apkDeltaBytes",
                                0L
                            ),
                        aabDeltaBytes =
                            it.optLong(
                                "aabDeltaBytes",
                                0L
                            ),
                        changeCount =
                            it.optInt(
                                "changeCount",
                                0
                            ),
                        changes =
                            it.optJSONArray(
                                "changes"
                            ).toStrings()
                    )
                }

        val readinessJson =
            json.optJSONObject(
                "readiness"
            )

        val readiness =
            AppForgeAgentReleaseReadiness(
                ready =
                    readinessJson
                        ?.optBoolean(
                            "ready",
                            false
                        )
                        ?: false,
                reviewRequired = true,
                checks =
                    readinessJson
                        ?.optJSONArray(
                            "checks"
                        ).toStrings(),
                blockers =
                    readinessJson
                        ?.optJSONArray(
                            "blockers"
                        ).toStrings()
            )

        return AppForgeAgentReleaseReviewState(
            busy = false,
            message =
                json.optString(
                    "message",
                    ""
                ),
            history =
                history
                    .take(
                        MAX_HISTORY
                    ),
            comparison = comparison,
            releaseNotes =
                json.optJSONArray(
                    "releaseNotes"
                )
                    .toStrings()
                    .take(
                        MAX_RELEASE_NOTES
                    ),
            readiness = readiness
        )
    }

    private fun stringsToJson(
        values: List<String>
    ) = JSONArray().apply {
        values.forEach(
            ::put
        )
    }

    private fun JSONArray?.toStrings():
        List<String> {
        if (this == null) {
            return emptyList()
        }

        return buildList {
            for (
                index in
                0 until length()
            ) {
                val value =
                    optString(
                        index,
                        ""
                    )

                if (
                    value.isNotBlank()
                ) {
                    add(
                        value
                    )
                }
            }
        }
    }

    private fun JSONObject.optStringOrNull(
        key: String
    ): String? =
        if (
            has(key) &&
            !isNull(key)
        ) {
            optString(
                key,
                ""
            )
                .takeIf {
                    it.isNotBlank()
                }
        } else {
            null
        }

    private fun JSONObject.optLongOrNull(
        key: String
    ): Long? =
        if (
            has(key) &&
            !isNull(key)
        ) {
            optLong(
                key
            )
        } else {
            null
        }

    private fun sanitizeJsonObject(
        source: JSONObject
    ): JSONObject {
        val result =
            JSONObject()

        val keys =
            source.keys()

        while (
            keys.hasNext()
        ) {
            val key =
                keys.next()

            result.put(
                key,
                sanitizeJsonValue(
                    source.opt(
                        key
                    )
                )
            )
        }

        return result
    }

    private fun sanitizeJsonArray(
        source: JSONArray
    ): JSONArray {
        val result =
            JSONArray()

        for (
            index in
            0 until source.length()
        ) {
            result.put(
                sanitizeJsonValue(
                    source.opt(
                        index
                    )
                )
            )
        }

        return result
    }

    private fun sanitizeJsonValue(
        value: Any?
    ): Any? =
        when (value) {
            null,
            JSONObject.NULL ->
                JSONObject.NULL

            is JSONObject ->
                sanitizeJsonObject(
                    value
                )

            is JSONArray ->
                sanitizeJsonArray(
                    value
                )

            is String ->
                cleanText(
                    value,
                    8_000
                )

            else ->
                value
        }

    private fun cleanText(
        value: String,
        maxChars: Int
    ): String =
        AppForgeAgentArtifactSafety.sanitize(
            value,
            maxChars
        )

    private fun cleanSessionId(
        value: String
    ): String =
        value
            .filter {
                it.isLetterOrDigit() ||
                    it in "-_"
            }
            .take(80)
            .takeIf {
                it.length >= 8
            }
            ?: "session-invalid"
}
