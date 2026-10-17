package com.appforge.studio.build

import com.appforge.studio.model.ProjectDraft
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** AAPT2 Resources.proto XmlNode / XmlElement / XmlAttribute / Item / Primitive codec.
 * Strictly for the pinned Android Gradle HTML shell. Not a generic protobuf XML editor.
 * Unrecognized wire fields survive unchanged; unexpected identity shapes fail closed.
 */
internal object FastHtmlAabProtoManifest {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val STUB_PACKAGE = "com.appforge.htmlstub"
    private const val STUB_ACTIVITY = "com.appforge.htmlstub.TinyHtmlActivity"
    private const val STUB_LABEL = "APPFORGE_HTML_APP"
    private const val MAX_PROTO = 1_048_576
    private const val PINNED_MANIFEST_SHA256 = "42ce28b60c895c315870ce6fb3de01c176fbbf29b805efc2e09e7b363aea2256"
    private const val MAX_FIELDS = 4096

    private data class Field(val id: Int, val wire: Int, val bytes: ByteArray, val raw: ByteArray, val value: Long = -1)

    fun rewrite(input: ByteArray, draft: ProjectDraft, expectedManifestSha256: String = PINNED_MANIFEST_SHA256, selectedPermissions: Set<String>? = null, applicationCategory: Int? = null, rewriteDeepLinks: Boolean = false, mediaService: Boolean? = null, billing: Boolean? = null): ByteArray {
        require(draft.packageName.length <= 255 && draft.packageName.matches(
            Regex("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")
        )) { "Invalid application package." }
        require(draft.appName.isNotBlank() && draft.appName.length <= 100)
        require(draft.versionCode > 0 && draft.versionName.length in 1..64)
        require((draft.appName + draft.versionName).none { it.code < 32 || it.code == 127 })
        require(draft.minSdk == 26 && draft.targetSdk == 37) { "AAB shell requires SDK 26/37." }
        require(draft.packageName != STUB_PACKAGE) { "Shell package must be replaced." }
        requireWireFraming(input)
        require(MessageDigest.getInstance("SHA-256").digest(input).joinToString("") {
            "%02x".format(it.toInt() and 255)
        } == expectedManifestSha256) { "Unrecognized compiled HTML manifest template." }
        val root = parse(input)
        check(only(root, 1, 2).size == 1 && only(root, 2, 2).isEmpty()) { "Root must be one XmlNode element." }
        val rewritten = updateFields(root) { field ->
            if (field.id == 1) {
                lengthField(1, rewriteElement(field.bytes, "manifest", draft, selectedPermissions, applicationCategory, rewriteDeepLinks, mediaService, billing))
            } else field.raw
        }
        // Parse the result independently of the input fields, checking exact identity and class.
        val manifestNode = one(parse(rewritten), 1, 2)
        val manifestElement = parse(manifestNode.bytes)
        check(value(manifestElement, 3) == "manifest")
        val attrs = attributes(manifestElement)
        check(attribute(attrs, "", "package") == draft.packageName)
        check(attribute(attrs, ANDROID_NS, "versionCode") == draft.versionCode.toString())
        check(attribute(attrs, ANDROID_NS, "versionName") == draft.versionName)
        val children = elements(manifestElement)
        val sdk = child(children, "uses-sdk")
        val sdkAttributes = attributes(sdk)
        check(attribute(sdkAttributes, ANDROID_NS, "minSdkVersion") == "26")
        check(attribute(sdkAttributes, ANDROID_NS, "targetSdkVersion") == "37")
        val app = child(children, "application")
        check(attribute(attributes(app), ANDROID_NS, "label") == draft.appName)
        val activity = elements(app).single { value(it,3)=="activity" && attribute(attributes(it),ANDROID_NS,"name")==STUB_ACTIVITY }
        check(attribute(attributes(activity), ANDROID_NS, "name") == STUB_ACTIVITY)
        run {
            val provider = elements(app).single { value(it,3)=="provider" && attribute(attributes(it),ANDROID_NS,"name")=="$STUB_PACKAGE.AppForgeShareProvider" }
            val providerAttrs = attributes(provider)
            check(attribute(providerAttrs, ANDROID_NS, "name") == "$STUB_PACKAGE.AppForgeShareProvider")
            check(attribute(providerAttrs, ANDROID_NS, "authorities") == "${draft.packageName}.appforge.documents")
            check(attribute(providerAttrs, ANDROID_NS, "exported") == "false")
            check(attribute(providerAttrs, ANDROID_NS, "grantUriPermissions") == "true")
        }
        check(rewritten.size <= MAX_PROTO)
        return rewritten
    }

