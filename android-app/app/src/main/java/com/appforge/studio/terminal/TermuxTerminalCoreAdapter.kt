package com.appforge.studio.terminal

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
)

/**
 * Product callbacks intentionally kept independent from Termux APIs.
 */
internal data class TermuxTerminalCallbacks(
    val onTitleChanged: () -> Unit = {},
    val onSessionFinished: () -> Unit = {},
    val onBell: () -> Unit = {},
    val onCopyText: (String) -> Unit = {},
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
    spec: TermuxTerminalLaunchSpec,
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
                "onKeyDown",
                "onKeyUp",
                "onCodePoint",
                "onLongPress" ->
                    false

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

            view.isFocusable = true
            view.isFocusableInTouchMode = true
            view.requestFocus()
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
    ) {
        controllerConsumer(
            controller,
        )

        onDispose {
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
