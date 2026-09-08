package com.appforge.studio.task

import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext


enum class AppForgeTaskState {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}


data class AppForgeTaskSnapshot(
    val id: String,
    val name: String,
    val state: AppForgeTaskState,
    val progress: Int,
    val message: String?,
    val attempt: Int,
    val retryLimit: Int,
    val createdAt: Long,
    val startedAt: Long?,
    val finishedAt: Long?
) {
    val canCancel: Boolean
        get() =
            state ==
                AppForgeTaskState.QUEUED ||
            state ==
                AppForgeTaskState.RUNNING

    val canRetry: Boolean
        get() =
            (
                state ==
                    AppForgeTaskState.FAILED ||
                state ==
                    AppForgeTaskState.CANCELLED
            ) &&
            attempt <=
                retryLimit
}


class AppForgeTaskContext internal constructor(
    private val taskId: String
) {
    val id: String
        get() =
            taskId

    fun reportProgress(
        progress: Int,
        message: String? = null
    ) {
        AppForgeTaskManager
            .reportProgress(
                taskId,
                progress,
                message
            )
    }

    suspend fun checkpoint() {
        currentCoroutineContext()
            .ensureActive()
    }

    suspend fun <T> io(
        block: suspend () -> T
    ): T =
        withContext(
            Dispatchers.IO
        ) {
            block()
        }
}


private data class AppForgeTaskDefinition(
    val name: String,
    val uniqueKey: String?,
    val retryLimit: Int,
    val block:
        suspend AppForgeTaskContext.() -> Unit
)


object AppForgeTaskManager {

    /*
     * Heavy work is intentionally bounded.
     *
     * AppForge already has dedicated build-worker parallelism.
     * This queue is for device-side heavy jobs such as imports,
     * backups, conversion preparation and local AI operations.
     */
    private const val MAX_PARALLEL_TASKS =
        2

    private const val MAX_HISTORY =
        100

