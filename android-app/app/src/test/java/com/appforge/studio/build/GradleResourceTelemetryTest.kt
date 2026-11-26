package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test

internal class GradleResourceTelemetryTest {
    @Test fun individualGradleFailureIsNotFinalModuleError() {
        val observer=ExternalDownloadTelemetry(
            OfflineModuleId.ANDROID_CORE,"test-operation"
        )
        val json="""{"schema":1,"transfer":"gradle-1",
            "bytes":0,"total":null,"state":"error",
            "finished":false,
            "errorCode":"GRADLE_RESOURCE_FAILED"}"""

        val event=observer.accept(json,1000L)
        assertNotNull(event)
        assertEquals(ModulePhase.PREPARING,event!!.phase)
        assertNull(event.errorCode)
    }

    @Test fun otherTransferFailuresRemainErrors() {
        val observer=ExternalDownloadTelemetry(
            OfflineModuleId.ANDROID_CORE,"test-operation"
        )
        val json="""{"schema":1,"transfer":"curl-1",
            "bytes":0,"total":null,"state":"error",
            "errorCode":"CURL_EXIT_22"}"""

        val event=observer.accept(json,1000L)
        assertNotNull(event)
        assertEquals(ModulePhase.ERROR,event!!.phase)
    }
}
