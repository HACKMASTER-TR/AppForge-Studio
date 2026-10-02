package com.appforge.studio

import android.util.Base64
import com.appforge.studio.security.OwnerAccessPolicy
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * DEBUG-only physical acceptance harness.
 *
 * The Google ID token never leaves process memory except as the HTTPS
 * Authorization header and is never returned, rendered or logged.
 */
internal object PublisherSigningLiveAcceptance {

    private const val PURPOSE =
        "windows-publisher-signing-v1"

    private const val MAX_RESPONSE_BYTES =
        16 * 1024

    private val secureRandom =
        SecureRandom()

    fun run(
        serverUrl: String
    ): String {
        check(
            BuildConfig.DEBUG
        ) {
            "Live publisher acceptance is debug-only."
        }

        val token =
            OwnerAccessPolicy
                .currentGoogleIdToken()
                ?: error(
                    "Google yönetici oturumu aktif değil."
                )

        val base =
            serverUrl
                .trim()
                .trimEnd('/')

        require(
            base.startsWith(
                "https://"
            )
        ) {
            "Live acceptance HTTPS gerektirir."
        }

        val buildId =
            "live-accept-" +
                System.currentTimeMillis()
                    .toString()

        val artifactSha256 =
            randomSha256()

        val requestNonce =
            randomNonce()

        val issue =
            post(
                base = base,
                path =
                    "/api/admin/windows-signing/grant",
                token = token,
                body =
                    JSONObject()
                        .put(
                            "purpose",
                            PURPOSE
                        )
                        .put(
                            "buildId",
                            buildId
                        )
                        .put(
                            "artifactSha256",
                            artifactSha256
                        )
                        .put(
                            "requestNonce",
                            requestNonce
                        )
            )

        check(
            issue.status == 201 &&
                issue.body.optBoolean(
                    "ok",
                    false
                )
        ) {
            "Grant issue failed (${issue.safeError()})."
        }

        val grant =
            issue.body

        val grantId =
            grant.getString(
                "grantId"
            )

        check(
            runCatching {
                UUID.fromString(
                    grantId
                )
            }.isSuccess
        ) {
            "Grant ID invalid."
        }

        check(
            grant.getString(
                "purpose"
            ) ==
                PURPOSE
        )

        check(
            grant.getString(
                "buildId"
            ) ==
                buildId
        )

        check(
            grant.getString(
                "artifactSha256"
            ) ==
                artifactSha256
        )

        check(
            grant.getString(
                "requestNonce"
            ) ==
                requestNonce
        )

        val issuedAt =
            grant.getLong(
                "issuedAt"
            )

        val expiresAt =
            grant.getLong(
                "expiresAt"
            )

        val now =
            System.currentTimeMillis() /
                1000L

        check(
            issuedAt in
                (now - 60L)..(now + 60L)
        )

        check(
            expiresAt >
                now &&
                expiresAt -
                    issuedAt in
                1L..180L
        )

        val tampered =
            JSONObject(
                grant.toString()
            )
                .put(
                    "artifactSha256",
                    mutateSha256(
                        artifactSha256
                    )
                )

        val mismatch =
            post(
                base = base,
                path =
                    "/api/admin/windows-signing/consume",
                token = token,
                body = tampered
            )

        check(
            mismatch.status == 403 &&
                mismatch.body.optString(
                    "error"
                ) ==
                    "signing_grant_invalid"
        ) {
            "Artifact mismatch was not rejected (${mismatch.safeError()})."
        }

        val consume =
            post(
                base = base,
                path =
                    "/api/admin/windows-signing/consume",
                token = token,
                body =
                    JSONObject(
                        grant.toString()
                    )
            )

        check(
            consume.status == 200 &&
                consume.body.optBoolean(
                    "ok",
                    false
                ) &&
                consume.body.optBoolean(
                    "consumed",
                    false
                ) &&
                consume.body.optString(
                    "grantId"
                ) ==
                    grantId
        ) {
            "Valid grant consume failed (${consume.safeError()})."
        }

        val replay =
            post(
                base = base,
                path =
                    "/api/admin/windows-signing/consume",
                token = token,
                body =
                    JSONObject(
                        grant.toString()
                    )
            )

        check(
            replay.status == 409 &&
                replay.body.optString(
                    "error"
                ) ==
                    "signing_grant_replay"
        ) {
            "Replay was not blocked (${replay.safeError()})."
        }

        return listOf(
            "SERVER_VERIFIED_GOOGLE_ADMIN=PASS",
            "SIGNING_GRANT_ISSUE=PASS",
            "ARTIFACT_HASH_MISMATCH=BLOCKED",
            "SIGNING_GRANT_CONSUME=PASS",
            "SIGNING_GRANT_REPLAY=BLOCKED",
            "LIVE_SIGNING_GRANT_ACCEPTANCE=PASS"
        )
            .joinToString(
                "\n"
            )
    }

