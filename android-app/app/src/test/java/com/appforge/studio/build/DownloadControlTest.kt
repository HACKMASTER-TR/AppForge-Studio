package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test

class DownloadControlTest {
    @Test fun stoppingIsCooperativeAndDoesNotRunUntilCheckpoint() {
        val control=DownloadControl()
        control.checkpoint()
        control.requestPause()
        assertEquals(DownloadControl.Request.PAUSE,control.request)
        // Requesting pause itself does not cancel an ongoing package transaction.
        assertThrows(java.util.concurrent.CancellationException::class.java){control.checkpoint()}
        control.requestCancel()
        assertEquals(DownloadControl.Request.CANCEL,control.request)
        assertThrows(java.util.concurrent.CancellationException::class.java){control.checkpoint()}
    }
}
