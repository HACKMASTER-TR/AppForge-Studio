package com.appforge.studio.build

import java.io.File
import java.nio.file.Files
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

internal class AptSnapshotLockTest {
    private fun fixture(test:(File,File)->Unit) {
        val root=Files.createTempDirectory("apt-lock").toFile()
        try {
            fun write(path:String,body:String)=File(root,path).apply{parentFile.mkdirs();writeText(body)}
            write("usr/lib/os-release","ID=ubuntu\nVERSION_CODENAME=noble\n")
            val lock=File(root,"opt/appforge-device/apt-locks-v5/ANDROID_CORE/snapshot-v1/"+"a".repeat(64)).apply{mkdirs()}
            val store=File(root,"opt/appforge-device/apt-snapshot-v1/amd64-${AptModuleLocks.SNAPSHOT}").apply{mkdirs()}
            val lists=File(store,"lists").apply{mkdirs()}
            for(suite in listOf("noble","noble-updates","noble-security","noble-backports")) {
                File(lists,"snapshot.ubuntu.com_ubuntu_${AptModuleLocks.SNAPSHOT}_dists_${suite}_InRelease").writeBytes(javaClass.getResourceAsStream("/apt-snapshot/${suite}_InRelease.asc")!!.readBytes())
            }
            File(store,"indexes.sha256").writeText(lists.listFiles()!!.sortedBy{it.name}.joinToString(""){VerifiedModuleDownload.sha(it)+"  "+it.name+"\n"})
            write("var/lib/dpkg/status","Package: hello\nVersion: 2.10-3build1\nArchitecture: amd64\nStatus: install ok installed\n\n")
            val installed=write("usr/bin/hello","fixture executable")
            val archive=write("archive-fixture.deb","fixture archive");val hash=VerifiedModuleDownload.sha(archive)
            File(store,"archives").mkdirs();archive.copyTo(File(store,"archives/$hash.deb"));archive.delete()
            File(lock,"identity").writeText("schema=1 module=ANDROID_CORE ubuntu=noble arch=amd64 snapshot=${AptModuleLocks.SNAPSHOT}\n")
            File(lock,"profile.sha256").writeText(AptModuleLocks.PROFILE+"\n")
            File(lock,"catalog.sha256").writeText(AptModuleLocks.CATALOG+"\n")
            File(lock,"metadata.sha256").writeText(VerifiedModuleDownload.sha(File(store,"indexes.sha256"))+"\n")
            File(lock,"requests.txt").writeText("hello\n");File(lock,"baseline.tsv").writeText("");File(lock,"plan.tsv").writeText("fixture plan")
            File(lock,"packages.tsv").writeText("hello\t2.10-3build1\tamd64\t$hash\t${File(store,"archives/$hash.deb").length()}\thttps://snapshot.ubuntu.com/ubuntu/${AptModuleLocks.SNAPSHOT}/pool/main/h/hello/hello.deb\n")
            File(lock,"dependencies.tsv").writeText("hello\tlibc6 (>= 2.34)\t\n")
            File(lock,"installed.tsv").writeText("hello\t2.10-3build1\tamd64\n")
            File(lock,"installed-files.sha256").writeText(VerifiedModuleDownload.sha(installed)+"  /usr/bin/hello\n")
            File(lock,"verification").writeText("APT_NO_DOWNLOAD_INSTALLED\n");seal(lock)
            val canonical=File(lock.parentFile,VerifiedModuleDownload.sha(File(lock,"requests.txt")));assertTrue(lock.renameTo(canonical))
            test(root,canonical)
        }finally{root.deleteRecursively()}
    }
    private fun seal(lock:File) {File(lock,"manifest.sha256").writeText(lock.listFiles()!!.filter{it.name!="manifest.sha256"}.sortedBy{it.name}.joinToString(""){VerifiedModuleDownload.sha(it)+"  "+it.name+"\n"})}
    @Test fun sameLockSamePackagesAndUniqueBytes()=fixture{root,lock->
        val first=AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)
        assertEquals(first,AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE))
        val duplicate=File(lock.parentFile,"b".repeat(64));lock.copyRecursively(duplicate)
        File(duplicate,"requests.txt").appendText("--no-upgrade\n");seal(duplicate)
        assertTrue(duplicate.renameTo(File(duplicate.parentFile,VerifiedModuleDownload.sha(File(duplicate,"requests.txt")))))
        val second=AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)
        assertEquals(2,second.transactions);assertEquals(first.archiveBytes,second.archiveBytes);assertEquals(1,second.packages)
    }
    @Test fun alteredDistributionArchitectureAndProfileRejected()=fixture{root,lock->
        val f=File(lock,"identity");val valid=f.readText()
        for(change in listOf(valid.replace("noble","jammy"),valid.replace("amd64","arm64"),valid.replace("20261018","20261019"))) {
            f.writeText(change);seal(lock);assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
        }
        f.writeText(valid);File(lock,"profile.sha256").writeText("0".repeat(64));seal(lock)
        assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
    }
    @Test fun missingAndWrongArchiveHashRejected()=fixture{root,_->
        val cache=File(root,"opt/appforge-device/apt-snapshot-v1/amd64-${AptModuleLocks.SNAPSHOT}/archives").listFiles()!!.single()
        cache.writeText("corrupted");assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
        cache.delete();assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
    }
    @Test fun metadataAndInstalledFileTamperingRejected()=fixture{root,_->
        val file=File(root,"usr/bin/hello");file.writeText("modified")
        assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
    }
    @Test fun signedRepositoryIdentityCannotBeReplacedByLocalChecksum()=fixture{root,lock->
        val store=File(root,"opt/appforge-device/apt-snapshot-v1/amd64-${AptModuleLocks.SNAPSHOT}")
        val release=File(store,"lists").listFiles()!!.first();release.appendText("altered signed data")
        File(store,"indexes.sha256").writeText(File(store,"lists").listFiles()!!.joinToString(""){VerifiedModuleDownload.sha(it)+"  "+it.name+"\n"})
        File(lock,"metadata.sha256").writeText(VerifiedModuleDownload.sha(File(store,"indexes.sha256")));seal(lock)
        assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
    }
    @Test fun missingDependencyAndVersionConflictRejected()=fixture{root,_->
        val status=File(root,"var/lib/dpkg/status");val body=status.readText()
        status.writeText(body.replace("2.10-3build1","2.10-4"));assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
        status.writeText("");assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
    }
    @Test fun symlinksAndTraversalRejected()=fixture{root,lock->
        val f=File(root,"usr/bin/hello");val outside=File(root.parentFile,"apt-outside-${root.name}").apply{writeText("user key fixture")}
        try {f.delete();Files.createSymbolicLink(f.toPath(),outside.toPath());assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
            f.delete();File(lock,"installed-files.sha256").writeText(VerifiedModuleDownload.sha(outside)+"  /../${outside.name}\n");seal(lock)
            assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
            assertEquals("user key fixture",outside.readText())
        }finally{outside.delete()}
    }
    @Test fun markerAloneAndInterruptedLockRejected()=fixture{root,lock->
        File(lock,"packages.tsv").delete();assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
        lock.renameTo(File(lock.path+".part"));assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
    }
    @Test fun importedPlanDoesNotGrantConsentAndExactOperationRequired() {
        val workspace=Files.createTempDirectory("apt-consent").toFile()
        try {
            val p=File(workspace,"apt-consent.plan.tsv").apply{writeText("hello_2.10-3build1_amd64.deb\t26006\t${"a".repeat(64)}\thttps://snapshot.ubuntu.com/ubuntu/${AptModuleLocks.SNAPSHOT}/pool/main/h/hello.deb\n")}
            File(workspace,"apt-consent.request.json").writeText(JSONObject().put("schema",1).put("module","ANDROID_CORE").put("sha256",VerifiedModuleDownload.sha(p)).toString())
            val event=AptDownloadConsent.poll(workspace,OfflineModuleId.ANDROID_CORE,"operation-one")!!
            assertEquals(ModulePhase.AWAITING_CONSENT,event.phase);assertEquals(26006L,event.bytesTotal)
            assertFalse(File(workspace,"apt-consent.approved").exists())
            assertThrows(IllegalArgumentException::class.java){AptDownloadConsent.approve("other-operation")}
            AptDownloadConsent.approve("operation-one");assertEquals(VerifiedModuleDownload.sha(p),File(workspace,"apt-consent.approved").readText().trim())
            p.appendText("changed");assertThrows(IllegalArgumentException::class.java){AptDownloadConsent.approve("operation-one")}
        }finally{AptDownloadConsent.clear("operation-one");workspace.deleteRecursively()}
    }
    @Test fun actualUbuntuReleaseMismatchRejected()=fixture{root,_->
        File(root,"usr/lib/os-release").writeText("ID=ubuntu\nVERSION_CODENAME=jammy\n")
        assertThrows(IllegalArgumentException::class.java){AptModuleLocks.inspect(root,OfflineModuleId.ANDROID_CORE)}
    }
    @Test fun legacyMigrationBlockedWithoutBlessingReceipts() {
        val root=Files.createTempDirectory("legacy-apt").toFile()
        try {
            assertNull(AptSnapshotMigration.blockedReason(root))
            val receipt=File(root,"opt/appforge-device/modules-v3/NODE_WEB.json").apply{parentFile.mkdirs();writeText("legacy fixture")}
            assertNotNull(AptSnapshotMigration.blockedReason(root))
            val facts=ModuleLifecyclePolicy.Facts(OfflineModuleId.NODE_WEB,true,true,null,null,null,updateError=AptSnapshotMigration.blockedReason(root))
            val policy=ModuleLifecyclePolicy.evaluate(facts);assertFalse(policy.update.allowed);assertTrue(policy.rollback.allowed)
            assertEquals("legacy fixture",receipt.readText())
            File(root,"opt/appforge-device/apt-snapshot-v1/amd64-${AptModuleLocks.SNAPSHOT}/managed-origin").apply{parentFile.mkdirs();writeText("fresh-managed-snapshot-v1\n")}
            assertNull(AptSnapshotMigration.blockedReason(root))
        }finally{root.deleteRecursively()}
    }
    @Test fun phaseEventsDoNotForgePercentOrReady() {
        val workspace=Files.createTempDirectory("apt-phase").toFile()
        try {
            val file=File(workspace,"apt-phase.json")
            fun write(phase:String){file.writeText(JSONObject().put("schema",1).put("module","ANDROID_CORE").put("phase",phase).put("detail","fixture phase").toString())}
            write("INSTALLING");val event=AptPhaseProgress.poll(workspace,OfflineModuleId.ANDROID_CORE,"op")!!
            assertNull(event.percent);assertEquals(ModulePhase.INSTALLING,event.phase)
            write("DOWNLOADING");assertNull(AptPhaseProgress.poll(workspace,OfflineModuleId.ANDROID_CORE,"op"))
            write("READY");assertThrows(IllegalArgumentException::class.java){AptPhaseProgress.poll(workspace,OfflineModuleId.ANDROID_CORE,"op")}
        }finally{workspace.deleteRecursively()}
    }

}