    private data class HttpResult(
        val status: Int,
        val body: JSONObject
    ) {
        fun safeError(): String =
            "HTTP $status • " +
                body
                    .optString(
                        "error",
                        "unknown_error"
                    )
                    .take(
                        80
                    )
    }

    private fun post(
        base: String,
        path: String,
        token: String,
        body: JSONObject
    ): HttpResult {
        val connection =
            (
                URL(
                    base +
                        path
                ).openConnection()
                as HttpURLConnection
            )

        try {
            connection.requestMethod =
                "POST"

            connection.doOutput =
                true

            connection.instanceFollowRedirects =
                false

            connection.connectTimeout =
                15_000

            connection.readTimeout =
                15_000

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json; charset=utf-8"
            )

            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )

            connection.outputStream.use {
                stream ->

                stream.write(
                    body
                        .toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            val status =
                connection.responseCode

            val input =
                if (
                    status in
                    200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                input
                    ?.use {
                        readBounded(
                            it
                        )
                    }
                    .orEmpty()

            val json =
                runCatching {
                    JSONObject(
                        text
                    )
                }.getOrElse {
                    JSONObject()
                        .put(
                            "error",
                            "invalid_response"
                        )
                }

            return HttpResult(
                status = status,
                body = json
            )

        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(
        input: java.io.InputStream
    ): String {
        val output =
            ByteArrayOutputStream()

        val buffer =
            ByteArray(
                4096
            )

        var total =
            0

        while (
            true
        ) {
            val count =
                input.read(
                    buffer
                )

            if (
                count < 0
            ) {
                break
            }

            total +=
                count

            check(
                total <=
                    MAX_RESPONSE_BYTES
            ) {
                "Acceptance response too large."
            }

            output.write(
                buffer,
                0,
                count
            )
        }

        return output
            .toString(
                Charsets.UTF_8.name()
            )
    }

    private fun randomNonce():
        String =
        ByteArray(
            32
        ).also {
            secureRandom
                .nextBytes(
                    it
                )
        }.let {
            Base64.encodeToString(
                it,
                Base64.URL_SAFE or
                    Base64.NO_WRAP or
                    Base64.NO_PADDING
            )
        }

    private fun randomSha256():
        String {
        val input =
            ByteArray(
                32
            ).also {
                secureRandom
                    .nextBytes(
                        it
                    )
            }

        return MessageDigest
            .getInstance(
                "SHA-256"
            )
            .digest(
                input
            )
            .joinToString(
                ""
            ) {
                byte ->

                "%02x".format(
                    byte.toInt() and
                        0xff
                )
            }
    }

    private fun mutateSha256(
        value: String
    ): String {
        require(
            value.length == 64
        )

        val first =
            if (
                value[0] ==
                    '0'
            ) {
                '1'
            } else {
                '0'
            }

        return first +
            value.substring(
                1
            )
    }
}
