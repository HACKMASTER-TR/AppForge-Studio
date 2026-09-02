package com.hackmaster.videoforge

import android.content.ContentValues
import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object AudioMedia {
    data class MonoAudio(val samples: FloatArray, val sampleRate: Int, val durationSeconds: Double)

    fun decodeMono(context: Context, uri: Uri): MonoAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var track = -1
        var inputFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                track = i
                inputFormat = f
                break
            }
        }
        require(track >= 0 && inputFormat != null) { "Videoda ses parçası bulunamadı." }
        val inFormat = requireNotNull(inputFormat)
        extractor.selectTrack(track)

        val mime = inFormat.getString(MediaFormat.KEY_MIME)!!
        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inFormat, null, null, 0)
        decoder.start()

        val builder = FloatBuilder(1_000_000)
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var outRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var outChannels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inputIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        outRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        outChannels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            pcmEncoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                        }
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            appendMonoPcm(buffer.slice(), pcmEncoding, outChannels, builder)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            runCatching { decoder.stop() }
            decoder.release()
            extractor.release()
        }

        val samples = builder.toArray()
        require(samples.isNotEmpty()) { "Video sesi çözülemedi." }
        return MonoAudio(samples, outRate, samples.size.toDouble() / outRate)
    }

    private fun appendMonoPcm(
        source: ByteBuffer,
        encoding: Int,
        channels: Int,
        out: FloatBuilder
    ) {
        val ch = max(1, channels)
        source.order(ByteOrder.LITTLE_ENDIAN)
        when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> {
                val fb = source.asFloatBuffer()
                val frame = FloatArray(ch)
                while (fb.remaining() >= ch) {
                    var sum = 0f
                    for (c in 0 until ch) {
                        frame[c] = fb.get()
                        sum += frame[c]
                    }
                    out.add((sum / ch).coerceIn(-1f, 1f))
                }
            }
            AudioFormat.ENCODING_PCM_8BIT -> {
                while (source.remaining() >= ch) {
                    var sum = 0f
                    for (c in 0 until ch) {
                        sum += ((source.get().toInt() and 0xff) - 128) / 128f
                    }
                    out.add((sum / ch).coerceIn(-1f, 1f))
                }
            }
            else -> {
                val sb = source.asShortBuffer()
                while (sb.remaining() >= ch) {
                    var sum = 0f
                    for (c in 0 until ch) sum += sb.get() / 32768f
                    out.add((sum / ch).coerceIn(-1f, 1f))
                }
            }
        }
    }

    fun resample(input: FloatArray, fromRate: Int, toRate: Int): FloatArray {
        if (fromRate == toRate) return input.copyOf()
        require(fromRate > 0 && toRate > 0)
        val outputSize = max(1, (input.size.toLong() * toRate / fromRate).toInt())
        val out = FloatArray(outputSize)
        val ratio = fromRate.toDouble() / toRate
        for (i in out.indices) {
            val pos = i * ratio
            val left = floor(pos).toInt().coerceIn(0, input.lastIndex)
            val right = min(left + 1, input.lastIndex)
            val frac = (pos - left).toFloat()
            out[i] = input[left] * (1f - frac) + input[right] * frac
        }
        return out
    }

    fun slice(samples: FloatArray, sampleRate: Int, startSec: Double, endSec: Double): FloatArray {
        val a = (startSec * sampleRate).roundToInt().coerceIn(0, samples.size)
        val b = (endSec * sampleRate).roundToInt().coerceIn(a, samples.size)
        return samples.copyOfRange(a, b)
    }

    data class DubAudio(val startSeconds: Double, val endSeconds: Double, val samples: FloatArray, val sampleRate: Int)

    fun replaceSpeechWithDubs(
        original: FloatArray,
        sampleRate: Int,
        dubs: List<DubAudio>,
        mutePaddingMs: Int = 120,
        preserveBackground: Boolean = false,
        backgroundVolume: Float = 1.0f,
        ttsVolume: Float = 1.0f,
        timeSync: Boolean = true
    ): FloatArray {
        val out = original.copyOf()
        val padding = max(0, sampleRate * mutePaddingMs / 1000)
        val bgGain = backgroundVolume.coerceIn(0f, 1.25f)
        val ordered = dubs.sortedBy { it.startSeconds }

        if (bgGain != 1f) {
            for (i in out.indices) out[i] *= bgGain
        }

        // Remove the source-language speech first. We do this once per stabilized
        // turn; duplicate overlapping TTS turns are filtered before this stage.
        for (dub in ordered) {
            val rawStart = (dub.startSeconds * sampleRate).roundToInt()
            val rawEnd = (dub.endSeconds * sampleRate).roundToInt()
            val start = (rawStart - padding).coerceIn(0, out.size)
            val end = (rawEnd + padding).coerceIn(start, out.size)
            if (!preserveBackground || end <= start) {
                for (i in start until end) out[i] = 0f
            } else {
                fillAmbientFromEdges(out, start, end, sampleRate)
            }
        }

        // Place the translated voice at a natural speed. Older builds always
        // resampled every TTS WAV to the exact source-turn duration, which could
        // make a sentence unintelligibly fast. V4.1.2 only performs a small,
        // bounded timing correction and lets speech use nearby silence when safe.
        val safetyGap = (sampleRate * 0.06).roundToInt()
        val voiceGain = ttsVolume.coerceIn(0.2f, 1.5f)
        for ((index, dub) in ordered.withIndex()) {
            val start = (dub.startSeconds * sampleRate).roundToInt().coerceIn(0, out.size)
            val sourceEnd = (dub.endSeconds * sampleRate).roundToInt().coerceIn(start, out.size)
            if (start >= out.size) continue

            var voice = if (dub.sampleRate == sampleRate) dub.samples else resample(dub.samples, dub.sampleRate, sampleRate)
            if (voice.isEmpty()) continue

            val nextStart = ordered.getOrNull(index + 1)
                ?.let { (it.startSeconds * sampleRate).roundToInt().coerceIn(start, out.size) }
                ?: out.size

            // Prefer the original turn, but permit the synthetic sentence to use
            // the silent gap before the next detected sentence. Never overlap the
            // next dub unless there is effectively no gap at all.
            val naturalEnd = (start + voice.size).coerceAtMost(out.size)
            val gapEnd = (nextStart - safetyGap).coerceAtLeast(sourceEnd).coerceAtMost(out.size)
            val placementEnd = max(sourceEnd, min(naturalEnd, gapEnd)).coerceIn(start, out.size)
            val capacity = max(0, placementEnd - start)

            if (timeSync && capacity > 0 && voice.size > capacity) {
                voice = fitToLengthSafely(voice, capacity, maxCompression = 1.12)
            }

            val maxLen = min(voice.size, min(max(0, placementEnd - start), out.size - start))
            if (maxLen <= 0) continue

            // If the translated voice legitimately extends into the quiet gap,
            // clear only that extra placement area so old speech cannot leak back.
            val extendedEnd = min(out.size, start + maxLen)
            if (extendedEnd > sourceEnd && !preserveBackground) {
                for (i in sourceEnd until extendedEnd) out[i] = 0f
            }

            val fade = max(1, min(maxLen / 4, (sampleRate * 0.02).toInt()))
            for (j in 0 until maxLen) {
                val fadeIn = (j.toFloat() / fade).coerceIn(0f, 1f)
                val fadeOut = ((maxLen - 1 - j).toFloat() / fade).coerceIn(0f, 1f)
                val envelope = min(fadeIn, fadeOut)
                out[start + j] += voice[j] * voiceGain * envelope
            }
        }

        var peak = 0f
        for (v in out) peak = max(peak, abs(v))
        if (peak > 0.98f) {
            val gain = 0.98f / peak
            for (i in out.indices) out[i] *= gain
        }
        return out
    }

    private fun fitToLengthSafely(input: FloatArray, targetSize: Int, maxCompression: Double): FloatArray {
        if (targetSize <= 0 || input.isEmpty()) return FloatArray(0)
        if (input.size <= targetSize) return input

        val requestedCompression = input.size.toDouble() / targetSize.toDouble().coerceAtLeast(1.0)
        val safeCompression = min(requestedCompression, maxCompression.coerceAtLeast(1.0))
        val safeTarget = max(targetSize, (input.size / safeCompression).roundToInt())
        if (safeTarget >= input.size) return input
        return fitToLength(input, safeTarget)
    }

    private fun fitToLength(input: FloatArray, targetSize: Int): FloatArray {
        if (targetSize <= 0) return FloatArray(0)
        if (input.isEmpty()) return FloatArray(targetSize)
        if (input.size == targetSize) return input
        val out = FloatArray(targetSize)
        val ratio = (input.size - 1).toDouble() / max(1, targetSize - 1)
        for (i in out.indices) {
            val pos = i * ratio
            val a = floor(pos).toInt().coerceIn(0, input.lastIndex)
            val b = min(a + 1, input.lastIndex)
            val f = (pos - a).toFloat()
            out[i] = input[a] * (1f - f) + input[b] * f
        }
        return out
    }

    private fun fillAmbientFromEdges(out: FloatArray, start: Int, end: Int, sampleRate: Int) {
        val span = end - start
        if (span <= 0) return
        val edge = max(1, (sampleRate * 0.12).toInt())
        val beforeStart = max(0, start - edge)
        val beforeLen = start - beforeStart
        val afterEnd = min(out.size, end + edge)
        val afterLen = afterEnd - end
        if (beforeLen <= 0 && afterLen <= 0) {
            for (i in start until end) out[i] = 0f
            return
        }
        for (i in 0 until span) {
            val t = if (span <= 1) 0f else i.toFloat() / (span - 1)
            val left = if (beforeLen > 0) out[beforeStart + (i % beforeLen)] else 0f
            val right = if (afterLen > 0) out[end + (i % afterLen)] else 0f
            // Keep the ambience conservative so copied edge samples do not sound dominant.
            out[start + i] = (left * (1f - t) + right * t) * 0.55f
        }
    }

    fun muxVideoWithMonoAac(
        context: Context,
        inputUri: Uri,
        mixedAudio: FloatArray,
        audioSampleRate: Int,
        outputFile: File,
        bitrate: Int = 128_000,
        maxDurationUs: Long? = null
    ) {
        if (outputFile.exists()) outputFile.delete()
        outputFile.parentFile?.mkdirs()

        val extractor = MediaExtractor()
        extractor.setDataSource(context, inputUri, null)
        var videoTrack = -1
        var videoFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) {
                videoTrack = i
                videoFormat = f
                break
            }
        }
        require(videoTrack >= 0 && videoFormat != null) { "Videoda görüntü parçası bulunamadı." }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val videoMuxTrack = muxer.addTrack(videoFormat!!)

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, inputUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let {
                if (it != 0) muxer.setOrientationHint(it)
            }
        } catch (_: Throwable) {
        } finally {
            retriever.release()
        }

        val aacFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        encoder.configure(aacFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        encoder.start()

        var audioMuxTrack = -1
        var muxerStarted = false
        var sampleOffset = 0
        var inputDone = false
        var outputDone = false
        val info = MediaCodec.BufferInfo()

        try {
            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = encoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val buffer = encoder.getInputBuffer(inputIndex)!!
                        buffer.clear()
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val maxSamples = buffer.remaining() / 2
                        val n = min(maxSamples, mixedAudio.size - sampleOffset)
                        if (n > 0) {
                            for (i in 0 until n) {
                                val s = (mixedAudio[sampleOffset + i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                                buffer.putShort(s)
                            }
                            val pts = sampleOffset.toLong() * 1_000_000L / audioSampleRate
                            encoder.queueInputBuffer(inputIndex, 0, n * 2, pts, 0)
                            sampleOffset += n
                        } else {
                            val pts = sampleOffset.toLong() * 1_000_000L / audioSampleRate
                            encoder.queueInputBuffer(inputIndex, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                when (val outputIndex = encoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        check(audioMuxTrack < 0) { "AAC formatı birden fazla değişti." }
                        audioMuxTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = encoder.getOutputBuffer(outputIndex)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0) {
                            check(muxerStarted) { "MediaMuxer AAC formatı gelmeden başlatılamadı." }
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            muxer.writeSampleData(audioMuxTrack, buffer, info)
                        }
                        outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        encoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            check(muxerStarted) { "AAC ses parçası oluşturulamadı." }

            extractor.selectTrack(videoTrack)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val maxInput = if (videoFormat!!.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                videoFormat!!.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(1024 * 1024)
            } else 2 * 1024 * 1024
            val videoBuffer = ByteBuffer.allocateDirect(maxInput.coerceAtMost(16 * 1024 * 1024))
            val videoInfo = MediaCodec.BufferInfo()
            while (true) {
                videoBuffer.clear()
                val size = extractor.readSampleData(videoBuffer, 0)
                if (size < 0) break
                val pts = extractor.sampleTime
                if (maxDurationUs != null && pts > maxDurationUs) break
                videoInfo.offset = 0
                videoInfo.size = size
                videoInfo.presentationTimeUs = pts
                videoInfo.flags = extractor.sampleFlags
                muxer.writeSampleData(videoMuxTrack, videoBuffer, videoInfo)
                extractor.advance()
            }
        } finally {
            runCatching { encoder.stop() }
            encoder.release()
            extractor.release()
            if (muxerStarted) runCatching { muxer.stop() }
            muxer.release()
        }
    }

    fun saveVideoToMediaStore(context: Context, source: File, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoForge")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Çıktı dosyası oluşturulamadı.")
        try {
            context.contentResolver.openOutputStream(uri, "w")!!.use { out ->
                source.inputStream().use { it.copyTo(out, 256 * 1024) }
            }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            return uri
        } catch (t: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw t
        }
    }

    fun saveSrtToDownloads(context: Context, text: String, displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/VideoForge")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Altyazı dosyası oluşturulamadı.")
        context.contentResolver.openOutputStream(uri, "w")!!.bufferedWriter(Charsets.UTF_8).use { it.write(text) }
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
        return uri
    }

    class FloatBuilder(initial: Int = 1024) {
        private var data = FloatArray(max(16, initial))
        private var size = 0
        fun add(value: Float) {
            if (size >= data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }
        fun toArray(): FloatArray = data.copyOf(size)
    }
}
