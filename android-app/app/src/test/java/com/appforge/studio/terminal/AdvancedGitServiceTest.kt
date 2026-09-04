package com.appforge.studio.terminal

import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedGitServiceTest {
    @Test
    fun stagesUnstagesAndSwitchesBranches() =
        runBlocking {
            withRepository { root, git ->
                val file =
                    File(
                        root,
                        "hello.txt"
                    )

                file.writeText(
                    "one\n"
                )

                git.add()
                    .addFilepattern(".")
                    .call()

                git.commit()
                    .setMessage("initial")
                    .setAuthor(
                        "AppForge",
                        "appforge@local"
                    )
                    .call()

                file.writeText(
                    "two\n"
                )

                var snapshot =
                    AdvancedGitService
                        .inspect(
                            root
                        )

                assertTrue(
                    "hello.txt" in
                        snapshot.unstaged
                )

                snapshot =
                    AdvancedGitService
                        .stagePath(
                            root,
                            "hello.txt"
                        )

                assertTrue(
                    "hello.txt" in
                        snapshot.staged
                )

                snapshot =
                    AdvancedGitService
                        .unstagePath(
                            root,
                            "hello.txt"
                        )

                assertFalse(
                    "hello.txt" in
                        snapshot.staged
                )

                AdvancedGitService
                    .createBranch(
                        root,
                        "feature/test"
                    )

                snapshot =
                    AdvancedGitService
                        .checkoutBranch(
                            root,
                            "feature/test"
                        )

                assertEquals(
                    "feature/test",
                    snapshot.branch
                )
            }
        }

    @Test
    fun githubSlugAcceptsOnlyTrustedHttpsRemote() {
        assertEquals(
            "HACKMASTER-TR/AppForge-Studio",
            AdvancedGitService
                .repositorySlug(
                    "https://github.com/HACKMASTER-TR/AppForge-Studio.git"
                )
        )

        assertTrue(
            runCatching {
                AdvancedGitService
                    .repositorySlug(
                        "http://github.com/HACKMASTER-TR/AppForge-Studio.git"
                    )
            }.isFailure
        )

        assertTrue(
            runCatching {
                AdvancedGitService
                    .repositorySlug(
                        "https://example.com/HACKMASTER-TR/AppForge-Studio.git"
                    )
            }.isFailure
        )
    }

    private fun withRepository(
        block:
            suspend (
                File,
                Git
            ) -> Unit
    ) =
        runBlocking {
            val root =
                Files
                    .createTempDirectory(
                        "appforge-advanced-git"
                    )
                    .toFile()

            val git =
                Git.init()
                    .setDirectory(
                        root
                    )
                    .call()

            try {
                block(
                    root,
                    git
                )
            } finally {
                git.close()
                root.deleteRecursively()
            }
        }
}
