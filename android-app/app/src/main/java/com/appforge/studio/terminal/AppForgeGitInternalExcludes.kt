package com.appforge.studio.terminal

import org.eclipse.jgit.lib.Repository
import java.io.File


/*
 * AppForge workspace metadata must never become project source.
 *
 * Use .git/info/exclude rather than .gitignore:
 * - no project file is modified
 * - nothing is committed or pushed
 * - git status and git add respect the rule
 */
internal object AppForgeGitInternalExcludes {

    private const val TRASH_RULE =
        "/.appforge-trash/"

    private const val COMMENT =
        "# AppForge internal workspace files"


    @Synchronized
    fun ensure(
        repository: Repository
    ): Boolean {
        val gitDirectory =
            repository
                .directory
                .canonicalFile

        val infoDirectory =
            File(
                gitDirectory,
                "info"
            )

        if (!infoDirectory.exists()) {
            check(
                infoDirectory.mkdirs()
            ) {
                "Git info klasörü oluşturulamadı."
            }
        }

        check(
            infoDirectory.isDirectory
        ) {
            "Git info yolu klasör değil."
        }

        val excludeFile =
            File(
                infoDirectory,
                "exclude"
            )

        val existing =
            if (
                excludeFile.isFile
            ) {
                excludeFile.readText(
                    Charsets.UTF_8
                )
            } else {
                ""
            }

        val updated =
            mergedContent(
                existing
            )

        if (
            updated ==
                existing
        ) {
            return false
        }

        excludeFile.writeText(
            updated,
            Charsets.UTF_8
        )

        return true
    }


    internal fun mergedContent(
        existing: String
    ): String {
        if (
            existing
                .lineSequence()
                .map {
                    it.trim()
                }
                .any {
                    it ==
                        TRASH_RULE ||
                    it ==
                        ".appforge-trash/" ||
                    it ==
                        ".appforge-trash"
                }
        ) {
            return existing
        }

        val prefix =
            when {
                existing.isEmpty() ->
                    ""

                existing.endsWith(
                    "\n"
                ) ->
                    existing

                else ->
                    existing +
                        "\n"
            }

        return buildString {
            append(
                prefix
            )

            append(
                COMMENT
            )
            append(
                '\n'
            )

            append(
                TRASH_RULE
            )
            append(
                '\n'
            )
        }
    }
}