    private fun rewriteElement(input: ByteArray, kind: String, draft: ProjectDraft, selectedPermissions: Set<String>?, applicationCategory: Int?, rewriteDeepLinks: Boolean, mediaService: Boolean?, billing: Boolean?): ByteArray {
        val fields = parse(input)
        check(value(fields, 3) == kind) { "Unexpected AAPT2 XML element." }
        val attrKeys = HashSet<String>()
        for (f in fields.filter { it.id == 4 }) {
            check(f.wire == 2)
            val attr = parse(f.bytes)
            val key = value(attr, 1, optional = true) + "|" + value(attr, 2)
            check(attrKeys.add(key)) { "Duplicate AAPT2 XML attribute." }
        }
        val inventory = setOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.VIBRATE", "android.permission.POST_NOTIFICATIONS", "android.permission.WAKE_LOCK", "android.permission.CAMERA", "android.permission.RECORD_AUDIO", "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_FINE_LOCATION", "android.permission.NFC", "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT", "android.permission.USE_BIOMETRIC", "android.permission.READ_CALENDAR", "android.permission.WRITE_CALENDAR", "android.permission.READ_CONTACTS", "android.permission.WRITE_CONTACTS", "android.permission.ACCESS_BACKGROUND_LOCATION", "android.permission.SCHEDULE_EXACT_ALARM", "android.permission.READ_MEDIA_IMAGES", "android.permission.READ_MEDIA_VIDEO", "android.permission.ACTIVITY_RECOGNITION", "android.permission.FOREGROUND_SERVICE", "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK", "com.android.vending.BILLING", "$STUB_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val targetSignature="${draft.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val templateSelection=selectedPermissions?.map{if(it==targetSignature)"$STUB_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION" else it}?.toSet()
        require(templateSelection == null || templateSelection.all { it in inventory })
        val permissionNames = HashSet<String>()
        require(applicationCategory == null || applicationCategory in setOf(-1, 0))
        var deepLinkCount = 0
        var categoryCount = 0
        var packageCount = 0
        var versionCodeCount = 0
        var versionNameCount = 0
        var labelCount = 0
        var sdkMinCount = 0
        var sdkTargetCount = 0
        var activityNameCount = 0
        var appCount = 0
        var sdkCount = 0
        var activityCount = 0
        val proxies=setOf("com.android.billingclient.api.ProxyBillingActivity","com.android.billingclient.api.ProxyBillingActivityV2")
        val proxyNames=HashSet<String>()
        var billingMetadata=0
        val richKinds=mapOf("com.google.android.gms.common.api.GoogleApiActivity" to "activity",
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery" to "service",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService" to "service",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver" to "receiver",
            "androidx.startup.InitializationProvider" to "provider", "androidx.profileinstaller.ProfileInstallReceiver" to "receiver")
        val richSeen=HashSet<String>()
        val richRequired=richKinds.keys+setOf("gms-version","signature-permission")
        var serviceCount = 0
        var providerCount = 0
        var authorityCount = 0
        val output = updateFields(fields) { f ->
            when (f.id) {
                4 -> {
                    val attr = parse(f.bytes)
                    val ns = value(attr, 1, optional = true)
                    val name = value(attr, 2)
                    val before = value(attr, 3)
                    val replacement = when {
                        kind == "application" && ns == ANDROID_NS && name == "appCategory" && applicationCategory != null -> {
                            check(before == "game" || before == "0"); categoryCount++; applicationCategory.toString()
                        }
                        kind == "manifest" && ns == "" && name == "package" -> {
                            check(before == STUB_PACKAGE); packageCount++; draft.packageName
                        }
                        kind == "manifest" && ns == ANDROID_NS && name == "versionCode" -> {
                            check(before == "1"); versionCodeCount++; draft.versionCode.toString()
                        }
                        kind == "manifest" && ns == ANDROID_NS && name == "versionName" -> {
                            check(before == "1.0.0"); versionNameCount++; draft.versionName
                        }
                        kind == "uses-sdk" && ns == ANDROID_NS && name == "minSdkVersion" -> {
                            check(before == "26"); sdkMinCount++; before
                        }
                        kind == "uses-sdk" && ns == ANDROID_NS && name == "targetSdkVersion" -> {
                            check(before == "37"); sdkTargetCount++; before
                        }
                        kind == "application" && ns == ANDROID_NS && name == "label" -> {
                            check(before == STUB_LABEL); labelCount++; draft.appName
                        }
                        kind == "provider" && ns == ANDROID_NS && name == "authorities" -> {
                            check(before == "$STUB_PACKAGE.appforge.documents"); authorityCount++
                            "${draft.packageName}.appforge.documents"
                        }
                        kind == "activity" && ns == ANDROID_NS && name == "name" -> {
                            check(before == STUB_ACTIVITY); activityNameCount++; before
                        }
                        else -> null
                    }
                    if (replacement == null) f.raw else {
                        val compiled = attr.filter { it.id == 6 }
                        if (kind == "application" && name == "appCategory" && applicationCategory != null && ns == ANDROID_NS) {
                            check(compiled.size == 1 && compiled[0].wire == 2)
                            val item = parse(compiled[0].bytes)
                            check(item.size == 1 && item[0].id == 7 && item[0].wire == 2)
                            val prim = parse(item[0].bytes)
                            check(prim.size == 1 && prim[0].id in setOf(6,7) && prim[0].wire == 0 &&
                                prim[0].value == 0L)
                            val newItem = lengthField(7, varintField(6, applicationCategory.toLong()))
                            lengthField(4, updateFields(attr) { a -> when(a.id) {
                                3 -> lengthField(3, replacement.toByteArray(StandardCharsets.UTF_8))
                                6 -> lengthField(6, newItem)
                                else -> a.raw
                            } })
                        } else if (kind == "manifest" && name == "versionCode" && ns == ANDROID_NS) {
                            check(compiled.size == 1 && compiled[0].wire == 2)
                            val item = parse(compiled[0].bytes)
                            check(item.size == 1 && item[0].id == 7 && item[0].wire == 2)
                            val prim = parse(item[0].bytes)
                            check(prim.size == 1 && prim[0].id == 6 && prim[0].wire == 0 && prim[0].value == 1L)
                            val newItem = lengthField(7, varintField(6, draft.versionCode.toLong()))
                            lengthField(4, updateFields(attr) { a ->
                                when (a.id) {
                                    3 -> lengthField(3, replacement.toByteArray(StandardCharsets.UTF_8))
                                    6 -> lengthField(6, newItem)
                                    else -> a.raw
                                }
                            })
                        } else {
                            // Fixed SDK primitive attributes must remain byte-identical. Other
                            // editable string attributes of this shell must not carry compiled_item.
                            if (kind == "uses-sdk") {
                                check(compiled.size == 1 && compiled[0].wire == 2)
                                check(f.raw.isNotEmpty()); f.raw
                            } else {
                                check(compiled.isEmpty()) { "Unsupported compiled XML identity attribute." }
                                lengthField(4, updateFields(attr) { a ->
                                    if (a.id == 3) lengthField(3, replacement.toByteArray(StandardCharsets.UTF_8)) else a.raw
                                })
                            }
                        }
                    }
                }
                5 -> {
                    check(f.wire == 2)
                    val xml = parse(f.bytes)
                    val nested = xml.filter { it.id == 1 }
                    check(nested.size <= 1)
                    if (nested.isEmpty()) f.raw else {
                        val element = parse(nested.single().bytes)
                        val name = value(element, 3)
                        val named=if(name in setOf("activity","service","provider","receiver","meta-data","permission"))attribute(attributes(element),ANDROID_NS,"name") else ""
                        val rich=kind=="application" && named in richKinds
                        val signature=kind=="manifest" && name=="permission" && named=="$STUB_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                        val signatureUse=kind=="manifest" && name=="uses-permission" && attribute(attributes(element),ANDROID_NS,"name")=="$STUB_PACKAGE.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
                        val gmsVersion=kind=="application" && name=="meta-data" && named=="com.google.android.gms.version"
                        if(rich) {
                            check(name==richKinds.getValue(named) && richSeen.add(named))
                            val attrs=attributes(element)
                            check(attribute(attrs,ANDROID_NS,"exported")==if(named=="androidx.profileinstaller.ProfileInstallReceiver")"true" else "false")
                            when(named) {
                                "com.google.android.gms.common.api.GoogleApiActivity" -> {check(attrs.size==3);check(attribute(attrs,ANDROID_NS,"theme")=="@android:style/Theme.Translucent.NoTitleBar")}
                                "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery" -> check(attrs.size==2)
                                "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService" -> {check(attrs.size==3);check(attribute(attrs,ANDROID_NS,"permission")=="android.permission.BIND_JOB_SERVICE")}
                                "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver" -> check(attrs.size==2)
                                "androidx.startup.InitializationProvider" -> {check(attrs.size==3);check(attribute(attrs,ANDROID_NS,"authorities")=="$STUB_PACKAGE.androidx-startup")}
                                "androidx.profileinstaller.ProfileInstallReceiver" -> {check(attrs.size==5);check(attribute(attrs,ANDROID_NS,"permission")=="android.permission.DUMP");check(attribute(attrs,ANDROID_NS,"enabled")=="true");check(attribute(attrs,ANDROID_NS,"directBootAware")=="false")}
                            }
                        }
                        if(signature) {check(richSeen.add("signature-permission"));val attrs=attributes(element);check(attrs.size==2 && attribute(attrs,ANDROID_NS,"protectionLevel") in setOf("signature","2"))}
                        if(gmsVersion) {check(richSeen.add("gms-version"));val attrs=attributes(element);check(attrs.size==2 && attribute(attrs,ANDROID_NS,"value")=="@integer/google_play_services_version")}
                        val proxy = kind=="application" && name=="activity" && attribute(attributes(element),ANDROID_NS,"name") in proxies
                        val metadata = kind=="application" && name=="meta-data" && attribute(attributes(element),ANDROID_NS,"name")=="com.google.android.play.billingclient.version"
                        if(proxy) {
                            val attrs=attributes(element);check(attrs.size==4 && elements(element).isEmpty())
                            check(proxyNames.add(attribute(attrs,ANDROID_NS,"name")))
                            check(attribute(attrs,ANDROID_NS,"exported")=="false")
                            check(attribute(attrs,ANDROID_NS,"theme")=="@android:style/Theme.Translucent.NoTitleBar")
                            check(attribute(attrs,ANDROID_NS,"configChanges").split('|').toSet()==setOf("keyboard","keyboardHidden","orientation","screenLayout","screenSize"))
                        }
                        if(metadata) {
                            val attrs=attributes(element);check(++billingMetadata==1 && attrs.size==2 && elements(element).isEmpty())
                            check(attribute(attrs,ANDROID_NS,"value")=="9.1.0")
                        }
                        val eligible = when {
                            kind == "manifest" && name == "uses-sdk" -> { sdkCount++; true }
                            kind == "manifest" && name == "application" -> { appCount++; true }
                            kind == "application" && name == "provider" && !rich -> { providerCount++; true }
                            kind == "application" && name == "activity" && !proxy && !rich -> { activityCount++; true }
                            else -> false
                        }
                        val mediaElement = kind == "application" && name == "service" && named=="$STUB_PACKAGE.AppForgeMediaService" && mediaService != null
                        if (mediaElement) {
                            check(++serviceCount == 1)
                            val attrs=attributes(element)
                            check(attrs.size==3 && elements(element).isEmpty())
                            check(attribute(attrs,ANDROID_NS,"name")=="$STUB_PACKAGE.AppForgeMediaService")
                            check(attribute(attrs,ANDROID_NS,"exported")=="false")
                            check(attribute(attrs,ANDROID_NS,"foregroundServiceType") in setOf("mediaPlayback","2"))
                        }
                        val drop = if (rich || signature || gmsVersion) billing == false else if (proxy || metadata) billing == false else if (mediaElement) mediaService == false else if (kind == "manifest" && name == "uses-permission" && selectedPermissions != null) {
                            val attrs = attributes(element)
                            check(attrs.size == 1 && elements(element).isEmpty())
                            val permission = attribute(attrs, ANDROID_NS, "name")
                            check(permission in inventory && permissionNames.add(permission)) { "Unexpected or duplicate shell permission." }
                            permission !in requireNotNull(templateSelection)
                        } else false
                        if (rewriteDeepLinks && kind == "activity" && name == "intent-filter" &&
                            elements(element).any { value(it,3)=="data" }) {
                            check(++deepLinkCount == 1)
                            val data = child(elements(element), "data")
                            check(attribute(attributes(data),ANDROID_NS,"scheme")=="appforge-template")
                            check(attribute(attributes(data),ANDROID_NS,"host")=="appforge.invalid")
                            check(attribute(attributes(data),ANDROID_NS,"pathPrefix")=="/__appforge__")
                            check(elements(element).size==4 && attributes(data).size==3)
                            if (!draft.deepLinkEnabled) byteArrayOf() else lengthField(5, updateFields(xml) { node ->
                                if(node.id!=1)node.raw else lengthField(1, updateFields(parse(node.bytes)) { item ->
                                    if(item.id!=5)item.raw else {
                                        val children=parse(item.bytes);val childElement=only(children,1,2).singleOrNull()
                                        if(childElement==null || value(parse(childElement.bytes),3)!="data")item.raw else lengthField(5, updateFields(children) { child ->
                                            if(child.id!=1)child.raw else lengthField(1, updateFields(parse(child.bytes)) { a ->
                                                if(a.id!=4)a.raw else {
                                                    val attr=parse(a.bytes)
                                                    check(attr.none {it.id==6})
                                                    val selected=when(value(attr,2)) {"scheme"->draft.deepLinkScheme;"host"->draft.deepLinkHost;"pathPrefix"->draft.deepLinkPathPrefix;else->error("Unexpected deep link attr")}
                                                    lengthField(4,updateFields(attr){v->if(v.id==3)lengthField(3,selected.toByteArray(StandardCharsets.UTF_8)) else v.raw})
                                                }
                                            })
                                        })
                                    }
                                })
                            })
                        } else if(drop)byteArrayOf() else if(signature || signatureUse) rewriteNodeAttribute(xml,"name",targetSignature)
                        else if(rich && named=="androidx.startup.InitializationProvider")rewriteNodeAttribute(xml,"authorities","${draft.packageName}.androidx-startup")
                        else if (!eligible) f.raw else lengthField(5, updateFields(xml) { n ->
                            if (n.id == 1) lengthField(1, rewriteElement(n.bytes, name, draft, selectedPermissions, applicationCategory, rewriteDeepLinks, mediaService, billing)) else n.raw
                        })
                    }
                }
                else -> f.raw
            }
        }
        if(kind=="manifest") {
            // The signature declaration is a sibling of application; application validates its own fixed SDK set.
            check(billing==null || "signature-permission" in richSeen)
        }
        if(kind=="application") {
            val appRequired=richRequired-setOf("signature-permission")
            check(richSeen.isEmpty() || richSeen==appRequired) { "SDK component inventory mismatch." }
            check(billing==null || richSeen==appRequired)

            check(proxyNames.size in setOf(0,2) && (proxyNames.isEmpty()==(billingMetadata==0)))
            check(billing==null || proxyNames==proxies && billingMetadata==1) { "Billing component inventory mismatch." }
        }
        if (kind == "application" && mediaService != null) check(serviceCount == 1) { "Media service inventory mismatch." }
        if (kind == "activity" && rewriteDeepLinks) check(deepLinkCount==1) { "Deep link template slot mismatch." }
        if (kind == "application" && applicationCategory != null) check(categoryCount == 1)
        if (kind == "manifest" && selectedPermissions != null) check(permissionNames == inventory) {
            "V2 shell permission inventory mismatch."
        }
        when (kind) {
            "manifest" -> check(packageCount == 1 && versionCodeCount == 1 &&
                versionNameCount == 1 && appCount == 1 && sdkCount == 1)
            "uses-sdk" -> check(sdkMinCount == 1 && sdkTargetCount == 1)
            "application" -> check(labelCount == 1 && activityCount == 1 && providerCount == 1)
            "provider" -> check(authorityCount == 1)
            "activity" -> check(activityNameCount == 1)
            else -> error("Unexpected AAPT2 XML element.")
        }
        return output
    }

