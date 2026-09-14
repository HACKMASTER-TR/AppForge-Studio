package com.appforge.studio.ai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppForgeAgentSessionStoreTest {
    @Test
    fun roundTripPreservesBlueprintAndBuildResumeIdentity() {
        val root =
            tempRoot()

        val blueprint =
            validBlueprint()

        val store =
            AppForgeAgentSessionStore(
                rootDir = root,
                clock = {
                    1_700_000_000_123L
                }
            )

        store.save(
            AppForgeAgentPersistentSession(
                sessionId =
                    "session-roundtrip-01",
                state =
                    AppForgeAgentStudioState(
                        step =
                            AppForgeAgentStudioStep.BUILD,
                        prompt =
                            "Görev uygulaması",
                        platform =
                            AppForgeAgentPlatform.ANDROID,
                        blueprint =
                            blueprint,
                        validation =
                            AppForgeAgentBlueprintValidator.validate(
                                blueprint
                            ),
                        busy = true,
                        remoteBuild =
                            AppForgeAgentRemoteBuildInfo(
                                buildId =
                                    "build-123",
                                buildNo = 7,
                                status =
                                    "running",
                                progress = 42,
                                apkAvailable =
                                    false,
                                aabAvailable =
                                    false,
                                exeAvailable =
                                    false
                            )
                    ),
                workspacePath =
                    "/data/user/0/app/files/unified-agent-workspaces/demo"
            )
        )

        val loaded =
            store.load()

        assertTrue(
            loaded is
                AppForgeAgentSessionLoadResult.Loaded
        )

        val session =
            (
                loaded as
                    AppForgeAgentSessionLoadResult.Loaded
                ).session

        assertEquals(
            "build-123",
            session.resumableBuildId
        )
        assertEquals(
            AppForgeAgentStudioStep.BUILD,
            session.state.step
        )
        assertFalse(
            session.state.busy
        )
        assertEquals(
            "TaskFlow",
            session.state.blueprint?.appName
        )
        assertEquals(
            1_700_000_000_123L,
            session.updatedAtEpochMs
        )

        root.deleteRecursively()
    }

    @Test
    fun credentialsAreRedactedBeforeDiskPersistence() {
        val root =
            tempRoot()

        val store =
            AppForgeAgentSessionStore(
                rootDir = root
            )

        store.save(
            AppForgeAgentPersistentSession(
                sessionId =
                    "session-secret-01",
                state =
                    AppForgeAgentStudioState(
                        prompt =
                            "token=super-secret-value " +
                                "ghp_ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890"
                    )
            )
        )

        val raw =
            File(
                root,
                "unified-agent-session.json"
            ).readText()

        assertFalse(
            raw.contains(
                "super-secret-value"
            )
        )
        assertFalse(
            raw.contains(
                "ghp_"
            )
        )
        assertTrue(
            raw.contains(
                "[REDACTED]"
            )
        )

        root.deleteRecursively()
    }

    @Test
    fun corruptSessionIsQuarantinedInsteadOfCrashing() {
        val root =
            tempRoot()

        val session =
            File(
                root,
                "unified-agent-session.json"
            )

        session.writeText(
            """{"format":"appforge-unified-agent-session","schemaVersion":1,"checksumSha256":"bad","payload":"{}"}"""
        )

        val store =
            AppForgeAgentSessionStore(
                rootDir = root,
                clock = {
                    1_700_000_000_999L
                }
            )

        val result =
            store.load()

        assertTrue(
            result is
                AppForgeAgentSessionLoadResult.Quarantined
        )
        assertFalse(
            session.exists()
        )
        assertEquals(
            1,
            File(
                root,
                "unified-agent-session-quarantine"
            )
                .listFiles()
                .orEmpty()
                .size
        )

        root.deleteRecursively()
    }

    @Test
    fun interruptedBuildWithoutRemoteIdRestoresBlocked() {
        val payload =
            AppForgeAgentSessionCodec.encode(
                AppForgeAgentPersistentSession(
                    sessionId =
                        "session-interrupted-01",
                    state =
                        AppForgeAgentStudioState(
                            step =
                                AppForgeAgentStudioStep.BUILD,
                            prompt =
                                "Görev uygulaması",
                            busy = true,
                            remoteBuild = null
                        )
                )
            )

        val restored =
            AppForgeAgentSessionCodec.decode(
                payload
            )

        assertEquals(
            AppForgeAgentStudioStep.BLOCKED,
            restored.state.step
        )
        assertFalse(
            restored.state.busy
        )
        assertTrue(
            restored.state.message.contains(
                "güvenli"
            )
        )
    }

    @Test
    fun saveLeavesNoTemporaryFileAfterAtomicReplace() {
        val root =
            tempRoot()

        val store =
            AppForgeAgentSessionStore(
                rootDir = root
            )

        store.save(
            AppForgeAgentPersistentSession(
                sessionId =
                    "session-atomic-01"
            )
        )

        assertTrue(
            File(
                root,
                "unified-agent-session.json"
            ).isFile
        )

        assertFalse(
            File(
                root,
                "unified-agent-session.tmp"
            ).exists()
        )

        root.deleteRecursively()
    }

    private fun validBlueprint() =
        AppForgeAgentBlueprint(
            appName = "TaskFlow",
            prompt =
                "Görev takip uygulaması",
            platform =
                AppForgeAgentPlatform.ANDROID,
            startRoute = "/home",
            screens =
                listOf(
                    AppForgeAgentScreenSpec(
                        id = "home",
                        title =
                            "Ana Sayfa",
                        route = "/home",
                        purpose =
                            "Görevleri gösterir."
                    )
                )
        )

    private fun tempRoot(): File =
        kotlin.io.path
            .createTempDirectory(
                "appforge-v12-session-"
            )
            .toFile()
            .canonicalFile
}
