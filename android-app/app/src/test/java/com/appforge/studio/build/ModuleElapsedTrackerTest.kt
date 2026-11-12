package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

class ModuleElapsedTrackerTest {
    @Test fun phaseTimingStopsAtReadyAndResetsForNewOperations() {
        val module=OfflineModuleId.RUST
        val tracker=ModuleElapsedTracker()
        fun record(op:String,phase:ModulePhase,ms:Long)=tracker.observe(ModuleProgress(module,op,phase),ms)
        record("first",ModulePhase.RESOLVING,1000)
        record("first",ModulePhase.DOWNLOADING,2000)
        record("first",ModulePhase.VERIFYING,3500)
        record("first",ModulePhase.INSTALLING,5000)
        val ready=record("first",ModulePhase.READY,8500)
        assertEquals(7500L,ready.elapsedMs)
        assertEquals(1500L,ready.downloadElapsedMs)
        assertEquals(1500L,ready.verificationElapsedMs)
        assertEquals(3500L,ready.installationElapsedMs)
        assertEquals(7500L,record("first",ModulePhase.READY,9000).elapsedMs)
        assertEquals(0L,record("second",ModulePhase.RESOLVING,9200).elapsedMs)
    }

    @Test fun journalRetainsTimingButNeverPromotesRestoredReadyToReady() {
        val dir=Files.createTempDirectory("appforge-timing-test").toFile()
        try {
            val file=File(dir,"op.json")
            val journal=ModuleOperationJournal(file)
            val event=ModuleProgress(OfflineModuleId.RUST,"op",ModulePhase.READY,
                elapsedMs=9000L,downloadElapsedMs=4000L,
                verificationElapsedMs=2000L,installationElapsedMs=2000L)
            journal.save(mapOf(OfflineModuleId.RUST to event))
            val json=JSONObject(file.readText()).getJSONObject("RUST")
            assertEquals(9000L,json.getLong("elapsedMs"))
            assertEquals(4000L,json.getLong("downloadMs"))
            assertTrue(journal.history().any{it.contains("toplam: 9 sn")})
            val recovered=journal.restoreStrict().getValue(OfflineModuleId.RUST)
            assertEquals(ModulePhase.PAUSED,recovered.phase)
            assertEquals(9000L,recovered.elapsedMs)
            assertEquals(4000L,recovered.downloadElapsedMs)
            assertEquals(2000L,recovered.verificationElapsedMs)
            assertEquals(2000L,recovered.installationElapsedMs)
            assertFalse(file.readText().contains("token="))
        } finally {dir.deleteRecursively()}
    }

    @Test fun legacyJournalWithoutTimingFieldsStillRestoresWithZeroDuration() {
        val dir=Files.createTempDirectory("appforge-timing-legacy").toFile()
        try {
            val file=File(dir,"op.json")
            file.writeText("""{"schema":1,"RUST":{"operation":"legacy","phase":"ERROR","bytes":0,"total":null}}""")
            val restored=ModuleOperationJournal(file).restoreStrict().getValue(OfflineModuleId.RUST)
            assertEquals(0L,restored.elapsedMs)
            assertEquals(0L,restored.downloadElapsedMs)
        } finally {dir.deleteRecursively()}
    }

    @Test(expected=IllegalArgumentException::class)
    fun rejectsImpossibleTimingCounters() {
        val dir=Files.createTempDirectory("appforge-timing-invalid").toFile()
        try {
            ModuleOperationJournal(File(dir,"op.json")).save(mapOf(
                OfflineModuleId.RUST to ModuleProgress(OfflineModuleId.RUST,"op",ModulePhase.ERROR,
                    elapsedMs=100,downloadElapsedMs=120)))
        } finally {dir.deleteRecursively()}
    }
}
