package com.appforge.studio.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

internal data class GitHubPullRequestSummary(
    val number: Int,
    val title: String,
    val head: String,
    val base: String,
    val htmlUrl: String
)

internal data class GitHubWorkflowRunSummary(
    val id: Long,
    val name: String,
    val status: String,
    val conclusion: String,
    val branch: String
)

internal data class GitHubReleaseSummary(
    val tag: String,
    val name: String,
    val publishedAt: String
)

internal data class GitHubArtifactSummary(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val expired: Boolean
)

internal data class GitHubDevOpsDashboard(
    val repository: String,
    val pullRequests: List<GitHubPullRequestSummary>,
    val workflowRuns: List<GitHubWorkflowRunSummary>,
    val releases: List<GitHubReleaseSummary>,
    val artifacts: List<GitHubArtifactSummary>
)

internal object GitHubDevOpsClient {
    private const val API_HOST =
        "api.github.com"

    private const val API_BASE =
        "https://api.github.com"

    private const val MAX_RESPONSE_BYTES =
        2 * 1024 * 1024

    suspend fun loadDashboard(
        originUrl: String,
        accessToken: String
    ): GitHubDevOpsDashboard =
        withContext(Dispatchers.IO) {
            val repository =
                repositorySlug(
                    originUrl
                )

            val pulls =
                requestJsonArray(
                    "/repos/$repository/pulls?state=open&per_page=20",
                    accessToken
                )

            val runs =
                requestJsonObject(
                    "/repos/$repository/actions/runs?per_page=20",
                    accessToken
                )
                    .optJSONArray(
                        "workflow_runs"
                    )
                    ?: JSONArray()

            val releases =
                requestJsonArray(
                    "/repos/$repository/releases?per_page=20",
                    accessToken
                )

            val artifacts =
                requestJsonObject(
                    "/repos/$repository/actions/artifacts?per_page=20",
                    accessToken
                )
                    .optJSONArray(
                        "artifacts"
                    )
                    ?: JSONArray()

            GitHubDevOpsDashboard(
                repository =
                    repository,
                pullRequests =
                    buildList {
                        for (
                            index in
                            0 until pulls.length()
                        ) {
                            val item =
                                pulls.getJSONObject(
                                    index
                                )

                            add(
                                GitHubPullRequestSummary(
                                    number =
                                        item.optInt(
                                            "number"
                                        ),
                                    title =
                                        safeText(
                                            item.optString(
                                                "title"
                                            ),
                                            512
                                        ),
                                    head =
                                        safeText(
                                            item
                                                .optJSONObject(
                                                    "head"
                                                )
                                                ?.optString(
                                                    "ref"
                                                )
                                                .orEmpty(),
                                            256
                                        ),
                                    base =
                                        safeText(
                                            item
                                                .optJSONObject(
                                                    "base"
                                                )
                                                ?.optString(
                                                    "ref"
                                                )
                                                .orEmpty(),
                                            256
                                        ),
                                    htmlUrl =
                                        safeText(
                                            item.optString(
                                                "html_url"
                                            ),
                                            2_048
                                        )
                                )
                            )
                        }
                    },
                workflowRuns =
                    buildList {
                        for (
                            index in
                            0 until runs.length()
                        ) {
                            val item =
                                runs.getJSONObject(
                                    index
                                )

                            add(
                                GitHubWorkflowRunSummary(
                                    id =
                                        item.optLong(
                                            "id"
                                        ),
                                    name =
                                        safeText(
                                            item.optString(
                                                "name"
                                            ),
                                            512
                                        ),
                                    status =
                                        safeText(
                                            item.optString(
                                                "status"
                                            ),
                                            128
                                        ),
                                    conclusion =
                                        safeText(
                                            item.optString(
                                                "conclusion"
                                            ),
                                            128
                                        ),
                                    branch =
                                        safeText(
                                            item.optString(
                                                "head_branch"
                                            ),
                                            256
                                        )
                                )
                            )
                        }
                    },
                releases =
                    buildList {
                        for (
                            index in
                            0 until releases.length()
                        ) {
                            val item =
                                releases.getJSONObject(
                                    index
                                )

                            add(
                                GitHubReleaseSummary(
                                    tag =
                                        safeText(
                                            item.optString(
                                                "tag_name"
                                            ),
                                            256
                                        ),
                                    name =
                                        safeText(
                                            item.optString(
                                                "name"
                                            ),
                                            512
                                        ),
                                    publishedAt =
                                        safeText(
                                            item.optString(
                                                "published_at"
                                            ),
                                            128
                                        )
                                )
                            )
                        }
                    },
                artifacts =
                    buildList {
                        for (
                            index in
                            0 until artifacts.length()
                        ) {
                            val item =
                                artifacts.getJSONObject(
                                    index
                                )

                            add(
                                GitHubArtifactSummary(
                                    id =
                                        item.optLong(
                                            "id"
                                        ),
                                    name =
                                        safeText(
                                            item.optString(
                                                "name"
                                            ),
                                            512
                                        ),
                                    sizeBytes =
                                        item.optLong(
                                            "size_in_bytes"
                                        ),
                                    expired =
                                        item.optBoolean(
                                            "expired"
                                        )
                                )
                            )
                        }
                    }
            )
        }

