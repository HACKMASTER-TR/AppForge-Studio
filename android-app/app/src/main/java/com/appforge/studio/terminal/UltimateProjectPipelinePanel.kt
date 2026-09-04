package com.appforge.studio.terminal

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun UltimateProjectPipelinePanel(
    workspace: File,
    plan: UltimateProjectAutomationPlan,
    distribution: LinuxDistribution,
    selectedToolchains: Set<LinuxToolchainId>,
    onOpenDeployment: () -> Unit,
    onAiHandoff: (String) -> Unit
) {
    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val engine =
        remember {
            UltimateProjectPipelineEngine(
                context.applicationContext
            )
        }

    var health by
        remember(
            workspace.absolutePath,
            distribution,
            selectedToolchains
        ) {
            mutableStateOf<
                ProjectHealthReport?
                >(null)
        }

    var result by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf<
                ProjectPipelineRunResult?
                >(null)
        }

    var busy by
        remember {
            mutableStateOf(false)
        }

    var confirmPipeline by
        remember {
            mutableStateOf(false)
        }

    var message by
        remember(
            workspace.absolutePath
        ) {
            mutableStateOf(
                "Pipeline hazır. Önce sağlık kontrolü yapabilir veya tek akış pipeline'ı onaylayabilirsin."
            )
        }

    fun runHealth() {
        if (busy) {
            return
        }

        busy = true

        scope.launch {
            runCatching {
                engine.checkHealth(
                    workspace = workspace,
                    distribution = distribution,
                    plan = plan,
                    selectedToolchains =
                        selectedToolchains
                )
            }
                .onSuccess {
                    health = it
                    message =
                        if (
                            it.missingCommands
                                .isEmpty()
                        ) {
                            "Sağlık kontrolü tamamlandı; gerekli Linux araçları hazır."
                        } else {
                            "Eksik araçlar: ${it.missingCommands.joinToString()}"
                        }
                }
                .onFailure {
                    message =
                        "Sağlık kontrolü başarısız: ${it.message ?: "Bilinmeyen hata"}"
                }

            busy = false
        }
    }

    if (confirmPipeline) {
        AlertDialog(
            onDismissRequest = {
                if (!busy) {
                    confirmPipeline = false
                }
            },
            title = {
                Text(
                    "Install → Test → Build pipeline"
                )
            },
            text = {
                Column(
                    verticalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        )
                ) {
                    Text(
                        "Tek onayla şu zincir çalışacak: sağlık kontrolü → eksik araç zincirlerini kur → proje bağımlılıklarını kur → test → build → Deployment Merkezi onay kapısı."
                    )

                    Text(
                        "Gerçek deploy bu zincirde sessizce başlamaz. Build başarılı olursa 5B Deployment Merkezi açılır ve sağlayıcıya özel son deploy onayı yine kullanıcıdan alınır.",
                        color =
                            TerminalSecondary,
                        fontSize =
                            10.sp
                    )

                    Text(
                        "Linux: ${distribution.title}\nAraç grupları: ${selectedToolchains.joinToString { it.name }}",
                        color =
                            TerminalMuted,
                        fontFamily =
                            FontFamily.Monospace,
                        fontSize =
                            9.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmPipeline = false

                        if (busy) {
                            return@Button
                        }

                        busy = true
                        result = null

                        scope.launch {
                            runCatching {
                                engine.run(
                                    workspace = workspace,
                                    distribution = distribution,
                                    plan = plan,
                                    selectedToolchains =
                                        selectedToolchains,
                                    confirmed = true
                                )
                            }
                                .onSuccess { pipeline ->
                                    result = pipeline
                                    health = pipeline.health

                                    if (pipeline.success) {
                                        message =
                                            "Install/Test/Build pipeline tamamlandı. Deploy onay kapısı açılıyor."
                                    } else {
                                        message =
                                            "Pipeline ilk başarısız adımda durdu. Maskelenmiş hata paketi Ultimate AI'ya hazırlandı."
                                    }
                                }
                                .onFailure { error ->
                                    message =
                                        "Pipeline başlatılamadı: ${error.message ?: "Bilinmeyen hata"}"
                                }

                            busy = false

                            val completed =
                                result

                            if (
                                completed?.success == true &&
                                completed.deployReady
                            ) {
                                onOpenDeployment()
                            }
                        }
                    },
                    enabled =
                        !busy &&
                            selectedToolchains
                                .isNotEmpty()
                ) {
                    Text(
                        "Pipeline'ı Başlat"
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        confirmPipeline = false
                    },
                    enabled = !busy
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    TerminalSurface
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    12.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Proje Sağlığı + Pipeline",
                        color =
                            TerminalText,
                        fontWeight =
                            FontWeight.Black
                    )

                    Text(
                        "Install → Test → Build → Deploy onay kapısı",
                        color =
                            TerminalPrimary,
                        fontSize =
                            10.sp
                    )
                }

                if (busy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp
                    )
                }
            }

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(
                            rememberScrollState()
                        ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        7.dp
                    )
            ) {
                OutlinedButton(
                    onClick = {
                        runHealth()
                    },
                    enabled = !busy
                ) {
                    Text(
                        "Sağlık Kontrolü"
                    )
                }

                Button(
                    onClick = {
                        confirmPipeline = true
                    },
                    enabled =
                        !busy &&
                            selectedToolchains
                                .isNotEmpty()
                ) {
                    Text(
                        "Tüm Pipeline…"
                    )
                }
            }

            health
                ?.let { report ->
                    report.issues
                        .take(20)
                        .forEach { issue ->
                            Text(
                                when (issue.level) {
                                    ProjectPipelineIssueLevel.INFO ->
                                        "✓ ${issue.title}: ${issue.detail}"

                                    ProjectPipelineIssueLevel.WARNING ->
                                        "⚠ ${issue.title}: ${issue.detail}"

                                    ProjectPipelineIssueLevel.ERROR ->
                                        "✕ ${issue.title}: ${issue.detail}"
                                },
                                color =
                                    when (issue.level) {
                                        ProjectPipelineIssueLevel.INFO ->
                                            TerminalSecondary

                                        ProjectPipelineIssueLevel.WARNING ->
                                            TerminalWarning

                                        ProjectPipelineIssueLevel.ERROR ->
                                            TerminalError
                                    },
                                fontSize =
                                    9.sp,
                                lineHeight =
                                    13.sp
                            )
                        }
                }

            result
                ?.let { pipeline ->
                    pipeline.steps
                        .forEach { step ->
                            Text(
                                "${statusIcon(step.status)} ${step.phase} • ${step.title}${step.exitCode?.let { " • exit $it" }.orEmpty()}",
                                color =
                                    when (step.status) {
                                        ProjectPipelineStatus.PASSED ->
                                            TerminalPrimary

                                        ProjectPipelineStatus.SKIPPED ->
                                            TerminalMuted

                                        ProjectPipelineStatus.FAILED ->
                                            TerminalError
                                    },
                                fontFamily =
                                    FontFamily.Monospace,
                                fontSize =
                                    9.sp
                            )
                        }

                    pipeline.failureContext
                        ?.let { packet ->
                            Button(
                                onClick = {
                                    UltimateAiHandoffStore
                                        .publish(packet)
                                    onAiHandoff(packet)
                                },
                                enabled = !busy
                            ) {
                                Text(
                                    "Hatayı Ultimate AI'ya Aktar"
                                )
                            }
                        }
                }

            Text(
                message,
                color =
                    if (
                        message.contains(
                            "başarısız",
                            ignoreCase = true
                        ) ||
                        message.contains(
                            "durdu",
                            ignoreCase = true
                        )
                    ) {
                        TerminalError
                    } else {
                        TerminalMuted
                    },
                fontSize =
                    9.sp,
                lineHeight =
                    13.sp
            )
        }
    }
}

private fun statusIcon(
    status: ProjectPipelineStatus
): String =
    when (status) {
        ProjectPipelineStatus.PASSED -> "✓"
        ProjectPipelineStatus.SKIPPED -> "•"
        ProjectPipelineStatus.FAILED -> "✕"
    }
