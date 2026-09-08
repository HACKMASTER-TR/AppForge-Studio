package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class AppForgeGitInternalExcludesTest {

    @Test
    fun mergePreservesExistingRulesAndIsIdempotent() {
        val original =
            "*.local\nbuild/\n"

        val first =
            AppForgeGitInternalExcludes
                .mergedContent(
                    original
                )

        val second =
            AppForgeGitInternalExcludes
                .mergedContent(
                    first
                )

        assertTrue(
            first.contains(
                "*.local"
            )
        )

        assertTrue(
            first.contains(
                "build/"
            )
        )

        assertTrue(
            first.contains(
                "/.appforge-trash/"
            )
        )

        assertTrue(
            first ==
                second
        )
    }


    @Test
    fun trashIsIgnoredByStatusAndAddAll() {
        val root =
            Files.createTempDirectory(
                "appforge-git-exclude"
            ).toFile()

        try {
            Git.init()
                .setDirectory(
                    root
                )
                .call()
                .use { git ->

                    assertTrue(
                        AppForgeGitInternalExcludes
                            .ensure(
                                git.repository
                            )
                    )

                    assertFalse(
                        AppForgeGitInternalExcludes
                            .ensure(
                                git.repository
                            )
                    )

                    File(
                        root,
                        ".appforge-trash"
                    ).mkdirs()

                    File(
                        root,
                        ".appforge-trash/deleted.txt"
                    ).writeText(
                        "deleted",
                        Charsets.UTF_8
                    )

                    File(
                        root,
                        "visible.txt"
                    ).writeText(
                        "visible",
                        Charsets.UTF_8
                    )

                    val beforeAdd =
                        git.status()
                            .call()

                    assertTrue(
                        "visible.txt" in
                            beforeAdd.untracked
                    )

                    assertFalse(
                        beforeAdd
                            .untracked
                            .any {
                                it.startsWith(
                                    ".appforge-trash"
                                )
                            }
                    )

                    git.add()
                        .addFilepattern(
                            "."
                        )
                        .call()

                    val afterAdd =
                        git.status()
                            .call()

                    assertTrue(
                        "visible.txt" in
                            afterAdd.added
                    )

                    assertFalse(
                        afterAdd
                            .added
                            .any {
                                it.startsWith(
                                    ".appforge-trash"
                                )
                            }
                    )
                }
        } finally {
            root.deleteRecursively()
        }
    }
}
