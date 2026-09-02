package com.hackmaster.videoforge

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.UUID

class DeviceTts private constructor(
    private val tts: TextToSpeech,
    private val voices: List<Voice>,
    private val locale: Locale
) {
    suspend fun synthesize(
        text: String,
        speaker: Int,
        targetSeconds: Double,
        acousticPitch: Float? = null,
        speakerProfile: String = "auto",
        output: File
    ): WavReader.Wav = withContext(Dispatchers.Main) {
        val voice = if (voices.isNotEmpty()) voices[Math.floorMod(speaker, voices.size)] else null
        if (voice != null) tts.voice = voice

        val fallbackPitchProfiles = floatArrayOf(0.92f, 1.08f, 0.99f, 1.14f, 0.88f, 1.03f)
        val automatic = acousticPitch?.coerceIn(0.84f, 1.18f)
            ?: fallbackPitchProfiles[Math.floorMod(speaker, fallbackPitchProfiles.size)]
        val profileMultiplier = when (speakerProfile) {
            "deep" -> 0.88f
            "bright" -> 1.12f
            "natural" -> 1.0f
            else -> 1.0f
        }
        tts.setPitch((automatic * profileMultiplier).coerceIn(0.78f, 1.24f))

        // Keep speech intelligible. Older builds could push Android TTS up to 1.65x
        // and then compress the WAV again during mixing, producing very fast speech.
        // V4.1.2 limits TTS to a natural range; timing is handled conservatively later.
        val estimatedSeconds = (text.length / 10.8).coerceAtLeast(0.9)
        val speed = (estimatedSeconds / targetSeconds.coerceAtLeast(0.8)).coerceIn(0.90, 1.12).toFloat()
        tts.setSpeechRate(speed)

        output.parentFile?.mkdirs()
        if (output.exists()) output.delete()
        val id = "vf-${UUID.randomUUID()}"
        val done = CompletableDeferred<Unit>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == id && !done.isCompleted) done.complete(Unit)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == id && !done.isCompleted) {
                    done.completeExceptionally(IllegalStateException("${locale.displayLanguage} TTS ses üretimi başarısız."))
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == id && !done.isCompleted) {
                    done.completeExceptionally(IllegalStateException("TTS hata kodu: $errorCode"))
                }
            }
        })
        val params = Bundle()
        val result = tts.synthesizeToFile(text.take(3900), params, output, id)
        if (result != TextToSpeech.SUCCESS) {
            throw IllegalStateException("Telefonun TTS motoru ses dosyası oluşturamadı.")
        }
        done.await()
        withContext(Dispatchers.IO) { WavReader.read(output) }
    }

    fun close() = tts.shutdown()

    companion object {
        suspend fun create(context: Context, locale: Locale): DeviceTts = withContext(Dispatchers.Main) {
            val ready = CompletableDeferred<Int>()
            lateinit var engine: TextToSpeech
            engine = TextToSpeech(context.applicationContext) { status ->
                if (!ready.isCompleted) ready.complete(status)
            }
            val status = ready.await()
            if (status != TextToSpeech.SUCCESS) {
                engine.shutdown()
                throw IllegalStateException("Telefonun metin-okuma (TTS) motoru başlatılamadı.")
            }

            val languageResult = engine.setLanguage(locale)
            if (languageResult == TextToSpeech.LANG_MISSING_DATA || languageResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                engine.shutdown()
                throw IllegalStateException("Telefonda ${locale.displayLanguage} metin-okuma sesi kurulu değil. Android TTS ayarlarından çevrimdışı sesi indirip tekrar deneyin.")
            }

            val offline = engine.voices
                ?.filter { it.locale.language.equals(locale.language, true) && !it.isNetworkConnectionRequired }
                ?.sortedByDescending { it.quality }
                .orEmpty()

            val usable = if (offline.isNotEmpty()) offline else listOfNotNull(engine.voice)
            DeviceTts(engine, usable, locale)
        }
    }
}
