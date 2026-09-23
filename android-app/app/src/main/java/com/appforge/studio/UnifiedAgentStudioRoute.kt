@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.appforge.studio.ai.AgentBlueprintIssueLevel
import com.appforge.studio.ai.AppForgeAgentBlueprintJson
import com.appforge.studio.ai.AppForgeAgentBlueprintPrompt
import com.appforge.studio.ai.AppForgeAgentBlueprintValidator
import com.appforge.studio.ai.AppForgeAgentStudioState
import com.appforge.studio.ai.AppForgeAgentStudioStep
import com.appforge.studio.ai.AppForgeAgentBlueprintProvider
import com.appforge.studio.ai.AppForgeAgentBuildServiceStageRunner
import com.appforge.studio.ai.AppForgeAgentLocalPatchProvider
import com.appforge.studio.ai.AppForgeAgentStudioBuildRequest
import com.appforge.studio.ai.AppForgeAgentStudioOrchestrator
import com.appforge.studio.ai.AppForgeAgentWorkspaceStore
import com.appforge.studio.ai.AppForgeAgentArtifactClient
import com.appforge.studio.ai.AppForgeAgentArtifactState
import com.appforge.studio.ai.AppForgeAgentSourceExporter
import com.appforge.studio.ai.AppForgeAgentReleaseReviewClient
import com.appforge.studio.ai.AppForgeAgentReleaseReviewState
import com.appforge.studio.ai.AppForgeAgentPersistentSession
import com.appforge.studio.ai.AppForgeAgentSessionLoadResult
import com.appforge.studio.ai.AppForgeAgentSessionRuntimePolicy
import com.appforge.studio.ai.AppForgeAgentSessionStore
import com.appforge.studio.ai.AppForgeAgentRemoteBuildResumer
import com.appforge.studio.ai.AppForgeAgentRemoteBuildResumeOutcome
import com.appforge.studio.ai.AppForgeAgentRecoveryPolicy
import com.appforge.studio.ai.AppForgeAgentProjectMemoryStore
import com.appforge.studio.ai.AppForgeAgentQualityGate
import com.appforge.studio.ai.AppForgeAgentFinalAcceptance
import com.appforge.studio.ai.AppForgeUnifiedAgentStudioScreen
import com.appforge.studio.ai.AppForgeLocalAssistant
import com.appforge.studio.ai.LocalAiModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun UnifiedAgentStudioRoute(
    buildServiceUrl: String,
    buildApiKey: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val assistant = remember {
        AppForgeLocalAssistant(context)
    }

    var state by remember {
        mutableStateOf(
            AppForgeAgentStudioState()
        )
    }

    var artifactState by remember {
        mutableStateOf(
            AppForgeAgentArtifactState()
        )
    }

    var releaseReviewState by remember {
        mutableStateOf(
            AppForgeAgentReleaseReviewState()
        )
    }

    var lastWorkspacePath by remember {
        mutableStateOf<String?>(null)
    }

    var pendingSession by remember {
        mutableStateOf<AppForgeAgentPersistentSession?>(null)
    }

    var recentSessions by remember {
        mutableStateOf<List<AppForgeAgentPersistentSession>>(
            emptyList()
        )
    }

    var archivedSessions by remember {
        mutableStateOf<List<AppForgeAgentPersistentSession>>(
            emptyList()
        )
    }

    var quarantinedSessionCount by remember {
        mutableStateOf(
            0
        )
    }

    var currentSessionId by remember {
        mutableStateOf(
            UUID.randomUUID().toString()
        )
    }

    var persistenceReady by remember {
        mutableStateOf(false)
    }

    val sessionStore = remember {
        AppForgeAgentSessionStore(
            rootDir =
                File(
                    context.filesDir,
                    "unified-agent-session"
                )
        )
    }

    val projectMemoryStore = remember {
        AppForgeAgentProjectMemoryStore(
            filesDir = context.filesDir
        )
    }

    val artifactClient = remember(
        buildServiceUrl,
        buildApiKey
    ) {
        AppForgeAgentArtifactClient(
            context = context,
            buildServiceUrl = buildServiceUrl,
            buildApiKey = buildApiKey
        )
    }

    val releaseReviewClient = remember(
        buildServiceUrl,
        buildApiKey
    ) {
        AppForgeAgentReleaseReviewClient(
            context = context,
            buildServiceUrl = buildServiceUrl,
            buildApiKey = buildApiKey
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            assistant.close()
        }
    }

    LaunchedEffect(Unit) {
        val loaded =
            withContext(
                Dispatchers.IO
            ) {
                sessionStore.load()
            }

        val initialCollections =
            withContext(
                Dispatchers.IO
            ) {
                sessionStore.cleanupStorage(
                    context.filesDir
                )

                Triple(
                    sessionStore.listRecent(),
                    sessionStore.listArchived(),
                    sessionStore.quarantineCount()
                )
            }

        recentSessions =
            initialCollections.first

        archivedSessions =
            initialCollections.second

        quarantinedSessionCount =
            initialCollections.third

        when (loaded) {
            AppForgeAgentSessionLoadResult.Empty -> {
                pendingSession =
                    null
            }

            is AppForgeAgentSessionLoadResult.Loaded -> {
                pendingSession =
                    loaded.session
            }

            is AppForgeAgentSessionLoadResult.Quarantined -> {
                state =
                    state.copy(
                        message =
                            loaded.reason
                    )
            }
        }

        persistenceReady =
            true
    }

    LaunchedEffect(
        persistenceReady,
        pendingSession,
        currentSessionId,
        state,
        artifactState,
        releaseReviewState,
        lastWorkspacePath
    ) {
        if (
            !persistenceReady ||
            pendingSession != null
        ) {
            return@LaunchedEffect
        }

        if (
            !AppForgeAgentSessionRuntimePolicy.shouldPersist(
                state = state,
                artifactState =
                    artifactState,
                releaseReviewState =
                    releaseReviewState,
                workspacePath =
                    lastWorkspacePath
            )
        ) {
            return@LaunchedEffect
        }

        delay(
            450L
        )

        val snapshot =
            AppForgeAgentPersistentSession(
                sessionId =
                    currentSessionId,
                state = state,
                artifactState =
                    artifactState,
                releaseReviewState =
                    releaseReviewState,
                workspacePath =
                    lastWorkspacePath
            )

        val refreshedSessions =
            withContext(
                Dispatchers.IO
            ) {
                sessionStore.save(
                    snapshot
                )

                sessionStore.listRecent() to
                    sessionStore.listArchived()
            }

        recentSessions =
            refreshedSessions.first

        archivedSessions =
            refreshedSessions.second
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = {
                Text("Unified Agent")
            },
            navigationIcon = {
                TextButton(
                    onClick = onBack
                ) {
                    Text("← Geri")
                }
            }
        )

        AppForgeUnifiedAgentStudioScreen(
            state = state,
            artifactState = artifactState,
            releaseReviewState = releaseReviewState,
            resumeInfo =
                pendingSession?.let(
                    AppForgeAgentSessionRuntimePolicy::resumeInfo
                ),
            recentSessions =
                recentSessions
                    .filter {
                        it.sessionId !=
                            currentSessionId &&
                            it.sessionId !=
                                pendingSession
                                    ?.sessionId
                    }
                    .map(
                        AppForgeAgentSessionRuntimePolicy::resumeInfo
                    ),
            archivedSessions =
                archivedSessions
                    .map(
                        AppForgeAgentSessionRuntimePolicy::resumeInfo
                    ),
            recoveryAssessment =
                pendingSession?.let {
                    AppForgeAgentRecoveryPolicy.assess(
                        filesDir =
                            context.filesDir,
                        session =
                            it,
                        quarantinedSessionCount =
                            quarantinedSessionCount
                    )
                },
            canExportSource =
                lastWorkspacePath != null &&
                    state.step == AppForgeAgentStudioStep.RESULT,
            onResumeSession = {
                pendingSession?.let { session ->
                    val resumeBuildId =
                        session.resumableBuildId
                    val resumeBuild =
                        session.state.step ==
                            AppForgeAgentStudioStep.BUILD &&
                            !resumeBuildId.isNullOrBlank()

                    currentSessionId =
                        session.sessionId

                    state =
                        session.state.copy(
                            busy = resumeBuild,
                            message =
                                if (resumeBuild) {
                                    "Mevcut cihaz build kaydı yeniden açılıyor..."
                                } else {
                                    session.state.message
                                }
                        )

                    artifactState =
                        session.artifactState.copy(
                            busy = false
                        )

                    releaseReviewState =
                        session.releaseReviewState.copy(
                            busy = false
                        )

                    lastWorkspacePath =
                        AppForgeAgentSessionRuntimePolicy
                            .restoreWorkspacePath(
                                filesDir =
                                    context.filesDir,
                                savedPath =
                                    session.workspacePath
                            )

                    pendingSession =
                        null

                    if (
                        resumeBuild &&
                        resumeBuildId != null
                    ) {
                        scope.launch {
                            val resumer =
                                AppForgeAgentRemoteBuildResumer(
                                    context = context,
                                    buildServiceUrl =
                                        buildServiceUrl,
                                    buildApiKey =
                                        buildApiKey
                                )

                            val resumed =
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    resumer.resume(
                                        resumeBuildId
                                    ) {
                                        remote,
                                        message ->
                                            scope.launch {
                                                state =
                                                    state.copy(
                                                        step =
                                                            AppForgeAgentStudioStep.BUILD,
                                                        busy =
                                                            true,
                                                        remoteBuild =
                                                            remote,
                                                        message =
                                                            message
                                                    )
                                            }
                                    }
                                }

                            state =
                                when (
                                    resumed.outcome
                                ) {
                                    AppForgeAgentRemoteBuildResumeOutcome.SUCCESS ->
                                        state.copy(
                                            step =
                                                AppForgeAgentStudioStep.RESULT,
                                            busy =
                                                false,
                                            remoteBuild =
                                                resumed.remote,
                                            message =
                                                resumed.message
                                        )

                                    AppForgeAgentRemoteBuildResumeOutcome.FAILURE,
                                    AppForgeAgentRemoteBuildResumeOutcome.TRACKING_TIMEOUT ->
                                        state.copy(
                                            step =
                                                AppForgeAgentStudioStep.BLOCKED,
                                            busy =
                                                false,
                                            remoteBuild =
                                                resumed.remote
                                                    ?: state.remoteBuild,
                                            message =
                                                resumed.message
                                        )

                                    AppForgeAgentRemoteBuildResumeOutcome.RUNNING ->
                                        state
                                }

                            val finalRemote =
                                state.remoteBuild

                            if (
                                finalRemote != null &&
                                resumed.outcome !=
                                    AppForgeAgentRemoteBuildResumeOutcome.RUNNING
                            ) {
                                val inspected =
                                    runCatching {
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            artifactClient.inspect(
                                                finalRemote.buildId
                                            )
                                        }
                                    }.getOrElse { error ->
                                        AppForgeAgentArtifactState(
                                            busy = false,
                                            buildId =
                                                finalRemote.buildId,
                                            message =
                                                studioSafeMessage(
                                                    error
                                                )
                                        )
                                    }

                                artifactState =
                                    inspected

                                state.blueprint?.let {
                                    blueprint ->
                                        releaseReviewState =
                                            runCatching {
                                                withContext(
                                                    Dispatchers.IO
                                                ) {
                                                    releaseReviewClient.load(
                                                        remote =
                                                            finalRemote,
                                                        blueprint =
                                                            blueprint,
                                                        artifacts =
                                                            inspected
                                                    )
                                                }
                                            }.getOrElse {
                                                error ->
                                                    AppForgeAgentReleaseReviewState(
                                                        busy =
                                                            false,
                                                        message =
                                                            studioSafeMessage(
                                                                error
                                                            )
                                                    )
                                            }
                                }
                            }
                        }
                    }
                }
            },
            onDiscardSession = {
                val discardedSessionId =
                    pendingSession
                        ?.sessionId

                pendingSession =
                    null

                currentSessionId =
                    UUID.randomUUID()
                        .toString()

                state =
                    AppForgeAgentStudioState()

                artifactState =
                    AppForgeAgentArtifactState()

                releaseReviewState =
                    AppForgeAgentReleaseReviewState()

                lastWorkspacePath =
                    null

                scope.launch {
                    val refreshed =
                        withContext(
                            Dispatchers.IO
                        ) {
                            if (
                                discardedSessionId != null
                            ) {
                                sessionStore.deleteSession(
                                    discardedSessionId
                                )
                            } else {
                                sessionStore.clear()
                            }

                            sessionStore.listRecent() to
                                sessionStore.listArchived()
                        }

                    recentSessions =
                        refreshed.first

                    archivedSessions =
                        refreshed.second
                }
            },
            onSelectRecentSession = {
                sessionId ->
                    scope.launch {
                        when (
                            val loaded =
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    sessionStore.loadById(
                                        sessionId
                                    )
                                }
                        ) {
                            is AppForgeAgentSessionLoadResult.Loaded -> {
                                pendingSession =
                                    loaded.session

                                state =
                                    state.copy(
                                        message =
                                            "Kayıt seçildi. Devam etmek için yukarıdaki kartı aç."
                                    )
                            }

                            is AppForgeAgentSessionLoadResult.Quarantined -> {
                                state =
                                    state.copy(
                                        message =
                                            loaded.reason
                                    )

                                val refreshed =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        sessionStore.listRecent() to
                                            sessionStore.quarantineCount()
                                    }

                                recentSessions =
                                    refreshed.first

                                quarantinedSessionCount =
                                    refreshed.second
                            }

                            AppForgeAgentSessionLoadResult.Empty -> {
                                state =
                                    state.copy(
                                        message =
                                            "Seçilen kayıt artık bulunamıyor."
                                    )
                            }
                        }
                    }
            },
            onDeleteRecentSession = {
                sessionId ->
                    scope.launch {
                        val refreshed =
                            withContext(
                                Dispatchers.IO
                            ) {
                                sessionStore.deleteSession(
                                    sessionId
                                )

                                sessionStore.listRecent() to
                                    sessionStore.listArchived()
                            }

                        if (
                            pendingSession
                                ?.sessionId ==
                                sessionId
                        ) {
                            pendingSession =
                                null
                        }

                        recentSessions =
                            refreshed.first

                        archivedSessions =
                            refreshed.second
                    }
            },
            onRenameSession = {
                sessionId,
                name ->
                    scope.launch {
                        val refreshed =
                            withContext(
                                Dispatchers.IO
                            ) {
                                sessionStore.renameSession(
                                    sessionId,
                                    name
                                )

                                sessionStore.listRecent() to
                                    sessionStore.listArchived()
                            }

                        recentSessions =
                            refreshed.first

                        archivedSessions =
                            refreshed.second

                        if (
                            pendingSession
                                ?.sessionId ==
                                sessionId
                        ) {
                            pendingSession =
                                when (
                                    val loaded =
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            sessionStore.loadById(
                                                sessionId
                                            )
                                        }
                                ) {
                                    is AppForgeAgentSessionLoadResult.Loaded ->
                                        loaded.session

                                    else ->
                                        pendingSession
                                }
                        }
                    }
            },
            onTogglePinned = {
                sessionId,
                pinned ->
                    scope.launch {
                        val refreshed =
                            withContext(
                                Dispatchers.IO
                            ) {
                                sessionStore.setPinned(
                                    sessionId,
                                    pinned
                                )

                                sessionStore.listRecent()
                            }

                        recentSessions =
                            refreshed
                    }
            },
            onArchiveSession = {
                sessionId ->
                    scope.launch {
                        val refreshed =
                            withContext(
                                Dispatchers.IO
                            ) {
                                sessionStore.setArchived(
                                    sessionId,
                                    true
                                )

                                sessionStore.listRecent() to
                                    sessionStore.listArchived()
                            }

                        if (
                            pendingSession
                                ?.sessionId ==
                                sessionId
                        ) {
                            pendingSession =
                                null
                        }

                        recentSessions =
                            refreshed.first

                        archivedSessions =
                            refreshed.second
                    }
            },
            onRestoreArchivedSession = {
                sessionId ->
                    scope.launch {
                        val refreshed =
                            withContext(
                                Dispatchers.IO
                            ) {
                                sessionStore.setArchived(
                                    sessionId,
                                    false
                                )

                                sessionStore.listRecent() to
                                    sessionStore.listArchived()
                            }

                        recentSessions =
                            refreshed.first

                        archivedSessions =
                            refreshed.second
                    }
            },
            onRefreshArtifacts = {
                val buildId =
                    state.remoteBuild
                        ?.buildId

                if (!buildId.isNullOrBlank()) {
                    artifactState =
                        artifactState.copy(
                            busy = true,
                            message =
                                "Build logları ve Test Lab yükleniyor..."
                        )

                    scope.launch {
                        val inspected =
                            withContext(
                                Dispatchers.IO
                            ) {
                                artifactClient.inspect(
                                    buildId
                                )
                            }

                        artifactState =
                            inspected

                        val remote =
                            state.remoteBuild
                        val blueprint =
                            state.blueprint

                        if (
                            remote != null &&
                            blueprint != null
                        ) {
                            releaseReviewState =
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    releaseReviewClient.load(
                                        remote = remote,
                                        blueprint = blueprint,
                                        artifacts = inspected
                                    )
                                }
                        }
                    }
                }
            },
            onRefreshReleaseReview = {
                val remote =
                    state.remoteBuild
                val blueprint =
                    state.blueprint

                if (
                    remote != null &&
                    blueprint != null
                ) {
                    releaseReviewState =
                        releaseReviewState.copy(
                            busy = true,
                            message =
                                "Build geçmişi ve release kontrolü yükleniyor..."
                        )

                    scope.launch {
                        releaseReviewState =
                            runCatching {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    releaseReviewClient.load(
                                        remote = remote,
                                        blueprint = blueprint,
                                        artifacts = artifactState
                                    )
                                }
                            }.getOrElse { error ->
                                AppForgeAgentReleaseReviewState(
                                    busy = false,
                                    message =
                                        studioSafeMessage(
                                            error
                                        )
                                )
                            }
                    }
                }
            },
            onDownloadArtifact = { kind ->
                val buildId =
                    state.remoteBuild
                        ?.buildId

                if (!buildId.isNullOrBlank()) {
                    artifactState =
                        artifactState.copy(
                            busy = true,
                            message =
                                "${kind.uppercase()} download hazırlanıyor..."
                        )

                    scope.launch {
                        val result =
                            runCatching {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    artifactClient.enqueueDownload(
                                        buildId = buildId,
                                        kind = kind
                                    )
                                }
                            }

                        artifactState =
                            result.fold(
                                onSuccess = { downloadId ->
                                    artifactState.copy(
                                        busy = false,
                                        lastDownloadId =
                                            downloadId,
                                        message =
                                            "${kind.uppercase()} indirme kuyruğuna eklendi."
                                    )
                                },
                                onFailure = { error ->
                                    artifactState.copy(
                                        busy = false,
                                        message =
                                            studioSafeMessage(
                                                error
                                            )
                                    )
                                }
                            )
                    }
                }
            },
            onExportSource = {
                val path =
                    lastWorkspacePath

                val blueprint =
                    state.blueprint

                if (
                    !path.isNullOrBlank() &&
                    blueprint != null
                ) {
                    artifactState =
                        artifactState.copy(
                            busy = true,
                            message =
                                "Üretilen kaynak ZIP hazırlanıyor..."
                        )

                    scope.launch {
                        val result =
                            runCatching {
                                withContext(
                                    Dispatchers.IO
                                ) {
                                    AppForgeAgentSourceExporter.export(
                                        context = context,
                                        workspace =
                                            java.io.File(
                                                path
                                            ),
                                        blueprint = blueprint
                                    )
                                }
                            }

                        artifactState =
                            result.fold(
                                onSuccess = { location ->
                                    artifactState.copy(
                                        busy = false,
                                        message =
                                            "Kaynak ZIP hazır: $location"
                                    )
                                },
                                onFailure = { error ->
                                    artifactState.copy(
                                        busy = false,
                                        message =
                                            studioSafeMessage(
                                                error
                                            )
                                    )
                                }
                            )
                    }
                }
            },
            onPromptChange = { prompt ->
                state = state.copy(
                    prompt = prompt.take(4_000),
                    message = ""
                )
            },
            onPlatformChange = { platform ->
                artifactState =
                    AppForgeAgentArtifactState()
                releaseReviewState =
                    AppForgeAgentReleaseReviewState()
                lastWorkspacePath =
                    null

                state = AppForgeAgentStudioState(
                    prompt = state.prompt,
                    platform = platform
                )
            },
            onGenerateBlueprint = {
                if (state.canGenerate) {
                    scope.launch {
                        state = state.copy(
                            busy = true,
                            message = "Yerel AI Blueprint oluşturuyor..."
                        )

                        state = runCatching {
                            val model = LocalAiModelStore.load(context)
                                ?: error(
                                    "Yerel AI modeli kurulu değil. Önce AI bölümünden modeli kur."
                                )

                            if (!assistant.isReady) {
                                assistant.initialize(
                                    model = model,
                                    requestedBackend = model.backend
                                )
                            }

                            val promptContract = AppForgeAgentBlueprintPrompt.build(
                                userPrompt = state.prompt.trim(),
                                preferredPlatform = state.platform
                            )

                            val raw = assistant.generateStructuredJson(
                                promptContract
                            )
                            val blueprint =
                                AppForgeAgentBlueprintJson.parse(
                                    raw = raw,
                                    fallbackPlatform = state.platform
                                )

                            require(blueprint.platform == state.platform) {
                                "AI Blueprint platformu seçilen platform ile eşleşmiyor."
                            }

                            val validation =
                                AppForgeAgentBlueprintValidator.validate(blueprint)

                            require(validation.valid) {
                                validation.issues
                                    .filter {
                                        it.level == AgentBlueprintIssueLevel.ERROR
                                    }
                                    .joinToString(" | ") {
                                        "${it.field}: ${it.message}"
                                    }
                            }

                            state.copy(
                                step = AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
                                blueprint = blueprint,
                                validation = validation,
                                autonomousResult = null,
                                busy = false,
                                message =
                                    "Blueprint hazır. İncele veya Visual Designer'da düzenle."
                            )
                        }.getOrElse { error ->
                            state.copy(
                                step = AppForgeAgentStudioStep.PROMPT,
                                busy = false,
                                message = studioSafeMessage(error)
                            )
                        }
                    }
                }
            },
            onOpenDesigner = {
                val blueprint = state.blueprint
                if (blueprint != null) {
                    val validation =
                        AppForgeAgentBlueprintValidator.validate(blueprint)

                    state = state.copy(
                        validation = validation,
                        step =
                            if (validation.valid) {
                                AppForgeAgentStudioStep.DESIGN
                            } else {
                                AppForgeAgentStudioStep.BLUEPRINT_REVIEW
                            },
                        message =
                            if (validation.valid) {
                                "Visual Designer aktif."
                            } else {
                                "Blueprint doğrulaması başarısız."
                            }
                    )
                }
            },
            onBlueprintChange = { blueprint ->
                val validation =
                    AppForgeAgentBlueprintValidator.validate(blueprint)

                state = state.copy(
                    blueprint = blueprint,
                    validation = validation,
                    autonomousResult = null,
                    message =
                        if (validation.valid) {
                            "Tasarım değişiklikleri doğrulandı."
                        } else {
                            "Blueprint içinde düzeltilmesi gereken alanlar var."
                        }
                )
            },
            onBackToReview = {
                state = state.copy(
                    step = AppForgeAgentStudioStep.BLUEPRINT_REVIEW,
                    message = ""
                )
            },
            onBuild = {
                val buildInput =
                    state.copy(
                        busy = false,
                        remoteBuild = null
                    )

                val buildSessionId =
                    currentSessionId

                val previousWorkspacePath =
                    lastWorkspacePath

                val buildAllowed =
                    state.canBuild ||
                        state.canRebuild

                if (buildAllowed) {
                    artifactState =
                        AppForgeAgentArtifactState(
                            busy = false,
                            message =
                                "Build devam ediyor. Artifact inceleme build sonunda açılacak."
                        )

                    releaseReviewState =
                        AppForgeAgentReleaseReviewState(
                            busy = true,
                            message =
                                "Yeni build tamamlanınca release kontrolü çalışacak."
                        )

                    lastWorkspacePath =
                        null

                    state = state.copy(
                        step = AppForgeAgentStudioStep.BUILD,
                        busy = true,
                        remoteBuild = null,
                        message =
                            "TEST → cihazda BUILD → güvenli repair hazırlanıyor..."
                    )

                    scope.launch {
                        val finalState = runCatching {
                            withContext(Dispatchers.IO) {
                                val model =
                                    LocalAiModelStore.load(context)
                                        ?: error(
                                            "Yerel AI modeli kurulu değil. Repair için modeli kur."
                                        )

                                if (!assistant.isReady) {
                                    assistant.initialize(
                                        model = model,
                                        requestedBackend = model.backend
                                    )
                                }

                                val blueprint =
                                    buildInput.blueprint
                                        ?: error(
                                            "Build için Blueprint bulunamadı."
                                        )

                                val quality =
                                    AppForgeAgentQualityGate.assess(
                                        blueprint
                                    )

                                require(
                                    quality.pass
                                ) {
                                    quality.findings
                                        .filter {
                                            it.level ==
                                                com.appforge.studio.ai.AppForgeAgentQualityLevel.ERROR
                                        }
                                        .joinToString(
                                            prefix = "V14 Quality Gate BLOCKED: ",
                                            separator = " | "
                                        ) {
                                            "${it.code}: ${it.message}"
                                        }
                                }

                                previousWorkspacePath
                                    ?.takeIf {
                                        it.isNotBlank()
                                    }
                                    ?.let {
                                        previousPath ->
                                            runCatching {
                                                projectMemoryStore.createCheckpoint(
                                                    sessionId =
                                                        buildSessionId,
                                                    workspace =
                                                        File(
                                                            previousPath
                                                        ),
                                                    label =
                                                        "before-rebuild"
                                                )
                                            }
                                    }

                                val workspace =
                                    AppForgeAgentWorkspaceStore.create(
                                        filesDir = context.filesDir,
                                        appName = blueprint.appName
                                    )

                                withContext(
                                    Dispatchers.Main
                                ) {
                                    lastWorkspacePath =
                                        workspace.absolutePath
                                }

                                val runner =
                                    AppForgeAgentBuildServiceStageRunner(
                                        context = context,
                                        blueprint = blueprint,
                                        buildServiceUrl = buildServiceUrl,
                                        buildApiKey = buildApiKey
                                    ) { remote, message ->
                                        scope.launch {
                                            state = state.copy(
                                                step = AppForgeAgentStudioStep.BUILD,
                                                busy = true,
                                                remoteBuild =
                                                    remote ?: state.remoteBuild,
                                                message = message
                                            )
                                        }
                                    }

                                val patchProvider =
                                    AppForgeAgentLocalPatchProvider(
                                        assistant = assistant,
                                        workspace = workspace
                                    )

                                val orchestrator =
                                    AppForgeAgentStudioOrchestrator(
                                        blueprintProvider =
                                            AppForgeAgentBlueprintProvider {
                                                AppForgeAgentBlueprintJson.encode(
                                                    blueprint
                                                )
                                            },
                                        stageRunner = runner,
                                        patchProvider = patchProvider
                                    )

                                val built =
                                    orchestrator.build(
                                        state = buildInput,
                                        request =
                                            AppForgeAgentStudioBuildRequest(
                                                workspace = workspace,
                                                maxRepairAttempts =
                                                    AppForgeAgentQualityGate
                                                        .boundedRepairBudget(
                                                            blueprint =
                                                                blueprint,
                                                            requestedAttempts =
                                                                blueprint
                                                                    .maxRepairAttempts
                                                        ),
                                                rollbackOnFailure = true
                                            )
                                    ).copy(
                                        busy = false,
                                        remoteBuild = runner.lastBuild
                                    )

                                if (
                                    built.step ==
                                        AppForgeAgentStudioStep.RESULT
                                ) {
                                    runCatching {
                                        projectMemoryStore.createCheckpoint(
                                            sessionId =
                                                buildSessionId,
                                            workspace =
                                                workspace,
                                            label =
                                                "build-success"
                                        )
                                    }
                                }

                                built.copy(
                                    message =
                                        "${built.message} • ${quality.summary}"
                                            .take(
                                                2_000
                                            )
                                )
                            }
                        }.getOrElse { error ->
                            buildInput.copy(
                                step = AppForgeAgentStudioStep.BLOCKED,
                                busy = false,
                                message = studioSafeMessage(error)
                            )
                        }

                        state = finalState

                        finalState.remoteBuild
                            ?.buildId
                            ?.takeIf {
                                it.isNotBlank()
                            }
                            ?.let { buildId ->
                                artifactState =
                                    artifactState.copy(
                                        busy = true,
                                        message =
                                            "Build logları ve Test Lab yükleniyor..."
                                    )

                                val inspected =
                                    withContext(
                                        Dispatchers.IO
                                    ) {
                                        artifactClient.inspect(
                                            buildId
                                        )
                                    }

                                artifactState =
                                    inspected

                                val blueprint =
                                    finalState.blueprint

                                val remote =
                                    finalState.remoteBuild

                                if (
                                    blueprint != null &&
                                    remote != null
                                ) {
                                    val loadedReleaseReview =
                                        withContext(
                                            Dispatchers.IO
                                        ) {
                                            releaseReviewClient.load(
                                                remote = remote,
                                                blueprint = blueprint,
                                                artifacts = inspected
                                            )
                                        }

                                    releaseReviewState =
                                        loadedReleaseReview

                                    val finalAcceptance =
                                        AppForgeAgentFinalAcceptance.evaluate(
                                            state = finalState,
                                            artifacts = inspected,
                                            releaseReviewState =
                                                loadedReleaseReview,
                                            workspacePath =
                                                lastWorkspacePath
                                        )

                                    state =
                                        state.copy(
                                            message =
                                                "${state.message} • ${finalAcceptance.summary}"
                                                    .take(
                                                        2_000
                                                    )
                                        )
                                }
                            }
                    }
                }
            },
            onReset = {
                artifactState =
                    AppForgeAgentArtifactState()

                releaseReviewState =
                    AppForgeAgentReleaseReviewState()

                pendingSession =
                    null

                currentSessionId =
                    UUID.randomUUID()
                        .toString()

                lastWorkspacePath =
                    null

                state = AppForgeAgentStudioState(
                    platform = state.platform
                )

                scope.launch(
                    Dispatchers.IO
                ) {
                    sessionStore.clear()
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
        )
    }
}

