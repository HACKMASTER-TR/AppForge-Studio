package com.appforge.studio.ai

import android.content.Context
import com.appforge.studio.io.AppSettingsStore
import com.appforge.studio.model.ProjectDraft
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.io.File
import java.nio.charset.Charset

private fun cleanAssistantOutput(raw: String): String {
    var text = raw

    if (
        text.contains("Ã") ||
        text.contains("Ä") ||
        text.contains("Å") ||
        text.contains("Â")
    ) {
        text = runCatching {
            String(
                text.toByteArray(Charset.forName("windows-1252")),
                Charsets.UTF_8
            )
        }.getOrDefault(text)
    }

    return text
        .replace(Regex("(?is)<think>.*?</think>"), "")
        .replace(Regex("(?is)<think>.*$"), "")
        .replace("</think>", "", ignoreCase = true)
        .replace("**", "")
        .replace("__", "")
        .replace("`", "")
        .replace("\r\n", "\n")
        .replace(Regex("\n{3,}"), "\n\n")
}

private fun extractFinalAssistantAnswer(
    raw: String,
    languageCode: String
): String {
    val cleaned = cleanAssistantOutput(raw).trim()

    Regex("(?is)<final_answer>\\s*(.*?)\\s*</final_answer>")
        .findAll(cleaned)
        .lastOrNull()
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }

    val openTag = cleaned.lastIndexOf("<final_answer>", ignoreCase = true)
    if (openTag >= 0) {
        return cleaned
            .substring(openTag + "<final_answer>".length)
            .replace("</final_answer>", "", ignoreCase = true)
            .trim()
    }

    val filtered = cleaned
        .lineSequence()
        .filterNot { line ->
            val value = line.trim().lowercase()
            value.startsWith("okay,") ||
                value.startsWith("the user") ||
                value.startsWith("first, i need") ||
                value.startsWith("i need to") ||
                value.startsWith("looking at") ||
                value.startsWith("let me")
        }
        .joinToString("\n")
        .trim()

    if (
        (languageCode == "tr" || languageCode == "system") &&
        filtered.isBlank()
    ) {
        return "Geçerli bir Türkçe yanıt üretilemedi."
    }

    return filtered
}

data class LocalAiInitResult(
    val backend: LocalAiBackend,
    val modelName: String
)