    suspend fun createPullRequest(
        originUrl: String,
        accessToken: String,
        title: String,
        head: String,
        base: String,
        body: String
    ): GitHubPullRequestSummary =
        withContext(Dispatchers.IO) {
            val repository =
                repositorySlug(
                    originUrl
                )

            val cleanToken =
                requireToken(
                    accessToken
                )

            val cleanTitle =
                requireText(
                    title,
                    256,
                    "PR başlığı"
                )

            val cleanHead =
                requireRef(
                    head,
                    "Kaynak dal"
                )

            val cleanBase =
                requireRef(
                    base,
                    "Hedef dal"
                )

            val cleanBody =
                body.take(
                    16 * 1024
                )

            val payload =
                JSONObject().apply {
                    put(
                        "title",
                        cleanTitle
                    )
                    put(
                        "head",
                        cleanHead
                    )
                    put(
                        "base",
                        cleanBase
                    )
                    put(
                        "body",
                        cleanBody
                    )
                }

            val item =
                requestJsonObject(
                    path =
                        "/repos/$repository/pulls",
                    accessToken =
                        cleanToken,
                    method =
                        "POST",
                    body =
                        payload.toString()
                )

            GitHubPullRequestSummary(
                number =
                    item.optInt(
                        "number"
                    ),
                title =
                    safeText(
                        item.optString(
                            "title"
                        ),
                        512
                    ),
                head =
                    safeText(
                        item
                            .optJSONObject(
                                "head"
                            )
                            ?.optString(
                                "ref"
                            )
                            .orEmpty(),
                        256
                    ),
                base =
                    safeText(
                        item
                            .optJSONObject(
                                "base"
                            )
                            ?.optString(
                                "ref"
                            )
                            .orEmpty(),
                        256
                    ),
                htmlUrl =
                    safeText(
                        item.optString(
                            "html_url"
                        ),
                        2_048
                    )
            )
        }

    internal fun repositorySlug(
        originUrl: String
    ): String {
        val uri =
            runCatching {
                URI(
                    originUrl.trim()
                )
            }.getOrElse {
                throw IllegalArgumentException(
                    "GitHub origin adresi geçersiz."
                )
            }

        require(
            uri.scheme.equals(
                "https",
                ignoreCase = true
            ) &&
                uri.host.equals(
                    "github.com",
                    ignoreCase = true
                ) &&
                uri.userInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null
        ) {
            "Gelişmiş GitHub merkezi yalnız güvenilir github.com HTTPS origin adresini kabul eder."
        }

        val parts =
            uri.path
                .trim('/')
                .removeSuffix(
                    ".git"
                )
                .split('/')
                .filter {
                    it.isNotBlank()
                }

        require(
            parts.size == 2 &&
                parts.all {
                    it.length in 1..100 &&
                        it.matches(
                            Regex(
                                "[A-Za-z0-9._-]+"
                            )
                        )
                }
        ) {
            "GitHub owner/repo adresi çözümlenemedi."
        }

        return parts.joinToString(
            "/"
        )
    }

