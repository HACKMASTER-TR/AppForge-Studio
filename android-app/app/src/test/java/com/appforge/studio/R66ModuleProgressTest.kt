package com.appforge.studio

import com.appforge.studio.build.*
import org.junit.Assert.*
import org.junit.Test

class R66ModuleProgressTest {
    private fun event(p:ModulePhase,done:Long=0,total:Long?=null)=
        ModuleProgress(OfflineModuleId.EXPO_REACT_NATIVE,"test",p,done,total)

    @Test fun actualByteRatioAndUnknownTotals() {
        val p=R66ModuleProgress.state(event(ModulePhase.DOWNLOADING,40,100),false,true,false,false)
        assertEquals(.20f,p.fraction,.001f)
        assertTrue(p.label.contains("dosya %40"))
        assertEquals(0f,R66ModuleProgress.state(event(ModulePhase.DOWNLOADING),false,true,false,false).fraction,.001f)
    }
    @Test fun onlyVerifiedInstalledStateIsFull() {
        for(phase in ModulePhase.entries)
            assertTrue(R66ModuleProgress.state(event(phase),false,true,false,false).fraction<1f)
        assertEquals(1f,R66ModuleProgress.state(null,true,false,false,false).fraction,0f)
        assertEquals("HAZIR ✓",R66ModuleProgress.state(null,true,false,false,false).label)
    }
    @Test fun measuredRowsAndStages() {
        assertEquals("10,14 GB",R66ModuleMeasurements.area(OfflineModuleId.RUST_ANDROID_NDK))
        assertEquals("9,42 GB",R66ModuleMeasurements.area(OfflineModuleId.EXPO_REACT_NATIVE))
        assertEquals("1,55 GB",R66ModuleMeasurements.area(OfflineModuleId.WINDOWS_NATIVE))
        assertEquals(.5f,R66ModuleProgress.state(event(ModulePhase.INSTALLING),false,true,false,false).fraction,0f)
        assertEquals(.85f,R66ModuleProgress.state(event(ModulePhase.OFFLINE_TEST),false,true,false,false).fraction,0f)
        assertEquals(0xFFFF8A24,R66ModuleProgress.state(event(ModulePhase.INSTALLING),false,true,false,false).color)
        assertEquals(0xFFFFD42A,R66ModuleProgress.state(event(ModulePhase.OFFLINE_TEST),false,true,false,false).color)
    }
}
