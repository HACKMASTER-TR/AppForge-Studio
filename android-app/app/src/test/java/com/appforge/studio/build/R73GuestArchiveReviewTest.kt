package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class R73GuestArchiveReviewTest {
    @Test fun reviewPlanMatchesPhysicalEvidence() {
        val plan=R73GuestArchiveReview.plannedNames()
        assertEquals(5,plan.size)
        assertEquals(28,plan.values.sumOf{it.size})
        assertEquals(8,plan.values.flatten().distinct().size)
        assertEquals(1,plan.getValue(OfflineModuleId.RUST).size)
    }
    @Test fun matchingArchiveIsOnlyReportedAndMissingOrDamagedCenterIsRejected() {
        val home=Files.createTempDirectory("r73-review").toFile()
        try {
            val guest=File(home,"guest").apply{writeText("pinned bytes")}
            val center=File(home,"center")
            val pin=R70SharedArchives.Pin("fixture.zip",VerifiedModuleDownload.sha(guest))
            assertEquals("CENTRAL_MISSING",R73GuestArchiveReview.inspectCopy(guest,center,pin).state)
            center.writeText("invalid")
            assertEquals("CENTRAL_MISMATCH",R73GuestArchiveReview.inspectCopy(guest,center,pin).state)
            center.writeText("pinned bytes")
            val result=R73GuestArchiveReview.inspectCopy(guest,center,pin)
            assertEquals("MATCH",result.state);assertEquals(guest.length(),result.bytes)
            assertEquals("SAME_INODE",R73GuestArchiveReview.inspectCopy(guest,guest,pin).state)
            assertEquals("pinned bytes",guest.readText());assertEquals("pinned bytes",center.readText())
        } finally {home.deleteRecursively()}
    }
    @Test fun missingGenerationSealIsBlockedWithoutCreatingIt() {
        val home=Files.createTempDirectory("r73-seal").toFile()
        try {
            val root=File(home,"rootfs").apply{mkdir()}
            assertThrows(IllegalArgumentException::class.java){RuntimeGenerationStore(root).reviewArchiveSeal()}
            assertFalse(File(home,".v3-active-generation.json").exists())
        } finally {home.deleteRecursively()}
    }
}
