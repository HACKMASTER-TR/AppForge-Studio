package com.appforge.studio.ai

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

enum class LocalAiBackend {
    CPU,
    GPU
}

data class LocalAiModelInfo(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAt: Long,
    val backend: LocalAiBackend
)

object LocalAiModelStore {
    private const val MODEL_DIR =
        "local-ai/models"

    private const val META_FILE =
        "local-ai/model.json"

    private fun modelDir(
        context: Context
    ) =
        File(
            context.filesDir,
            MODEL_DIR
        ).apply {
            mkdirs()
        }

    private fun metaFile(
        context: Context
    ) =
        File(
            context.filesDir,
            META_FILE
        ).apply {
            parentFile?.mkdirs()
        }

    fun load(
        context: Context
    ): LocalAiModelInfo? {
        val meta =
            metaFile(context)

        if (!meta.exists()) {
            return null
        }

        return runCatching {
            val o =
                JSONObject(
                    meta.readText()
                )

            val file =
                File(
                    o.getString(
                        "path"
                    )
                )

            require(
                file.exists() &&
                file.length() >
                0
            )

            LocalAiModelInfo(
                name =
                    o.optString(
                        "name",
                        file.name
                    ),
                path =
                    file.absolutePath,
                sizeBytes =
                    file.length(),
                sha256 =
                    o.optString(
                        "sha256"
                    ),
                importedAt =
                    o.optLong(
                        "importedAt",
                        file.lastModified()
                    ),
                backend =
                    runCatching {
                        LocalAiBackend.valueOf(
                            o.optString(
                                "backend",
                                LocalAiBackend.CPU.name
                            )
                        )
                    }.getOrDefault(
                        LocalAiBackend.CPU
                    )
            )
        }.getOrNull()
    }

    fun updateBackend(
        context: Context,
        backend: LocalAiBackend
    ): LocalAiModelInfo? {
        val current =
            load(context)
                ?: return null

        val updated =
            current.copy(
                backend =
                    backend
            )

        saveMeta(
            context,
            updated
        )

        return updated
    }

    fun importModel(
        context: Context,
        uri: Uri
    ): LocalAiModelInfo {
        val name =
            displayName(
                context,
                uri
            )
                ?: "appforge-model.litertlm"

        require(
            name.lowercase()
                .endsWith(
                    ".litertlm"
                )
        ) {
            "Model dosyası .litertlm uzantılı olmalı."
        }

        val size =
            declaredSize(
                context,
                uri
            )

        val available =
            StatFs(
                context.filesDir
                    .absolutePath
            ).availableBytes

        if (
            size >
            0 &&
            available <
            size +
                512L *
                1024L *
                1024L
        ) {
            error(
                "Model için yeterli boş alan yok."
            )
        }

        val dir =
            modelDir(
                context
            )

        val temp =
            File(
                dir,
                "model.importing"
            )

        val target =
            File(
                dir,
                "appforge-assistant.litertlm"
            )

        temp.delete()

        val digest =
            MessageDigest
                .getInstance(
                    "SHA-256"
                )

        var copied =
            0L

        context.contentResolver
            .openInputStream(uri)
            ?.use {
                input ->
                temp.outputStream()
                    .buffered(
                        1024 *
                        1024
                    )
                    .use {
                        output ->
                        val buffer =
                            ByteArray(
                                1024 *
                                1024
                            )

                        while (true) {
                            val read =
                                input.read(
                                    buffer
                                )

                            if (read < 0) {
                                break
                            }

                            copied +=
                                read

                            require(
                                copied <
                                available -
                                    256L *
                                    1024L *
                                    1024L
                            ) {
                                "Depolama alanı yetersiz."
                            }

                            digest.update(
                                buffer,
                                0,
                                read
                            )

                            output.write(
                                buffer,
                                0,
                                read
                            )
                        }
                    }
            }
            ?: error(
                "Model dosyası açılamadı."
            )

        require(
            copied >
            1L *
                1024L *
                1024L
        ) {
            "Model dosyası geçersiz veya çok küçük."
        }

        if (
            target.exists()
        ) {
            target.delete()
        }

        if (
            !temp.renameTo(
                target
            )
        ) {
            temp.copyTo(
                target,
                overwrite =
                    true
            )

            temp.delete()
        }

        val info =
            LocalAiModelInfo(
                name =
                    name,
                path =
                    target
                        .absolutePath,
                sizeBytes =
                    target.length(),
                sha256 =
                    digest
                        .digest()
                        .joinToString(
                            ""
                        ) {
                            "%02x".format(
                                it
                            )
                        },
                importedAt =
                    System.currentTimeMillis(),
                backend =
                    LocalAiBackend.CPU
            )

        saveMeta(
            context,
            info
        )

        return info
    }

    fun installDownloadedModel(
        context: Context,
        source: File,
        displayName: String,
        sha256: String
    ): LocalAiModelInfo {

        require(
            source.exists() &&
            source.length() >
                100L * 1024L * 1024L
        ) {
            "İndirilen model dosyası geçersiz."
        }

        val dir =
            modelDir(
                context
            )

        val target =
            File(
                dir,
                "appforge-assistant.litertlm"
            )

        if (
            target.exists()
        ) {
            target.delete()
        }

        val moved =
            source.renameTo(
                target
            )

        if (!moved) {
            source.copyTo(
                target,
                overwrite = true
            )

            source.delete()
        }

        require(
            target.exists() &&
            target.length() > 0L
        ) {
            "Model uygulama depolamasına taşınamadı."
        }

        val info =
            LocalAiModelInfo(
                name =
                    displayName,
                path =
                    target.absolutePath,
                sizeBytes =
                    target.length(),
                sha256 =
                    sha256,
                importedAt =
                    System.currentTimeMillis(),
                backend =
                    LocalAiBackend.CPU
            )

        saveMeta(
            context,
            info
        )

        return info
    }

    fun remove(
        context: Context
    ) {
        load(context)
            ?.let {
                File(
                    it.path
                ).delete()
            }

        metaFile(context)
            .delete()
    }

    private fun saveMeta(
        context: Context,
        info: LocalAiModelInfo
    ) {
        metaFile(context)
            .writeText(
                JSONObject()
                    .put(
                        "name",
                        info.name
                    )
                    .put(
                        "path",
                        info.path
                    )
                    .put(
                        "sha256",
                        info.sha256
                    )
                    .put(
                        "importedAt",
                        info.importedAt
                    )
                    .put(
                        "backend",
                        info.backend.name
                    )
                    .toString(2)
            )
    }

    private fun displayName(
        context: Context,
        uri: Uri
    ): String? =
        context.contentResolver
            .query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )
            ?.use {
                cursor ->
                if (
                    cursor.moveToFirst()
                ) {
                    cursor.getString(0)
                } else {
                    null
                }
            }

    private fun declaredSize(
        context: Context,
        uri: Uri
    ): Long =
        context.contentResolver
            .query(
                uri,
                arrayOf(
                    OpenableColumns.SIZE
                ),
                null,
                null,
                null
            )
            ?.use {
                cursor ->
                if (
                    cursor.moveToFirst() &&
                    !cursor.isNull(0)
                ) {
                    cursor.getLong(0)
                } else {
                    -1L
                }
            }
            ?: -1L
}
