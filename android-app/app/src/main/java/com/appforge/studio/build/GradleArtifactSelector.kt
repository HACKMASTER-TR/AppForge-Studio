package com.appforge.studio.build

import org.json.JSONObject
import java.io.File
import java.util.Locale

/*
 * APPFORGE_DETERMINISTIC_GRADLE_ARTIFACT_SELECTION_V22
 *
 * Never select Android artifacts by project-wide mtime.
 *
 * APK:
 * - only :app build outputs
 * - AGP output-metadata.json must match the requested variant
 * - prefer the single unfiltered/universal output
 * - ambiguity fails closed
 *
 * AAB:
 * - only :app/build/outputs/bundle/<variant>
 * - bundles do not support APK-style multiple outputs
 * - ambiguity fails closed
 */
internal object GradleArtifactSelector {

    private data class ApkCandidate(
        val file: File,
        val filterCount: Int
    )


    fun selectApk(
        project: File,
        variant: String
    ): File? {

        val root =
            File(
                project,
                "app/build/outputs/apk"
            )
                .canonicalFile

        if (
            !root.isDirectory
        ) {
            return null
        }

        val expectedVariant =
            normalizeVariant(
                variant
            )

        val candidates =
            mutableListOf<ApkCandidate>()

        root
            .walkTopDown()
            .maxDepth(
                8
            )
            .filter {
                it.isFile &&
                    it.name.equals(
                        "output-metadata.json",
                        ignoreCase = true
                    )
            }
            .sortedBy {
                it.relativeTo(
                    root
                )
                    .invariantSeparatorsPath
                    .lowercase(
                        Locale.ROOT
                    )
            }
            .forEach metadataLoop@ {
                metadata ->

                val document =
                    runCatching {
                        JSONObject(
                            metadata.readText()
                        )
                    }
                        .getOrNull()
                        ?: return@metadataLoop

                val declaredVariant =
                    document
                        .optString(
                            "variantName"
                        )
                        .trim()

                if (
                    declaredVariant.isBlank() ||
                    normalizeVariant(
                        declaredVariant
                    ) !=
                    expectedVariant
                ) {
                    return@metadataLoop
                }

                val elements =
                    document
                        .optJSONArray(
                            "elements"
                        )
                        ?: return@metadataLoop

                for (
                    index in
                    0 until elements.length()
                ) {
                    val element =
                        elements
                            .optJSONObject(
                                index
                            )
                            ?: continue

                    val outputFile =
                        element
                            .optString(
                                "outputFile"
                            )
                            .trim()

                    if (
                        outputFile.isBlank()
                    ) {
                        continue
                    }

                    val candidate =
                        runCatching {
                            File(
                                metadata.parentFile,
                                outputFile
                            )
                                .canonicalFile
                        }
                            .getOrNull()
                            ?: continue

                    if (
                        !isInside(
                            root,
                            candidate
                        ) ||
                        !candidate.isFile ||
                        !candidate.extension.equals(
                            "apk",
                            ignoreCase = true
                        )
                    ) {
                        continue
                    }

                    val filterCount =
                        element
                            .optJSONArray(
                                "filters"
                            )
                            ?.length()
                            ?: 0

                    candidates.add(
                        ApkCandidate(
                            file =
                                candidate,
                            filterCount =
                                filterCount
                        )
                    )
                }
            }

        val unique =
            candidates
                .groupBy {
                    it.file
                        .canonicalPath
                }
                .map {
                    (_, sameFile) ->

                    sameFile
                        .minByOrNull {
                            it.filterCount
                        }!!
                }

        if (
            unique.isNotEmpty()
        ) {
            val lowestFilterCount =
                unique
                    .minOf {
                        it.filterCount
                    }

            val preferred =
                unique
                    .filter {
                        it.filterCount ==
                            lowestFilterCount
                    }
                    .sortedBy {
                        it.file
                            .canonicalPath
                            .lowercase(
                                Locale.ROOT
                            )
                    }

            require(
                preferred.size ==
                    1
            ) {
                "Birden fazla eşdeğer APK çıktısı bulundu; " +
                    "yanlış artifact seçmemek için build durduruldu."
            }

            return preferred
                .single()
                .file
        }

        /*
         * Compatibility fallback for a simple non-split variant
         * whose AGP metadata is unavailable.
         *
         * Still scoped to the exact :app variant directory.
         */
        return selectSingleVariantFile(
            root =
                root,
            variant =
                variant,
            extension =
                "apk",
            ambiguousMessage =
                "APK metadata bulunamadı ve variant klasöründe " +
                    "birden fazla APK var."
        )
    }


    fun selectAab(
        project: File,
        variant: String
    ): File? {

        val root =
            File(
                project,
                "app/build/outputs/bundle"
            )
                .canonicalFile

        if (
            !root.isDirectory
        ) {
            return null
        }

        return selectSingleVariantFile(
            root =
                root,
            variant =
                variant,
            extension =
                "aab",
            ambiguousMessage =
                "Birden fazla AAB çıktısı bulundu; " +
                    "yanlış artifact seçmemek için build durduruldu."
        )
    }


    private fun selectSingleVariantFile(
        root: File,
        variant: String,
        extension: String,
        ambiguousMessage: String
    ): File? {

        val variantDirectory =
            File(
                root,
                variantDirectoryName(
                    variant
                )
            )
                .canonicalFile

        if (
            !isInside(
                root,
                variantDirectory
            ) ||
            !variantDirectory.isDirectory
        ) {
            return null
        }

        val candidates =
            variantDirectory
                .listFiles()
                .orEmpty()
                .filter {
                    it.isFile &&
                        it.extension.equals(
                            extension,
                            ignoreCase = true
                        )
                }
                .sortedBy {
                    it.name
                        .lowercase(
                            Locale.ROOT
                        )
                }

        return when (
            candidates.size
        ) {
            0 ->
                null

            1 ->
                candidates.single()

            else -> {
                error(
                    ambiguousMessage
                )
            }
        }
    }


    private fun variantDirectoryName(
        variant: String
    ): String {

        val clean =
            variant
                .trim()

        require(
            clean.isNotBlank()
        ) {
            "Gradle variant boş olamaz."
        }

        return (
            clean
                .take(
                    1
                )
                .lowercase(
                    Locale.ROOT
                ) +
                clean.drop(
                    1
                )
        )
    }


    private fun normalizeVariant(
        value: String
    ): String =
        value
            .filter {
                it.isLetterOrDigit()
            }
            .lowercase(
                Locale.ROOT
            )


    private fun isInside(
        root: File,
        candidate: File
    ): Boolean {

        val safeRoot =
            root
                .canonicalFile

        val safeCandidate =
            candidate
                .canonicalFile

        return (
            safeCandidate ==
                safeRoot ||
            safeCandidate.path
                .startsWith(
                    safeRoot.path +
                        File.separator
                )
        )
    }
}