    private fun rewriteNodeAttribute(xml:List<Field>,name:String,replacement:String):ByteArray = lengthField(5,updateFields(xml){node->
        if(node.id!=1)node.raw else lengthField(1,updateFields(parse(node.bytes)){part->
            if(part.id!=4)part.raw else {
                val attr=parse(part.bytes)
                if(value(attr,1,optional=true)!=ANDROID_NS || value(attr,2)!=name)part.raw else {
                    check(attr.none{it.id==6})
                    lengthField(4,updateFields(attr){a->if(a.id==3)lengthField(3,replacement.toByteArray(StandardCharsets.UTF_8)) else a.raw})
                }
            }
        })
    })

    private fun attributes(element: List<Field>): List<List<Field>> =
        element.filter { it.id == 4 && it.wire == 2 }.map { parse(it.bytes) }
    private fun elements(element: List<Field>): List<List<Field>> = element.filter { it.id == 5 && it.wire == 2 }
        .mapNotNull { child -> parse(child.bytes).singleOrNull { it.id == 1 && it.wire == 2 }?.let { parse(it.bytes) } }
    private fun child(children: List<List<Field>>, name: String): List<Field> =
        children.single { value(it, 3) == name }
    private fun attribute(attrs: List<List<Field>>, ns: String, name: String): String =
        value(attrs.single { value(it, 1, optional = true) == ns && value(it, 2) == name }, 3)
    private fun value(fields: List<Field>, id: Int, optional: Boolean = false): String {
        val f = fields.filter { it.id == id }
        if (optional && f.isEmpty()) return ""
        check(f.size == 1 && f[0].wire == 2) { "Missing or duplicate XML string attribute." }
        val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder.decode(ByteBuffer.wrap(f.single().bytes)).toString()
    }
    private fun only(fields: List<Field>, id: Int, wire: Int): List<Field> = fields.filter { it.id == id && it.wire == wire }
    private fun one(fields: List<Field>, id: Int, wire: Int): Field = only(fields, id, wire).single()
    private fun updateFields(fields: List<Field>, transform: (Field) -> ByteArray): ByteArray =
        ByteArrayOutputStream().also { dst -> fields.forEach { dst.write(transform(it)) } }.toByteArray()
    private fun varint(v: Long): ByteArray = ByteArrayOutputStream().also { output ->
        var x = v
        while ((x and 0x7f.inv().toLong()) != 0L) {
            output.write(((x and 127L) or 128L).toInt()); x = x ushr 7
        }
        output.write(x.toInt())
    }.toByteArray()
    private fun varintField(id: Int, v: Long): ByteArray =
        varint((id * 8).toLong()) + varint(v)
    private fun lengthField(id: Int, data: ByteArray): ByteArray =
        varint((id * 8 + 2).toLong()) + varint(data.size.toLong()) + data

