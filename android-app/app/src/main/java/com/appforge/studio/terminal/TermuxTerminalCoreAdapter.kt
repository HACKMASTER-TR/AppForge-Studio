package com.appforge.studio.terminal

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

/**
 * Launch description for the vendored Termux terminal core.
 *
 * AppForge remains responsible for deciding which executable,
 * cwd, argv and environment are trusted and allowed.
 */
internal data class TermuxTerminalLaunchSpec(
    val executable: String,
    val workingDirectory: String,
    val arguments: List<String>,
    val environment: List<String>,
    val transcriptRows: Int = 5_000,
    val inputEnabled: Boolean = true,
)

/**
 * Product callbacks intentionally kept independent from Termux APIs.
 */
internal data class TermuxTerminalCallbacks(
    val onTitleChanged: () -> Unit = {},
    val onSessionFinished: () -> Unit = {},
    val onBell: () -> Unit = {},
    val onCopyText: (String) -> Unit = {},
    val onSingleTap: () -> Unit = {},
)

/**
 * Thin compatibility layer around the vendored Termux terminal engine.
 *
 * Important:
 * - TerminalView owns viewport / scrollback behaviour.
 * - Compose does not own the terminal line list.
 * - PTY output does not cause LazyColumn rebuilding.
 * - AppForge product/session management remains outside this class.
 */