    private fun requestJsonArray(
        path: String,
        accessToken: String
    ): JSONArray =
        JSONArray(
            request(
                path =
                    path,
                accessToken =
                    accessToken
            )
        )

    private fun requestJsonObject(
        path: String,
        accessToken: String,
        method: String = "GET",
        body: String? = null
    ): JSONObject =
        JSONObject(
            request(
                path =
                    path,
                accessToken =
                    accessToken,
                method =
                    method,
                body =
                    body
            )
        )

    private fun request(
        path: String,
        accessToken: String,
        method: String = "GET",
        body: String? = null
    ): String {
        require(
            path.startsWith(
                "/repos/"
            ) &&
                !path.contains(
                    "://"
                ) &&
                path.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "GitHub API yolu geçersiz."
        }

        val token =
            accessToken
                .trim()
                .also {
                    if (it.isNotBlank()) {
                        requireToken(it)
                    }
                }

        val url =
            URL(
                "$API_BASE$path"
            )

        require(
            url.protocol == "https" &&
                url.host == API_HOST
        ) {
            "Güvenilir olmayan GitHub API hedefi engellendi."
        }

        val connection =
            url.openConnection() as
                HttpURLConnection

        try {
            connection.requestMethod =
                method
            connection.instanceFollowRedirects =
                false
            connection.connectTimeout =
                15_000
            connection.readTimeout =
                20_000
            connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
            )
            connection.setRequestProperty(
                "X-GitHub-Api-Version",
                "2022-11-28"
            )
            connection.setRequestProperty(
                "User-Agent",
                "AppForge-Studio-Android"
            )

            if (token.isNotBlank()) {
                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )
            }

            if (body != null) {
                require(
                    method == "POST" &&
                        body.toByteArray(
                            Charsets.UTF_8
                        ).size <=
                            32 * 1024
                ) {
                    "GitHub istek gövdesi geçersiz."
                }

                connection.doOutput =
                    true
                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                connection.outputStream
                    .use {
                        it.write(
                            body.toByteArray(
                                Charsets.UTF_8
                            )
                        )
                    }
            }

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response =
                stream
                    ?.use(
                        ::readLimited
                    )
                    .orEmpty()

            require(
                code in 200..299
            ) {
                "GitHub isteği başarısız (HTTP $code)."
            }

            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(
        input: InputStream
    ): String {
        val buffer =
            ByteArray(
                16 * 1024
            )

        val output =
            java.io.ByteArrayOutputStream()

        while (true) {
            val read =
                input.read(
                    buffer
                )

            if (read < 0) {
                break
            }

            require(
                output.size() + read <=
                    MAX_RESPONSE_BYTES
            ) {
                "GitHub yanıtı güvenlik sınırını aştı."
            }

            output.write(
                buffer,
                0,
                read
            )
        }

        return output.toString(
            Charsets.UTF_8.name()
        )
    }

    private fun requireToken(
        value: String
    ): String {
        val clean =
            value.trim()

        require(
            clean.isNotBlank() &&
                clean.length <=
                    32 * 1024 &&
                clean.none {
                    it == '\n' ||
                        it == '\r' ||
                        it == '\u0000'
                }
        ) {
            "GitHub erişim anahtarı geçersiz."
        }

        return clean
    }

    private fun requireText(
        value: String,
        max: Int,
        title: String
    ): String {
        val clean =
            value.trim()

        require(
            clean.isNotBlank() &&
                clean.length <= max &&
                clean.none {
                    it == '\u0000'
                }
        ) {
            "$title geçersiz."
        }

        return clean
    }

    private fun requireRef(
        value: String,
        title: String
    ): String {
        val clean =
            value.trim()

        require(
            clean.length in 1..120 &&
                clean.matches(
                    Regex(
                        "[A-Za-z0-9._/-]+"
                    )
                ) &&
                !clean.contains(
                    ".."
                ) &&
                !clean.contains(
                    "@{"
                )
        ) {
            "$title geçersiz."
        }

        return clean
    }

    private fun safeText(
        value: String,
        max: Int
    ): String =
        value
            .replace(
                '\u0000',
                ' '
            )
            .take(max)
}
