package com.appforge.studio.ui

import com.appforge.studio.AppForgeBuildNumbers
import com.appforge.studio.io.SavedBuild
import java.util.Locale

internal enum class BuildArtifactType(
    val ticketKind: String,
    val extension: String,
    val label: String,
    val mimeType: String
) {
    ANDROID_APK(
        ticketKind = "apk",
        extension = "apk",
        label = "Android APK",
        mimeType = "application/vnd.android.package-archive"
    ),

    WINDOWS_PORTABLE_EXE(
        ticketKind = "exe",
        extension = "exe",
        label = "Windows Portable EXE",
        mimeType = "application/vnd.microsoft.portable-executable"
    ),

    ANDROID_AAB(
        ticketKind = "aab",
        extension = "aab",
        label = "Android AAB",
        mimeType = "application/octet-stream"
    ),

    WEB_ZIP(
        ticketKind = "web",
        extension = "zip",
        label = "Web ZIP",
        mimeType = "application/zip"
    ),

    SOURCE_ZIP(
        ticketKind = "source",
        extension = "zip",
        label = "Source ZIP",
        mimeType = "application/zip"
    )
}


internal data class BuildArtifactRecord(
    val build: SavedBuild,
    val type: BuildArtifactType
) {
    val id: String
        get() =
            "${build.id}:${type.name}"
}


internal fun SavedBuild.availableArtifacts():
    List<BuildArtifactRecord> {

    if (
        !status.equals(
            "success",
            ignoreCase = true
        )
    ) {
        return emptyList()
    }

    return buildList {

        if (!apkUrl.isNullOrBlank()) {
            add(
                BuildArtifactRecord(
                    this@availableArtifacts,
                    BuildArtifactType.ANDROID_APK
                )
            )
        }

        if (!aabUrl.isNullOrBlank()) {
            add(
                BuildArtifactRecord(
                    this@availableArtifacts,
                    BuildArtifactType.ANDROID_AAB
                )
            )
        }

        if (!exeUrl.isNullOrBlank()) {
            add(
                BuildArtifactRecord(
                    this@availableArtifacts,
                    BuildArtifactType.WINDOWS_PORTABLE_EXE
                )
            )
        }
    }
}


internal fun buildArtifactFileName(
    record: BuildArtifactRecord
): String {

    val project =
        record.build.projectName
            .trim()
            .replace(
                Regex(
                    """[^\p{L}\p{N}._-]+"""
                ),
                "_"
            )
            .trim(
                '_',
                '-',
                '.'
            )
            .take(
                48
            )
            .ifBlank {
                "AppForge-App"
            }

    val buildLabel =
        AppForgeBuildNumbers
            .label(
                record.build.buildNo
            )
            .takeIf {
                it.isNotBlank() &&
                    it != "AF------"
            }
            ?: record.build.id
                .take(
                    8
                )
                .ifBlank {
                    "build"
                }

    val safeBuild =
        buildLabel
            .replace(
                Regex(
                    """[^\p{L}\p{N}._-]+"""
                ),
                "_"
            )
            .lowercase(
                Locale.ROOT
            )

    return (
        "${project}_${safeBuild}." +
            record.type.extension
    )
}
