package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Mandatory real AGP outputs: no synthetic manifest can authorize this acceptance. */
class CompiledHtmlTemplateIdentityTest {
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    @Test fun compiledTemplatesHaveIsolatedProviderIdentitiesAndExactPins() {
        val build = File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val metadata = File(build, "generated/html-integrity/assets/html-template-integrity.txt")
            .readLines().associate { val parts=it.split(' '); parts[0] to parts[1] }
        val apk = File(build, "generated/html-template/assets/html-apk-template.apk")
        val aab = File(build, "generated/html-aab-template/assets/html-aab-template.aab")
        assertEquals(metadata[apk.name], hash(apk.readBytes()))
        assertEquals(metadata[aab.name], hash(aab.readBytes()))
        val binary = ZipFile(apk).use { it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes() }
        val proto = ZipFile(aab).use { it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes() }
        val pin = requireNotNull(metadata["html-aab-manifest"])
        assertEquals(pin, hash(proto))
        val outputs = listOf("com.example.first", "com.example.second").map { pkg ->
            val draft=ProjectDraft(packageName=pkg, appName="Provider", versionCode=47)
            val b=HtmlBinaryManifest.rewrite(binary,pkg,draft.appName,47,"1.0.0",26,37)
            val p=FastHtmlAabProtoManifest.rewrite(proto,draft,pin)
            // Independent string decoding below checks exact rewritten binary authority.
            assertTrue(binaryStrings(b).contains("$pkg.appforge.documents"))
            assertFalse(binaryStrings(b).contains("com.appforge.htmlstub.appforge.documents"))
            assertTrue(p.toString(Charsets.UTF_8).contains("$pkg.appforge.documents"))
            b to p
        }
        assertFalse(outputs[0].first.contentEquals(outputs[1].first))
        assertFalse(outputs[0].second.contentEquals(outputs[1].second))
        assertThrows(IllegalArgumentException::class.java) {
            HtmlBinaryManifest.rewrite(binary,"com.appforge.htmlstub","Invalid",1,"1",26,37)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabProtoManifest.rewrite(proto,ProjectDraft(appName="Invalid"),"0".repeat(64))
        }
        val corrupt=proto.clone();corrupt[corrupt.lastIndex]=(corrupt.last().toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabProtoManifest.rewrite(corrupt,ProjectDraft(appName="Invalid"),pin)
        }
    }
    @Test fun v2CompiledApkAndAabHaveExactSelectedPermissionParity() {
        val build = File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val apk = File(build, "generated/html-template/assets/html-apk-template.apk")
        val aab = File(build, "generated/html-aab-template/assets/html-aab-template.aab")
        val binary = ZipFile(apk).use { it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes() }
        val proto = ZipFile(aab).use { it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes() }
        val inventory = setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.VIBRATE", "android.permission.POST_NOTIFICATIONS", "android.permission.WAKE_LOCK",
            "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.NFC",
            "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.USE_BIOMETRIC",
            "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR", "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS",
            "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO", "android.permission.ACTIVITY_RECOGNITION", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", "com.android.vending.BILLING", "com.appforge.htmlstub.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        assertEquals(inventory, binaryPermissions(binary))
        assertEquals(inventory, protoPermissions(proto))
        for (network in listOf(false, true)) for (vibration in listOf(false, true))
            for (notifications in listOf(false, true)) for (bridge in listOf(false, true)) {
                val draft = ProjectDraft(packageName="com.example.v2", appName="V2", networkState=network,
                    vibrationBridge=vibration, notifications=notifications, javascriptBridge=bridge)
                val selected = FastHtmlApkPolicy.templatePermissions(draft)
                val b = HtmlBinaryManifest.rewrite(binary, draft.packageName, draft.appName, 1, "1.0.0", 26, 37, selected)
                val a = FastHtmlAabProtoManifest.rewrite(proto, draft, hash(proto), selected)
                assertEquals(selected, binaryPermissions(b))
                assertEquals(selected, protoPermissions(a))
                val config = org.json.JSONObject(FastHtmlApkPackager.runtimeConfig(draft, "index.html").toString(Charsets.UTF_8))
                assertEquals(network, "android.permission.ACCESS_NETWORK_STATE" in binaryPermissions(b))
                assertEquals(vibration && bridge, "android.permission.VIBRATE" in protoPermissions(a))
                assertEquals(notifications, config.getBoolean("notifications"))
                assertEquals(bridge, config.getBoolean("nativeBridge"))
            }
        assertThrows(IllegalArgumentException::class.java) {
            HtmlBinaryManifest.rewrite(binary,"com.example.denied","Denied",1,"1",26,37,setOf("android.permission.INVENTED"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            FastHtmlAabProtoManifest.rewrite(proto,ProjectDraft(appName="Denied"),hash(proto),setOf("android.permission.INVENTED"))
        }
        for (permission in inventory) {
            val selected=setOf("android.permission.INTERNET",if(permission=="com.appforge.htmlstub.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")"com.example.perm.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" else permission)
            assertEquals(selected,binaryPermissions(HtmlBinaryManifest.rewrite(binary,"com.example.perm","Permission",1,"1",26,37,selected)))
            assertEquals(selected,protoPermissions(FastHtmlAabProtoManifest.rewrite(proto,ProjectDraft(appName="Permission",packageName="com.example.perm"),hash(proto),selected)))
        }
        val minimal = setOf("android.permission.INTERNET")
        val alreadyPruned = HtmlBinaryManifest.rewrite(binary,"com.example.once","Once",1,"1",26,37,minimal)
        assertThrows(IllegalArgumentException::class.java) {
            HtmlBinaryManifest.rewrite(alreadyPruned,"com.example.twice","Twice",1,"1",26,37,minimal)
        }
        val prunedProto = FastHtmlAabProtoManifest.rewrite(proto,ProjectDraft(appName="Once"),hash(proto),minimal)
        assertThrows(IllegalStateException::class.java) {
            FastHtmlAabProtoManifest.rewrite(prunedProto,ProjectDraft(appName="Twice"),hash(prunedProto),minimal)
        }
    }

    @Test fun compiledPrimaryResourcesAndCategorySupportEveryAppearanceSelection() {
        val build = File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val apk = File(build, "generated/html-template/assets/html-apk-template.apk")
        val aab = File(build, "generated/html-aab-template/assets/html-aab-template.aab")
        val binary = ZipFile(apk).use { it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes() }
        val proto = ZipFile(aab).use { it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes() }
        val arsc = ZipFile(apk).use { it.getInputStream(it.getEntry("resources.arsc")).readBytes() }
        val resources = ZipFile(aab).use { it.getInputStream(it.getEntry("base/resources.pb")).readBytes() }
        for(category in listOf("auto","none","game")) for(color in listOf("#6B7CFF","#BC9CF6","#80112233")) {
            val draft=ProjectDraft(appName="Appearance",packageName="com.example.appearance",appCategory=category,primaryColor=color)
            val selected=FastHtmlApkPolicy.applicationCategory(draft)
            val b=HtmlBinaryManifest.rewrite(binary,draft.packageName,draft.appName,1,"1.0.0",26,37,applicationCategory=selected)
            val p=FastHtmlAabProtoManifest.rewrite(proto,draft,hash(proto),applicationCategory=selected)
            assertTrue(b.size>64 && p.size>64)
            val br=FastHtmlResourceColor.binary(arsc,color,draft.packageName)
            val pr=FastHtmlResourceColor.proto(resources,color,draft.packageName)
            assertTrue(pr.toString(Charsets.UTF_8).contains(draft.packageName))
            assertThrows(IllegalArgumentException::class.java) { FastHtmlResourceColor.binary(br,color,"com.example.second") }
            assertThrows(IllegalArgumentException::class.java) { FastHtmlResourceColor.proto(pr,color,"com.example.second") }
        }
        for(bad in listOf("red","#GGGGGG","#12345","#123456789")) {
            assertThrows(IllegalArgumentException::class.java) { FastHtmlResourceColor.binary(arsc,bad,"com.example.denied") }
            assertThrows(IllegalArgumentException::class.java) { FastHtmlResourceColor.proto(resources,bad,"com.example.denied") }
        }
        assertEquals(0xffbc9cf6L,FastHtmlResourceColor.argb("#BC9CF6"))
        assertEquals(0x80112233L,FastHtmlResourceColor.argb("#80112233"))
        assertThrows(IllegalArgumentException::class.java) { FastHtmlResourceColor.binary(arsc.copyOf(30),"#123456","com.example.denied") }
        assertThrows(IllegalArgumentException::class.java) { FastHtmlResourceColor.proto(byteArrayOf(18,127),"#123456","com.example.denied") }
    }

    @Test fun compiledDeepLinkSlotsHandleOnOffAndIconResourcePaths() {
        val build=File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val apk=File(build,"generated/html-template/assets/html-apk-template.apk")
        val aab=File(build,"generated/html-aab-template/assets/html-aab-template.aab")
        val binary=ZipFile(apk).use{it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes()}
        val proto=ZipFile(aab).use{it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes()}
        val resources=ZipFile(apk).use{it.getInputStream(it.getEntry("resources.arsc")).readBytes()}
        val iconPath=FastHtmlResourceColor.binaryIconPath(resources)
        ZipFile(apk).use{assertNotNull(it.getEntry(iconPath))}
        ZipFile(aab).use{assertNotNull(it.getEntry(FastHtmlIcon.AAB_PATH))}
        for(enabled in listOf(false,true)) {
            val draft=ProjectDraft(appName="Link",deepLinkEnabled=enabled,deepLinkScheme="sample",deepLinkHost="example.com",deepLinkPathPrefix="/open")
            val b=HtmlBinaryManifest.rewrite(binary,draft.packageName,draft.appName,1,"1",26,37,
                deepLink=if(enabled)listOf("sample","example.com","/open") else null,rewriteDeepLink=true)
            val p=FastHtmlAabProtoManifest.rewrite(proto,draft,hash(proto),rewriteDeepLinks=true)
            assertEquals(if(enabled)listOf("sample","example.com","/open")else emptyList<String>(),binaryDataValues(b))
            assertEquals(enabled,p.toString(Charsets.UTF_8).contains("example.com"))
            assertFalse(p.toString(Charsets.UTF_8).contains("appforge.invalid"))
        }
        assertThrows(IllegalArgumentException::class.java){FastHtmlResourceColor.binaryIconPath(resources.copyOf(24))}
    }
    @Test fun mediaServiceIsPresentOnlyWhenSelectedInBothCompiledManifests() {
        val build=File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val binary=ZipFile(File(build,"generated/html-template/assets/html-apk-template.apk")).use{it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes()}
        val proto=ZipFile(File(build,"generated/html-aab-template/assets/html-aab-template.aab")).use{it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes()}
        for(enabled in listOf(false,true)) {
            val draft=ProjectDraft(appName="Media",mediaPlayerBridge=enabled,notifications=false,wakeLock=false)
            val selected=FastHtmlApkPolicy.templatePermissions(draft)
            val b=HtmlBinaryManifest.rewrite(binary,draft.packageName,draft.appName,1,"1",26,37,selected,mediaService=enabled,billing=false)
            val p=FastHtmlAabProtoManifest.rewrite(proto,draft,hash(proto),selected,mediaService=enabled,billing=false)
            val strings=binaryStrings(b)
            fun n(i:Int)=java.nio.ByteBuffer.wrap(b,i,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            fun sh(i:Int)=java.nio.ByteBuffer.wrap(b,i,2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
            var at=8;var services=0
            while(at<b.size){if(sh(at)==0x102 && strings[n(at+20)]=="service")services++;at+=n(at+4)}
            assertEquals(if(enabled)1 else 0,services)
            assertEquals(enabled,p.toString(Charsets.UTF_8).contains("com.appforge.htmlstub.AppForgeMediaService"))
            assertEquals(selected,binaryPermissions(b));assertEquals(selected,protoPermissions(p))
        }
    }

    @Test fun billingProxyActivitiesAndPermissionArePrunedWhenOff() {
        val build=File(requireNotNull(System.getProperty("appforge.templateAssets")))
        val binary=ZipFile(File(build,"generated/html-template/assets/html-apk-template.apk")).use{it.getInputStream(it.getEntry("AndroidManifest.xml")).readBytes()}
        val proto=ZipFile(File(build,"generated/html-aab-template/assets/html-aab-template.aab")).use{it.getInputStream(it.getEntry("base/manifest/AndroidManifest.xml")).readBytes()}
        for(enabled in listOf(false,true)) {
            val draft=ProjectDraft(appName="Billing",sourceTechnology="html",importedFolder="/fixture",startPage="index.html",billingEnabled=enabled,billingProductIds="one",purchaseVerificationUrl="https://verify.example/purchase",networkState=false)
            FastHtmlApkPolicy.requireSupported(draft)
            val selected=FastHtmlApkPolicy.templatePermissions(draft)
            val b=HtmlBinaryManifest.rewrite(binary,draft.packageName,draft.appName,1,"1",26,37,selected,billing=enabled)
            val p=FastHtmlAabProtoManifest.rewrite(proto,draft,hash(proto),selected,billing=enabled)
            val strings=binaryStrings(b)
            fun n(i:Int)=java.nio.ByteBuffer.wrap(b,i,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            fun sh(i:Int)=java.nio.ByteBuffer.wrap(b,i,2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
            var at=8;val activities=HashSet<String>();val sdkComponents=HashSet<String>()
            while(at<b.size) {
                if(sh(at)==0x102 && strings[n(at+20)]=="activity")repeat(sh(at+28)){i->val a=at+16+sh(at+24)+i*sh(at+26);if(strings[n(a+4)]=="name")activities+=strings[n(a+16)]}
                if(sh(at)==0x102 && strings[n(at+20)] in setOf("activity","provider","service","receiver","permission"))repeat(sh(at+28)){i->val a=at+16+sh(at+24)+i*sh(at+26);if(strings[n(a+4)]=="name")sdkComponents+=strings[n(a+16)]}
                at+=n(at+4)
            }
            val expected=mutableSetOf("com.appforge.htmlstub.TinyHtmlActivity")
            if(enabled)expected.addAll(setOf("com.android.billingclient.api.ProxyBillingActivity","com.android.billingclient.api.ProxyBillingActivityV2","com.google.android.gms.common.api.GoogleApiActivity"))
            assertEquals(expected,activities)
            val sdkNames=setOf("com.google.android.gms.common.api.GoogleApiActivity",
                "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
                "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
                "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
                "androidx.startup.InitializationProvider","androidx.profileinstaller.ProfileInstallReceiver",
                "${draft.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
            for(name in sdkNames) {
                assertEquals("binary component $name",enabled,name in sdkComponents)
                assertEquals("protobuf component $name",enabled,p.toString(Charsets.UTF_8).contains(name))
            }
            if(enabled)assertTrue(p.toString(Charsets.UTF_8).contains("${draft.packageName}.androidx-startup"))
            assertEquals(enabled,p.toString(Charsets.UTF_8).contains("com.google.android.play.billingclient.version"))
            assertEquals(enabled,p.toString(Charsets.UTF_8).contains("ProxyBillingActivity"))
            assertEquals(selected,binaryPermissions(b));assertEquals(selected,protoPermissions(p))
            assertEquals(enabled,"com.android.vending.BILLING" in selected)
            assertEquals(enabled,"android.permission.ACCESS_NETWORK_STATE" in selected)
        }
    }

    private fun binaryDataValues(b: ByteArray): List<String> {
        val strings=binaryStrings(b);val values=ArrayList<String>()
        fun n(i:Int)=java.nio.ByteBuffer.wrap(b,i,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        fun s(i:Int)=java.nio.ByteBuffer.wrap(b,i,2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
        var at=8
        while(at<b.size) {
            if(s(at)==0x102 && strings[n(at+20)]=="data") {
                val attrs=(0 until s(at+28)).associate {i->val a=at+16+s(at+24)+i*s(at+26);strings[n(a+4)] to strings[n(a+16)]}
                assertEquals(setOf("scheme","host","pathPrefix"),attrs.keys)
                values.addAll(listOf(attrs.getValue("scheme"),attrs.getValue("host"),attrs.getValue("pathPrefix")))
            }
            at+=n(at+4)
        }
        return values
    }

    // Independent readers inspect element attributes, not unused string-pool entries.
    private fun binaryPermissions(b: ByteArray): Set<String> {
        val strings = binaryStrings(b)
        fun n(i: Int) = java.nio.ByteBuffer.wrap(b,i,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        fun s(i: Int) = java.nio.ByteBuffer.wrap(b,i,2).order(java.nio.ByteOrder.LITTLE_ENDIAN).short.toInt() and 65535
        val permissions = mutableListOf<String>()
        var at = 8
        while (at < b.size) {
            if (s(at) == 0x102 && strings[n(at+20)] == "uses-permission") {
                val start = at+16+s(at+24)
                repeat(s(at+28)) { index ->
                    val attr = start+index*s(at+26)
                    if (strings[n(attr+4)] == "name") permissions += strings[n(attr+16)]
                }
            }
            at += n(at+4)
        }
        assertEquals(permissions.size, permissions.toSet().size)
        return permissions.toSet()
    }
    private fun protoPermissions(b: ByteArray): Set<String> {
        fun fields(bytes: ByteArray): List<Pair<Int,ByteArray>> {
            var at=0
            fun number(): Int { var value=0;var shift=0;do {val next=bytes[at++].toInt() and 255;value=value or ((next and 127) shl shift);shift+=7;if(next and 128==0)return value} while(shift<35);error("varint") }
            val result=mutableListOf<Pair<Int,ByteArray>>()
            while(at<bytes.size) {
                val tag=number()
                when(tag and 7) {
                    0 -> number()
                    2 -> {val size=number();result += (tag ushr 3) to bytes.copyOfRange(at,at+size);at+=size}
                    1 -> at+=8
                    5 -> at+=4
                    else -> error("wire")
                }
            }
            return result
        }
        val root = fields(fields(b).single { it.first==1 }.second)
        val result = mutableListOf<String>()
        for (node in root.filter { it.first==5 }) {
            val element = fields(node.second).singleOrNull { it.first==1 } ?: continue
            val elementFields = fields(element.second)
            if (elementFields.single { it.first==3 }.second.toString(Charsets.UTF_8) != "uses-permission") continue
            val attr = elementFields.filter { it.first==4 }.map { fields(it.second) }.single {
                it.single { f -> f.first==2 }.second.toString(Charsets.UTF_8)=="name"
            }
            result += attr.single { it.first==3 }.second.toString(Charsets.UTF_8)
        }
        assertEquals(result.size, result.toSet().size)
        return result.toSet()
    }

    private fun binaryStrings(b: ByteArray): List<String> {
        fun n(i: Int)=java.nio.ByteBuffer.wrap(b,i,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
        var at=8
        while ((b[at].toInt() and 255) != 1 || b[at+1].toInt()!=0) at+=n(at+4)
        val count=n(at+8); val utf8=n(at+16) and 256 != 0; val start=at+n(at+20)
        return (0 until count).map {
            var p=start+n(at+28+4*it)
            if(utf8) {
                fun len(): Int {val first=b[p++].toInt() and 255;return if(first and 128==0)first else ((first and 127) shl 8) or (b[p++].toInt() and 255)}
                len();val size=len();String(b,p,size,Charsets.UTF_8)
            } else {
                var size=(b[p++].toInt() and 255) or ((b[p++].toInt() and 255) shl 8)
                if(size and 32768!=0) {size=((size and 32767) shl 16) or (b[p++].toInt() and 255) or ((b[p++].toInt() and 255) shl 8)}
                String(b,p,size*2,Charsets.UTF_16LE)
            }
        }
    }
}
