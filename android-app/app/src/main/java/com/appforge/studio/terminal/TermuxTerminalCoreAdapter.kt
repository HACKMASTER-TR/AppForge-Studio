package com.appforge.studio.terminal

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

private const val DEFAULT_TERMUX_TERMINAL_TEXT_SIZE_SP = 14f

/**
 * Launch description for the vendored Termux terminal core.
 *
 * AppForge remains responsible for deciding which executable,
 * cwd, argv and environment are trusted and allowed.
 */
internal data class TermuxTerminalLaunchSpec(
    val executable: String,
    val workingDirectory: String,
    // Full execvp argv. Element 0 is argv[0], not the first shell option.
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
    callbacks: TermuxTerminalCallbacks =
        TermuxTerminalCallbacks(),
) : AutoCloseable {

    private val terminalView =
        AtomicReference<TerminalView?>(null)

    private val callbacksRef =
        AtomicReference(
            callbacks
        )

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
                    callbacksRef.get().onTitleChanged()
                    null
                }

                "onSessionFinished" -> {
                    callbacksRef.get().onSessionFinished()
                    null
                }

                "onBell" -> {
                    callbacksRef.get().onBell()
                    null
                }

                "onCopyTextToClipboard" -> {
                    val text =
                        args
                            ?.filterIsInstance<String>()
                            ?.firstOrNull()
                            .orEmpty()

                    callbacksRef.get().onCopyText(text)
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
                    callbacksRef.get().onSingleTap()
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

    fun updateCallbacks(
        callbacks: TermuxTerminalCallbacks,
    ) {
        callbacksRef.set(
            callbacks
        )
    }

    fun createView(
        context: Context,
    ): TerminalView =
        TerminalView(
            context,
            null,
        ).also { view ->
            /*
             * Termux TerminalView starts with mRenderer == null.
             * Compose AndroidView can lay the view out immediately, which calls
             * onSizeChanged() -> updateSize(). Initialize the renderer before
             * attachSession/layout so updateSize() can safely read font metrics.
             */
            view.setTextSize(
                (
                    DEFAULT_TERMUX_TERMINAL_TEXT_SIZE_SP *
                        context.resources.displayMetrics.scaledDensity
                )
                    .roundToInt()
                    .coerceAtLeast(1),
            )

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

    fun detachView() {
        terminalView
            .getAndSet(null)
            ?.attachSession(null)
    }

    override fun close() {
        detachView()
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
 * Keeps the native Termux emulator alive for the lifetime of the
 * AppForge PTY session, not for the lifetime of a Compose screen.
 *
 * This preserves viewport + scrollback across:
 * - copy mode overlays,
 * - Terminal tab/screen navigation,
 * - normal Compose disposal/recreation.
 */
internal object TermuxTerminalMirrorControllerRegistry {

    private data class Entry(
        val spec: TermuxTerminalLaunchSpec,
        val controller: TermuxTerminalCoreController,
        var registration: AutoCloseable? = null,
    )

    private val lock = Any()

    private val entries =
        mutableMapOf<String, Entry>()

    fun acquire(
        sessionId: String,
        spec: TermuxTerminalLaunchSpec,
        callbacks: TermuxTerminalCallbacks,
    ): TermuxTerminalCoreController =
        synchronized(lock) {
            val current =
                entries[sessionId]

            if (
                current != null &&
                current.spec == spec
            ) {
                current.controller
                    .updateCallbacks(
                        callbacks
                    )

                return@synchronized current.controller
            }

            current
                ?.registration
                ?.close()

            current
                ?.controller
                ?.close()

            val controller =
                TermuxTerminalCoreController(
                    spec = spec,
                    callbacks = callbacks,
                )

            entries[sessionId] =
                Entry(
                    spec = spec,
                    controller = controller,
                )

            controller
        }

    fun ensureRegistered(
        sessionId: String,
        controller: TermuxTerminalCoreController,
    ) {
        synchronized(lock) {
            val entry =
                entries[sessionId]
                    ?: return

            if (
                entry.controller !==
                controller
            ) {
                return
            }

            if (
                entry.registration !=
                null
            ) {
                return
            }

            /*
             * Called only after TerminalView has attached to the
             * TerminalSession. Existing mirror backlog is replayed here.
             */
            entry.registration =
                TermuxTerminalMirrorRegistry
                    .register(
                        sessionId,
                        controller::write,
                    )
        }
    }

    fun detachView(
        sessionId: String,
        controller: TermuxTerminalCoreController,
    ) {
        synchronized(lock) {
            val entry =
                entries[sessionId]
                    ?: return

            if (
                entry.controller ===
                controller
            ) {
                controller.detachView()
            }
        }
    }

    fun release(
        sessionId: String,
    ) {
        val removed =
            synchronized(lock) {
                entries.remove(
                    sessionId
                )
            }

        removed
            ?.registration
            ?.close()

        removed
            ?.controller
            ?.close()

        TermuxTerminalMirrorRegistry
            .drop(
                sessionId
            )
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

    fun drop(
        sessionId: String,
    ) {
        synchronized(lock) {
            channels.remove(
                sessionId
            )
        }
    }

    fun reset(
        sessionId: String,
    ) {
        val listener =
            synchronized(lock) {
                val channel =
                    channels[sessionId]
                        ?: return@synchronized null

                channel.backlog.setLength(0)

                channel.listener
            }

        /*
         * CSI 3 J clears terminal scrollback.
         * CSI 2 J + H clears and homes the visible screen.
         */
        listener
            ?.consumer
            ?.invoke(
                "\u001b[3J\u001b[2J\u001b[H",
            )
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
    onGeometryChanged: (
        rows: Int,
        columns: Int,
    ) -> Unit = { _, _ -> },
    controllerConsumer: (
        TermuxTerminalCoreController,
    ) -> Unit = {},
) {
    val controller =
        remember(
            spec,
            mirrorSessionId,
        ) {
            if (
                mirrorSessionId == null
            ) {
                TermuxTerminalCoreController(
                    spec = spec,
                    callbacks = callbacks,
                )
            } else {
                TermuxTerminalMirrorControllerRegistry
                    .acquire(
                        sessionId =
                            mirrorSessionId,
                        spec =
                            spec,
                        callbacks =
                            callbacks,
                    )
            }
        }

    SideEffect {
        controller.updateCallbacks(
            callbacks
        )
    }

    val currentOnGeometryChanged =
        rememberUpdatedState(
            onGeometryChanged,
        )

    fun reportGeometry(
        view: TerminalView,
    ) {
        view.post {
            if (
                view.width <= 0 ||
                view.height <= 0
            ) {
                return@post
            }

            /*
             * Let Termux calculate its real character-cell geometry first.
             * Then make the AppForge-owned PTY use those exact rows/columns.
             */
            view.updateSize()

            val emulator =
                view.mEmulator
                    ?: return@post

            currentOnGeometryChanged
                .value(
                    emulator.mRows,
                    emulator.mColumns,
                )
        }
    }

    DisposableEffect(
        controller,
        mirrorSessionId,
    ) {
        controllerConsumer(
            controller,
        )

        onDispose {
            if (
                mirrorSessionId == null
            ) {
                controller.close()
            } else {
                TermuxTerminalMirrorControllerRegistry
                    .detachView(
                        sessionId =
                            mirrorSessionId,
                        controller =
                            controller,
                    )
            }
        }
    }

    /*
     * A PTY session switch changes the persistent mirror controller.
     * AndroidView itself must be recreated for that controller so
     * createView() attaches the TerminalSession and ensureRegistered()
     * binds the new mirror channel. Reusing the previous Android View
     * leaves the new terminal black even though its PTY is running.
     */
    key(
        controller,
        mirrorSessionId,
    ) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                controller
                    .createView(
                        context,
                    )
                    .also { view ->
                        /*
                         * Replay only after TerminalView is attached to its
                         * TerminalSession. This makes copy-mode exit and screen
                         * navigation reconstruction deterministic.
                         */
                        mirrorSessionId
                            ?.let { sessionId ->
                                TermuxTerminalMirrorControllerRegistry
                                    .ensureRegistered(
                                        sessionId =
                                            sessionId,
                                        controller =
                                            controller,
                                    )
                            }

                        view.addOnLayoutChangeListener {
                                changedView,
                                _,
                                _,
                                _,
                                _,
                                _,
                                _,
                                _,
                                _,
                            ->
                            reportGeometry(
                                changedView as TerminalView,
                            )
                        }

                        reportGeometry(
                            view,
                        )
                    }
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

                reportGeometry(
                    view,
                )
            },
        )
    }
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
    onGeometryChanged: (
        rows: Int,
        columns: Int,
    ) -> Unit,
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
                        "/system/bin/sh",
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
        onGeometryChanged =
            onGeometryChanged,
    )
}
