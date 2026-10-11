package com.appforge.studio.terminal

/**
 * Best-effort interactive Bash command boundary recognition.
 * The OSC marker is a shell protocol hint, NOT an authentication mechanism.
 * Never use marker text to grant authority; the server-verified owner gate is
 * checked again immediately before writing the Android clipboard.
 * Insecure/missing boundaries, oversized output and interrupted commands fail closed.
 */
internal data class TerminalPtyCopyEvent(
    val sessionId: String,
    val sequence: Long,
    val output: String,
    val exitCode: Int,
    val reason: String? = null
)

internal data class TerminalPtyCapturedCommand(
    val sequence: Long,
    val output: String,
    val exitCode: Int,
    val reason: String? = null
)

internal class TerminalPtyCopyCapture(
    private val markerToken: String,
    private val historyLimit: Int = 20_000
) {
    init {
        require(Regex("^[a-f0-9]{32}$").matches(markerToken))
        require(historyLimit in 1..20_000)
    }

    private val beginMarker = "\u001b]777;afcopy-begin=$markerToken\u0007"
    private val endPrefix = "\u001b]777;afcopy-end=$markerToken;"
    private val pending = StringBuilder()
    private var capturing = false
    private var overflow = false
    private var interrupted = false
    private var unsupportedScreen = false
    private var lastControlTail = ""
    private var bytesSeen = 0
    private var output = AnsiTerminalBuffer(maxScrollbackLines = historyLimit)
    var latestSequence = 0L
        private set

    fun reset() {
        latestSequence++
        capturing = false
        overflow = false
        interrupted = false
        unsupportedScreen = false
        lastControlTail = ""
        bytesSeen = 0
        pending.clear()
        output = AnsiTerminalBuffer(maxScrollbackLines = historyLimit)
    }

    fun cancel() {
        latestSequence++ // Invalidate even a completed result awaiting fresh HTTPS verification.
        interrupted = true
        // Keep the current end marker to close the interrupted command without copying it.
    }

    fun feed(chunk: String): List<TerminalPtyCapturedCommand> {
        if (chunk.isEmpty()) return emptyList()
        pending.append(chunk)
        val completed = mutableListOf<TerminalPtyCapturedCommand>()
        while (true) {
            if (!capturing) {
                val index = pending.indexOf(beginMarker)
                if (index < 0) {
                    val keep = beginMarker.length - 1
                    if (pending.length > keep) pending.delete(0, pending.length - keep)
                    break
                }
                pending.delete(0, index + beginMarker.length)
                latestSequence++
                capturing = true
                overflow = false
                interrupted = false
                unsupportedScreen = false
                lastControlTail = ""
                bytesSeen = 0
                output = AnsiTerminalBuffer(maxScrollbackLines = historyLimit)
            }

            val index = pending.indexOf(endPrefix)
            if (index >= 0) {
                val close = pending.indexOf("\u0007", index + endPrefix.length)
                if (close < 0 && pending.length - index <= endPrefix.length + 4) {
                    if (index > 0) {
                        absorb(pending.substring(0, index))
                        pending.delete(0, index)
                    }
                    break
                }
                if (close >= 0) {
                    absorb(pending.substring(0, index))
                    val status = pending.substring(index + endPrefix.length, close).toIntOrNull()
                    if (!interrupted && status != null && status in 0..255 &&
                        status != 130 && status != 143) {
                        if (overflow) {
                            completed.add(TerminalPtyCapturedCommand(latestSequence, "", status, "OUTPUT_LIMIT"))
                        } else if (output.outputHistoryIncomplete) {
                            completed.add(TerminalPtyCapturedCommand(latestSequence, "", status, "OUTPUT_INCOMPLETE"))
                        } else if (unsupportedScreen) {
                            // Interactive alternate-screen apps are outside the Bash copy contract.
                        } else {
                            val text = output.snapshot().plainText()
                            if (text.isNotBlank()) {
                                if (looksSensitive(text)) {
                                    completed.add(TerminalPtyCapturedCommand(latestSequence, "", status, "SENSITIVE_OUTPUT"))
                                } else if (text.toByteArray(Charsets.UTF_8).size <= MAX_COPY_BYTES) {
                                    completed.add(TerminalPtyCapturedCommand(latestSequence, text, status))
                                }
                            }
                        }
                    }
                    capturing = false
                    pending.delete(0, close + 1)
                    continue
                }
                // An overlong or malformed end marker is not trusted.
                interrupted = true
                absorb(pending.substring(0, index + 1))
                pending.delete(0, index + 1)
                continue
            }
            // Preserve a possible OSC marker split across arbitrary PTY read chunks.
            val reserve = endPrefix.length + 4
            if (pending.length > reserve) {
                val n = pending.length - reserve
                absorb(pending.substring(0, n))
                pending.delete(0, n)
            }
            break
        }
        return completed
    }

    private fun absorb(piece: String) {
        if (!capturing || piece.isEmpty() || overflow) return
        val control = lastControlTail + piece
        if (listOf("\u001b[?1049h", "\u001b[?1047h", "\u001b[?47h").any { it in control })
            unsupportedScreen = true
        lastControlTail = control.takeLast(12)
        bytesSeen += piece.toByteArray(Charsets.UTF_8).size
        if (bytesSeen > MAX_COPY_BYTES) {
            overflow = true
            output = AnsiTerminalBuffer(maxScrollbackLines = historyLimit)
            return
        }
        output.feed(piece)
    }

    private fun looksSensitive(text: String): Boolean =
        Regex("(?i)(?:-----BEGIN [A-Z ]*PRIVATE KEY-----|(?:authorization\\s*[:=]\\s*bearer)|(?:api[_-]?key|password|access[_-]?token|secret[_-]?key)\\s*[:=]\\s*\\S+)")
            .containsMatchIn(text)

    companion object {
        const val MAX_COPY_BYTES = 512 * 1024
    }
}
