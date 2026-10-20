package com.appforge.studio.build

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/** Strict Android binary XML string-pool writer for the pinned, generated HTML shell. */
internal object HtmlBinaryManifest {
    private const val XML = 0x0003
    private const val STRINGS = 0x0001
    private const val START_ELEMENT = 0x0102
    private const val TYPE_STRING = 0x03
    private const val TYPE_INT_DEC = 0x10
    private const val UTF8_FLAG = 0x100
    private const val TEMPLATE_PACKAGE = "com.appforge.htmlstub"
    private const val TEMPLATE_LABEL = "APPFORGE_HTML_APP"

    fun rewrite(
        input: ByteArray,
        packageName: String,
        appName: String,
        versionCode: Int,
        versionName: String,
        minSdk: Int,
        targetSdk: Int,
        selectedPermissions: Set<String>? = null,
        applicationCategory: Int? = null,
        deepLink: List<String>? = null,
        rewriteDeepLink: Boolean = false,
        mediaService: Boolean? = null,
        billing: Boolean? = null,
        cleartextTraffic: Boolean? = null
    ): ByteArray {
        require(input.size in 64..1_048_576) { "Geçersiz AndroidManifest.xml boyutu." }
        require(u16(input, 0) == XML && u16(input, 2) == 8 && u32(input, 4) == input.size) {
            "Android binary manifest başlığı hatalı."
        }
        require(versionCode > 0 && minSdk in 26..37 && targetSdk in minSdk..37)
        require(packageName.matches(Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")))
        require(appName.isNotBlank() && appName.length <= 100 && versionName.length in 1..64)
        require(appName.none { it == '\u0000' } && versionName.none { it == '\u0000' })

        val source = input.clone()
        var poolStart = -1
        var poolEnd = -1
        var strings = emptyList<String>()
        var isUtf8 = false
        var offset = 8
        var manifestSeen = 0
        var versionCodeSeen = 0
        var versionNameIndex = -1
        var minSdkSeen = 0
        var targetSdkSeen = 0
        require(applicationCategory == null || applicationCategory in setOf(-1, 0))
        var categoryCount = 0
        var cleartextCount = 0
        require(deepLink == null || deepLink.size == 3)
        var filterStart = -1
        var filterIsDeepLink = false
        var deepLinkCount = 0
        val proxies = setOf("com.android.billingclient.api.ProxyBillingActivity", "com.android.billingclient.api.ProxyBillingActivityV2")
        val proxyNames = HashSet<String>()
        var billingMetadata = 0
        var billingStart = -1
        var billingElement: String? = null
        val richKinds=mapOf("com.google.android.gms.common.api.GoogleApiActivity" to "activity",
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery" to "service",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService" to "service",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver" to "receiver",
            "androidx.startup.InitializationProvider" to "provider", "androidx.profileinstaller.ProfileInstallReceiver" to "receiver")
        val richSeen=HashSet<String>()
        val richRequired=richKinds.keys+setOf("gms-version","signature-permission")
        var richStart=-1;var richDepth=0;var richRoot=""
        var serviceCount = 0
        var serviceStart = -1
        var providers = 0
        val inventory = setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.VIBRATE", "android.permission.POST_NOTIFICATIONS", "android.permission.WAKE_LOCK", "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.NFC", "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.USE_BIOMETRIC", "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR", "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.ACTIVITY_RECOGNITION", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", "com.android.vending.BILLING", "$TEMPLATE_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val targetSignature="$packageName.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val templateSelection=selectedPermissions?.map{if(it==targetSignature)"$TEMPLATE_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" else it}?.toSet()
        require(templateSelection == null || templateSelection.all { it in inventory })
        val permissionNames = HashSet<String>()
        val removed = ArrayList<IntRange>()
        var permissionStart = -1
        var omitPermission = false
        while (offset + 8 <= source.size) {
            val kind = u16(source, offset)
            val header = u16(source, offset + 2)
            val length = u32(source, offset + 4)
            require(header >= 8 && length >= header && offset + length <= source.size) {
                "Android manifest chunk sınırı geçersiz."
            }
            if(richStart>=0) {
                if(kind==START_ELEMENT)richDepth++
                if(kind==0x0103) {richDepth--;if(richDepth==0){if(billing==false)removed+=richStart until(offset+length);richStart=-1;richRoot=""}}
            }
            if (kind == 0x0103 && rewriteDeepLink && strings.getOrNull(u32(source, offset + 20)) == "intent-filter") {
                require(filterStart >= 0)
                if (filterIsDeepLink) {
                    deepLinkCount++
                    if (deepLink == null) removed += filterStart until (offset + length)
                }
                filterStart = -1; filterIsDeepLink = false
            }
            if (billingStart >= 0) {
                require(kind == 0x0103 && strings.getOrNull(u32(source,offset+20)) == billingElement) { "Billing component must be an empty fixed element." }
                if (billing == false) removed += billingStart until (offset+length)
                billingStart = -1;billingElement = null
            }
            if (serviceStart >= 0) {
                require(kind == 0x0103 && strings.getOrNull(u32(source, offset+20)) == "service") { "Media service must be an empty pinned element." }
                if (mediaService == false) removed += serviceStart until (offset+length)
                serviceStart = -1
            }
            if (permissionStart >= 0) {
                require(kind == 0x0103 && strings.getOrNull(u32(source, offset + 20)) == "uses-permission") {
                    "Permission must be an empty compiled element."
                }
                if (omitPermission) removed += permissionStart until (offset + length)
                permissionStart = -1
            }
            if (kind == STRINGS) {
                require(poolStart == -1 && header == 28) { "Beklenmeyen string pool yapısı." }
                val count = u32(source, offset + 8)
                val styleCount = u32(source, offset + 12)
                val flags = u32(source, offset + 16)
                val first = u32(source, offset + 20)
                require(styleCount == 0 && count in 1..4096 && first >= 28 + 4 * count)
                require(offset + first < offset + length)
                isUtf8 = flags and UTF8_FLAG != 0
                strings = (0 until count).map { i ->
                    val relative = u32(source, offset + 28 + i * 4)
                    val at = offset + first + relative
                    require(at >= offset + first && at < offset + length)
                    readText(source, at, isUtf8)
                }
                poolStart = offset
                poolEnd = offset + length
            } else if (kind == START_ELEMENT) {
                require(poolStart >= 0 && header == 16 && length >= 36)
                val elementName = strings.getOrNull(u32(source, offset + 20))
                if (rewriteDeepLink && elementName == "intent-filter") { require(filterStart == -1); filterStart = offset }
                if (elementName == "manifest") manifestSeen++
                if (elementName == "provider") providers++
                val providerAttributes = mutableMapOf<String, Int>()
                val attrsStart = u16(source, offset + 24) + offset + 16
                val attrSize = u16(source, offset + 26)
                val attrCount = u16(source, offset + 28)
                require(attrSize == 20 && attrsStart >= offset + 36 && attrsStart + attrCount * 20 <= offset + length)
                repeat(attrCount) { i ->
                    val at = attrsStart + i * 20
                    val attributeName = strings.getOrNull(u32(source, at + 4))
                    if (elementName in setOf("provider", "service", "activity", "meta-data", "receiver", "permission")) {
                        require(attributeName != null && providerAttributes.put(attributeName, at) == null)
                    }
                    when (attributeName) {
                        "usesCleartextTraffic" -> if(elementName=="application" && cleartextTraffic!=null) {
                            require(source[at+15].toInt() and 255==0x12 && u32(source,at+16)==0)
                            put32(source,at+8,-1);put32(source,at+16,if(cleartextTraffic)-1 else 0);cleartextCount++
                        }
                        "appCategory" -> if (elementName == "application" && applicationCategory != null) {
                            require(source[at + 15].toInt() and 255 in setOf(0x10, 0x11))
                            require((0..3).all { source[at + 16 + it].toInt() == 0 })
                            put32(source, at + 8, -1) // Numeric enum has no stale raw "game" spelling.
                            put32(source, at + 16, applicationCategory)
                            categoryCount++
                        }
                        "versionCode" -> if (elementName == "manifest") {
                            require(source[at + 15].toInt() and 255 == TYPE_INT_DEC)
                            put32(source, at + 16, versionCode)
                            versionCodeSeen++
                        }
                        "versionName" -> if (elementName == "manifest") {
                            require(source[at + 15].toInt() and 255 == TYPE_STRING)
                            versionNameIndex = u32(source, at + 16)
                        }
                        "minSdkVersion" -> if (elementName == "uses-sdk") {
                            require(source[at + 15].toInt() and 255 == TYPE_INT_DEC)
                            put32(source, at + 16, minSdk)
                            minSdkSeen++
                        }
                        "targetSdkVersion" -> if (elementName == "uses-sdk") {
                            require(source[at + 15].toInt() and 255 == TYPE_INT_DEC)
                            put32(source, at + 16, targetSdk)
                            targetSdkSeen++
                        }
                    }
                }
                if (rewriteDeepLink && elementName == "data") {
                    require(filterStart >= 0 && !filterIsDeepLink && attrCount == 3)
                    val expected = mapOf("scheme" to "appforge-template", "host" to "appforge.invalid", "pathPrefix" to "/__appforge__")
                    val seen = HashSet<String>()
                    repeat(attrCount) { index ->
                        val at = attrsStart + index * attrSize
                        val name = strings[u32(source, at + 4)]
                        require(seen.add(name) && name in expected && source[at+15].toInt() and 255 == TYPE_STRING)
                        require(strings[u32(source, at+16)] == expected[name])
                    }
                    require(seen == expected.keys); filterIsDeepLink = true
                }
                if (elementName == "uses-permission" && selectedPermissions != null) {
                    require(attrCount == 1)
                    val at = attrsStart
                    require(strings.getOrNull(u32(source, at + 4)) == "name" &&
                        source[at + 15].toInt() and 255 == TYPE_STRING)
                    val name = strings[u32(source, at + 16)]
                    require(name in inventory && permissionNames.add(name)) { "Unexpected or duplicate shell permission." }
                    permissionStart = offset
                    omitPermission = name !in requireNotNull(templateSelection)
                }
                if(elementName in setOf("activity","service","provider","receiver","permission","meta-data")) {
                    fun text(name:String):String {val at=providerAttributes.getValue(name);require(source[at+15].toInt() and 255==TYPE_STRING);return strings[u32(source,at+16)]}
                    fun bool(name:String,value:Boolean) {val at=providerAttributes.getValue(name);require(source[at+15].toInt() and 255==0x12);require((0..3).all { source[at+16+it] == (if(value) 0xff.toByte() else 0.toByte()) })}
                    val component=text("name")
                    if(component in richKinds) {
                        require(elementName==richKinds.getValue(component) && richSeen.add(component) && richStart<0)
                        bool("exported",component=="androidx.profileinstaller.ProfileInstallReceiver")
                        when(component) {
                            "com.google.android.gms.common.api.GoogleApiActivity" -> {require(attrCount==3);val t=providerAttributes.getValue("theme");require(source[t+15].toInt() and 255==1 && u32(source,t+16)==0x01030010)}
                            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery" -> require(attrCount==2)
                            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService" -> {require(attrCount==3);require(text("permission")=="android.permission.BIND_JOB_SERVICE")}
                            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver" -> require(attrCount==2)
                            "androidx.startup.InitializationProvider" -> {require(attrCount==3);require(text("authorities")=="$TEMPLATE_PACKAGE.androidx-startup")}
                            "androidx.profileinstaller.ProfileInstallReceiver" -> {require(attrCount==5);require(text("permission")=="android.permission.DUMP");bool("enabled",true);bool("directBootAware",false)}
                        }
                        richStart=offset;richDepth=1;richRoot=component
                    } else if(elementName=="permission") {
                        require(component=="$TEMPLATE_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" && richSeen.add("signature-permission") && attrCount==2)
                        val level=providerAttributes.getValue("protectionLevel");require(source[level+15].toInt() and 255 in setOf(0x10,0x11) && u32(source,level+16)==2)
                        require(richStart<0);richStart=offset;richDepth=1;richRoot=component
                    } else if(elementName=="meta-data" && component=="com.google.android.gms.version") {
                        require(richSeen.add("gms-version") && attrCount==2)
                        val value=providerAttributes.getValue("value");require(source[value+15].toInt() and 255==1 && u32(source,value+16)==0x7f060000)
                        require(richStart<0);richStart=offset;richDepth=1;richRoot=component
                    }
                    if(elementName=="service")require(component=="$TEMPLATE_PACKAGE.AppForgeMediaService" || component in richKinds)
                    if(elementName=="receiver")require(component in richKinds)
                }
                if (elementName == "activity" || elementName == "meta-data") {
                    val at = providerAttributes["name"] ?: error("Component name missing")
                    require(source[at+15].toInt() and 255 == TYPE_STRING)
                    val component = strings[u32(source,at+16)]
                    val proxy = elementName == "activity" && component in proxies
                    val metadata = elementName == "meta-data" && component == "com.google.android.play.billingclient.version"
                    if (proxy) {
                        require(proxyNames.add(component) && attrCount == 4)
                        val exported = providerAttributes.getValue("exported")
                        require(source[exported+15].toInt() and 255 == 0x12 && u32(source,exported+16)==0)
                        val theme = providerAttributes.getValue("theme")
                        require(source[theme+15].toInt() and 255 == 1 && u32(source,theme+16)==0x01030010)
                        val config = providerAttributes.getValue("configChanges")
                        require(source[config+15].toInt() and 255 in setOf(0x10,0x11) && u32(source,config+16)==0x5b0)
                    }
                    if (metadata) {
                        require(++billingMetadata == 1 && attrCount == 2)
                        val value = providerAttributes.getValue("value")
                        require(source[value+15].toInt() and 255 == TYPE_STRING && strings[u32(source,value+16)] == "9.1.0")
                    }
                    if (proxy || metadata) {billingStart=offset;billingElement=elementName}
                    if (elementName == "activity") require(proxy || component == "$TEMPLATE_PACKAGE.TinyHtmlActivity" || component in richKinds) { "Unknown shell activity." }
                }
                if (elementName == "service" && mediaService != null && strings[u32(source,providerAttributes.getValue("name")+16)] == "$TEMPLATE_PACKAGE.AppForgeMediaService") {
                    require(++serviceCount == 1 && attrCount == 3)
                    val name = providerAttributes.getValue("name")
                    require(source[name+15].toInt() and 255 == TYPE_STRING && strings[u32(source,name+16)] == "$TEMPLATE_PACKAGE.AppForgeMediaService")
                    val exported = providerAttributes.getValue("exported")
                    require(source[exported+15].toInt() and 255 == 0x12 && u32(source,exported+16)==0)
                    val type = providerAttributes.getValue("foregroundServiceType")
                    require(source[type+15].toInt() and 255 in setOf(0x10,0x11) && u32(source,type+16)==2)
                    serviceStart = offset
                }
                if (elementName == "provider" && strings[u32(source,providerAttributes.getValue("name")+16)] != "androidx.startup.InitializationProvider") {
                    fun stringAttribute(name: String): String? {
                        val at = providerAttributes[name] ?: error("Missing provider $name")
                        require(source[at + 15].toInt() and 255 == TYPE_STRING)
                        return strings.getOrNull(u32(source, at + 16))
                    }
                    require(stringAttribute("name") == "$TEMPLATE_PACKAGE.AppForgeShareProvider")
                    require(stringAttribute("authorities") == "$TEMPLATE_PACKAGE.appforge.documents")
                    for ((name, enabled) in listOf("exported" to false, "grantUriPermissions" to true)) {
                        val at = providerAttributes[name] ?: error("Missing provider $name")
                        require(source[at + 15].toInt() and 255 == 0x12)
                        require((0..3).any { source[at + 16 + it].toInt() != 0 } == enabled)
                    }
                }
            }
            offset += length
        }
        require(!rewriteDeepLink || deepLinkCount == 1 && filterStart == -1) { "Deep link template slot mismatch." }
        require(cleartextTraffic==null || cleartextCount==1)
        require(applicationCategory == null || categoryCount == 1) { "Missing or duplicate shell appCategory." }
        require(richStart==-1 && (richSeen.isEmpty() || richSeen==richRequired)) { "SDK component inventory mismatch." }
        require(billing==null || richSeen==richRequired)
        require(billingStart == -1 && proxyNames.size in setOf(0,2) && (proxyNames.isEmpty() == (billingMetadata == 0)))
        require(billing == null || proxyNames == proxies && billingMetadata == 1) { "Billing component inventory mismatch." }
        require(serviceStart == -1 && (mediaService == null || serviceCount == 1)) { "Media service inventory mismatch." }
        require(permissionStart == -1)
        require(selectedPermissions == null || permissionNames == inventory) { "V2 shell permission inventory mismatch." }
        require(offset == source.size && poolStart >= 0 && manifestSeen == 1 && providers == (if(richSeen.isEmpty())1 else 2))
        require(versionCodeSeen == 1 && versionNameIndex in strings.indices)
        require(minSdkSeen == 1 && targetSdkSeen == 1)
        val mutable = strings.toMutableList()
        require(mutable.count { it == TEMPLATE_PACKAGE } == 1) { "Şablon package işareti bulunamadı." }
        require(mutable.count { it == TEMPLATE_LABEL } == 1) { "Şablon label işareti bulunamadı." }
        for ((before,after) in listOf("$TEMPLATE_PACKAGE.androidx-startup" to "$packageName.androidx-startup",
            "$TEMPLATE_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" to targetSignature)) {
            if(!richSeen.isEmpty()){require(mutable.count{it==before}==1);mutable[mutable.indexOf(before)]=after}
        }
        val authority = "$TEMPLATE_PACKAGE.appforge.documents"
        require(mutable.count { it == authority } == 1) { "Missing unique share authority." }
        require(packageName != TEMPLATE_PACKAGE) { "Shell package must be replaced." }
        mutable[mutable.indexOf(authority)] = "$packageName.appforge.documents"
        mutable[mutable.indexOf(TEMPLATE_PACKAGE)] = packageName
        mutable[mutable.indexOf(TEMPLATE_LABEL)] = appName
        mutable[versionNameIndex] = versionName

        if (rewriteDeepLink && deepLink != null) {
            for ((before, after) in listOf("appforge-template", "appforge.invalid", "/__appforge__").zip(deepLink)) {
                require(mutable.count { it == before } == 1)
                mutable[mutable.indexOf(before)] = after
            }
        }
        val pool = encodePool(mutable, isUtf8)
        val out = ByteArrayOutputStream(source.size + pool.size)
        out.write(source, 0, poolStart)
        out.write(pool)
        var copied = poolEnd
        for (range in removed) {
            out.write(source, copied, range.first - copied)
            copied = range.last + 1
        }
        out.write(source, copied, source.size - copied)
        val result = out.toByteArray()
        put32(result, 4, result.size)
        return result
    }

    /** Only the already SHA-pinned APK's fixed NSC slot may be rewritten. */
    fun networkSecurity(input:ByteArray,host:String?):ByteArray {
        require(host==null || host.matches(Regex("[a-z0-9.:-]+")))
        require(input.size in 64..65536 && u16(input,0)==XML && u32(input,4)==input.size)
        val source=input.clone();var offset=8;var poolStart=-1;var poolEnd=-1;var utf8=false
        var strings=emptyList<String>();var domainPolicies=0;var basePolicies=0
        while(offset<source.size) {
            val kind=u16(source,offset);val header=u16(source,offset+2);val length=u32(source,offset+4)
            require(header>=8 && length>=header && offset+length<=source.size)
            if(kind==STRINGS) {
                require(poolStart<0 && header==28 && u32(source,offset+12)==0)
                val count=u32(source,offset+8);val first=u32(source,offset+20);require(count in 1..128 && first>=28+4*count)
                utf8=u32(source,offset+16) and UTF8_FLAG!=0
                strings=(0 until count).map { i->val at=offset+first+u32(source,offset+28+i*4);require(at in offset+first until offset+length);readText(source,at,utf8) }
                poolStart=offset;poolEnd=offset+length
            } else if(kind==START_ELEMENT) {
                require(poolStart>=0 && header==16 && length>=36)
                val element=strings[u32(source,offset+20)]
                if(element in setOf("domain-config","base-config")) {
                    val at=offset+16+u16(source,offset+24)
                    require(u16(source,offset+26)==20 && u16(source,offset+28)==1 && at+20<=offset+length)
                    require(strings[u32(source,at+4)]=="cleartextTrafficPermitted" && source[at+15].toInt() and 255==0x12 && u32(source,at+16)==0)
                    if(element=="domain-config") {domainPolicies++;put32(source,at+8,-1);put32(source,at+16,if(host!=null)-1 else 0)} else basePolicies++
                }
            }
            offset+=length
        }
        require(offset==source.size && domainPolicies==1 && basePolicies==1 && strings.count{it=="appforge-cleartext.invalid"}==1)
        val updated=strings.toMutableList();if(host!=null)updated[updated.indexOf("appforge-cleartext.invalid")]=host
        val pool=encodePool(updated,utf8);val out=ByteArrayOutputStream()
        out.write(source,0,poolStart);out.write(pool);out.write(source,poolEnd,source.size-poolEnd)
        return out.toByteArray().also{put32(it,4,it.size)}
    }

    private fun encodePool(strings: List<String>, utf8: Boolean): ByteArray {
        val bytes = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        for (value in strings) {
            offsets += bytes.size()
            if (utf8) {
                val raw = value.toByteArray(Charsets.UTF_8)
                writeLen8(bytes, value.length)
                writeLen8(bytes, raw.size)
                bytes.write(raw)
                bytes.write(0)
            } else {
                val raw = value.toByteArray(Charsets.UTF_16LE)
                writeLen16(bytes, value.length)
                bytes.write(raw)
                bytes.write(0); bytes.write(0)
            }
        }
        while (bytes.size() % 4 != 0) bytes.write(0)
        val payload = bytes.toByteArray()
        val start = 28 + strings.size * 4
        val out = ByteArray(start + payload.size)
        put16(out, 0, STRINGS)
        put16(out, 2, 28)
        put32(out, 4, out.size)
        put32(out, 8, strings.size)
        put32(out, 12, 0)
        put32(out, 16, if (utf8) UTF8_FLAG else 0)
        put32(out, 20, start)
        put32(out, 24, 0)
        offsets.forEachIndexed { i, value -> put32(out, 28 + i * 4, value) }
        payload.copyInto(out, start)
        return out
    }

    private fun readText(raw: ByteArray, from: Int, utf8: Boolean): String {
        var at = from
        if (utf8) {
            val (_, skip) = readLen8(raw, at); at += skip
            val (length, width) = readLen8(raw, at); at += width
            require(length >= 0 && length <= 65536 && at + length < raw.size && raw[at + length] == 0.toByte())
            return String(raw, at, length, Charsets.UTF_8)
        }
        val (chars, width) = readLen16(raw, at); at += width
        val count = chars * 2
        require(chars in 0..65536 && at + count + 1 < raw.size)
        return String(raw, at, count, Charsets.UTF_16LE)
    }

    private fun readLen8(b: ByteArray, i: Int): Pair<Int, Int> {
        require(i in b.indices)
        val first = b[i].toInt() and 255
        if (first and 128 == 0) return first to 1
        require(i + 1 < b.size)
        return (((first and 127) shl 8) or (b[i + 1].toInt() and 255)) to 2
    }

    private fun readLen16(b: ByteArray, i: Int): Pair<Int, Int> {
        val first = u16(b, i)
        if (first and 32768 == 0) return first to 2
        return (((first and 32767) shl 16) or u16(b, i + 2)) to 4
    }

    private fun writeLen8(out: ByteArrayOutputStream, length: Int) {
        require(length in 0..32767)
        if (length < 128) out.write(length)
        else { out.write((length ushr 8) or 128); out.write(length and 255) }
    }
    private fun writeLen16(out: ByteArrayOutputStream, length: Int) {
        if (length < 32768) { out.write(length and 255); out.write(length ushr 8) }
        else {
            val hi = (length ushr 16) or 32768
            out.write(hi and 255); out.write(hi ushr 8)
            out.write(length and 255); out.write(length ushr 8)
        }
    }
    private fun u16(b: ByteArray, i: Int): Int {
        require(i >= 0 && i + 1 < b.size)
        return (b[i].toInt() and 255) or ((b[i + 1].toInt() and 255) shl 8)
    }
    private fun u32(b: ByteArray, i: Int): Int {
        require(i >= 0 && i + 3 < b.size)
        val value = (b[i].toInt() and 255) or ((b[i+1].toInt() and 255) shl 8) or
            ((b[i+2].toInt() and 255) shl 16) or ((b[i+3].toInt() and 255) shl 24)
        require(value >= 0) { "Android binary XML boyutu/indeksi geçersiz." }
        return value
    }
    private fun put16(b: ByteArray, i: Int, n: Int) {
        b[i] = n.toByte(); b[i + 1] = (n ushr 8).toByte()
    }
    private fun put32(b: ByteArray, i: Int, n: Int) {
        b[i] = n.toByte(); b[i+1] = (n ushr 8).toByte()
        b[i+2] = (n ushr 16).toByte(); b[i+3] = (n ushr 24).toByte()
    }
}
