package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test

class InstalledModuleReadinessTest {
    private val graph = mapOf("android" to setOf("linux"), "node" to setOf("linux"), "linux" to emptySet())
    @Test fun previouslyVerifiedModulesAreReadyWithoutAnOperationJournal() {
        val checks = mutableMapOf<String,Int>()
        val result = InstalledModuleReadiness.inspect(graph.keys.toList(), { graph.getValue(it) }, {
            checks[it] = (checks[it] ?: 0) + 1; true
        }, { false })
        assertTrue(result.values.all { it })
        assertEquals(mapOf("android" to 1, "node" to 1, "linux" to 1), checks)
    }
    @Test fun aFreshRefreshDetectsRemovedOrRepairedInstallations() {
        var valid = true
        fun refresh() = InstalledModuleReadiness.inspect(graph.keys.toList(), { graph.getValue(it) }, { valid }, { false })
        assertTrue(refresh().getValue("node"))
        valid = false
        assertFalse(refresh().getValue("node"))
        valid = true
        assertTrue(refresh().getValue("node"))
    }
    @Test fun incompleteDependencyAndPendingLifecycleNeverGrantReady() {
        for (pending in listOf(false,true)) {
            val result = InstalledModuleReadiness.inspect(graph.keys.toList(), { graph.getValue(it) }, { it != "linux" || pending }, { pending && it == "linux" })
            assertFalse(result.getValue("node"))
            assertFalse(result.getValue("android"))
        }
    }
    @Test fun corruptInspectionDoesNotHideOtherIndependentModules() {
        val result = InstalledModuleReadiness.inspect(listOf("good","bad"), { emptySet<String>() }, { if(it=="bad")error("corrupt receipt") else true }, { false })
        assertTrue(result.getValue("good"));assertFalse(result.getValue("bad"))
    }
    @Test fun invalidDependencyCyclesFailClosed() {
        val result = InstalledModuleReadiness.inspect(listOf("a","b"), { if(it=="a")setOf("b")else setOf("a") }, { true }, { false })
        assertFalse(result.getValue("a"));assertFalse(result.getValue("b"))
    }
}
