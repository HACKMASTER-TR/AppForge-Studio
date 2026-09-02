package com.hackmaster.videoforge

import android.content.Context
import android.net.Uri
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translation
import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class OfflineDubEngine(
    private val context: Context,
    private val models: ModelManager
) {
    data class Segment(val start: Double, val end: Double, val speaker: Int)

    data class Turn(
        val start: Double,
        val end: Double,
        val speaker: Int,
        val sourceText: String,
        val sourceLanguage: String,
        val translatedText: String
    )

    data class Result(
        val videoUri: Uri,
        val subtitleUri: Uri?,
        val turns: Int,
        val speakers: Int,
        val preview: Boolean
    )

    suspend fun run(
        input: Uri,
        options: StudioOptions,
        checkpointKey: String = input.toString(),
        onProgress: (Int, String) -> Unit
    ): Result = withContext(Dispatchers.Default) {
        require(models.isReady()) { "AI modelleri hazır değil." }

        val previewLimit = if (options.previewOnly) options.previewSeconds else null
        StorageGuard.requireEnough(context, input, previewLimit)

        onProgress(3, "Video sesi telefonda çözülüyor…")
        val decodedRaw = withContext(Dispatchers.IO) { AudioMedia.decodeMono(context, input) }
        val duration = if (previewLimit != null) min(decodedRaw.durationSeconds, previewLimit.toDouble()) else decodedRaw.durationSeconds
        val decodedSamples = if (duration < decodedRaw.durationSeconds) {
            AudioMedia.slice(decodedRaw.samples, decodedRaw.sampleRate, 0.0, duration)
        } else decodedRaw.samples

        val original48 = withContext(Dispatchers.Default) {
            AudioMedia.resample(decodedSamples, decodedRaw.sampleRate, OUTPUT_SAMPLE_RATE)
        }
        val aiAudio = withContext(Dispatchers.Default) {
            AudioMedia.resample(decodedSamples, decodedRaw.sampleRate, AI_SAMPLE_RATE)
        }

        val checkpoint = JobCheckpointStore(context, checkpointKey, options.sourceLanguage, options.targetLanguage)
        val savedState = if (options.resumeEnabled) checkpoint.load() else null

        val segments: List<Segment>
        var resumeIndex = 0
        val turns = mutableListOf<Turn>()

        if (savedState != null && savedState.segments.isNotEmpty()) {
            onProgress(12, "Kaldığı yerden devam için önceki analiz yükleniyor…")
            segments = savedState.segments.map { Segment(it.start, it.end, it.speaker) }
                .filter { it.start < duration }
                .map { it.copy(end = min(it.end, duration)) }
            resumeIndex = savedState.nextSegmentIndex.coerceIn(0, segments.size)
            turns += savedState.turns.map {
                Turn(it.start, it.end, it.speaker, it.sourceText, it.sourceLanguage, it.translatedText)
            }
        } else {
            segments = when {
                options.speakerMode == StudioOptions.SPEAKER_SINGLE -> {
                    onProgress(20, "Tek konuşmacı modu: konuşma parçaları hazırlanıyor…")
                    fixedSegments(duration, 18.0)
                }
                options.diarizationEnabled -> {
                    onProgress(12, "Konuşmacılar otomatik ayrılıyor…")
                    val raw = diarizeInChunks(aiAudio, options) { done, total ->
                        val pct = 12 + (done * 16 / total.coerceAtLeast(1))
                        onProgress(pct.coerceIn(12, 28), "Konuşmacılar ayrılıyor… $done/$total")
                    }
                    normalizeSegments(raw, duration)
                }
                else -> {
                    onProgress(20, "Hızlı mod: konuşma parçaları hazırlanıyor…")
                    fixedSegments(duration, 18.0)
                }
            }
            require(segments.isNotEmpty()) { "Videoda dublaj yapılabilecek konuşma bulunamadı." }
            if (options.resumeEnabled) {
                checkpoint.save(
                    JobCheckpointStore.State(
                        nextSegmentIndex = 0,
                        segments = segments.map { JobCheckpointStore.Segment(it.start, it.end, it.speaker) },
                        turns = emptyList()
                    )
                )
            }
        }

        onProgress(29, "Whisper konuşma modeli yükleniyor…")
        val recognizer = createRecognizer(options.asrThreads)
        val translators = mutableMapOf<String, Translator>()
        val languageId = LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder().setConfidenceThreshold(0.35f).build()
        )

        try {
            for (index in resumeIndex until segments.size) {
                val segment = segments[index]
                val pct = 30 + ((index + 1) * 28 / segments.size.coerceAtLeast(1))
                onProgress(pct, "Konuşmalar çevriliyor… ${index + 1}/${segments.size}")

                val clip = AudioMedia.slice(aiAudio, AI_SAMPLE_RATE, segment.start, segment.end)
                if (clip.size >= AI_SAMPLE_RATE / 3) {
                    val transcribed = transcribe(recognizer, clip)
                    val text = transcribed.first.trim()
                    if (text.isNotBlank()) {
                        val language = resolveSourceLanguage(
                            text = text,
                            whisperLanguage = transcribed.second,
                            requestedSource = options.sourceLanguage,
                            languageId = languageId
                        )
                        val translated = translate(text, language, options.targetLanguage, translators)
                        if (translated.isNotBlank()) {
                            turns += Turn(
                                start = segment.start,
                                end = segment.end,
                                speaker = segment.speaker,
                                sourceText = text,
                                sourceLanguage = language,
                                translatedText = translated
                            )
                        }
                    }
                }

                if (options.resumeEnabled) {
                    checkpoint.save(
                        JobCheckpointStore.State(
                            nextSegmentIndex = index + 1,
                            segments = segments.map { JobCheckpointStore.Segment(it.start, it.end, it.speaker) },
                            turns = turns.map {
                                JobCheckpointStore.SavedTurn(
                                    it.start, it.end, it.speaker, it.sourceText, it.sourceLanguage, it.translatedText
                                )
                            }
                        )
                    )
                }
            }
        } finally {
            recognizer.release()
            languageId.close()
            translators.values.forEach { it.close() }
        }

        require(turns.isNotEmpty()) { "Konuşma algılandı fakat dublaj metni oluşturulamadı." }

        onProgress(59, "Çift konuşmalar temizleniyor ve ses profilleri dengeleniyor…")
        val stableTurns = stabilizeTurns(aiAudio, turns, options.speakerMode)
        require(stableTurns.isNotEmpty()) { "Dublaj için geçerli konuşma kalmadı." }

        val speakerPitches = estimateSpeakerPitchProfiles(aiAudio, stableTurns)
        onProgress(60, "${options.targetLabel} yapay sesler hazırlanıyor…")

        val tts = DeviceTts.create(context, options.targetLocale)
        val dubAudios = mutableListOf<AudioMedia.DubAudio>()
        val tempDir = File(context.cacheDir, "vf-studio-${System.nanoTime()}").apply { mkdirs() }
        try {
            for ((index, turn) in stableTurns.withIndex()) {
                val pct = 60 + ((index + 1) * 22 / stableTurns.size.coerceAtLeast(1))
                onProgress(pct, "Dublaj sesleri oluşturuluyor… ${index + 1}/${stableTurns.size}")
                val persistentWav = checkpoint.ttsFile(index)
                val wav = if (options.resumeEnabled && persistentWav.isFile && persistentWav.length() > 44) {
                    withContext(Dispatchers.IO) { WavReader.read(persistentWav) }
                } else {
                    val output = if (options.resumeEnabled) persistentWav else File(tempDir, "tts-$index.wav")
                    tts.synthesize(
                        text = turn.translatedText,
                        speaker = turn.speaker,
                        targetSeconds = (turn.end - turn.start).coerceAtLeast(0.7),
                        acousticPitch = speakerPitches[turn.speaker],
                        speakerProfile = options.speakerProfiles.getOrElse(Math.floorMod(turn.speaker, options.speakerProfiles.size.coerceAtLeast(1))) { "auto" },
                        output = output
                    )
                }
                dubAudios += AudioMedia.DubAudio(turn.start, turn.end, wav.samples, wav.sampleRate)
            }
        } finally {
            tts.close()
        }

        onProgress(84, if (options.preserveBackground) "Konuşma kaldırılıp arka plan korunuyor…" else "Eski konuşma susturulup dublaj yerleştiriliyor…")
        val mixed = withContext(Dispatchers.Default) {
            AudioMedia.replaceSpeechWithDubs(
                original = original48,
                sampleRate = OUTPUT_SAMPLE_RATE,
                dubs = dubAudios,
                mutePaddingMs = 120,
                preserveBackground = options.preserveBackground,
                backgroundVolume = options.backgroundVolume,
                ttsVolume = options.ttsVolume,
                timeSync = options.timeSync
            )
        }

        onProgress(91, "MP4 oluşturuluyor…")
        val tempMp4 = File(tempDir, "VideoForge_Studio.mp4")
        withContext(Dispatchers.IO) {
            AudioMedia.muxVideoWithMonoAac(
                context,
                input,
                mixed,
                OUTPUT_SAMPLE_RATE,
                tempMp4,
                bitrate = options.audioBitrate,
                maxDurationUs = if (options.previewOnly) (duration * 1_000_000.0).toLong() else null
            )
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val prefix = if (options.previewOnly) "VideoForge_PREVIEW" else "VideoForge_${options.targetLanguage.uppercase(Locale.US)}"
        val videoUri = withContext(Dispatchers.IO) {
            AudioMedia.saveVideoToMediaStore(context, tempMp4, "${prefix}_$stamp.mp4")
        }
        val subtitleUri = if (options.saveSubtitles) {
            withContext(Dispatchers.IO) {
                AudioMedia.saveSrtToDownloads(context, toSrt(stableTurns), "${prefix}_$stamp.srt")
            }
        } else null

        tempDir.deleteRecursively()
        if (options.resumeEnabled) checkpoint.clear()
        onProgress(100, if (options.previewOnly) "Önizleme hazır." else "Dublaj tamamlandı ve Movies/VideoForge klasörüne kaydedildi.")
        Result(
            videoUri = videoUri,
            subtitleUri = subtitleUri,
            turns = stableTurns.size,
            speakers = stableTurns.map { it.speaker }.distinct().size,
            preview = options.previewOnly
        )
    }

    private fun stabilizeTurns(samples: FloatArray, rawTurns: List<Turn>, speakerMode: String): List<Turn> {
        if (rawTurns.isEmpty()) return emptyList()

        val sorted = rawTurns.sortedWith(compareBy<Turn> { it.start }.thenBy { it.end })
        val deduped = mutableListOf<Turn>()
        for (candidate in sorted) {
            val duplicateIndex = deduped.indexOfLast { previous ->
                val overlapStart = max(previous.start, candidate.start)
                val overlapEnd = min(previous.end, candidate.end)
                val overlap = max(0.0, overlapEnd - overlapStart)
                val shorter = min(previous.end - previous.start, candidate.end - candidate.start).coerceAtLeast(0.001)
                val overlapRatio = overlap / shorter
                val closeBoundary = candidate.start - previous.end <= 0.18 && candidate.start >= previous.start
                val similarity = textSimilarity(previous.sourceText, candidate.sourceText)
                (overlapRatio >= 0.48 && similarity >= 0.66) || (closeBoundary && similarity >= 0.88)
            }
            if (duplicateIndex >= 0) {
                val old = deduped[duplicateIndex]
                val keepCandidate = candidate.sourceText.length > old.sourceText.length + 4
                val chosen = if (keepCandidate) candidate else old
                deduped[duplicateIndex] = chosen.copy(
                    start = min(old.start, candidate.start),
                    end = max(old.end, candidate.end),
                    speaker = min(old.speaker, candidate.speaker)
                )
            } else {
                deduped += candidate
            }
        }

        if (speakerMode == StudioOptions.SPEAKER_SINGLE) {
            return deduped.map { it.copy(speaker = 0) }
        }
        if (speakerMode == StudioOptions.SPEAKER_MULTI) {
            return reindexSpeakers(deduped)
        }

        // Chunked diarization uses chunk-local speaker IDs. Reconcile those IDs
        // conservatively with acoustic pitch statistics so one real speaker does
        // not become multiple synthetic voices between chunks.
        val bySpeaker = deduped.groupBy { it.speaker }
        if (bySpeaker.size <= 1) return deduped.map { it.copy(speaker = 0) }

        data class SpeakerStat(val id: Int, val duration: Double, val pitchHz: Double?)
        val stats = bySpeaker.map { (speaker, speakerTurns) ->
            val duration = speakerTurns.sumOf { (it.end - it.start).coerceAtLeast(0.0) }
            val pitches = speakerTurns
                .sortedByDescending { it.end - it.start }
                .take(6)
                .mapNotNull { turn ->
                    val a = turn.start + 0.06
                    val b = min(turn.end - 0.06, a + 1.15)
                    if (b <= a + 0.24) null else estimateFundamentalHz(AudioMedia.slice(samples, AI_SAMPLE_RATE, a, b), AI_SAMPLE_RATE)
                }
                .sorted()
            SpeakerStat(speaker, duration, pitches.takeIf { it.isNotEmpty() }?.get(pitches.size / 2))
        }.sortedByDescending { it.duration }

        val totalDuration = stats.sumOf { it.duration }.coerceAtLeast(0.001)
        val dominant = stats.first()
        val canonical = mutableMapOf<Int, Int>()
        canonical[dominant.id] = dominant.id

        for (stat in stats.drop(1)) {
            val compatible = stats.firstOrNull { target ->
                canonical.containsKey(target.id) && target.pitchHz != null && stat.pitchHz != null &&
                    (abs(target.pitchHz - stat.pitchHz) <= 28.0 ||
                        abs(target.pitchHz - stat.pitchHz) / max(target.pitchHz, stat.pitchHz) <= 0.14)
            }
            when {
                compatible != null -> canonical[stat.id] = canonical[compatible.id] ?: compatible.id
                stat.pitchHz == null && stat.duration / totalDuration <= 0.24 -> canonical[stat.id] = dominant.id
                dominant.duration / totalDuration >= 0.72 && stat.duration / totalDuration <= 0.18 -> canonical[stat.id] = dominant.id
                else -> canonical[stat.id] = stat.id
            }
        }

        return reindexSpeakers(deduped.map { it.copy(speaker = canonical[it.speaker] ?: it.speaker) })
    }

    private fun reindexSpeakers(turns: List<Turn>): List<Turn> {
        val ids = linkedMapOf<Int, Int>()
        return turns.sortedBy { it.start }.map { turn ->
            val id = ids.getOrPut(turn.speaker) { ids.size }
            turn.copy(speaker = id)
        }
    }

    private fun textSimilarity(a: String, b: String): Double {
        fun words(value: String): Set<String> = value
            .lowercase(Locale.US)
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 2 }
            .toSet()
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return if (a.trim().equals(b.trim(), true)) 1.0 else 0.0
        val intersection = wa.intersect(wb).size.toDouble()
        val union = wa.union(wb).size.toDouble().coerceAtLeast(1.0)
        val jaccard = intersection / union
        val aa = a.trim().lowercase(Locale.US)
        val bb = b.trim().lowercase(Locale.US)
        val containment = if (aa.length >= 8 && bb.length >= 8 && (aa.contains(bb) || bb.contains(aa))) 0.92 else 0.0
        return max(jaccard, containment)
    }

    private fun fixedSegments(duration: Double, chunkSeconds: Double): List<Segment> {
        val result = mutableListOf<Segment>()
        var start = 0.0
        while (start < duration) {
            val end = min(duration, start + chunkSeconds)
            if (end - start >= 0.35) result += Segment(start, end, 0)
            start = end
        }
        return result
    }

    private fun createDiarizer(options: StudioOptions): OfflineSpeakerDiarization {
        val threads = min(4, max(2, options.asrThreads))
        val threshold = if (options.quality == StudioOptions.QUALITY_HIGH) 0.60f else 0.65f
        val config = OfflineSpeakerDiarizationConfig(
            segmentation = OfflineSpeakerSegmentationModelConfig(
                pyannote = OfflineSpeakerSegmentationPyannoteModelConfig(
                    model = models.segmentationModel.absolutePath
                ),
                numThreads = threads,
                debug = false,
                provider = "cpu"
            ),
            embedding = SpeakerEmbeddingExtractorConfig(
                model = models.embeddingModel.absolutePath,
                numThreads = threads,
                debug = false,
                provider = "cpu"
            ),
            clustering = FastClusteringConfig(
                numClusters = -1,
                threshold = threshold
            ),
            minDurationOn = 0.25f,
            minDurationOff = 0.45f
        )
        return OfflineSpeakerDiarization(config = config)
    }

    private fun normalizeSegments(raw: List<Segment>, duration: Double): List<Segment> {
        val cleaned = raw
            .map { Segment(it.start.coerceAtLeast(0.0), min(it.end, duration), it.speaker.coerceAtLeast(0)) }
            .filter { it.end - it.start >= 0.35 }
            .sortedBy { it.start }

        val merged = mutableListOf<Segment>()
        for (seg in cleaned) {
            val last = merged.lastOrNull()
            if (last != null && last.speaker == seg.speaker && seg.start - last.end <= 0.30 && seg.end - last.start <= 24.0) {
                merged[merged.lastIndex] = Segment(last.start, max(last.end, seg.end), last.speaker)
            } else merged += seg
        }

        val split = mutableListOf<Segment>()
        for (seg in merged) {
            var start = seg.start
            while (seg.end - start > 24.0) {
                split += Segment(start, start + 24.0, seg.speaker)
                start += 24.0
            }
            if (seg.end - start >= 0.35) split += Segment(start, seg.end, seg.speaker)
        }
        return split
    }

    private fun diarizeInChunks(
        samples: FloatArray,
        options: StudioOptions,
        onChunk: (Int, Int) -> Unit
    ): List<Segment> {
        if (samples.isEmpty()) return emptyList()
        val chunkSamples = AI_SAMPLE_RATE * options.diarizationChunkSeconds
        val total = ((samples.size + chunkSamples - 1) / chunkSamples).coerceAtLeast(1)
        val result = mutableListOf<Segment>()
        val sd = createDiarizer(options)
        try {
            var offset = 0
            var index = 0
            while (offset < samples.size) {
                val end = min(samples.size, offset + chunkSamples)
                val chunk = samples.copyOfRange(offset, end)
                val baseSeconds = offset.toDouble() / AI_SAMPLE_RATE
                val local = try {
                    sd.process(chunk).toList().map { Segment(it.start.toDouble(), it.end.toDouble(), it.speaker) }
                } catch (_: Throwable) {
                    listOf(Segment(0.0, chunk.size.toDouble() / AI_SAMPLE_RATE, 0))
                }
                local.forEach { seg -> result += Segment(seg.start + baseSeconds, seg.end + baseSeconds, seg.speaker) }
                index++
                onChunk(index, total)
                offset = end
            }
        } finally {
            sd.release()
        }
        return result
    }

    private fun createRecognizer(threads: Int): OfflineRecognizer {
        val whisper = OfflineWhisperModelConfig(
            encoder = models.whisperEncoder.absolutePath,
            decoder = models.whisperDecoder.absolutePath,
            language = "",
            task = "transcribe",
            tailPaddings = 300,
            enableTokenTimestamps = false,
            enableSegmentTimestamps = false
        )
        val model = OfflineModelConfig(
            whisper = whisper,
            numThreads = threads.coerceIn(1, 4),
            debug = false,
            provider = "cpu",
            tokens = models.whisperTokens.absolutePath,
            modelType = "whisper"
        )
        return OfflineRecognizer(
            config = OfflineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = AI_SAMPLE_RATE, featureDim = 80),
                modelConfig = model,
                decodingMethod = "greedy_search"
            )
        )
    }

    private fun transcribe(recognizer: OfflineRecognizer, samples: FloatArray): Pair<String, String> {
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, AI_SAMPLE_RATE)
            recognizer.decode(stream)
            val result = recognizer.getResult(stream)
            result.text to result.lang
        } finally {
            stream.release()
        }
    }


    private suspend fun resolveSourceLanguage(
        text: String,
        whisperLanguage: String?,
        requestedSource: String,
        languageId: com.google.mlkit.nl.languageid.LanguageIdentifier
    ): String {
        val requested = normalizeLanguageTag(requestedSource)
        if (requested.isNotBlank() && requested != "auto") return requested

        val whisper = normalizeLanguageTag(whisperLanguage)
        if (isTranslationLanguageSupported(whisper)) return whisper

        val candidates = runCatching { languageId.identifyPossibleLanguages(text).await() }.getOrDefault(emptyList())
            .map { normalizeLanguageTag(it.languageTag) to it.confidence }
            .filter { (tag, confidence) -> confidence >= 0.35f && isTranslationLanguageSupported(tag) }
            .sortedByDescending { it.second }
        if (candidates.isNotEmpty()) return candidates.first().first

        val fallback = fallbackLanguageByScript(text)
        if (isTranslationLanguageSupported(fallback)) return fallback

        throw IllegalStateException("Kaynak dil otomatik algılanamadı. Studio ayarlarından Kaynak dil seç.")
    }

    private fun isTranslationLanguageSupported(tag: String): Boolean {
        if (tag.isBlank() || tag == "und" || tag == "auto") return false
        return TranslateLanguage.fromLanguageTag(tag) != null
    }

    private fun fallbackLanguageByScript(text: String): String {
        if (text.any { it.code in 0x3040..0x30FF }) return "ja"
        if (text.any { it.code in 0xAC00..0xD7AF }) return "ko"
        if (text.any { it.code in 0x4E00..0x9FFF }) return "zh"
        val latinLetters = text.count { it in 'A'..'Z' || it in 'a'..'z' }
        val letters = text.count { it.isLetter() }.coerceAtLeast(1)
        if (latinLetters.toDouble() / letters >= 0.7) return "en"
        return ""
    }

    private suspend fun translate(
        text: String,
        languageTag: String,
        targetTag: String,
        cache: MutableMap<String, Translator>
    ): String {
        val sourceTag = normalizeLanguageTag(languageTag)
        val target = normalizeLanguageTag(targetTag)
        if (sourceTag == target) return text
        val source = TranslateLanguage.fromLanguageTag(sourceTag)
            ?: throw IllegalStateException("Algılanan '$sourceTag' dili cihaz-içi çeviri tarafından desteklenmiyor.")
        val targetLanguage = TranslateLanguage.fromLanguageTag(target)
            ?: throw IllegalStateException("Hedef '$target' dili cihaz-içi çeviri tarafından desteklenmiyor.")
        val key = "$source->$targetLanguage"
        val translator = cache.getOrPut(key) {
            Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(source)
                    .setTargetLanguage(targetLanguage)
                    .build()
            )
        }
        translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        return translator.translate(text).await().trim()
    }

    private fun normalizeLanguageTag(raw: String?): String {
        val value = raw.orEmpty().trim().lowercase(Locale.US)
        return when (value) {
            "jp" -> "ja"
            "zh", "zh-cn", "chinese" -> "zh"
            "pt-br" -> "pt"
            "iw" -> "he"
            "" -> ""
            else -> value.substringBefore('_').substringBefore('-')
        }
    }

    private fun estimateSpeakerPitchProfiles(samples: FloatArray, turns: List<Turn>): Map<Int, Float> {
        val grouped = turns.groupBy { it.speaker }
        val result = mutableMapOf<Int, Float>()
        for ((speaker, speakerTurns) in grouped) {
            val estimates = mutableListOf<Double>()
            for (turn in speakerTurns.sortedByDescending { it.end - it.start }.take(4)) {
                val start = turn.start + 0.08
                val end = min(turn.end - 0.08, start + 1.35)
                if (end <= start + 0.25) continue
                val clip = AudioMedia.slice(samples, AI_SAMPLE_RATE, start, end)
                estimateFundamentalHz(clip, AI_SAMPLE_RATE)?.let { estimates += it }
            }
            if (estimates.isNotEmpty()) {
                val sorted = estimates.sorted()
                val hz = sorted[sorted.size / 2]
                val normalized = ((hz - 75.0) / (300.0 - 75.0)).coerceIn(0.0, 1.0)
                result[speaker] = (0.86 + normalized * 0.30).toFloat().coerceIn(0.86f, 1.16f)
            }
        }
        return result
    }

    private fun estimateFundamentalHz(samples: FloatArray, sampleRate: Int): Double? {
        if (samples.size < sampleRate / 4) return null
        val maxSamples = min(samples.size, (sampleRate * 1.2).toInt())
        var mean = 0.0
        for (i in 0 until maxSamples) mean += samples[i]
        mean /= maxSamples
        var energy = 0.0
        for (i in 0 until maxSamples) {
            val v = samples[i] - mean.toFloat()
            energy += v * v
        }
        val rms = sqrt(energy / maxSamples)
        if (rms < 0.008) return null
        val minLag = max(1, sampleRate / 320)
        val maxLag = min(maxSamples / 2, sampleRate / 75)
        var bestLag = -1
        var bestCorr = 0.0
        for (lag in minLag..maxLag) {
            var xy = 0.0
            var xx = 0.0
            var yy = 0.0
            var i = 0
            while (i + lag < maxSamples) {
                val a = (samples[i] - mean.toFloat()).toDouble()
                val b = (samples[i + lag] - mean.toFloat()).toDouble()
                xy += a * b
                xx += a * a
                yy += b * b
                i += 2
            }
            if (xx <= 1e-9 || yy <= 1e-9) continue
            val corr = xy / sqrt(xx * yy)
            if (corr > bestCorr) {
                bestCorr = corr
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestCorr < 0.22 || abs(bestCorr) > 1.01) return null
        return sampleRate.toDouble() / bestLag
    }

    private fun toSrt(turns: List<Turn>): String = turns.mapIndexed { index, turn ->
        buildString {
            append(index + 1).append('\n')
            append(srtTime(turn.start)).append(" --> ").append(srtTime(turn.end)).append('\n')
            append(turn.translatedText.trim()).append("\n\n")
        }
    }.joinToString("")

    private fun srtTime(seconds: Double): String {
        val ms = (seconds.coerceAtLeast(0.0) * 1000.0).toLong()
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1000
        val r = ms % 1000
        return "%02d:%02d:%02d,%03d".format(Locale.US, h, m, s, r)
    }

    companion object {
        private const val AI_SAMPLE_RATE = 16_000
        private const val OUTPUT_SAMPLE_RATE = 48_000
    }
}
