package com.appforge.studio.build

import org.junit.Assert.*
import org.junit.Test

class TemplateIntegrityTest {
    private val emptySha="e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    @Test fun exactGeneratedMetadataAndBytesAreRequired() {
        val rows=listOf("html-apk-template.apk $emptySha","html-aab-template.aab $emptySha","html-aab-manifest $emptySha")
        assertEquals(emptySha,TemplateIntegrity.parse(rows,"html-aab-manifest"))
        TemplateIntegrity.requireDigest(byteArrayOf(),emptySha)
        assertThrows(IllegalArgumentException::class.java) { TemplateIntegrity.requireDigest(byteArrayOf(1),emptySha) }
        for (invalid in listOf(rows.drop(1),rows+rows.first(),rows.map{it.replace(emptySha,"unknown")},
            listOf(rows[0],rows[0],rows[2]),rows.map{it.replace("html-aab-manifest","other")})) {
            assertThrows(IllegalArgumentException::class.java) { TemplateIntegrity.parse(invalid,"html-aab-manifest") }
        }
    }
}
