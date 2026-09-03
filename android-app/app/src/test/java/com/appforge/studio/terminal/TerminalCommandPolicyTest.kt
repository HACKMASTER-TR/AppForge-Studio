package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCommandPolicyTest {
    @Test
    fun blocksDeviceAndPrivilegeCommands() {
        listOf(
            "su",
            "reboot",
            "echo tamam\nreboot",
            "mkfs.ext4 /dev/block/test",
            "dd if=/dev/zero of=/dev/block/test"
        ).forEach { command ->
            assertFalse(
                command,
                TerminalCommandPolicy
                    .review(command)
                    .allowed
            )
        }
    }

    @Test
    fun destructiveWorkspaceCommandsRequireConfirmation() {
        listOf(
            "rm -rf build",
            "pwd\nrm -rf build",
            "find . -delete",
            "git reset --hard",
            "git clean -fd"
        ).forEach { command ->
            val result =
                TerminalCommandPolicy.review(command)

            assertTrue(command, result.allowed)
            assertTrue(
                command,
                result.requiresConfirmation
            )
        }
    }

    @Test
    fun normalProjectCommandsAreAllowed() {
        listOf(
            "pwd",
            "ls -la",
            "git status",
            "python3 --version"
        ).forEach { command ->
            val result =
                TerminalCommandPolicy.review(command)

            assertTrue(command, result.allowed)
            assertFalse(
                command,
                result.requiresConfirmation
            )
        }
    }

    @Test
    fun shellEscaperHandlesSingleQuotes() {
        assertEquals(
            "'project'\\''s files'",
            ShellEscaper.quote(
                "project's files"
            )
        )
    }
}
