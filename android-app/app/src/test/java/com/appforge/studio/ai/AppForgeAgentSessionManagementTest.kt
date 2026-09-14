package com.appforge.studio.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentSessionManagementTest {
    @Test
    fun renamePersistsCustomName() {
        val root = tempRoot()
        val store = AppForgeAgentSessionStore(root)

        store.save(session("session-manage-01", "İlk ad"))
        store.renameSession(
            "session-manage-01",
            "Müşteri Portalı"
        )

        val loaded = store.loadById("session-manage-01")
        assertTrue(
            loaded is AppForgeAgentSessionLoadResult.Loaded
        )

        val session =
            (loaded as AppForgeAgentSessionLoadResult.Loaded).session

        assertEquals(
            "Müşteri Portalı",
            session.customName
        )

        root.deleteRecursively()
    }

    @Test
    fun pinnedSessionsSortBeforeNewerUnpinnedSessions() {
        val root = tempRoot()
        var now = 1_000L
        val store = AppForgeAgentSessionStore(
            rootDir = root,
            clock = { now }
        )

        store.save(session("session-manage-02", "Eski"))
        store.setPinned("session-manage-02", true)

        now = 5_000L
        store.save(session("session-manage-03", "Yeni"))

        val recent = store.listRecent()

        assertEquals(
            "session-manage-02",
            recent.first().sessionId
        )
        assertTrue(
            recent.first().pinned
        )

        root.deleteRecursively()
    }

    @Test
    fun archiveHidesFromRecentAndCanBeRestored() {
        val root = tempRoot()
        val store = AppForgeAgentSessionStore(root)

        store.save(session("session-manage-04", "Arşiv"))
        store.setArchived("session-manage-04", true)

        assertTrue(
            store.listRecent().isEmpty()
        )
        assertEquals(
            1,
            store.listArchived().size
        )

        store.setArchived("session-manage-04", false)

        assertEquals(
            1,
            store.listRecent().size
        )
        assertTrue(
            store.listArchived().isEmpty()
        )

        root.deleteRecursively()
    }

    @Test
    fun cleanupDeletesOnlyOldUnreferencedUnifiedAgentWorkspaces() {
        val root = tempRoot()
        val filesDir = File(root, "files").apply { mkdirs() }
        val storeRoot = File(filesDir, "unified-agent-session")
        val workspaceRoot =
            File(filesDir, "unified-agent-workspaces").apply { mkdirs() }

        val referenced =
            File(workspaceRoot, "referenced").apply { mkdirs() }
        val orphan =
            File(workspaceRoot, "orphan").apply { mkdirs() }

        referenced.setLastModified(1_000L)
        orphan.setLastModified(1_000L)

        val store = AppForgeAgentSessionStore(
            rootDir = storeRoot,
            clock = { 10_000L }
        )

        store.save(
            session("session-manage-05", "Korunan").copy(
                workspacePath = referenced.absolutePath
            )
        )

        val report =
            store.cleanupStorage(
                filesDir = filesDir,
                nowEpochMs = 10_000L,
                orphanGraceMs = 1_000L
            )

        assertTrue(
            referenced.isDirectory
        )
        assertFalse(
            orphan.exists()
        )
        assertEquals(
            1,
            report.deletedWorkspaceCount
        )

        root.deleteRecursively()
    }

    @Test
    fun cleanupNeverDeletesOutsideUnifiedAgentWorkspaceRoot() {
        val root = tempRoot()
        val filesDir = File(root, "files").apply { mkdirs() }
        val outside = File(filesDir, "outside").apply { mkdirs() }

        val store = AppForgeAgentSessionStore(
            rootDir = File(filesDir, "unified-agent-session"),
            clock = { 10_000L }
        )

        store.cleanupStorage(
            filesDir = filesDir,
            nowEpochMs = 10_000L,
            orphanGraceMs = 0L
        )

        assertTrue(
            outside.isDirectory
        )

        root.deleteRecursively()
    }

    @Test
    fun resumeInfoUsesCustomNameAndMetadata() {
        val info =
            AppForgeAgentSessionRuntimePolicy.resumeInfo(
                session("session-manage-06", "Prompt").copy(
                    customName = "Özel Ad",
                    pinned = true,
                    archived = true
                )
            )

        assertEquals(
            "Özel Ad",
            info.title
        )
        assertTrue(
            info.pinned
        )
        assertTrue(
            info.archived
        )
    }

    private fun session(
        id: String,
        prompt: String
    ) =
        AppForgeAgentPersistentSession(
            sessionId = id,
            state = AppForgeAgentStudioState(
                prompt = prompt
            )
        )

    private fun tempRoot(): File =
        kotlin.io.path
            .createTempDirectory(
                "appforge-v12-management-"
            )
            .toFile()
            .canonicalFile
}
