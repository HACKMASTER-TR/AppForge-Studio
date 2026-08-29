package com.appforge.studio.security

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object StudioDeviceIdentity {
    fun value(context: Context): String {
        val androidId =
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ).orEmpty()

        require(androidId.isNotBlank()) {
            "Cihaz kimliği alınamadı."
        }

        val input =
            "${context.packageName}|$androidId"

        return MessageDigest
            .getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }
    }
}
