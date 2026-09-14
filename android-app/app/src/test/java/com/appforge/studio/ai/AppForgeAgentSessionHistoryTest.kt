package com.appforge.studio.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentSessionHistoryTest {
    @Test
    fun multipleSessionsAreRetainedAndSortedByUpdateTime() {
        val root =
            tempRoot()

        var now =
            1000L

        val store =
            AppForgeAgentSessionStore(
                rootDir = root,
                clock = {
                    now
                }
            )

        store.save(
            session(
                "session-history-01",
                "Bir"
            )
        )

        now =
            2000L

        store.save(
            session(
                "session-history-02",
                "İki"
            )
        )

        val recent =
            store.listRecent()

        assertEquals(
            2,
            recent.size
        )

        assertEquals(
            "session-history-02",
            recent.first().sessionId
        )

        assertEquals(
            "session-history-01",
            recent.last().sessionId
        )

        root.deleteRecursively()
    }

    @Test
    fun savingSameSessionUpdatesSingleHistoryEntry() {
        val root =
            tempRoot()

        var now =
            1000L

        val store =
            AppForgeAgentSessionStore(
                rootDir = root,
                clock = {
                    now
                }
            )

        store.save(
            session(
                "session-history-03",
                "İlk"
            )
        )

        now =
            3000L

        store.save(
            session(
                "session-history-03",
                "Güncel"
            )
        )

        val recent =
            store.listRecent()

        assertEquals(
            1,
            recent.size
        )

        assertEquals(
            "Güncel",
            recent.first().state.prompt
        )

        assertEquals(
            3000L,
            recent.first().updatedAtEpochMs
        )

        root.deleteRecursively()
    }

    @Test
    fun clearOnlyRemovesActiveSessionAndKeepsHistory() {
        val root =
            tempRoot()

        val store =
            AppForgeAgentSessionStore(
                rootDir = root
            )

        store.save(
            session(
                "session-history-04",
                "Korunacak"
            )
        )

        store.clear()

        assertFalse(
            store.exists()
        )

        assertEquals(
            1,
            store.listRecent().size
        )

        root.deleteRecursively()
    }

    @Test
    fun deleteSessionRemovesHistoryAndMatchingActive() {
        val root =
            tempRoot()

        val store =
            AppForgeAgentSessionStore(
                rootDir = root
            )

        store.save(
            session(
                "session-history-05",
                "Sil"
            )
        )

        store.deleteSession(
            "session-history-05"
        )

        assertFalse(
            store.exists()
        )

        assertTrue(
            store.listRecent().isEmpty()
        )

        root.deleteRecursively()
    }

    @Test
    fun loadByIdRestoresSelectedHistorySession() {
        val root =
            tempRoot()

        val store =
            AppForgeAgentSessionStore(
                rootDir = root
            )

        store.save(
            session(
                "session-history-06",
                "Altı"
            )
        )

        store.save(
            session(
                "session-history-07",
                "Yedi"
            )
        )

        val loaded =
            store.loadById(
                "session-history-06"
            )

        assertTrue(
            loaded is
                AppForgeAgentSessionLoadResult.Loaded
        )

        assertEquals(
            "Altı",
            (
                loaded as
                    AppForgeAgentSessionLoadResult.Loaded
                ).session.state.prompt
        )

        root.deleteRecursively()
    }

    @Test
    fun unsafeHistorySessionIdIsRejected() {
        val root =
            tempRoot()

        val store =
            AppForgeAgentSessionStore(
                rootDir = root
            )

        val failed =
            runCatching {
                store.loadById(
                    "../outside"
                )
            }.isFailure

        assertTrue(
            failed
        )

        root.deleteRecursively()
    }

    private fun session(
        id: String,
        prompt: String
    ) =
        AppForgeAgentPersistentSession(
            sessionId = id,
            state =
                AppForgeAgentStudioState(
                    prompt = prompt
                )
        )

    private fun tempRoot(): File =
        kotlin.io.path
            .createTempDirectory(
                "appforge-v12-history-"
            )
            .toFile()
            .canonicalFile
}