private fun studioSafeMessage(
    error: Throwable
): String {
    val raw =
        error.message
            ?: error::class.simpleName
            ?: "Unified Agent işlemi başarısız."

    val friendly =
        if (
            raw.contains("FAILED_PRECONDITION", ignoreCase = true) &&
            (
                raw.contains("session already exists", ignoreCase = true) ||
                raw.contains("one session is supported", ignoreCase = true)
            )
        ) {
            "Yerel AI oturumu çakıştı. Lütfen işlemi yeniden deneyin."
        } else if (
            raw.contains(
                "Input token ids are too long",
                ignoreCase = true
            ) ||
            (
                raw.contains(
                    "maximum number of tokens allowed",
                    ignoreCase = true
                ) &&
                raw.contains(
                    "token",
                    ignoreCase = true
                )
            )
        ) {
            "Uygulama açıklaması yerel AI bağlam sınırını aştı. Açıklamayı kısaltıp tekrar deneyin."
        } else {
            raw
        }

    return friendly
        .replace(
            Regex("ghp_[A-Za-z0-9]{20,}"),
            "[REDACTED]"
        )
        .replace(
            Regex("github_pat_[A-Za-z0-9_]{20,}"),
            "[REDACTED]"
        )
        .replace(
            Regex("\\bsk-[A-Za-z0-9_-]{20,}\\b"),
            "[REDACTED]"
        )
        .take(2_000)
}
