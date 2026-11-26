package com.appforge.studio.build

import org.junit.Test
import org.junit.Assert.*

class R68PinnedAndroidArchivesTest {
    @Test fun unrelatedModulesDoNotReadAndroidCatalog() {
        assertTrue(R68PinnedAndroidArchives.plan("not json",listOf(OfflineModuleId.RUST),true).isEmpty())
        assertTrue(R68PinnedAndroidArchives.plan("not json",listOf(OfflineModuleId.WINDOWS_NATIVE),true).isEmpty())
    }
}
