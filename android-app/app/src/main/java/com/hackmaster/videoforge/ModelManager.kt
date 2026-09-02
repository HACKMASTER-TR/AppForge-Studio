package com.hackmaster.videoforge

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelManager(private val context: Context) {
    private val root = File(context.filesDir, "videoforge-ai-models")
    private val whisperDir = File(root, "whisper-tiny")
    private val diarDir = File(root, "speaker-diarization")

    val whisperEncoder = File(whisperDir, "tiny-encoder.int8.onnx")
    val whisperDecoder = File(whisperDir, "tiny-decoder.int8.onnx")
    val whisperTokens = File(whisperDir, "tiny-tokens.txt")
    val segmentationModel = File(diarDir, "segmentation.onnx")
    val embeddingModel = File(diarDir, "embedding.onnx")

    fun isReady(): Boolean = listOf(
        whisperEncoder,
        whisperDecoder,
        whisperTokens,
        segmentationModel,
        embeddingModel
    ).all { it.isFile && it.length() > 1024 }

    suspend fun ensureReady(onProgress: (Int, String) -> Unit) = withContext(Dispatchers.IO) {
        root.mkdirs()
        whisperDir.mkdirs()
        diarDir.mkdirs()

        if (!whisperReady()) {
            onProgress(2, "Whisper konuşma modeli indiriliyor…")
            val archive = File(root, "whisper-tiny.tar.bz2.part")
            download(
                WHISPER_URL,
                archive,
                2,
                58,
                onProgress,
                "Whisper konuşma modeli indiriliyor…"
            )
            extractTarBz2Selected(
                archive,
                whisperDir,
                mapOf(
                    "tiny-encoder.int8.onnx" to "tiny-encoder.int8.onnx",
                    "tiny-decoder.int8.onnx" to "tiny-decoder.int8.onnx",
                    "tiny-tokens.txt" to "tiny-tokens.txt"
                )
            )
            archive.delete()
            require(whisperReady()) { "Whisper model dosyaları hazırlanamadı." }
        }

        if (!segmentationModel.isFile || segmentationModel.length() < 1024) {
            onProgress(60, "Konuşmacı ayırma modeli indiriliyor…")
            val archive = File(root, "speaker-segmentation.tar.bz2.part")
            download(
                SEGMENTATION_URL,
                archive,
                60,
                78,
                onProgress,
                "Konuşmacı ayırma modeli indiriliyor…"
            )
            extractTarBz2Selected(
                archive,
                diarDir,
                mapOf("model.onnx" to "segmentation.onnx")
            )
            archive.delete()
            require(segmentationModel.isFile && segmentationModel.length() > 1024) {
                "Konuşmacı segmentasyon modeli hazırlanamadı."
            }
        }

        if (!embeddingModel.isFile || embeddingModel.length() < 1024) {
            onProgress(80, "Konuşmacı profil modeli indiriliyor…")
            val temp = File(root, "embedding.onnx.part")
            download(
                EMBEDDING_URL,
                temp,
                80,
                98,
                onProgress,
                "Konuşmacı profil modeli indiriliyor…"
            )
            atomicReplace(temp, embeddingModel)
        }

        require(isReady()) { "AI modellerinin kurulumu tamamlanamadı." }
        onProgress(100, "AI modelleri hazır. Bundan sonra konuşma ve konuşmacı analizi cihazda yapılacak.")
    }


    fun totalSizeBytes(): Long = if (!root.exists()) 0L else root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearAll() {
        if (root.exists()) root.deleteRecursively()
    }
    private fun whisperReady(): Boolean = whisperEncoder.isFile && whisperEncoder.length() > 1024 &&
        whisperDecoder.isFile && whisperDecoder.length() > 1024 &&
        whisperTokens.isFile && whisperTokens.length() > 1024

    private fun download(
        address: String,
        output: File,
        startPct: Int,
        endPct: Int,
        onProgress: (Int, String) -> Unit,
        label: String
    ) {
        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()

        val connection = (URL(address).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "VideoForge/4.1 Android")
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Model indirilemedi (HTTP ${connection.responseCode}).")
            }
            val total = connection.contentLengthLong
            var copied = 0L
            BufferedInputStream(connection.inputStream, 128 * 1024).use { input ->
                FileOutputStream(output).use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        copied += n
                        val ratio = if (total > 0) copied.toDouble() / total else 0.0
                        val pct = startPct + ((endPct - startPct) * ratio).toInt()
                        onProgress(pct.coerceIn(startPct, endPct), label)
                    }
                    out.fd.sync()
                }
            }
            if (output.length() < 1024) throw IllegalStateException("İndirilen model dosyası geçersiz.")
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally {
            connection.disconnect()
        }
    }

    private fun extractTarBz2Selected(archive: File, destination: File, wanted: Map<String, String>) {
        destination.mkdirs()
        val found = mutableSetOf<String>()
        FileInputStream(archive).use { fis ->
            BufferedInputStream(fis, 128 * 1024).use { bis ->
                BZip2CompressorInputStream(bis, true).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        while (true) {
                            val entry = tar.nextTarEntry ?: break
                            if (!entry.isFile) continue
                            val base = entry.name.substringAfterLast('/')
                            val targetName = wanted[base] ?: continue
                            val target = File(destination, targetName)
                            val canonicalRoot = destination.canonicalFile
                            val canonicalTarget = target.canonicalFile
                            require(canonicalTarget.path.startsWith(canonicalRoot.path + File.separator)) {
                                "Güvensiz model arşivi yolu."
                            }
                            val temp = File(destination, "$targetName.part")
                            FileOutputStream(temp).use { out ->
                                val buf = ByteArray(128 * 1024)
                                while (true) {
                                    val n = tar.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                }
                                out.fd.sync()
                            }
                            require(temp.length() > 1024) { "Arşivdeki $targetName geçersiz." }
                            atomicReplace(temp, target)
                            found += base
                        }
                    }
                }
            }
        }
        val missing = wanted.keys - found
        require(missing.isEmpty()) { "Model arşivinde eksik dosyalar var: ${missing.joinToString()}" }
    }

    private fun atomicReplace(source: File, target: File) {
        target.parentFile?.mkdirs()
        if (target.exists()) target.delete()
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
    }

    companion object {
        private const val WHISPER_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2"
        private const val SEGMENTATION_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2"
        private const val EMBEDDING_URL =
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/3dspeaker_speech_eres2net_base_sv_zh-cn_3dspeaker_16k.onnx"
    }
}
