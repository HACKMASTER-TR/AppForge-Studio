package com.hackmaster.videoforge

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavReader {
    data class Wav(val samples: FloatArray, val sampleRate: Int)

    fun read(file: File): Wav {
        RandomAccessFile(file, "r").use { raf ->
            require(readFourCC(raf) == "RIFF") { "TTS çıktısı RIFF/WAV değil." }
            readIntLE(raf)
            require(readFourCC(raf) == "WAVE") { "TTS çıktısı WAVE değil." }

            var audioFormat = 1
            var channels = 1
            var sampleRate = 0
            var bits = 16
            var dataOffset = -1L
            var dataSize = 0

            while (raf.filePointer + 8 <= raf.length()) {
                val id = readFourCC(raf)
                val size = readIntLE(raf)
                val next = raf.filePointer + size + (size and 1)
                when (id) {
                    "fmt " -> {
                        audioFormat = readShortLE(raf)
                        channels = readShortLE(raf)
                        sampleRate = readIntLE(raf)
                        readIntLE(raf)
                        readShortLE(raf)
                        bits = readShortLE(raf)
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size
                        break
                    }
                }
                raf.seek(next)
            }

            require(dataOffset >= 0 && dataSize > 0 && sampleRate > 0) { "WAV ses verisi okunamadı." }
            raf.seek(dataOffset)
            val bytes = ByteArray(dataSize)
            raf.readFully(bytes)
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val ch = channels.coerceAtLeast(1)
            val out = AudioMedia.FloatBuilder((dataSize / (bits / 8).coerceAtLeast(1) / ch).coerceAtLeast(1))

            when {
                audioFormat == 3 && bits == 32 -> {
                    while (bb.remaining() >= 4 * ch) {
                        var sum = 0f
                        repeat(ch) { sum += bb.float }
                        out.add((sum / ch).coerceIn(-1f, 1f))
                    }
                }
                audioFormat == 1 && bits == 16 -> {
                    while (bb.remaining() >= 2 * ch) {
                        var sum = 0f
                        repeat(ch) { sum += bb.short / 32768f }
                        out.add((sum / ch).coerceIn(-1f, 1f))
                    }
                }
                audioFormat == 1 && bits == 8 -> {
                    while (bb.remaining() >= ch) {
                        var sum = 0f
                        repeat(ch) { sum += ((bb.get().toInt() and 0xff) - 128) / 128f }
                        out.add((sum / ch).coerceIn(-1f, 1f))
                    }
                }
                else -> error("Desteklenmeyen TTS WAV biçimi: format=$audioFormat bit=$bits")
            }
            return Wav(out.toArray(), sampleRate)
        }
    }

    private fun readFourCC(raf: RandomAccessFile): String {
        val b = ByteArray(4)
        raf.readFully(b)
        return String(b, Charsets.US_ASCII)
    }

    private fun readIntLE(raf: RandomAccessFile): Int = Integer.reverseBytes(raf.readInt())
    private fun readShortLE(raf: RandomAccessFile): Int = java.lang.Short.reverseBytes(raf.readShort()).toInt() and 0xffff
}
