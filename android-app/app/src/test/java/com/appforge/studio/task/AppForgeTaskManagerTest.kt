package com.appforge.studio.task

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test


class AppForgeTaskManagerTest {

    @Test
    fun progressFinishesAtSuccess() =
        runBlocking {
            val id =
                AppForgeTaskManager
                    .submit(
                        name =
                            "test-success"
                    ) {
                        reportProgress(
                            35,
                            "Çalışıyor"
                        )
                    }

            val result =
                waitForTerminalState(
                    id
                )

            assertEquals(
                AppForgeTaskState.SUCCESS,
                result.state
            )

            assertEquals(
                100,
                result.progress
            )

            assertFalse(
                result.canCancel
            )
        }


    @Test
    fun runningTaskCanBeCancelled() =
        runBlocking {
            val id =
                AppForgeTaskManager
                    .submit(
                        name =
                            "test-cancel"
                    ) {
                        reportProgress(
                            10
                        )

                        awaitCancellation()
                    }

            withTimeout(
                2_000L
            ) {
                AppForgeTaskManager
                    .tasks
                    .first {
                        tasks ->
                        tasks
                            .firstOrNull {
                                it.id ==
                                    id
                            }
                            ?.state ==
                            AppForgeTaskState.RUNNING
                    }
            }

            assertTrue(
                AppForgeTaskManager
                    .cancel(
                        id
                    )
            )

            val result =
                waitForTerminalState(
                    id
                )

            assertEquals(
                AppForgeTaskState.CANCELLED,
                result.state
            )
        }


    @Test
    fun failedTaskCanBeRetried() =
        runBlocking {
            var runs =
                0

            val id =
                AppForgeTaskManager
                    .submit(
                        name =
                            "test-retry",
                        retryLimit =
                            1
                    ) {
                        runs +=
                            1

                        if (
                            runs ==
                                1
                        ) {
                            error(
                                "ilk deneme"
                            )
                        }
                    }

            val first =
                waitForTerminalState(
                    id
                )

            assertEquals(
                AppForgeTaskState.FAILED,
                first.state
            )

            assertTrue(
                first.canRetry
            )

            assertTrue(
                AppForgeTaskManager
                    .retry(
                        id
                    )
            )

            val second =
                waitForTerminalState(
                    id,
                    minimumAttempt =
                        2
                )

            assertEquals(
                AppForgeTaskState.SUCCESS,
                second.state
            )

            assertEquals(
                2,
                second.attempt
            )

            assertEquals(
                2,
                runs
            )

            assertFalse(
                second.canRetry
            )
        }


    @Test
    fun duplicateUniqueTaskRunsOnlyOnce() =
        runBlocking {
            val gate =
                CompletableDeferred<Unit>()

            var runs =
                0

            val firstId =
                AppForgeTaskManager
                    .submit(
                        name =
                            "dedup-test",
                        uniqueKey =
                            "dedup-test-key"
                    ) {
                        runs +=
                            1

                        gate.await()
                    }

            withTimeout(
                2_000L
            ) {
                AppForgeTaskManager
                    .tasks
                    .first {
                        tasks ->
                        tasks
                            .firstOrNull {
                                it.id ==
                                    firstId
                            }
                            ?.state ==
                            AppForgeTaskState.RUNNING
                    }
            }

            val duplicateId =
                AppForgeTaskManager
                    .submit(
                        name =
                            "dedup-test-second",
                        uniqueKey =
                            "dedup-test-key"
                    ) {
                        runs +=
                            100
                    }

            assertEquals(
                firstId,
                duplicateId
            )

            gate.complete(
                Unit
            )

            val firstResult =
                waitForTerminalState(
                    firstId
                )

            assertEquals(
                AppForgeTaskState.SUCCESS,
                firstResult.state
            )

            assertEquals(
                1,
                runs
            )

            /*
             * Terminal state releases the identity, therefore the same
             * logical operation may be started again later.
             */
            val nextId =
                AppForgeTaskManager
                    .submit(
                        name =
                            "dedup-test-next",
                        uniqueKey =
                            "dedup-test-key"
                    ) {
                        runs +=
                            1
                    }

            assertTrue(
                nextId !=
                    firstId
            )

            val nextResult =
                waitForTerminalState(
                    nextId
                )

            assertEquals(
                AppForgeTaskState.SUCCESS,
                nextResult.state
            )

            assertEquals(
                2,
                runs
            )
        }


    @Test
    fun historyKeepsActiveAndLatestFiftyFinished() {
        val finished =
            (1..60).map {
                index ->

                AppForgeTaskSnapshot(
                    id =
                        "finished-$index",
                    name =
                        "Finished $index",
                    state =
                        AppForgeTaskState.SUCCESS,
                    progress =
                        100,
                    message =
                        null,
                    attempt =
                        1,
                    retryLimit =
                        0,
                    createdAt =
                        index.toLong(),
                    startedAt =
                        index.toLong(),
                    finishedAt =
                        index.toLong()
                )
            }

        val active =
            listOf(
                AppForgeTaskSnapshot(
                    id =
                        "active-running",
                    name =
                        "Running",
                    state =
                        AppForgeTaskState.RUNNING,
                    progress =
                        20,
                    message =
                        null,
                    attempt =
                        1,
                    retryLimit =
                        0,
                    createdAt =
                        61L,
                    startedAt =
                        61L,
                    finishedAt =
                        null
                ),
                AppForgeTaskSnapshot(
                    id =
                        "active-queued",
                    name =
                        "Queued",
                    state =
                        AppForgeTaskState.QUEUED,
                    progress =
                        0,
                    message =
                        null,
                    attempt =
                        1,
                    retryLimit =
                        0,
                    createdAt =
                        62L,
                    startedAt =
                        null,
                    finishedAt =
                        null
                )
            )

        val trimmed =
            AppForgeTaskManager
                .trimHistoryForTests(
                    finished +
                        active
                )

        assertEquals(
            52,
            trimmed.size
        )

        assertTrue(
            trimmed.any {
                it.id ==
                    "active-running"
            }
        )

        assertTrue(
            trimmed.any {
                it.id ==
                    "active-queued"
            }
        )

        assertFalse(
            trimmed.any {
                it.id ==
                    "finished-1"
            }
        )

        assertTrue(
            trimmed.any {
                it.id ==
                    "finished-60"
            }
        )
    }


    private suspend fun waitForTerminalState(
        id: String,
        minimumAttempt: Int = 1
    ): AppForgeTaskSnapshot =
        withTimeout(
            3_000L
        ) {
            AppForgeTaskManager
                .tasks
                .first {
                    tasks ->

                    val item =
                        tasks.firstOrNull {
                            it.id ==
                                id
                        }

                    item != null &&
                        item.attempt >=
                            minimumAttempt &&
                        (
                            item.state ==
                                AppForgeTaskState.SUCCESS ||
                            item.state ==
                                AppForgeTaskState.FAILED ||
                            item.state ==
                                AppForgeTaskState.CANCELLED
                        )
                }
                .first {
                    it.id ==
                        id
                }
        }
}
