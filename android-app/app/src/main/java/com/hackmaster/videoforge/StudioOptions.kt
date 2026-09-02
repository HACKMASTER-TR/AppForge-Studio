package com.hackmaster.videoforge

import android.content.Intent
import java.util.Locale

data class StudioOptions(
    val sourceLanguage: String = "auto",
    val targetLanguage: String = "tr",
    val quality: String = QUALITY_BALANCED,
    val speakerMode: String = SPEAKER_AUTO,
    val saveSubtitles: Boolean = true,
    val previewOnly: Boolean = false,
    val previewSeconds: Int = 30,
    val preserveBackground: Boolean = false,
    val timeSync: Boolean = true,
    val resumeEnabled: Boolean = true,
    val ttsVolume: Float = 1.0f,
    val backgroundVolume: Float = 1.0f,
    val speakerProfiles: List<String> = listOf("auto", "deep", "natural", "bright")
) {
    val targetLocale: Locale
        get() = when (targetLanguage) {
            "tr" -> Locale("tr", "TR")
            "en" -> Locale.US
            "de" -> Locale.GERMANY
            "fr" -> Locale.FRANCE
            "es" -> Locale("es", "ES")
            "it" -> Locale.ITALY
            "pt" -> Locale("pt", "BR")
            "ja" -> Locale.JAPAN
            "ko" -> Locale.KOREA
            "zh" -> Locale.SIMPLIFIED_CHINESE
            else -> Locale.forLanguageTag(targetLanguage)
        }

    val targetLabel: String
        get() = TARGETS.firstOrNull { it.code == targetLanguage }?.label ?: targetLanguage

    val audioBitrate: Int
        get() = when (quality) {
            QUALITY_FAST -> 96_000
            QUALITY_HIGH -> 192_000
            else -> 128_000
        }

    val asrThreads: Int
        get() = when (quality) {
            QUALITY_FAST -> 2
            QUALITY_HIGH -> 4
            else -> 3
        }

    val diarizationEnabled: Boolean
        get() = quality != QUALITY_FAST

    val diarizationChunkSeconds: Int
        get() = when (quality) {
            QUALITY_HIGH -> 20
            QUALITY_FAST -> 45
            else -> 30
        }

    fun writeTo(intent: Intent) {
        intent.putExtra(EXTRA_SOURCE_LANGUAGE, sourceLanguage)
        intent.putExtra(EXTRA_TARGET_LANGUAGE, targetLanguage)
        intent.putExtra(EXTRA_QUALITY, quality)
        intent.putExtra(EXTRA_SPEAKER_MODE, speakerMode)
        intent.putExtra(EXTRA_SUBTITLES, saveSubtitles)
        intent.putExtra(EXTRA_PREVIEW, previewOnly)
        intent.putExtra(EXTRA_PREVIEW_SECONDS, previewSeconds)
        intent.putExtra(EXTRA_PRESERVE_BACKGROUND, preserveBackground)
        intent.putExtra(EXTRA_TIME_SYNC, timeSync)
        intent.putExtra(EXTRA_RESUME, resumeEnabled)
        intent.putExtra(EXTRA_TTS_VOLUME, ttsVolume)
        intent.putExtra(EXTRA_BACKGROUND_VOLUME, backgroundVolume)
        intent.putStringArrayListExtra(EXTRA_SPEAKER_PROFILES, ArrayList(speakerProfiles))
    }

    companion object {
        const val QUALITY_FAST = "fast"
        const val QUALITY_BALANCED = "balanced"
        const val QUALITY_HIGH = "high"

        const val SPEAKER_AUTO = "auto"
        const val SPEAKER_SINGLE = "single"
        const val SPEAKER_MULTI = "multi"

        const val EXTRA_SOURCE_LANGUAGE = "studio_source_language"
        const val EXTRA_TARGET_LANGUAGE = "studio_target_language"
        const val EXTRA_QUALITY = "studio_quality"
        const val EXTRA_SPEAKER_MODE = "studio_speaker_mode"
        const val EXTRA_SUBTITLES = "studio_subtitles"
        const val EXTRA_PREVIEW = "studio_preview"
        const val EXTRA_PREVIEW_SECONDS = "studio_preview_seconds"
        const val EXTRA_PRESERVE_BACKGROUND = "studio_preserve_background"
        const val EXTRA_TIME_SYNC = "studio_time_sync"
        const val EXTRA_RESUME = "studio_resume"
        const val EXTRA_TTS_VOLUME = "studio_tts_volume"
        const val EXTRA_BACKGROUND_VOLUME = "studio_background_volume"
        const val EXTRA_SPEAKER_PROFILES = "studio_speaker_profiles"

        data class Target(val label: String, val code: String)

        val SOURCES = listOf(
            Target("Otomatik", "auto"),
            Target("English", "en"),
            Target("Türkçe", "tr"),
            Target("Deutsch", "de"),
            Target("Français", "fr"),
            Target("Español", "es"),
            Target("Italiano", "it"),
            Target("Português", "pt"),
            Target("日本語", "ja"),
            Target("한국어", "ko"),
            Target("中文", "zh")
        )

        val TARGETS = listOf(
            Target("Türkçe", "tr"),
            Target("English", "en"),
            Target("Deutsch", "de"),
            Target("Français", "fr"),
            Target("Español", "es"),
            Target("Italiano", "it"),
            Target("Português", "pt"),
            Target("日本語", "ja"),
            Target("한국어", "ko"),
            Target("中文", "zh")
        )

        fun fromIntent(intent: Intent): StudioOptions = StudioOptions(
            sourceLanguage = intent.getStringExtra(EXTRA_SOURCE_LANGUAGE) ?: "auto",
            targetLanguage = intent.getStringExtra(EXTRA_TARGET_LANGUAGE) ?: "tr",
            quality = intent.getStringExtra(EXTRA_QUALITY) ?: QUALITY_BALANCED,
            speakerMode = intent.getStringExtra(EXTRA_SPEAKER_MODE) ?: SPEAKER_AUTO,
            saveSubtitles = intent.getBooleanExtra(EXTRA_SUBTITLES, true),
            previewOnly = intent.getBooleanExtra(EXTRA_PREVIEW, false),
            previewSeconds = intent.getIntExtra(EXTRA_PREVIEW_SECONDS, 30).coerceIn(10, 90),
            preserveBackground = intent.getBooleanExtra(EXTRA_PRESERVE_BACKGROUND, false),
            timeSync = intent.getBooleanExtra(EXTRA_TIME_SYNC, true),
            resumeEnabled = intent.getBooleanExtra(EXTRA_RESUME, true),
            ttsVolume = intent.getFloatExtra(EXTRA_TTS_VOLUME, 1.0f).coerceIn(0.2f, 1.5f),
            backgroundVolume = intent.getFloatExtra(EXTRA_BACKGROUND_VOLUME, 1.0f).coerceIn(0f, 1.25f),
            speakerProfiles = intent.getStringArrayListExtra(EXTRA_SPEAKER_PROFILES)
                ?.takeIf { it.isNotEmpty() }
                ?: listOf("auto", "deep", "natural", "bright")
        )
    }
}