    internal fun requireWireFraming(input: ByteArray) { parse(input) }
    private fun parse(data: ByteArray): List<Field> {
        require(data.size in 1..MAX_PROTO) { "Invalid protobuf message size." }
        val out = ArrayList<Field>()
        var p = 0
        fun number(): Long {
            var result = 0L
            for (i in 0..9) {
                require(p < data.size) { "Truncated protobuf varint." }
                val b = data[p++].toInt() and 255
                require(i != 9 || b <= 1) { "Overflowing protobuf varint." }
                result = result or ((b and 127).toLong() shl (i * 7))
                if (b and 128 == 0) {
                    require(i == 0 || b != 0) { "Noncanonical protobuf varint." }
                    return result
                }
            }
            throw IllegalArgumentException("Malformed protobuf varint.")
        }
        fun take(size: Long): ByteArray {
            require(size >= 0 && size <= (data.size - p).toLong()) { "Truncated protobuf payload." }
            val n = size.toInt()
            return data.copyOfRange(p, p + n).also { p += n }
        }
        while (p < data.size) {
            require(out.size < MAX_FIELDS) { "Excessive protobuf fields." }
            val start = p
            val tag = number()
            require((tag ushr 3) in 1L..536_870_911L) { "Invalid protobuf field number." }
            val id = (tag ushr 3).toInt()
            val wire = (tag and 7).toInt()
            var value = -1L
            val bytes = when (wire) {
                0 -> { value = number(); byteArrayOf() }
                1 -> take(8)
                2 -> take(number())
                5 -> take(4)
                else -> throw IllegalArgumentException("Unsupported protobuf wire type.")
            }
            out.add(Field(id, wire, bytes, data.copyOfRange(start, p), value))
        }
        return out
    }
}
