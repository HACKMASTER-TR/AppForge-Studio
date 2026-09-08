package com.appforge.studio.task

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