internal class TermuxTerminalCoreController(
    private val spec: TermuxTerminalLaunchSpec,
    private val callbacks: TermuxTerminalCallbacks =
        TermuxTerminalCallbacks(),
) : AutoCloseable {

    private val terminalView =
        AtomicReference<TerminalView?>(null)

    private val sessionClient: TerminalSessionClient =
        proxy(
            TerminalSessionClient::class.java,
        ) { method, args ->
            when (method.name) {
                "onTextChanged" -> {
                    terminalView
                        .get()
                        ?.onScreenUpdated()

                    null
                }

                "onTitleChanged" -> {
                    callbacks.onTitleChanged()
                    null
                }

                "onSessionFinished" -> {
                    callbacks.onSessionFinished()
                    null
                }

                "onBell" -> {
                    callbacks.onBell()
                    null
                }

                "onCopyTextToClipboard" -> {
                    val text =
                        args
                            ?.filterIsInstance<String>()
                            ?.firstOrNull()
                            .orEmpty()

                    callbacks.onCopyText(text)
                    null
                }

                else ->
                    defaultValue(
                        method.returnType,
                    )
            }
        }

    val session: TerminalSession =
        TerminalSession(
            spec.executable,
            spec.workingDirectory,
            spec.arguments.toTypedArray(),
            spec.environment.toTypedArray(),
            spec.transcriptRows
                .coerceIn(
                    100,
                    50_000,
                ),
            sessionClient,
        )

    private val viewClient: TerminalViewClient =
        proxy(
            TerminalViewClient::class.java,
        ) { method, _ ->
            when (method.name) {
                "onSingleTapUp" -> {
                    callbacks.onSingleTap()
                    null
                }

                "onScale" ->
                    1.0f

                "shouldEnforceCharBasedInput" ->
                    true

                "isTerminalViewSelected" ->
                    true

                "shouldUseCtrlSpaceWorkaround",
                "shouldBackButtonBeMappedToEscape",
                "isInputComposingDisabled",
                "readControlKey",
                "readAltKey",
                "readShiftKey",
                "readFnKey",
                "onLongPress" ->
                    false

                "onKeyDown",
                "onKeyUp",
                "onCodePoint" ->
                    !spec.inputEnabled

                else ->
                    defaultValue(
                        method.returnType,
                    )
            }
        }

    fun createView(
        context: Context,
    ): TerminalView =
        TerminalView(
            context,
            null,
        ).also { view ->
            terminalView.set(view)

            view.setTerminalViewClient(
                viewClient,
            )

            view.attachSession(
                session,
            )

            view.isFocusable =
                spec.inputEnabled

            view.isFocusableInTouchMode =
                spec.inputEnabled

            if (spec.inputEnabled) {
                view.requestFocus()
            }
        }

    fun write(
        text: String,
    ) {
        if (text.isEmpty()) return

        session.write(
            text,
        )
    }

    override fun close() {
        terminalView
            .getAndSet(null)
            ?.attachSession(null)

        session.finishIfRunning()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(
        type: Class<T>,
        handler: (
            method: Method,
            args: Array<out Any?>?,
        ) -> Any?,
    ): T =
        Proxy.newProxyInstance(
            type.classLoader,
            arrayOf(type),
        ) { proxy, method, args ->
            when (method.name) {
                "toString" ->
                    "AppForge${type.simpleName}"

                "hashCode" ->
                    System.identityHashCode(
                        proxy,
                    )

                "equals" ->
                    proxy === args?.firstOrNull()

                else ->
                    handler(
                        method,
                        args,
                    )
            }
        } as T

    private fun defaultValue(
        type: Class<*>,
    ): Any? =
        when (type) {
            java.lang.Boolean.TYPE ->
                false

            java.lang.Byte.TYPE ->
                0.toByte()

            java.lang.Short.TYPE ->
                0.toShort()

            java.lang.Integer.TYPE ->
                0

            java.lang.Long.TYPE ->
                0L

            java.lang.Float.TYPE ->
                0f

            java.lang.Double.TYPE ->
                0.0

            java.lang.Character.TYPE ->
                '\u0000'

            else ->
                null
        }
}


/**
 * Bridges the already-running AppForge PTY into Termux's emulator.
 *
 * It intentionally does not own the real Linux shell. A bounded raw-output
 * backlog lets a newly attached TerminalView reconstruct its scrollback.
 */
internal object TermuxTerminalMirrorRegistry {

    private const val MAX_BACKLOG_CHARS =
        256 * 1024

    private data class Listener(
        val token: Any,
        val consumer: (String) -> Unit,
    )

    private data class Channel(
        val backlog: StringBuilder =
            StringBuilder(),
        var listener: Listener? = null,
    )

    private val lock = Any()

    private val channels =
        mutableMapOf<String, Channel>()

    fun publish(
        sessionId: String,
        text: String,
    ) {
        if (text.isEmpty()) return

        val listener =
            synchronized(lock) {
                val channel =
                    channels.getOrPut(
                        sessionId,
                    ) {
                        Channel()
                    }

                channel.backlog.append(
                    text,
                )

                val overflow =
                    channel.backlog.length -
                        MAX_BACKLOG_CHARS

                if (overflow > 0) {
                    channel.backlog.delete(
                        0,
                        overflow,
                    )
                }

                channel.listener
            }

        listener
            ?.consumer
            ?.invoke(text)
    }

    fun register(
        sessionId: String,
        consumer: (String) -> Unit,
    ): AutoCloseable {
        val token = Any()

        synchronized(lock) {
            val channel =
                channels.getOrPut(
                    sessionId,
                ) {
                    Channel()
                }

            /*
             * Replay before publishing the live listener while holding the
             * bridge lock. This prevents a chunk from being duplicated or
             * lost between backlog replay and live delivery.
             */
            if (channel.backlog.isNotEmpty()) {
                consumer(
                    channel.backlog.toString(),
                )
            }

            channel.listener =
                Listener(
                    token = token,
                    consumer = consumer,
                )
        }

        return AutoCloseable {
            synchronized(lock) {
                val channel =
                    channels[sessionId]
                        ?: return@synchronized

                if (
                    channel.listener
                        ?.token === token
                ) {
                    channel.listener = null
                }
            }
        }
    }
}


/**
 * Compose only hosts the Android TerminalView.
 *
 * Terminal output, scrollback and viewport do not live in Compose state.
 */
@Composable
internal fun TermuxTerminalCoreHost(
    spec: TermuxTerminalLaunchSpec,
    modifier: Modifier = Modifier,
    callbacks: TermuxTerminalCallbacks =
        TermuxTerminalCallbacks(),
    mirrorSessionId: String? = null,
    controllerConsumer: (
        TermuxTerminalCoreController,
    ) -> Unit = {},
) {
    val controller =
        remember(
            spec,
        ) {
            TermuxTerminalCoreController(
                spec = spec,
                callbacks = callbacks,
            )
        }

    DisposableEffect(
        controller,
        mirrorSessionId,
    ) {
        controllerConsumer(
            controller,
        )

        val mirrorRegistration =
            mirrorSessionId
                ?.let { sessionId ->
                    TermuxTerminalMirrorRegistry
                        .register(
                            sessionId,
                            controller::write,
                        )
                }

        onDispose {
            mirrorRegistration
                ?.close()

            controller.close()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            controller.createView(
                context,
            )
        },
        update = { view ->
            if (
                view.getCurrentSession() !==
                controller.session
            ) {
                view.attachSession(
                    controller.session,
                )
            }
        },
    )
}



/**
 * Termux-backed viewport for an AppForge-owned PTY session.
 *
 * /system/bin/cat is only a byte relay into Termux TerminalEmulator.
 * The real Linux shell remains InteractiveLinuxPtySession.
 */
@Composable
internal fun TermuxTerminalMirrorHost(
    sessionId: String,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnSingleTap =
        rememberUpdatedState(
            onSingleTap,
        )

    val spec =
        remember(
            sessionId,
        ) {
            TermuxTerminalLaunchSpec(
                executable =
                    "/system/bin/sh",
                workingDirectory =
                    "/",
                arguments =
                    listOf(
                        "-c",
                        "stty raw -echo 2>/dev/null; exec /system/bin/cat",
                    ),
                environment =
                    listOf(
                        "TERM=xterm-256color",
                        "PATH=/system/bin:/system/xbin",
                    ),
                transcriptRows =
                    5_000,
                inputEnabled =
                    false,
            )
        }

    val callbacks =
        remember(
            sessionId,
        ) {
            TermuxTerminalCallbacks(
                onSingleTap = {
                    currentOnSingleTap
                        .value
                        .invoke()
                },
            )
        }

    TermuxTerminalCoreHost(
        spec = spec,
        modifier = modifier,
        callbacks = callbacks,
        mirrorSessionId = sessionId,
    )
}
