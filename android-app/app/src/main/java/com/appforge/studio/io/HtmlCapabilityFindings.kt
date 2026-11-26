package com.appforge.studio.io

/** Bounded syntax findings, not runtime proof. A plain file input never implies camera/audio permission. */
object HtmlCapabilityFindings {
    fun scan(text:String):Set<String> = buildSet {
        val source=text.replace(Regex("<!--[^]*?-->".replace("[^]","[\\s\\S]")),"")
            .replace(Regex("/\\*[\\s\\S]*?\\*/"),"")
            .replace(Regex("(?m)^\\s*//.*$"),"")
        for(input in Regex("<input\\b[^>]{0,8192}>",RegexOption.IGNORE_CASE).findAll(source)) {
            val attrs=Regex("""([a-zA-Z_:][a-zA-Z0-9_:.-]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+)))?""").findAll(input.value)
                .associate{it.groupValues[1].lowercase() to it.groupValues.drop(2).firstOrNull{v->v.isNotEmpty()}.orEmpty().lowercase()}
            if(attrs["type"]=="file") {
                add("fileUpload")
                if("capture" in attrs)add(if(attrs["accept"]?.contains("audio/")==true)"audioCapture" else "capture")
            }
        }
        val requests=Regex("""getUserMedia\s*\(\s*\{([\s\S]{0,2000}?)\}\s*\)""").findAll(source).toList()
        requests.forEach{request->
            if(Regex("""\bvideo\s*:\s*(?:true|\{)""").containsMatchIn(request.groupValues[1]))add("camera")
            if(Regex("""\baudio\s*:\s*(?:true|\{)""").containsMatchIn(request.groupValues[1]))add("microphone")
        }
        if(Regex("""getUserMedia\s*\(\s*[A-Za-z_$]""").containsMatchIn(source))add("webMediaUnknown")
        if(Regex("""(?:geolocation\s*\.\s*|AppForgeLocation\s*\.\s*)getCurrentPosition\s*\(""").containsMatchIn(source))add("location")
        if(Regex("""geolocation\s*\.\s*watchPosition\s*\(""").containsMatchIn(source))add("watchPosition")
        if(Regex("""AppForge(?:FastNative)?\s*\.\s*scanCode\s*\(""").containsMatchIn(source))add("qrScanner")
        if(Regex("""navigator\s*\.\s*bluetooth\s*\.\s*requestDevice\s*\(""").containsMatchIn(source))add("bluetoothApi")
        if(Regex("""navigator\s*\.\s*contacts\s*\.\s*select\s*\(""").containsMatchIn(source))add("contactsApi")
        if(Regex("""AppForgeCalendar\s*\.""").containsMatchIn(source))add("calendarApi")
        if(Regex("""new\s+NDEFReader\s*\(""").containsMatchIn(source))add("webNfcApi")
        if(Regex("""AppForgeFastNative\s*\.\s*scanNfc\s*\(""").containsMatchIn(source))add("nfc")
        if(Regex("""AppForgeMedia\s*\.\s*play\s*\(""").containsMatchIn(source))add("mediaPlayerBridge")
    }
}
