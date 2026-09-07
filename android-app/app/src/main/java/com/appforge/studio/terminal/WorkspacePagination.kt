package com.appforge.studio.terminal


internal data class WorkspacePageWindow(
    val pageIndex: Int,
    val pageSize: Int,
    val pageCount: Int,
    val fromIndex: Int,
    val toIndex: Int
)


/*
 * Pure pagination math so large-directory behaviour can be
 * unit-tested without Android or filesystem dependencies.
 */
internal object WorkspacePagination {

    fun window(
        totalCount: Int,
        requestedPage: Int,
        requestedPageSize: Int
    ): WorkspacePageWindow {
        val safeTotal =
            totalCount
                .coerceAtLeast(0)

        val safePageSize =
            requestedPageSize
                .coerceIn(
                    MIN_PAGE_SIZE,
                    MAX_PAGE_SIZE
                )

        if (
            safeTotal == 0
        ) {
            return WorkspacePageWindow(
                pageIndex = 0,
                pageSize =
                    safePageSize,
                pageCount = 0,
                fromIndex = 0,
                toIndex = 0
            )
        }

        val pageCount =
            (
                safeTotal +
                    safePageSize -
                    1
                ) /
                safePageSize

        val safePage =
            requestedPage
                .coerceIn(
                    0,
                    pageCount - 1
                )

        val fromIndex =
            safePage *
                safePageSize

        val toIndex =
            minOf(
                safeTotal,
                fromIndex +
                    safePageSize
            )

        return WorkspacePageWindow(
            pageIndex =
                safePage,
            pageSize =
                safePageSize,
            pageCount =
                pageCount,
            fromIndex =
                fromIndex,
            toIndex =
                toIndex
        )
    }


    private const val MIN_PAGE_SIZE =
        25

    private const val MAX_PAGE_SIZE =
        500
}
