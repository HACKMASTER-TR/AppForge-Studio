package com.appforge.studio.terminal

import android.content.Context
import android.util.Log
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class TerminalEnvironmentPhase {
    IDLE,
    PREPARING,
    READY,
    ERROR
}

internal data class TerminalEnvironmentState(
    val phase: TerminalEnvironmentPhase =
        TerminalEnvironmentPhase.IDLE,
    val percent: Int? = null,
    val detail: String =
        "Geliştirme ortamı hazırlanıyor…"
)

internal object TerminalDevelopmentEnvironmentCoordinator {
    private val lock =
        Any()

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private val mutableState =
        MutableStateFlow(
            TerminalEnvironmentState()
        )

    val state:
        StateFlow<TerminalEnvironmentState> =
        mutableState.asStateFlow()

    private var setupJob: Job? =
        null

    private var toolsJob: Job? =
        null

    private var toolsAttempted =
        false

    fun ensure(
        context: Context,
        workspace: File
    ) {
        start(
            context = context,
            workspace = workspace,
            force = false
        )
    }

    fun retry(
        context: Context,
        workspace: File
    ) {
        start(
            context = context,
            workspace = workspace,
            force = true
        )
    }

    private fun start(
        context: Context,
        workspace: File,
        force: Boolean
    ) {
        val appContext =
            context.applicationContext

        synchronized(lock) {
            if (
                setupJob?.isActive ==
                    true
            ) {
                return
            }

            val manager =
                AndroidLinuxRuntimeManager(
                    appContext
                )

            if (
                manager.inspect(
                    LinuxDistribution.UBUNTU
                ).ready
            ) {
                mutableState.value =
                    TerminalEnvironmentState(
                        phase =
                            TerminalEnvironmentPhase.READY,
                        percent = 100,
                        detail = "Terminal hazır."
                    )

                startDevelopmentToolsInBackground(
                    context = appContext,
                    workspace = workspace
                )
                return
            }

            if (
                !force &&
                mutableState.value.phase ==
                    TerminalEnvironmentPhase.ERROR
            ) {
                return
            }

            mutableState.value =
                TerminalEnvironmentState(
                    phase =
                        TerminalEnvironmentPhase.PREPARING,
                    detail =
                        "Geliştirme ortamı hazırlanıyor…"
                )

            setupJob =
                scope.launch {
                    try {
                        manager.ensureBaseEnvironment(
                            distribution =
                                LinuxDistribution.UBUNTU
                        ) { progress ->
                            mutableState.value =
                                TerminalEnvironmentState(
                                    phase =
                                        if (
                                            progress.stage ==
                                            LinuxDevelopmentStage.READY
                                        ) {
                                            TerminalEnvironmentPhase.READY
                                        } else {
                                            TerminalEnvironmentPhase.PREPARING
                                        },
                                    percent =
                                        progress.percent,
                                    detail =
                                        progress.detail
                                )
                        }

                        mutableState.value =
                            TerminalEnvironmentState(
                                phase =
                                    TerminalEnvironmentPhase.READY,
                                percent = 100,
                                detail = "Terminal hazır."
                            )

                        startDevelopmentToolsInBackground(
                            context = appContext,
                            workspace = workspace
                        )
                    } catch (error: Throwable) {
                        Log.e(
                            "AppForgeTerminal",
                            "Development environment setup failed",
                            error
                        )

                        mutableState.value =
                            TerminalEnvironmentState(
                                phase =
                                    TerminalEnvironmentPhase.ERROR,
                                detail =
                                    friendlyError(
                                        error
                                    )
                            )
                    } finally {
                        synchronized(lock) {
                            setupJob = null
                        }
                    }
                }
        }
    }

    private fun startDevelopmentToolsInBackground(
        context: Context,
        workspace: File
    ) {
        synchronized(lock) {
            if (
                toolsAttempted ||
                toolsJob?.isActive == true
            ) {
                return
            }

            toolsAttempted = true

            val appContext =
                context.applicationContext

            toolsJob =
                scope.launch {
                    try {
                        val manager =
                            AndroidLinuxRuntimeManager(
                                appContext
                            )

                        if (
                            !manager.developmentProfileReady(
                                LinuxDistribution.UBUNTU
                            )
                        ) {
                            manager.ensureDevelopmentTools(
                                distribution =
                                    LinuxDistribution.UBUNTU,
                                workspace =
                                    workspace.canonicalFile
                            )
                        }
                    } catch (error: Throwable) {
                        Log.w(
                            "AppForgeTerminal",
                            "Developer tools background setup failed; base terminal remains available",
                            error
                        )
                    } finally {
                        synchronized(lock) {
                            toolsJob = null
                        }
                    }
                }
        }
    }

    private fun friendlyError(
        error: Throwable
    ): String {
        val message =
            error.message
                .orEmpty()
                .lowercase()

        return when {
            "no space" in message ||
                "enospc" in message ||
                "yeterli alan" in message ->
                "Cihazda yeterli boş alan yok. Alan açıp tekrar dene."

            "http" in message ||
                "indir" in message ||
                "network" in message ||
                "timeout" in message ||
                "zaman aş" in message ->
                "İnternet bağlantısını kontrol edip tekrar dene."

            else ->
                "Geliştirme ortamı hazırlanamadı. Tekrar dene."
        }
    }
}
