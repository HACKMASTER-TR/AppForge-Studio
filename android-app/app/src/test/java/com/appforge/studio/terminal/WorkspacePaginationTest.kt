package com.appforge.studio.terminal

import org.junit.Assert.assertEquals
import org.junit.Test


class WorkspacePaginationTest {

    @Test
    fun emptyDirectoryProducesNoPages() {
        val window =
            WorkspacePagination
                .window(
                    totalCount = 0,
                    requestedPage = 0,
                    requestedPageSize = 200
                )

        assertEquals(
            0,
            window.pageCount
        )

        assertEquals(
            0,
            window.fromIndex
        )

        assertEquals(
            0,
            window.toIndex
        )
    }


    @Test
    fun largeDirectoryUsesTwoHundredEntryPages() {
        val first =
            WorkspacePagination
                .window(
                    totalCount = 450,
                    requestedPage = 0,
                    requestedPageSize = 200
                )

        assertEquals(
            3,
            first.pageCount
        )

        assertEquals(
            0,
            first.fromIndex
        )

        assertEquals(
            200,
            first.toIndex
        )

        val second =
            WorkspacePagination
                .window(
                    totalCount = 450,
                    requestedPage = 1,
                    requestedPageSize = 200
                )

        assertEquals(
            200,
            second.fromIndex
        )

        assertEquals(
            400,
            second.toIndex
        )

        val third =
            WorkspacePagination
                .window(
                    totalCount = 450,
                    requestedPage = 2,
                    requestedPageSize = 200
                )

        assertEquals(
            400,
            third.fromIndex
        )

        assertEquals(
            450,
            third.toIndex
        )
    }


    @Test
    fun requestedPageIsClampedAfterDeletion() {
        val window =
            WorkspacePagination
                .window(
                    totalCount = 201,
                    requestedPage = 99,
                    requestedPageSize = 200
                )

        assertEquals(
            1,
            window.pageIndex
        )

        assertEquals(
            200,
            window.fromIndex
        )

        assertEquals(
            201,
            window.toIndex
        )
    }


    @Test
    fun pageSizeCannotGrowWithoutBound() {
        val window =
            WorkspacePagination
                .window(
                    totalCount = 5_000,
                    requestedPage = 0,
                    requestedPageSize =
                        Int.MAX_VALUE
                )

        assertEquals(
            500,
            window.pageSize
        )

        assertEquals(
            500,
            window.toIndex
        )
    }
}
