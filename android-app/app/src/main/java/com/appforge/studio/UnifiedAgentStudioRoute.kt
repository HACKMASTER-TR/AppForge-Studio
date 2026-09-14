@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.appforge.studio

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.appforge.studio.ai.AppForgeUnifiedAgentStudioScreen
import com.appforge.studio.ai.AppForgeLocalAssistant
import com.appforge.studio.ai.LocalAiModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
            canExportSource =
                lastWorkspacePath != null &&
                    state.step == AppForgeAgentStudioStep.RESULT,
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
                            val blueprint = AppForgeAgentBlueprintJson.parse(raw)

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
                            "TEST → AppForge Cloud BUILD → güvenli repair hazırlanıyor..."
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

                                orchestrator.build(
                                    state = buildInput,
                                    request =
                                        AppForgeAgentStudioBuildRequest(
                                            workspace = workspace,
                                            maxRepairAttempts =
                                                blueprint.maxRepairAttempts,
                                            rollbackOnFailure = true
                                        )
                                ).copy(
                                    busy = false,
                                    remoteBuild = runner.lastBuild
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
                }
            },
            onReset = {
                artifactState =
                    AppForgeAgentArtifactState()

                releaseReviewState =
                    AppForgeAgentReleaseReviewState()

                lastWorkspacePath =
                    null

                state = AppForgeAgentStudioState(
                    platform = state.platform
                )
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
): String =
    (error.message ?: error::class.simpleName ?: "Unified Agent işlemi başarısız.")
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