class AppForgeLocalAssistant(
    context: Context
) : Closeable {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    val isReady: Boolean
        get() = engine != null && conversation != null

    suspend fun initialize(
        model: LocalAiModelInfo,
        requestedBackend: LocalAiBackend
    ): LocalAiInitResult = mutex.withLock {
        closeInternal()

        require(File(model.path).exists()) {
            "Yerel model dosyası bulunamadı."
        }

        val actual = if (requestedBackend == LocalAiBackend.GPU) {
            runCatching {
                initializeWithBackend(model, LocalAiBackend.GPU)
                LocalAiBackend.GPU
            }.getOrElse {
                closeInternal()
                initializeWithBackend(model, LocalAiBackend.CPU)
                LocalAiBackend.CPU
            }
        } else {
            initializeWithBackend(model, LocalAiBackend.CPU)
            LocalAiBackend.CPU
        }

        LocalAiInitResult(
            backend = actual,
            modelName = model.name
        )
    }

    private fun answerLanguageInstruction(): String {
        return when (
            AppSettingsStore.load(appContext).languageCode
        ) {
            "en" -> """
                RESPONSE LANGUAGE: ENGLISH.
                Answer entirely in English from the first word.
                Show only the final answer; never expose internal reasoning.
            """.trimIndent()

            "de" -> """
                ANTWORTSPRACHE: DEUTSCH.
                Antworte ausschließlich auf Deutsch und zeige nur die endgültige Antwort.
            """.trimIndent()

            "ar" -> """
                لغة الإجابة: العربية.
                أجب باللغة العربية فقط واعرض الإجابة النهائية فقط.
            """.trimIndent()

            else -> """
                YANIT DİLİ KESİNLİKLE TÜRKÇEDİR.
                İlk kelimeden son kelimeye kadar yalnız Türkçe yaz.
                Kullanıcıya yalnız nihai cevabı ver; düşünme sürecini gösterme.
                Türkçe karakterleri doğru kullan ve doğal boşlukları koru.
                AppForge hakkında yalnız verilen doğrulanmış yerel bağlamı kullan.
                Bilgi tabanında veya güvenli proje snapshot'ında bulunmayan özelliği uydurma.
            """.trimIndent()
        }
    }

    suspend fun ask(
        question: String,
        draft: ProjectDraft,
        includeProjectContext: Boolean,
        runtimeContext: AssistantRuntimeContext? = null,
        onPartial: (String) -> Unit
    ) {
        val clean = question.trim()
        require(clean.isNotBlank()) { "Soru boş olamaz." }

        val selectedLanguage = AppSettingsStore.load(appContext).languageCode

        if (selectedLanguage == "tr" || selectedLanguage == "system") {
            AppForgeKnowledgeBase
                .directTurkishAnswer(clean)
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    onPartial(it)
                    return
                }
        }

        val grounding = AppForgeKnowledgeBase.promptContext(
            clean,
            draft,
            includeProjectContext,
            runtimeContext
        )

        val compactGrounding =
            grounding.take(700)

        val compactQuestion =
            clean.take(240)

        val snapshot = if (includeProjectContext || runtimeContext != null) {
            AppForgeAiSnapshotV2.build(
                draft = draft,
                runtime = runtimeContext
            )
        } else {
            "APPFORGE STUDIO COPILOT V2 - proje bağlamı kullanıcı tarafından kapatıldı."
        }

        val prompt = """
            /no_think

            ${answerLanguageInstruction()}

            DOĞRULANMIŞ APPFORGE SNAPSHOT V2:
            ${snapshot.take(2200)}

            İLGİLİ YARDIM BİLGİSİ:
            ${compactGrounding}

            SORU:
            ${compactQuestion}

            Kurallar:
            - Snapshot ve yardım bilgisini gerçek kaynak olarak kullan.
            - Parola, API anahtarı, token veya keystore şifresi isteme/uydurma.
            - Güncel internet bilgisi yoksa varmış gibi davranma.
            - Yalnız kısa ve uygulanabilir nihai cevabı üret.

            <final_answer>
            Nihai cevap
            </final_answer>
        """.trimIndent()

        mutex.withLock {
            val currentEngine = engine
                ?: error("Yerel AI modeli başlatılmadı.")

            conversation?.close()
            val current = currentEngine.createConversation(conversationConfig())
            conversation = current

            withTimeout(180_000L) {
                val rawResult = StringBuilder()
                val streamBuffer = StringBuilder()
                val openTag = "<final_answer>"
                val closeTag = "</final_answer>"
                var finalStarted = false
                var finalEmitted = false

                current.sendMessageAsync(prompt).collect { part ->
                    val chunk = part.toString()
                    rawResult.append(chunk)

                    if (finalEmitted) return@collect
                    streamBuffer.append(chunk)

                    if (!finalStarted) {
                        val start = streamBuffer.indexOf(openTag, ignoreCase = true)
                        if (start >= 0) {
                            streamBuffer.delete(0, start + openTag.length)
                            finalStarted = true
                        } else {
                            val keep = openTag.length + 4
                            if (streamBuffer.length > keep) {
                                streamBuffer.delete(0, streamBuffer.length - keep)
                            }
                            return@collect
                        }
                    }

                    val end = streamBuffer.indexOf(closeTag, ignoreCase = true)
                    if (end >= 0) {
                        val visible = cleanAssistantOutput(
                            streamBuffer.substring(0, end)
                        )
                        if (visible.isNotEmpty()) onPartial(visible)
                        streamBuffer.clear()
                        finalEmitted = true
                        return@collect
                    }

                    val safeLength = streamBuffer.length - (closeTag.length + 2)
                    if (safeLength > 0) {
                        val visible = cleanAssistantOutput(
                            streamBuffer.substring(0, safeLength)
                        )
                        streamBuffer.delete(0, safeLength)
                        if (visible.isNotEmpty()) onPartial(visible)
                    }
                }

                if (!finalStarted) {
                    val finalAnswer = extractFinalAssistantAnswer(
                        rawResult.toString(),
                        selectedLanguage
                    )
                    if (finalAnswer.isNotBlank()) {
                        onPartial(finalAnswer)
                    } else {
                        error("Yerel AI geçerli bir nihai cevap üretemedi.")
                    }
                } else if (!finalEmitted && streamBuffer.isNotEmpty()) {
                    val visible = cleanAssistantOutput(streamBuffer.toString())
                    if (visible.isNotEmpty()) onPartial(visible)
                }
            }
        }
    }

    suspend fun resetConversation() = mutex.withLock {
        val current = engine ?: return@withLock
        conversation?.close()
        conversation = current.createConversation(conversationConfig())
    }

    suspend fun unload() = mutex.withLock {
        closeInternal()
    }

    private suspend fun initializeWithBackend(
        model: LocalAiModelInfo,
        backend: LocalAiBackend
    ) {
        val config = EngineConfig(
            modelPath = model.path,
            backend = when (backend) {
                LocalAiBackend.CPU -> Backend.CPU()
                LocalAiBackend.GPU -> Backend.GPU()
            },
            maxNumTokens = 768,
            cacheDir = File(appContext.cacheDir, "litertlm")
                .apply { mkdirs() }
                .absolutePath
        )

        val created = Engine(config)

        try {
            withContext(Dispatchers.Default) {
                created.initialize()
            }
            engine = created
            conversation = created.createConversation(conversationConfig())
        } catch (error: Throwable) {
            runCatching { created.close() }
            throw error
        }
    }

    private fun conversationConfig() = ConversationConfig(
        systemInstruction = Contents.of(
            """
                Sen AppForge Studio içindeki yerel yardım asistanısın.
                AppForge Studio, Android build, HTML/WebView, Preview, Test Lab,
                imzalama, sürümleme, Pro planları ve proje ayarlarında uygulama içi yardım sağla.

                Her soruda sana doğrulanmış AppForge bilgi tabanı ve güvenli AppForge Studio Copilot V2
                proje/runtime snapshot'ı verilebilir. Kullanıcının sorusuyla ilgili ekranı, mevcut proje
                durumunu ve güvenli çalışma bağlamını birlikte değerlendir. Bağlam yoksa özellik uydurma.
                Güncel internet/Play bilgisine erişemiyorsan bunu açıkça belirt.

                Parola, API anahtarı, oturum tokenı ve keystore şifresi isteme veya tekrarlama.
                İç düşünme, reasoning, chain-of-thought veya <think> içeriği gösterme.
                Nihai cevabı <final_answer> ve </final_answer> etiketleri arasında üret.
                Etiketlerin dışında kullanıcıya dönük metin üretme ve seçili yanıt diline kesin uy.
            """.trimIndent()
        ),
        samplerConfig = SamplerConfig(
            topK = 12,
            topP = 0.80,
            temperature = 0.10
        )
    )

    override fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        runCatching { conversation?.close() }
        conversation = null
        runCatching { engine?.close() }
        engine = null
    }
}
