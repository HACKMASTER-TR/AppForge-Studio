package com.appforge.studio

import com.appforge.studio.build.*
import org.junit.Assert.*
import org.junit.Test

class R67AcquisitionProgressTest {
    private fun sample(phase:ModulePhase)=ModuleProgress(OfflineModuleId.RUST,"r67",phase)
    private fun state(event:ModuleProgress)=R66ModuleProgress.state(event,false,true,false,false)
    @Test fun acquisitionHashIsRedNotFinalVerification() {
        val s=state(sample(ModulePhase.VERIFYING).copy(acquisitionPending=true,bytesDownloaded=100,bytesTotal=100))
        assertEquals(.5f,s.fraction,.001f)
        assertEquals(0xFFEF3340,s.color)
        assertEquals("İndirilen paket doğrulanıyor",s.label)
    }
    @Test fun actualCompletedDependencyUnitsAndLateDownloads() {
        val e=sample(ModulePhase.INSTALLING).copy(installUnitsCompleted=1,installUnitsTotal=2)
        val s=state(e)
        assertEquals(.675f,s.fraction,.001f)
        val late=state(e.copy(phase=ModulePhase.DOWNLOADING,bytesDownloaded=30,bytesTotal=100))
        assertEquals(s.fraction,late.fraction,.001f)
        assertEquals("Ek paket indiriliyor · dosya %30",late.label)
        assertEquals(0xFFEF3340,late.color)
        assertEquals(0xFFFF8A24,state(e.copy(phase=ModulePhase.OFFLINE_TEST)).color)
        val final=state(e.copy(phase=ModulePhase.VERIFYING,installUnitsCompleted=2))
        assertEquals(.85f,final.fraction,.001f)
        assertEquals(0xFFFFD42A,final.color)
    }
}
