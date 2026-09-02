package com.hackmaster.videoforge

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlin.math.max

object StorageGuard {
    data class Estimate(val durationSeconds: Double, val requiredBytes: Long, val availableBytes: Long)

    fun estimate(context: Context, uri: Uri, previewSeconds: Int? = null): Estimate {
        var duration = 0.0
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            duration = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) / 1000.0
        } finally {
            r.release()
        }
        if (previewSeconds != null && previewSeconds > 0) duration = minOf(duration, previewSeconds.toDouble())
        // 48k float mono work buffer + 16k AI buffer + output/AAC/temp safety margin.
        val pcm48 = (duration * 48_000.0 * 4.0).toLong()
        val pcm16 = (duration * 16_000.0 * 4.0).toLong()
        val safety = 160L * 1024L * 1024L
        val required = max(220L * 1024L * 1024L, (pcm48 + pcm16) * 2 + safety)
        val available = context.cacheDir.usableSpace
        return Estimate(duration, required, available)
    }

    fun requireEnough(context: Context, uri: Uri, previewSeconds: Int? = null) {
        val e = estimate(context, uri, previewSeconds)
        require(e.availableBytes > e.requiredBytes) {
            "Yeterli boş alan yok. Yaklaşık ${human(e.requiredBytes)} gerekli, kullanılabilir ${human(e.availableBytes)}."
        }
    }

    fun human(bytes: Long): String {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1024.0 / 1024.0)
    }
}
