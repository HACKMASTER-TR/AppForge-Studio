package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JournalPrivacyAuditTest {
    @Test fun everyUntrustedDetailAndErrorWriterPersistsOnlyStructuredDiagnostics() {
        val dir=Files.createTempDirectory("audit-journal-privacy").toFile()
        try {
            val file=File(dir,"module.json");val journal=ModuleOperationJournal(file)
            val fixtures=listOf("HTTPS://audit.invalid/file?token=SYNTHETIC_MARKER", "token=SYNTHETIC_MARKER", "httpS://user:SYNTHETIC_MARKER@audit.invalid/a", "Authorization: Bearer SYNTHETIC_MARKER", "Cookie: sid=SYNTHETIC_MARKER", "password=SYNTHETIC_MARKER apiKey=SYNTHETIC_MARKER", "/private/user/SYNTHETIC_MARKER/key", "sh: command failed: SYNTHETIC_MARKER")
            fixtures.forEachIndexed { i,text->
                journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"audit-$i",ModulePhase.ERROR,detail=text,errorCode=text)))
                val raw=file.readText();assertFalse(raw.contains("SYNTHETIC_MARKER"));assertFalse(raw.contains("audit.invalid"));assertFalse(raw.contains("/private/user"))
                val e=JSONObject(raw).getJSONObject("NODE_WEB")
                assertEquals("DRIVER_FAILED",e.getString("detail"));assertTrue(e.getString("operation").matches(Regex("id_[a-f0-9]{64}")));assertEquals("ERROR",e.getString("phase"))
                assertTrue(journal.history().all{it.endsWith("DRIVER_FAILED")})
            }
        } finally {dir.deleteRecursively()}
    }
    @Test fun knownErrorCodesRetainDiagnosticsWithoutPersistingTheirRawMessages() {
        val dir=Files.createTempDirectory("audit-typed-diagnostic").toFile()
        try {
            val file=File(dir,"journal.json");val journal=ModuleOperationJournal(file)
            journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"safe-op",ModulePhase.ERROR,errorCode="SSLHandshakeException",detail="password=SYNTHETIC_MARKER")))
            val row=JSONObject(file.readText()).getJSONObject("NODE_WEB")
            assertEquals("TLS_FAILURE",row.getString("code"));assertFalse(file.readText().contains("SYNTHETIC_MARKER"))
            val first=row.getString("operation")
            journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"safe-op",ModulePhase.ERROR,errorCode="unknown password=SYNTHETIC_MARKER")))
            assertEquals("DRIVER_FAILED",JSONObject(file.readText()).getJSONObject("NODE_WEB").getString("code"))
            assertEquals(first,journal.restoreStrict().getValue(OfflineModuleId.NODE_WEB).operationId)
        } finally {dir.deleteRecursively()}
    }
    @Test fun legacyRawHistoryIsSanitizedOnReadAndRewrittenWithoutSensitiveCorrelation() {
        val dir=Files.createTempDirectory("audit-legacy-private").toFile()
        try {
            val file=File(dir,"module.json")
            file.writeText("""{"schema":1,"history":[{"module":"NODE_WEB","operation":"HTTPS://SYNTHETIC_MARKER","phase":"ERROR","detail":"token=SYNTHETIC_MARKER","time":1}]}""")
            val journal=ModuleOperationJournal(file)
            assertFalse(journal.history().toString().contains("SYNTHETIC_MARKER"))
            journal.save(mapOf(OfflineModuleId.NODE_WEB to ModuleProgress(OfflineModuleId.NODE_WEB,"HTTPS://SYNTHETIC_MARKER",ModulePhase.ERROR,detail="token=SYNTHETIC_MARKER")))
            assertFalse(file.readText().contains("SYNTHETIC_MARKER"))
            assertTrue(journal.restoreStrict().getValue(OfflineModuleId.NODE_WEB).operationId.startsWith("id_"))
        } finally {dir.deleteRecursively()}
    }
}
