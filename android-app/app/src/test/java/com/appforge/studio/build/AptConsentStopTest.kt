package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AptConsentStopTest {
    @Test fun rejectionIsBoundToExactOperationAndPreventsLaterApproval() {
        val dir=Files.createTempDirectory("apt-stop").toFile()
        try {
            val plan=File(dir,"apt-consent.plan.tsv").apply {writeText("pkg_1_amd64.deb\t100\t${"a".repeat(64)}\thttps://snapshot.ubuntu.com/ubuntu/${AptModuleLocks.SNAPSHOT}/pool/main/p/pkg.deb\n")}
            File(dir,"apt-consent.request.json").writeText(JSONObject().put("schema",1).put("module","ANDROID_CORE").put("sha256",VerifiedModuleDownload.sha(plan)).toString())
            AptDownloadConsent.poll(dir,OfflineModuleId.ANDROID_CORE,"stop-op")
            assertThrows(IllegalArgumentException::class.java){AptDownloadConsent.reject("other-op")}
            AptDownloadConsent.reject("stop-op")
            assertEquals(VerifiedModuleDownload.sha(plan),File(dir,"apt-consent.declined").readText().trim())
            assertThrows(IllegalArgumentException::class.java){AptDownloadConsent.approve("stop-op")}
            assertFalse(File(dir,"apt-consent.approved").exists())
        }finally{AptDownloadConsent.clear("stop-op");dir.deleteRecursively()}
    }
    @Test fun realShellConsentGateRejectsWithoutRunningTransaction() {
        val script=File("app/src/main/assets/device-build/apt-snapshot-lock.sh").let{if(it.exists())it else File("src/main/assets/device-build/apt-snapshot-lock.sh")}.canonicalFile
        val dir=Files.createTempDirectory("apt-gate").toFile()
        try {
            File(dir,"apt-consent.declined").writeText("pinned-plan\n")
            val command=". \"\$1\"; as_wait_for_consent \"\$2\" pinned-plan; result=\$?; [ \"\$result\" = 0 ] && touch \"\$2/transaction-ran\"; exit \"\$result\""
            val process=ProcessBuilder("/bin/sh","-c",command,"test",script.path,dir.path).start()
            assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(130,process.exitValue());assertFalse(File(dir,"transaction-ran").exists())
        }finally{dir.deleteRecursively()}
    }
}
