package com.appforge.studio.build

import android.content.Context
import android.util.Base64
import com.appforge.studio.model.DEFAULT_CONTROL_PLANE_URL
import com.appforge.studio.security.OwnerAccessPolicy
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal data class WindowsPublisherSigningAuthorization(
    val grantId: String,
    val purpose: String,
    val buildId: String,
    val artifactSha256: String,
    val requestNonce: String,
    val issuedAt: Long,
    val expiresAt: Long
)

internal object WindowsPublisherSigningAuthorizationClient {

    const val PURPOSE =
        "windows-publisher-signing-v1"

    private const val MAX_RESPONSE_BYTES =
        16 * 1024

    private val consumedGrantIds =
        ConcurrentHashMap
            .newKeySet<String>()

    fun authorizeAndConsume(
        context: Context,
        target: File,
        buildId: String,
        offline: Boolean
    ): WindowsPublisherSigningAuthorization {
        check(
            !offline
        ) {
            "Windows publisher signing server authorization requires internet."
        }

        OwnerAccessPolicy
            .requireActiveOwner(
                context
            )

        require(
            target.isFile &&
                target.length() > 0L
        ) {
            "Windows signing authorization target is invalid."
        }

        require(
            buildId.matches(
                Regex(
                    "^[A-Za-z0-9._:-]{1,160}$"
                )
            )
        ) {
            "Windows signing authorization build ID is invalid."
        }

        val token =
            OwnerAccessPolicy
                .currentGoogleIdToken()
                ?: error(
                    "Server-verified owner session is unavailable."
                )

        val endpoint =
            DEFAULT_CONTROL_PLANE_URL
                .trim()
                .trimEnd('/')

        require(
            endpoint.startsWith(
                "https://"
            )
        ) {
            "Windows signing authorization requires HTTPS control plane."
        }

        val artifactSha256 =
            sha256(
                target
            )

        val requestNonce =
            ByteArray(
                32
            ).also {
                SecureRandom()
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

        val grantJson =
            post(
                endpoint =
                    endpoint,
                path =
                    "/api/admin/windows-signing/grant",
                token =
                    token,
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
            grantJson.optBoolean(
                "ok",
                false
            )
        ) {
            "Windows signing authorization server did not grant access."
        }

        val authorization =
            WindowsPublisherSigningAuthorization(
                grantId =
                    grantJson.getString(
                        "grantId"
                    ),
                purpose =
                    grantJson.getString(
                        "purpose"
                    ),
                buildId =
                    grantJson.getString(
                        "buildId"
                    ),
                artifactSha256 =
                    grantJson.getString(
                        "artifactSha256"
                    )
                        .lowercase(),
                requestNonce =
                    grantJson.getString(
                        "requestNonce"
                    ),
                issuedAt =
                    grantJson.getLong(
                        "issuedAt"
                    ),
                expiresAt =
                    grantJson.getLong(
                        "expiresAt"
                    )
            )

        validateGrant(
            authorization =
                authorization,
            expectedBuildId =
                buildId,
            expectedArtifactSha256 =
                artifactSha256,
            expectedRequestNonce =
                requestNonce
        )

        requireArtifactMatch(
            target =
                target,
            authorization =
                authorization
        )

        check(
            consumedGrantIds.add(
                authorization.grantId
            )
        ) {
            "Windows signing authorization local replay blocked."
        }

        val consumeJson =
            post(
                endpoint =
                    endpoint,
                path =
                    "/api/admin/windows-signing/consume",
                token =
                    token,
                body =
                    JSONObject()
                        .put(
                            "grantId",
                            authorization.grantId
                        )
                        .put(
                            "purpose",
                            authorization.purpose
                        )
                        .put(
                            "buildId",
                            authorization.buildId
                        )
                        .put(
                            "artifactSha256",
                            authorization.artifactSha256
                        )
                        .put(
                            "requestNonce",
                            authorization.requestNonce
                        )
                        .put(
                            "issuedAt",
                            authorization.issuedAt
                        )
                        .put(
                            "expiresAt",
                            authorization.expiresAt
                        )
            )

        check(
            consumeJson.optBoolean(
                "ok",
                false
            ) &&
                consumeJson.optBoolean(
                    "consumed",
                    false
                ) &&
                consumeJson.optString(
                    "grantId"
                ) ==
                    authorization.grantId
        ) {
            "Windows signing authorization was not consumed by server."
        }

        requireArtifactMatch(
            target =
                target,
            authorization =
                authorization
        )

        return authorization
    }

    fun requireArtifactMatch(
        target: File,
        authorization: WindowsPublisherSigningAuthorization
    ) {
        require(
            target.isFile &&
                target.length() > 0L
        )

        check(
            sha256(
                target
            ) ==
                authorization.artifactSha256
        ) {
            "Windows signing artifact changed after server authorization."
        }
    }

    private fun validateGrant(
        authorization: WindowsPublisherSigningAuthorization,
        expectedBuildId: String,
        expectedArtifactSha256: String,
        expectedRequestNonce: String
    ) {
        check(
            runCatching {
                UUID.fromString(
                    authorization.grantId
                )
            }.isSuccess
        ) {
            "Windows signing authorization grant ID is invalid."
        }

        check(
            authorization.purpose ==
                PURPOSE
        )

        check(
            authorization.buildId ==
                expectedBuildId
        )

        check(
            authorization.artifactSha256 ==
                expectedArtifactSha256
        )

        check(
            authorization.requestNonce ==
                expectedRequestNonce
        )

        val now =
            System.currentTimeMillis() /
                1000L

        check(
            authorization.issuedAt in
                (now - 60L)..(now + 60L)
        ) {
            "Windows signing authorization issue time is invalid."
        }

        check(
            authorization.expiresAt >
                now &&
                authorization.expiresAt -
                    authorization.issuedAt in
                1L..180L
        ) {
            "Windows signing authorization is expired or invalid."
        }
    }

    private fun post(
        endpoint: String,
        path: String,
        token: String,
        body: JSONObject
    ): JSONObject {
        val connection =
            (
                URL(
                    endpoint +
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

            val source =
                if (
                    status in
                    200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val text =
                source
                    ?.use {
                        readBounded(
                            it
                        )
                    }
                    .orEmpty()

            if (
                status !in
                200..299
            ) {
                val safeCode =
                    runCatching {
                        JSONObject(
                            text
                        ).optString(
                            "error",
                            ""
                        )
                    }.getOrDefault(
                        ""
                    )

                throw IllegalStateException(
                    "Windows signing authorization denied " +
                        "(HTTP $status • " +
                        safeCode
                            .take(
                                80
                            )
                            .ifBlank {
                                "unknown_error"
                            } +
                        ")."
                )
            }

            return JSONObject(
                text
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
            val read =
                input.read(
                    buffer
                )

            if (
                read < 0
            ) {
                break
            }

            total +=
                read

            check(
                total <=
                    MAX_RESPONSE_BYTES
            ) {
                "Windows signing authorization response is too large."
            }

            output.write(
                buffer,
                0,
                read
            )
        }

        return output
            .toString(
                Charsets.UTF_8.name()
            )
    }

    private fun sha256(
        file: File
    ): String {
        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )

        file.inputStream()
            .buffered()
            .use {
                input ->

                val buffer =
                    ByteArray(
                        128 * 1024
                    )

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

                    digest.update(
                        buffer,
                        0,
                        count
                    )
                }
            }

        return digest
            .digest()
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
}
