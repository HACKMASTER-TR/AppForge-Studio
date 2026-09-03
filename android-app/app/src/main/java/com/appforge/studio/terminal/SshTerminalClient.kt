package com.appforge.studio.terminal

import android.content.Context
import com.jcraft.jsch.Channel
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

class SshTerminalClient(
    context: Context
) {
    private val knownHostsFile =
        File(
            context.filesDir,
            "terminal/ssh/known_hosts"
        )

    private val activeSessions =
        ConcurrentHashMap<String, Session>()

    private val activeChannels =
        ConcurrentHashMap<String, Channel>()

    suspend fun probe(
        profile: SshProfile
    ): SshHostProbe =
        withContext(Dispatchers.IO) {
            validate(profile)
            validate(auth, command, timeoutMs)

            // Do not present user credentials before the server fingerprint
            // has been verified. Authentication is intentionally allowed to
            // fail after key exchange with a disposable identity.
            val probeProfile =
                profile.copy(
                    username =
                        "appforge-host-key-probe"
                )

            val jsch =
                createJsch(
                    SshAuth(),
                    loadKnownHosts = false
                )

            val session =
                createSession(
                    jsch = jsch,
                    profile = probeProfile,
                    auth = SshAuth(),
                    strictHostKey = false
                )

            try {
                var connectionFailure: JSchException? =
                    null

                try {
                    session.connect(
                        CONNECT_TIMEOUT_MS
                    )
                } catch (failure: JSchException) {
                    connectionFailure =
                        failure
                }

                val hostKey =
                    session.hostKey
                        ?: throw IllegalStateException(
                            "Sunucu anahtarı alınamadı.",
                            connectionFailure
                        )

                SshHostProbe(
                    host = profile.host,
                    port = profile.port,
                    keyType = hostKey.type,
                    encodedKey = hostKey.key,
                    fingerprint =
                        sha256Fingerprint(
                            hostKey.key
                        )
                )
            } finally {
                session.disconnect()
            }
        }

    suspend fun trustHost(
        probe: SshHostProbe
    ) =
        withContext(Dispatchers.IO) {
            val hostToken =
                knownHostToken(
                    probe.host,
                    probe.port
                )

            knownHostsFile.parentFile
                ?.mkdirs()

            val retained =
                if (knownHostsFile.isFile) {
                    knownHostsFile
                        .readLines(Charsets.UTF_8)
                        .filterNot { line ->
                            line
                                .trimStart()
                                .substringBefore(' ') ==
                                hostToken
                        }
                } else {
                    emptyList()
                }

            val replacement =
                "$hostToken ${probe.keyType} ${probe.encodedKey}"

            knownHostsFile.writeText(
                retained
                    .plus(replacement)
                    .joinToString(
                        separator = "\n",
                        postfix = "\n"
                    ),
                Charsets.UTF_8
            )
        }

    fun isTrusted(
        profile: SshProfile
    ): Boolean {
        if (!knownHostsFile.isFile) {
            return false
        }

        val token =
            knownHostToken(
                profile.host,
                profile.port
            )

        return runCatching {
            knownHostsFile
                .useLines { lines ->
                    lines.any { line ->
                        line
                            .trimStart()
                            .substringBefore(' ') ==
                            token
                    }
                }
        }.getOrDefault(false)
    }

    suspend fun execute(
        operationId: String,
        profile: SshProfile,
        auth: SshAuth,
        command: String,
        timeoutMs: Long = 120_000L
    ): TerminalCommandResult =
        withContext(Dispatchers.IO) {
            validate(profile)

            require(
                isTrusted(profile)
            ) {
                "Komut çalıştırılmadan önce sunucu anahtarını doğrulayın."
            }

            val jsch =
                createJsch(auth)

            val session =
                createSession(
                    jsch = jsch,
                    profile = profile,
                    auth = auth,
                    strictHostKey = true
                )

            var channel: ChannelExec? =
                null

            try {
                activeSessions[operationId] =
                    session

                session.connect(CONNECT_TIMEOUT_MS)

                val marker =
                    "__APPFORGE_REMOTE_CWD__"

                val remoteScript =
                    buildString {
                        append("cd ")
                        append(
                            ShellEscaper.quote(
                                profile.workingDirectory
                            )
                        )
                        append(" 2>/dev/null || cd ~\n")
                        append(command)
                        append("\n__appforge_exit=$?\n")
                        append("printf '\\n")
                        append(marker)
                        append("%s\\t%s\\n' \"\$__appforge_exit\" \"\$PWD\"\n")
                    }

                channel =
                    session.openChannel("exec") as
                        ChannelExec

                activeChannels[operationId] =
                    channel

                channel.setCommand(
                    "sh -lc " +
                        ShellEscaper.quote(
                            remoteScript
                        )
                )

                channel.setPty(false)

                val input =
                    channel.inputStream

                channel.connect(CONNECT_TIMEOUT_MS)

                val output =
                    StringBuilder()

                val buffer =
                    ByteArray(4_096)

                val deadline =
                    System.currentTimeMillis() +
                        timeoutMs

                var timedOut =
                    false

                while (
                    !channel.isClosed ||
                    input.available() > 0
                ) {
                    val available =
                        input.available()

                    if (available > 0) {
                        val count =
                            input.read(
                                buffer,
                                0,
                                minOf(
                                    buffer.size,
                                    available
                                )
                            )

                        if (count > 0) {
                            val text =
                                String(
                                    buffer,
                                    0,
                                    count,
                                    Charsets.UTF_8
                                )

                            if (output.length < MAX_CAPTURE_CHARS) {
                                output.append(
                                    text.take(
                                        MAX_CAPTURE_CHARS -
                                            output.length
                                    )
                                )
                            }
                        }
                    } else {
                        Thread.sleep(25L)
                    }

                    if (
                        System.currentTimeMillis() >=
                        deadline
                    ) {
                        timedOut =
                            true

                        channel.disconnect()

                        break
                    }
                }

                parseResult(
                    raw = output.toString(),
                    marker = marker,
                    fallbackDirectory =
                        profile.workingDirectory,
                    fallbackExit =
                        if (timedOut) {
                            124
                        } else {
                            channel.exitStatus
                        },
                    timedOut = timedOut
                )
            } catch (cancelled: CancellationException) {
                channel?.disconnect()
                session.disconnect()
                throw cancelled
            } finally {
                activeChannels.remove(operationId)
                    ?.disconnect()

                activeSessions.remove(operationId)
                    ?.disconnect()
            }
        }

    fun cancel(operationId: String): Boolean {
        val channel =
            activeChannels.remove(operationId)

        val session =
            activeSessions.remove(operationId)

        channel?.disconnect()
        session?.disconnect()

        return channel != null ||
            session != null
    }

    private fun createJsch(
        auth: SshAuth,
        loadKnownHosts: Boolean = true
    ): JSch =
        JSch().apply {
            if (
                loadKnownHosts &&
                knownHostsFile.isFile
            ) {
                setKnownHosts(
                    knownHostsFile.absolutePath
                )
            }

            auth.privateKey
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?.let { key ->
                    addIdentity(
                        auth.privateKeyName
                            .ifBlank {
                                "appforge-session-key"
                            },
                        key,
                        null,
                        auth.passphrase
                            .takeIf {
                                it.isNotEmpty()
                            }
                            ?.toByteArray(
                                Charsets.UTF_8
                            )
                    )
                }
        }

    private fun createSession(
        jsch: JSch,
        profile: SshProfile,
        auth: SshAuth,
        strictHostKey: Boolean
    ): Session =
        jsch
            .getSession(
                profile.username,
                profile.host,
                profile.port
            )
            .apply {
                if (auth.password.isNotEmpty()) {
                    setPassword(auth.password)
                }

                setConfig(
                    "StrictHostKeyChecking",
                    if (strictHostKey) {
                        "yes"
                    } else {
                        "no"
                    }
                )

                setConfig(
                    "PreferredAuthentications",
                    "publickey,password,keyboard-interactive"
                )

                setServerAliveInterval(15_000)
                setServerAliveCountMax(2)
            }

    private fun parseResult(
        raw: String,
        marker: String,
        fallbackDirectory: String,
        fallbackExit: Int,
        timedOut: Boolean
    ): TerminalCommandResult {
        val clean =
            TerminalTextSanitizer.clean(raw)

        val markerIndex =
            clean.lastIndexOf(marker)

        if (markerIndex < 0) {
            return TerminalCommandResult(
                exitCode = fallbackExit,
                output = clean.trimEnd(),
                workingDirectory =
                    File(fallbackDirectory),
                timedOut = timedOut
            )
        }

        val metadata =
            clean.substring(
                markerIndex +
                    marker.length
            ).lineSequence()
                .firstOrNull()
                .orEmpty()

        val exitCode =
            metadata
                .substringBefore('\t')
                .trim()
                .toIntOrNull()
                ?: fallbackExit

        val directory =
            metadata
                .substringAfter(
                    '\t',
                    fallbackDirectory
                )
                .trim()
                .ifBlank {
                    fallbackDirectory
                }

        return TerminalCommandResult(
            exitCode = exitCode,
            output =
                clean
                    .substring(
                        0,
                        markerIndex
                    )
                    .trimEnd(),
            workingDirectory =
                File(directory),
            timedOut = timedOut
        )
    }

    private fun validate(profile: SshProfile) {
        require(
            profile.host.isNotBlank() &&
                profile.host.length <= 253 &&
                profile.host.none {
                    it.isWhitespace() ||
                        it == '/' ||
                        it == '\\'
                }
        ) {
            "Geçerli bir SSH sunucu adresi girin."
        }

        require(
            profile.port in
                1..65_535
        ) {
            "SSH portu 1–65535 arasında olmalı."
        }

        require(
            profile.username.isNotBlank() &&
                profile.username.length <= 128 &&
                profile.username.none {
                    it.isWhitespace() ||
                        it == '\u0000'
                }
        ) {
            "Geçerli bir SSH kullanıcı adı girin."
        }

        require(
            profile.workingDirectory.length <= 2_048 &&
                '\u0000' !in profile.workingDirectory
        ) {
            "SSH çalışma klasörü geçersiz."
        }
    }

    private fun validate(
        auth: SshAuth,
        command: String,
        timeoutMs: Long
    ) {
        require(
            auth.password.length <= 32 * 1_024 &&
                auth.passphrase.length <= 32 * 1_024 &&
                (auth.privateKey?.size ?: 0) <=
                1 * 1_024 * 1_024 &&
                command.isNotBlank() &&
                command.length <= 16 * 1_024 &&
                '\u0000' !in command &&
                timeoutMs in 1_000L..600_000L
        ) {
            "SSH komutu veya kimlik bilgisi geçersiz."
        }
    }

    private fun knownHostToken(
        host: String,
        port: Int
    ): String =
        if (port == 22) {
            host.lowercase()
        } else {
            "[${host.lowercase()}]:$port"
        }

    private fun sha256Fingerprint(
        encodedKey: String
    ): String {
        val keyBytes =
            Base64
                .getDecoder()
                .decode(encodedKey)

        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .digest(keyBytes)

        return "SHA256:" +
            Base64
                .getEncoder()
                .withoutPadding()
                .encodeToString(digest)
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS =
            15_000

        private const val MAX_CAPTURE_CHARS =
            512 * 1_024
    }
}
