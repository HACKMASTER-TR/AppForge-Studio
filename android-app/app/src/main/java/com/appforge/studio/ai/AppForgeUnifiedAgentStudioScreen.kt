package com.appforge.studio.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun AppForgeUnifiedAgentStudioScreen(
    state: AppForgeAgentStudioState,
    artifactState: AppForgeAgentArtifactState,
    releaseReviewState: AppForgeAgentReleaseReviewState,
    resumeInfo: AppForgeAgentSessionResumeInfo?,
    recentSessions: List<AppForgeAgentSessionResumeInfo>,
    archivedSessions: List<AppForgeAgentSessionResumeInfo>,
    recoveryAssessment: AppForgeAgentRecoveryAssessment?,
    canExportSource: Boolean,
    onResumeSession: () -> Unit,
    onDiscardSession: () -> Unit,
    onSelectRecentSession: (String) -> Unit,
    onDeleteRecentSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onArchiveSession: (String) -> Unit,
    onRestoreArchivedSession: (String) -> Unit,
    onRefreshArtifacts: () -> Unit,
    onRefreshReleaseReview: () -> Unit,
    onDownloadArtifact: (String) -> Unit,
    onExportSource: () -> Unit,
    onPromptChange: (String) -> Unit,
    onPlatformChange: (AppForgeAgentPlatform) -> Unit,
    onGenerateBlueprint: () -> Unit,
    onOpenDesigner: () -> Unit,
    onBlueprintChange: (AppForgeAgentBlueprint) -> Unit,
    onBackToReview: () -> Unit,
    onBuild: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (state.step) {
        AppForgeAgentStudioStep.PROMPT -> PromptStep(
            state = state,
            resumeInfo = resumeInfo,
            recentSessions = recentSessions,
            archivedSessions = archivedSessions,
            recoveryAssessment = recoveryAssessment,
            onResumeSession = onResumeSession,
            onDiscardSession = onDiscardSession,
            onSelectRecentSession = onSelectRecentSession,
            onDeleteRecentSession = onDeleteRecentSession,
            onRenameSession = onRenameSession,
            onTogglePinned = onTogglePinned,
            onArchiveSession = onArchiveSession,
            onRestoreArchivedSession = onRestoreArchivedSession,
            onPromptChange = onPromptChange,
            onPlatformChange = onPlatformChange,
            onGenerateBlueprint = onGenerateBlueprint,
            modifier = modifier
        )

        AppForgeAgentStudioStep.BLUEPRINT_REVIEW -> BlueprintReviewStep(
            state = state,
            onOpenDesigner = onOpenDesigner,
            onBuild = onBuild,
            onReset = onReset,
            modifier = modifier
        )

        AppForgeAgentStudioStep.DESIGN -> {
            val blueprint = state.blueprint
            if (blueprint == null) {
                StatusStep(
                    title = "Blueprint bulunamadı",
                    message = state.message,
                    onReset = onReset,
                    modifier = modifier
                )
            } else {
                Column(
                    modifier = modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = onBackToReview) {
                            Text("Özete dön")
                        }
                        Button(
                            onClick = onBuild,
                            enabled = state.validation?.valid == true
                        ) {
                            Text("Derlemeyi başlat")
                        }
                    }
                    AppForgeAgentVisualDesignerScreen(
                        initialBlueprint = blueprint,
                        onBlueprintChange = onBlueprintChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AppForgeAgentStudioStep.BUILD -> BuildProgressStep(
            state = state,
            modifier = modifier
        )

        AppForgeAgentStudioStep.RESULT -> ResultStep(
            state = state,
            artifactState = artifactState,
            releaseReviewState = releaseReviewState,
            canExportSource = canExportSource,
            onRefreshArtifacts = onRefreshArtifacts,
            onRefreshReleaseReview = onRefreshReleaseReview,
            onDownloadArtifact = onDownloadArtifact,
            onExportSource = onExportSource,
            onRebuild = onBuild,
            onReset = onReset,
            modifier = modifier
        )

        AppForgeAgentStudioStep.BLOCKED ->
            if (state.remoteBuild != null) {
                ResultStep(
                    state = state,
                    artifactState = artifactState,
                    releaseReviewState = releaseReviewState,
                    canExportSource = false,
                    onRefreshArtifacts = onRefreshArtifacts,
                    onRefreshReleaseReview = onRefreshReleaseReview,
                    onDownloadArtifact = onDownloadArtifact,
                    onExportSource = onExportSource,
                    onRebuild = onBuild,
                    onReset = onReset,
                    modifier = modifier
                )
            } else {
                StatusStep(
                    title = "İşlem engellendi",
                    message = state.message,
                    onReset = onReset,
                    modifier = modifier
                )
            }
    }
}

@Composable
private fun PromptStep(
    state: AppForgeAgentStudioState,
    resumeInfo: AppForgeAgentSessionResumeInfo?,
    recentSessions: List<AppForgeAgentSessionResumeInfo>,
    archivedSessions: List<AppForgeAgentSessionResumeInfo>,
    recoveryAssessment: AppForgeAgentRecoveryAssessment?,
    onResumeSession: () -> Unit,
    onDiscardSession: () -> Unit,
    onSelectRecentSession: (String) -> Unit,
    onDeleteRecentSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit,
    onTogglePinned: (String, Boolean) -> Unit,
    onArchiveSession: (String) -> Unit,
    onRestoreArchivedSession: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onPlatformChange: (AppForgeAgentPlatform) -> Unit,
    onGenerateBlueprint: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "AI ile Uygulama / Oyun Oluştur",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "İstediğin uygulamayı veya oyunu normal dille anlat. Yerel AI güvenli Blueprint oluşturur; Web hedefi cihazda APK ve hazırsa Portable EXE üretebilir.",
            style = MaterialTheme.typography.bodyMedium
        )

        recoveryAssessment?.let { recovery ->
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            14.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            6.dp
                        )
                ) {
                    Text(
                        "Kurtarma Merkezi",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )

                    Text(
                        recovery.headline,
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )

                    recovery.issues
                        .take(8)
                        .forEach { issue ->
                            Text(
                                "${issue.severity}: ${issue.title}",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium
                            )

                            Text(
                                issue.detail,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                    if (
                        !recovery.safeToOpen
                    ) {
                        Text(
                            "Bu kayıt otomatik olarak devam ettirilmez. Yeni build başlatılmaz.",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }
                }
            }
        }

        resumeInfo?.let { resume ->
            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier =
                        Modifier.padding(
                            14.dp
                        ),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    Text(
                        "Kaldığın yerden devam et",
                        style =
                            MaterialTheme
                                .typography
                                .titleMedium
                    )
                    Text(
                        resume.title,
                        style =
                            MaterialTheme
                                .typography
                                .bodyLarge
                    )
                    Text(
                        "${resume.platform} • ${resume.step}",
                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    resume.buildId?.let {
                        Text(
                            "Cloud Build: $it",
                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall
                        )
                    }

                    Button(
                        onClick =
                            onResumeSession,
                        enabled =
                            recoveryAssessment
                                ?.safeToOpen
                                ?: true,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Devam et"
                        )
                    }

                    OutlinedButton(
                        onClick =
                            onDiscardSession,
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Kaydı sil ve yeni başla"
                        )
                    }
                }
            }
        }

        if (recentSessions.isNotEmpty()) {
            Text(
                "Son çalışmalar",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            recentSessions
                .take(8)
                .forEach { recent ->
                    var renameText by remember(
                        recent.sessionId,
                        recent.title
                    ) {
                        mutableStateOf(
                            recent.title
                        )
                    }

                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(
                                    14.dp
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    6.dp
                                )
                        ) {
                            Text(
                                if (recent.pinned) {
                                    "📌 ${recent.title}"
                                } else {
                                    recent.title
                                },
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyLarge
                            )
                            Text(
                                "${recent.platform} • ${recent.step}",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )

                            recent.buildId?.let {
                                Text(
                                    "Build: $it",
                                    style =
                                        MaterialTheme
                                            .typography
                                            .bodySmall
                                )
                            }

                            OutlinedTextField(
                                value =
                                    renameText,
                                onValueChange = {
                                    renameText =
                                        it.take(
                                            80
                                        )
                                },
                                modifier =
                                    Modifier.fillMaxWidth(),
                                singleLine =
                                    true,
                                label = {
                                    Text(
                                        "Yeniden adlandır"
                                    )
                                }
                            )

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    )
                            ) {
                                Button(
                                    onClick = {
                                        onSelectRecentSession(
                                            recent.sessionId
                                        )
                                    }
                                ) {
                                    Text(
                                        "Aç"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        onRenameSession(
                                            recent.sessionId,
                                            renameText
                                        )
                                    },
                                    enabled =
                                        renameText
                                            .trim()
                                            .isNotBlank()
                                ) {
                                    Text(
                                        "Kaydet"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        onTogglePinned(
                                            recent.sessionId,
                                            !recent.pinned
                                        )
                                    }
                                ) {
                                    Text(
                                        if (
                                            recent.pinned
                                        ) {
                                            "Sabiti kaldır"
                                        } else {
                                            "Sabitle"
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    )
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        onArchiveSession(
                                            recent.sessionId
                                        )
                                    }
                                ) {
                                    Text(
                                        "Arşivle"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        onDeleteRecentSession(
                                            recent.sessionId
                                        )
                                    }
                                ) {
                                    Text(
                                        "Sil"
                                    )
                                }
                            }
                        }
                    }
                }
        }

        if (archivedSessions.isNotEmpty()) {
            Text(
                "Arşiv",
                style =
                    MaterialTheme
                        .typography
                        .titleMedium
            )

            archivedSessions
                .take(8)
                .forEach { archived ->
                    Card(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier =
                                Modifier.padding(
                                    14.dp
                                ),
                            verticalArrangement =
                                Arrangement.spacedBy(
                                    6.dp
                                )
                        ) {
                            Text(
                                archived.title,
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyLarge
                            )
                            Text(
                                "${archived.platform} • ${archived.step}",
                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )

                            Row(
                                modifier =
                                    Modifier.fillMaxWidth(),
                                horizontalArrangement =
                                    Arrangement.spacedBy(
                                        8.dp
                                    )
                            ) {
                                Button(
                                    onClick = {
                                        onRestoreArchivedSession(
                                            archived.sessionId
                                        )
                                    }
                                ) {
                                    Text(
                                        "Geri yükle"
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        onDeleteRecentSession(
                                            archived.sessionId
                                        )
                                    }
                                ) {
                                    Text(
                                        "Sil"
                                    )
                                }
                            }
                        }
                    }
                }
        }

        OutlinedTextField(
            value = state.prompt,
            onValueChange = onPromptChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 5,
            label = { Text("Uygulama açıklaması") },
            supportingText = {
                Text("${state.prompt.length}/4000")
            }
        )

        Text("Hedef platform", style = MaterialTheme.typography.titleMedium)
        AppForgeAgentPlatform.entries.forEach { platform ->
            OutlinedButton(
                onClick = { onPlatformChange(platform) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (platform == state.platform) {
                        "✓ ${platform.title}"
                    } else {
                        platform.title
                    }
                )
            }
        }

        Button(
            onClick = onGenerateBlueprint,
            enabled = state.canGenerate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (state.busy) {
                    "Blueprint oluşturuluyor..."
                } else {
                    "Blueprint oluştur"
                }
            )
        }

        if (state.message.isNotBlank()) {
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun BlueprintReviewStep(
    state: AppForgeAgentStudioState,
    onOpenDesigner: () -> Unit,
    onBuild: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier
) {
    val blueprint = state.blueprint
    var showBlueprintDetails by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Uygulama özeti", style = MaterialTheme.typography.headlineSmall)
        if (blueprint == null) {
            Text("Blueprint bulunamadı.")
            OutlinedButton(onClick = onReset) { Text("Başa dön") }
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(blueprint.appName, style = MaterialTheme.typography.titleLarge)
                Text(blueprint.platform.title)
                OutlinedButton(onClick = { showBlueprintDetails = !showBlueprintDetails }) {
                    Text(if (showBlueprintDetails) "Teknik ayrıntıları gizle" else "Teknik ayrıntılar")
                }
                if (showBlueprintDetails) {
                    Text("Ekran: ${blueprint.screens.size}")
                    Text("Başlangıç: ${blueprint.startRoute}")
                    Text("Repair sınırı: ${blueprint.maxRepairAttempts}")
                }
            }
        }

        state.validation?.issues?.forEach { issue ->
            Text(
                "${issue.level}: ${issue.field} — ${issue.message}",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (state.message.isNotBlank()) {
            Text(state.message, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = onOpenDesigner,
            enabled = state.canDesign,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Görünümü düzenle")
        }
        Button(
            onClick = onBuild,
            enabled = state.canBuild,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Uygulamayı oluştur")
        }
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Yeni açıklama")
        }
    }
}

@Composable
private fun BuildProgressStep(
    state: AppForgeAgentStudioState,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text("Unified Agent çalışıyor", style = MaterialTheme.typography.headlineSmall)
        Text(state.message.ifBlank { "Planlama → Test → Build → Repair" })
        state.remoteBuild?.let { remote ->
            Text(
                "Cihazda oluşturuluyor • %${remote.progress}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            "Deploy bu aşamada otomatik yapılmaz.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ResultStep(
    state: AppForgeAgentStudioState,
    artifactState: AppForgeAgentArtifactState,
    releaseReviewState: AppForgeAgentReleaseReviewState,
    canExportSource: Boolean,
    onRefreshArtifacts: () -> Unit,
    onRefreshReleaseReview: () -> Unit,
    onDownloadArtifact: (String) -> Unit,
    onExportSource: () -> Unit,
    onRebuild: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier
) {
    val result = state.autonomousResult
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        var technicalDetails by remember { mutableStateOf(false) }
        Text("Oluşturma sonucu", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (state.remoteBuild?.status.equals("success", ignoreCase = true)) {
                "Derleme tamamlandı. Dosyalarını aşağıdan indirebilirsin."
            } else state.message
        )
        if (artifactState.message.isNotBlank()) {
            Text(artifactState.message, style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(
            onClick = { technicalDetails = !technicalDetails },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (technicalDetails) "Teknik ayrıntıları gizle" else "Teknik ayrıntılar")
        }

        state.remoteBuild?.let { remote ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Cihazda oluşturuldu",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        remote.buildNo
                            ?.let { "Build #$it • ${remote.status}" }
                            ?: remote.status
                    )
                    if (technicalDetails) {
                        Text("Build ID: ${remote.buildId}")
                        Text("İlerleme: %${remote.progress}")
                    }
                    Text(
                        "APK: ${if (remote.apkAvailable) "Hazır" else "—"} • " +
                            "AAB: ${if (remote.aabAvailable) "Hazır" else "—"} • " +
                            "EXE: ${if (remote.exeAvailable) "Hazır" else "—"}"
                    )
                }
            }
        }

        state.remoteBuild?.let { remote ->
            Text(
                "İndirilebilir dosyalar",
                style = MaterialTheme.typography.titleMedium
            )

            if (remote.apkAvailable) {
                Button(
                    onClick = {
                        onDownloadArtifact("apk")
                    },
                    enabled = !artifactState.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("APK indir")
                }
            }

            if (remote.aabAvailable) {
                Button(
                    onClick = {
                        onDownloadArtifact("aab")
                    },
                    enabled = !artifactState.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("AAB indir")
                }
            }

            if (remote.exeAvailable) {
                Button(
                    onClick = {
                        onDownloadArtifact("exe")
                    },
                    enabled = !artifactState.busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Portable EXE indir")
                }
            }

            if (technicalDetails) {
            OutlinedButton(
                onClick = onRefreshArtifacts,
                enabled = !artifactState.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (artifactState.busy) {
                        "Log/Test Lab yükleniyor..."
                    } else {
                        "Log + Test Lab yenile"
                    }
                )
            }

            OutlinedButton(
                onClick = onExportSource,
                enabled =
                    canExportSource &&
                        !artifactState.busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Üretilen kaynak ZIP'i dışa aktar")
            }
            }
        }

        if (technicalDetails) {
        OutlinedButton(
            onClick = onRefreshReleaseReview,
            enabled =
                !artifactState.busy &&
                    !releaseReviewState.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (releaseReviewState.busy) {
                    "Release kontrolü yükleniyor..."
                } else {
                    "Build geçmişi + release kontrolünü yenile"
                }
            )
        }

        Button(
            onClick = onRebuild,
            enabled =
                state.canRebuild &&
                    !releaseReviewState.busy &&
                    !artifactState.busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Yeniden oluştur")
        }

        ReleaseReviewCard(
            review = releaseReviewState
        )

        artifactState.apk?.let { report ->
            ArtifactReportCard(report)
        }

        artifactState.aab?.let { report ->
            ArtifactReportCard(report)
        }

        if (artifactState.security.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Test Lab • Güvenlik",
                        style = MaterialTheme.typography.titleMedium
                    )

                    artifactState.security
                        .take(20)
                        .forEach { finding ->
                            Text(
                                "${finding.severity}: ${finding.title}",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            if (finding.detail.isNotBlank()) {
                                Text(
                                    finding.detail,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                }
            }
        }

        if (artifactState.logs.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Build Logları",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        artifactState.logs
                            .takeLast(60)
                            .joinToString("\n")
                            .takeLast(16_000),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        result?.let {
            Text("Durum: ${it.status}")
            Text("Deploy gate: ${it.deployGate}")
            Text("Checkpoint: ${it.checkpoints.size}")
            Text("Olaylar", style = MaterialTheme.typography.titleMedium)
            it.events.forEach { event ->
                Text(
                    "${event.stage}: ${event.message}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        }

        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Yeni uygulama")
        }
    }
}



@Composable
private fun ReleaseReviewCard(
    review: AppForgeAgentReleaseReviewState
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Release Ready Kontrolü",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                if (review.readiness.ready) {
                    "Teknik durum: PASS"
                } else {
                    "Teknik durum: BLOCKED"
                }
            )

            Text(
                "Deploy gate: REVIEW_REQUIRED",
                style = MaterialTheme.typography.bodySmall
            )

            review.readiness.checks.forEach { check ->
                Text(
                    "✓ $check",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            review.readiness.blockers.forEach { blocker ->
                Text(
                    "• $blocker",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (review.message.isNotBlank()) {
                Text(
                    review.message,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (review.history.isNotEmpty()) {
                Text(
                    "Build Geçmişi",
                    style = MaterialTheme.typography.titleMedium
                )

                review.history
                    .take(8)
                    .forEach { item ->
                        Text(
                            item.buildNo
                                ?.let {
                                    "#$it • ${item.status}"
                                }
                                ?: item.status,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
            }

            review.comparison?.let { comparison ->
                Text(
                    "Önceki SUCCESS build karşılaştırması",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Değişiklik: ${comparison.changeCount} • " +
                        "APK Δ ${AppForgeAgentArtifactSafety.formatBytesSigned(comparison.apkDeltaBytes)} • " +
                        "AAB Δ ${AppForgeAgentArtifactSafety.formatBytesSigned(comparison.aabDeltaBytes)}",
                    style = MaterialTheme.typography.bodySmall
                )

                comparison.changes
                    .take(12)
                    .forEach { change ->
                        Text(
                            change,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
            }

            if (review.releaseNotes.isNotEmpty()) {
                Text(
                    "Release Notları",
                    style = MaterialTheme.typography.titleMedium
                )

                review.releaseNotes
                    .take(12)
                    .forEach { note ->
                        Text(
                            "• $note",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
            }
        }
    }
}


@Composable
private fun ArtifactReportCard(
    report: AppForgeAgentArtifactReport
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                report.kind.uppercase(),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Dosya: " +
                    AppForgeAgentArtifactSafety.formatBytes(
                        report.fileSizeBytes
                    )
            )
            Text(
                "Açılmış boyut: " +
                    AppForgeAgentArtifactSafety.formatBytes(
                        report.uncompressedBytes
                    )
            )
            Text(
                "Girdi: ${report.entryCount}"
            )
        }
    }
}


@Composable
private fun StatusStep(
    title: String,
    message: String,
    onReset: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message.ifBlank { "İşlem tamamlanamadı." })
        OutlinedButton(onClick = onReset) {
            Text("Başa dön")
        }
    }
}