    private const val MAX_FINISHED_HISTORY =
        50

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.Default
        )

    private val permits =
        Semaphore(
            MAX_PARALLEL_TASKS
        )

    private val lock =
        Any()

    private val mutableTasks =
        MutableStateFlow<
            List<AppForgeTaskSnapshot>
        >(
            emptyList()
        )

    val tasks:
        StateFlow<
            List<AppForgeTaskSnapshot>
        > =
        mutableTasks
            .asStateFlow()

    private val jobs =
        HashMap<
            String,
            Job
        >()

    private val definitions =
        HashMap<
            String,
            AppForgeTaskDefinition
        >()

    /*
     * Only QUEUED/RUNNING tasks own a unique key.
     * Terminal states release it immediately so a new operation
     * may start later using the same logical identity.
     */
    private val activeUniqueKeys =
        HashMap<
            String,
            String
        >()


    fun submit(
        name: String,
        uniqueKey: String? = null,
        retryLimit: Int = 0,
        block:
            suspend AppForgeTaskContext.() -> Unit
    ): String {
        require(
            name.isNotBlank()
        ) {
            "Görev adı boş olamaz."
        }

        require(
            retryLimit >= 0
        ) {
            "Retry limiti negatif olamaz."
        }

        val safeUniqueKey =
            uniqueKey
                ?.trim()
                ?.takeIf {
                    it.isNotBlank()
                }

        val id =
            UUID.randomUUID()
                .toString()

        val createdAt =
            System.currentTimeMillis()

        synchronized(
            lock
        ) {
            /*
             * Double tap / repeated launcher callback:
             * return the already active logical task instead of
             * creating another heavy operation.
             */
            if (
                safeUniqueKey !=
                    null
            ) {
                val existingId =
                    activeUniqueKeys[
                        safeUniqueKey
                    ]

                if (
                    existingId !=
                        null
                ) {
                    val existing =
                        findLocked(
                            existingId
                        )

                    if (
                        existing
                            ?.canCancel ==
                            true
                    ) {
                        return existingId
                    }

                    /*
                     * Defensive stale-key cleanup.
                     */
                    activeUniqueKeys
                        .remove(
                            safeUniqueKey
                        )
                }
            }

            definitions[id] =
                AppForgeTaskDefinition(
                    name =
                        name.trim(),
                    uniqueKey =
                        safeUniqueKey,
                    retryLimit =
                        retryLimit,
                    block =
                        block
                )

            if (
                safeUniqueKey !=
                    null
            ) {
                activeUniqueKeys[
                    safeUniqueKey
                ] =
                    id
            }

            addSnapshotLocked(
                AppForgeTaskSnapshot(
                    id =
                        id,
                    name =
                        name.trim(),
                    state =
                        AppForgeTaskState.QUEUED,
                    progress =
                        0,
                    message =
                        null,
                    attempt =
                        1,
                    retryLimit =
                        retryLimit,
                    createdAt =
                        createdAt,
                    startedAt =
                        null,
                    finishedAt =
                        null
                )
            )
        }

        launchAttempt(
            id
        )

        return id
    }


    fun cancel(
        id: String
    ): Boolean {
        val job =
            synchronized(
                lock
            ) {
                val current =
                    findLocked(
                        id
                    )
                        ?: return false

                if (
                    !current.canCancel
                ) {
                    return false
                }

                jobs[id]
            }
                ?: return false

        job.cancel(
            CancellationException(
                "AppForge görevi kullanıcı tarafından iptal edildi."
            )
        )

        return true
    }


    fun retry(
        id: String
    ): Boolean {
        synchronized(
            lock
        ) {
            val current =
                findLocked(
                    id
                )
                    ?: return false

            if (
                !current.canRetry
            ) {
                return false
            }

            val definition =
                definitions[id]
                    ?: return false

            val uniqueKey =
                definition.uniqueKey

            if (
                uniqueKey !=
                    null
            ) {
                val owner =
                    activeUniqueKeys[
                        uniqueKey
                    ]

                if (
                    owner !=
                        null &&
                    owner !=
                        id
                ) {
                    return false
                }

                activeUniqueKeys[
                    uniqueKey
                ] =
                    id
            }

            updateLocked(
                id
            ) {
                it.copy(
                    state =
                        AppForgeTaskState.QUEUED,
                    progress =
                        0,
                    message =
                        null,
                    attempt =
                        it.attempt +
                            1,
                    startedAt =
                        null,
                    finishedAt =
                        null
                )
            }
        }

        launchAttempt(
            id
        )

        return true
    }


    fun clearFinished() {
        synchronized(
            lock
        ) {
            val activeIds =
                mutableTasks.value
                    .asSequence()
                    .filter {
                        it.state ==
                            AppForgeTaskState.QUEUED ||
                        it.state ==
                            AppForgeTaskState.RUNNING
                    }
                    .map {
                        it.id
                    }
                    .toSet()

            mutableTasks.value =
                mutableTasks.value
                    .filter {
                        it.id in
                            activeIds
                    }

            definitions
                .keys
                .retainAll(
                    activeIds
                )
        }
    }


    internal fun trimHistoryForTests(
        input:
            List<AppForgeTaskSnapshot>
    ): List<AppForgeTaskSnapshot> =
        trimHistory(
            input
        )


    internal fun reportProgress(
        id: String,
        progress: Int,
        message: String?
    ) {
        synchronized(
            lock
        ) {
            updateLocked(
                id
            ) {
                if (
                    it.state !=
                        AppForgeTaskState.RUNNING
                ) {
                    return@updateLocked it
                }

                it.copy(
                    progress =
                        progress.coerceIn(
                            0,
                            100
                        ),
                    message =
                        message
                            ?: it.message
                )
            }
        }
    }


    private fun launchAttempt(
        id: String
    ) {
        val job =
            scope.launch(
                start =
                    CoroutineStart.LAZY
            ) {
                try {
                    permits.withPermit {
                        currentCoroutineContext()
                            .ensureActive()

                        val definition =
                            synchronized(
                                lock
                            ) {
                                definitions[id]
                            }
                                ?: return@withPermit

                        synchronized(
                            lock
                        ) {
                            updateLocked(
                                id
                            ) {
                                it.copy(
                                    state =
                                        AppForgeTaskState.RUNNING,
                                    startedAt =
                                        System.currentTimeMillis(),
                                    finishedAt =
                                        null
                                )
                            }
                        }

                        val context =
                            AppForgeTaskContext(
                                id
                            )

                        definition.block(
                            context
                        )

                        currentCoroutineContext()
                            .ensureActive()

                        synchronized(
                            lock
                        ) {
                            updateLocked(
                                id
                            ) {
                                it.copy(
                                    state =
                                        AppForgeTaskState.SUCCESS,
                                    progress =
                                        100,
                                    finishedAt =
                                        System.currentTimeMillis()
                                )
                            }

                            releaseUniqueKeyLocked(
                                id
                            )

                            enforceHistoryLocked()
                        }
                    }
                } catch (
                    cancelled: CancellationException
                ) {
                    synchronized(
                        lock
                    ) {
                        updateLocked(
                            id
                        ) {
                            it.copy(
                                state =
                                    AppForgeTaskState.CANCELLED,
                                message =
                                    it.message
                                        ?: "İptal edildi.",
                                finishedAt =
                                    System.currentTimeMillis()
                            )
                        }

                        releaseUniqueKeyLocked(
                            id
                        )

                        enforceHistoryLocked()
                    }
                } catch (
                    error: Throwable
                ) {
                    synchronized(
                        lock
                    ) {
                        updateLocked(
                            id
                        ) {
                            it.copy(
                                state =
                                    AppForgeTaskState.FAILED,
                                message =
                                    error.message
                                        ?: error
                                            .javaClass
                                            .simpleName,
                                finishedAt =
                                    System.currentTimeMillis()
                            )
                        }

                        releaseUniqueKeyLocked(
                            id
                        )

                        enforceHistoryLocked()
                    }
                } finally {
                    synchronized(
                        lock
                    ) {
                        jobs.remove(
                            id
                        )
                    }
                }
            }

        synchronized(
            lock
        ) {
            jobs[id] =
                job
        }

        job.start()
    }


    private fun findLocked(
        id: String
    ): AppForgeTaskSnapshot? =
        mutableTasks.value
            .firstOrNull {
                it.id ==
                    id
            }


    private fun addSnapshotLocked(
        snapshot:
            AppForgeTaskSnapshot
    ) {
        mutableTasks.value =
            mutableTasks.value +
                snapshot

        enforceHistoryLocked()
    }


    private fun updateLocked(
        id: String,
        transform:
            (
                AppForgeTaskSnapshot
            ) ->
                AppForgeTaskSnapshot
    ) {
        mutableTasks.value =
            mutableTasks.value
                .map {
                    if (
                        it.id ==
                            id
                    ) {
                        transform(
                            it
                        )
                    } else {
                        it
                    }
                }
    }


    /*
     * Never throw away queued/running work.
     * Old completed entries are discarded first.
     */
    private fun enforceHistoryLocked() {
        val trimmed =
            trimHistory(
                mutableTasks.value
            )

        if (
            trimmed !=
                mutableTasks.value
        ) {
            mutableTasks.value =
                trimmed
        }

        /*
         * Definitions contain retry closures and may capture project
         * objects/URIs. Never retain definitions after their history
         * entry has been evicted.
         */
        val retainedIds =
            mutableTasks.value
                .mapTo(
                    HashSet()
                ) {
                    it.id
                }

        definitions
            .keys
            .retainAll(
                retainedIds
            )
    }


    /*
     * Active tasks are NEVER discarded.
     *
     * Once they finish, only the latest 50 completed entries remain.
     * MAX_HISTORY still caps completed slots further if a very large
     * number of queued tasks is active.
     */
    private fun trimHistory(
        input:
            List<AppForgeTaskSnapshot>
    ): List<AppForgeTaskSnapshot> {
        val active =
            input.filter {
                it.state ==
                    AppForgeTaskState.QUEUED ||
                it.state ==
                    AppForgeTaskState.RUNNING
            }

        val finished =
            input.filterNot {
                it.state ==
                    AppForgeTaskState.QUEUED ||
                it.state ==
                    AppForgeTaskState.RUNNING
            }

        val availableFinishedSlots =
            (
                MAX_HISTORY -
                    active.size
            )
                .coerceAtLeast(
                    0
                )
                .coerceAtMost(
                    MAX_FINISHED_HISTORY
                )

        return (
            finished
                .takeLast(
                    availableFinishedSlots
                ) +
            active
        )
            .sortedBy {
                it.createdAt
            }
    }


    private fun releaseUniqueKeyLocked(
        id: String
    ) {
        val uniqueKey =
            definitions[id]
                ?.uniqueKey
                ?: return

        if (
            activeUniqueKeys[
                uniqueKey
            ] ==
                id
        ) {
            activeUniqueKeys
                .remove(
                    uniqueKey
                )
        }
    }

}
