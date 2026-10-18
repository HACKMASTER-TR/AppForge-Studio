package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import java.io.File
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ProjectDependencyEvidenceTest {
    private fun draft(root:File)=ProjectDraft(importedFolder=root.path,sourceBuildEngine="node-web",sourceTechnology="node")
    private fun source(root:File) {
        File(root,"package.json").writeText("""{"name":"fixture","version":"1.0.0"}""")
        File(root,"package-lock.json").writeText("""{"lockfileVersion":3,"packages":{"":{"name":"fixture","version":"1.0.0"}}}""")
        File(root,"index.js").writeText("console.log('fixture');")
    }
    @Test fun noExecutionDuringAnalysisAndOfflineReceiptRequiresUnchangedSourceAndExactCache() {
        val root=Files.createTempDirectory("dependency-source").toFile();val privateDir=Files.createTempDirectory("dependency-state").toFile()
        try {
            source(root);val d=draft(root);val plan=ProjectDependencyEvidence.analyze(d);assertTrue(plan.blockers.isEmpty())
            assertFalse(d.allowDependencyDownloads)
            val cache=File(privateDir,"cache").apply{mkdir()};val jar=File(cache,"artifact.jar").apply{writeText("actual bytes")};val record=File(privateDir,"proof.json")
            ProjectDependencyEvidence.record(record,plan,listOf(cache),false);assertFalse(ProjectDependencyEvidence.ready(record,plan,listOf(cache)))
            ProjectDependencyEvidence.record(record,plan,listOf(cache),true);assertTrue(ProjectDependencyEvidence.ready(record,plan,listOf(cache)))
            jar.writeText("tampered");assertFalse(ProjectDependencyEvidence.ready(record,plan,listOf(cache)))
            jar.writeText("actual bytes");File(cache,"other-version.jar").writeText("new resolution")
            assertFalse(ProjectDependencyEvidence.ready(record,plan,listOf(cache)))
            File(root,"index.js").appendText("changed");assertNotEquals(plan.fingerprint,ProjectDependencyEvidence.analyze(d).fingerprint)
        }finally{root.deleteRecursively();privateDir.deleteRecursively()}
    }
    @Test fun weakNpmPinsGradleMissingChecksumsAndPythonUnhashedRequirementsAreBlocked() {
        val root=Files.createTempDirectory("dependency-lock").toFile()
        try {
            source(root)
            File(root,"package-lock.json").writeText("""{"lockfileVersion":3,"packages":{"node_modules/example":{"version":"1.0.0","resolved":"http://example.invalid/package.tgz","integrity":"sha1-untrusted"}}}""")
            assertTrue(ProjectDependencyEvidence.analyze(draft(root)).blockers.size>=2)
            assertTrue(ProjectDependencyEvidence.analyze(draft(root).copy(sourceBuildEngine="android-gradle")).blockers.size>=2)
            File(root,"requirements.txt").writeText("example==1.0.0")
            assertTrue(ProjectDependencyEvidence.analyze(draft(root).copy(sourceBuildEngine="python-android")).blockers.isNotEmpty())
        }finally{root.deleteRecursively()}
    }
    @Test fun sourceSnapshotPreservesOriginalAndRejectsSymlinkEscapes() {
        val root=Files.createTempDirectory("dependency-reviewed").toFile();val base=Files.createTempDirectory("dependency-copy").toFile()
        try {
            source(root);val d=draft(root);val copy=File(base,"source")
            ProjectDependencyEvidence.copyReviewedSource(d,copy)
            assertEquals(ProjectDependencyEvidence.analyze(d).fingerprint,ProjectDependencyEvidence.analyze(d.copy(importedFolder=copy.path)).fingerprint)
            File(copy,"index.js").writeText("build changes only copied source")
            assertEquals("console.log('fixture');",File(root,"index.js").readText())
            Files.createSymbolicLink(File(root,"foreign").toPath(),File(base,"source").toPath())
            assertThrows(IllegalArgumentException::class.java){ProjectDependencyEvidence.analyze(d)}
        }finally{root.deleteRecursively();base.deleteRecursively()}
    }
    @Test fun exclusivePreparationPermitBlocksBuildAndMutationUntilProofEnds() {
        val permit=BuildModuleUsage.preparationLease()
        try {
            assertThrows(IllegalStateException::class.java){BuildModuleUsage.buildLease()}
            assertThrows(IllegalStateException::class.java){BuildModuleUsage.mutationLease()}
            BuildModuleUsage.buildLease(permit).close()
            assertThrows(IllegalStateException::class.java){BuildModuleUsage.mutationLease()}
        }finally{permit.close()}
        BuildModuleUsage.mutationLease().close()
    }
}
