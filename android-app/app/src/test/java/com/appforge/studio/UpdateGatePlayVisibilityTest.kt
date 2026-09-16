package com.appforge.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateGatePlayVisibilityTest {

    @Test
    fun `backend only version is invisible until Play offers it`() {
        assertEquals(
            StudioUpdateState.NORMAL,
            playVisibleStudioUpdateState(
                currentVersionCode = 526,
                minSupportedVersionCode = 1,
                updateAvailable = false,
                updateInProgress = false,
                offeredVersionCode = 0
            )
        )
    }

    @Test
    fun `newer Play production version is optional`() {
        assertEquals(
            StudioUpdateState.OPTIONAL,
            playVisibleStudioUpdateState(
                currentVersionCode = 526,
                minSupportedVersionCode = 1,
                updateAvailable = true,
                updateInProgress = false,
                offeredVersionCode = 527
            )
        )
    }

    @Test
    fun `Play version satisfying minimum can be forced`() {
        assertEquals(
            StudioUpdateState.FORCED,
            playVisibleStudioUpdateState(
                currentVersionCode = 526,
                minSupportedVersionCode = 527,
                updateAvailable = true,
                updateInProgress = false,
                offeredVersionCode = 527
            )
        )
    }

    @Test
    fun `Play rollout below future minimum is never a hard lock`() {
        assertEquals(
            StudioUpdateState.OPTIONAL,
            playVisibleStudioUpdateState(
                currentVersionCode = 525,
                minSupportedVersionCode = 527,
                updateAvailable = true,
                updateInProgress = false,
                offeredVersionCode = 526
            )
        )
    }

    @Test
    fun `in progress immediate Play update resumes as forced`() {
        assertEquals(
            StudioUpdateState.FORCED,
            playVisibleStudioUpdateState(
                currentVersionCode = 526,
                minSupportedVersionCode = 527,
                updateAvailable = false,
                updateInProgress = true,
                offeredVersionCode = 527
            )
        )
    }
}
