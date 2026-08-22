package com.appforge.studio.ai

import android.content.Context
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


private fun cleanAssistantOutput(
    raw: String
): String {

    var text =
        raw

    /*
     * Bazı LiteRT-LM çıktılarında UTF-8 metin
     * Windows-1252 olarak yorumlanmış görünebiliyor:
     *
     * geÃ§erli -> geçerli
     * GÃ¶rev    -> Görev
     * seÃ§      -> seç
     */
    val looksBroken =
        text.contains("Ã") ||
        text.contains("Ä") ||
        text.contains("Å") ||
        text.contains("Â")

    if (looksBroken) {
        text =
            runCatching {
                String(
                    text.toByteArray(
                        Charset.forName(
                            "windows-1252"
                        )
                    ),
                    Charsets.UTF_8
                )
            }.getOrDefault(
                text
            )
    }

    /*
     * Modelin kullanıcıya gösterilmemesi gereken
     * düşünme bölümünü kaldır.
     */
    text =
        text.replace(
            Regex(
                "(?is)<think>.*?</think>"
            ),
            ""
        )

    /*
     * Streaming sırasında </think> henüz gelmediyse
     * yarım düşünme içeriğini de ekranda gösterme.
     */
    text =
        text.replace(
            Regex(
                "(?is)<think>.*$"
            ),
            ""
        )

    text =
        text.replace(
            "</think>",
            "",
            ignoreCase = true
        )

    /*
     * UI henüz Markdown renderer kullanmadığı için
     * ham Markdown işaretlerini temizle.
     */
    text =
        text.replace(
            "**",
            ""
        )

    text =
        text.replace(
            "__",
            ""
        )

    text =
        text.replace(
            "`",
            ""
        )

    return text
        .replace(
            "\r\n",
            "\n"
        )
        .replace(
            Regex(
                "\n{3,}"
            ),
            "\n\n"
        )
        .trimStart()
}

data class LocalAiInitResult(
    val backend: LocalAiBackend,
    val modelName: String
)

class AppForgeLocalAssistant(
    context: Context
) : Closeable {
    private val appContext =
        context.applicationContext

    private val mutex =
        Mutex()

    private var engine:
        Engine? =
        null

    private var conversation:
        Conversation? =
        null

    val isReady: Boolean
        get() =
            engine !=
                null &&
            conversation !=
                null

    suspend fun initialize(
        model: LocalAiModelInfo,
        requestedBackend: LocalAiBackend
    ): LocalAiInitResult =
        mutex.withLock {
            closeInternal()

            require(
                File(
                    model.path
                ).exists()
            ) {
                "Yerel model dosyası bulunamadı."
            }

            val actual =
                if (
                    requestedBackend ==
                    LocalAiBackend.GPU
                ) {
                    runCatching {
                        initializeWithBackend(
                            model,
                            LocalAiBackend.GPU
                        )

                        LocalAiBackend.GPU
                    }.getOrElse {
                        closeInternal()

                        initializeWithBackend(
                            model,
                            LocalAiBackend.CPU
                        )

                        LocalAiBackend.CPU
                    }
                } else {
                    initializeWithBackend(
                        model,
                        LocalAiBackend.CPU
                    )

                    LocalAiBackend.CPU
                }

            LocalAiInitResult(
                backend =
                    actual,
                modelName =
                    model.name
            )
        }

    suspend fun ask(
        question: String,
        draft: ProjectDraft,
        includeProjectContext: Boolean,
        onPartial: (String) -> Unit
    ) {
        val clean =
            question.trim()

        require(
            clean.isNotBlank()
        ) {
            "Soru boş olamaz."
        }

        val grounding =
            AppForgeKnowledgeBase
                .promptContext(
                    clean,
                    draft,
                    includeProjectContext
                )

        val prompt =
            """
            Aşağıdaki yerel AppForge bilgisini soruyla ilgiliyse kullan.
            AppForge hakkında bağlamda olmayan bir özelliği uydurma.
            Proje özeti editördeki mevcut durumu gösterir.
            Yanıtı kullanıcının dilinde, açık ve uygulanabilir ver.

            $grounding

            KULLANICI SORUSU:
            $clean
            """.trimIndent()

        mutex.withLock {
            val current =
                conversation
                    ?: error(
                        "Yerel AI modeli başlatılmadı."
                    )

            withTimeout(
                180_000L
            ) {
                current
                    .sendMessageAsync(
                        prompt
                    )
                    .collect {
                        val cleaned =
                            cleanAssistantOutput(
                                it.toString()
                            )

                        if (
                            cleaned.isNotBlank()
                        ) {
                            onPartial(
                                cleaned
                            )
                        }
                    }
            }
        }
    }

    suspend fun resetConversation() =
        mutex.withLock {
            val current =
                engine
                    ?: return@withLock

            conversation
                ?.close()

            conversation =
                current
                    .createConversation(
                        conversationConfig()
                    )
        }

    suspend fun unload() =
        mutex.withLock {
            closeInternal()
        }

    private suspend fun initializeWithBackend(
        model: LocalAiModelInfo,
        backend: LocalAiBackend
    ) {
        val config =
            EngineConfig(
                modelPath =
                    model.path,
                backend =
                    when (
                        backend
                    ) {
                        LocalAiBackend.CPU ->
                            Backend.CPU()

                        LocalAiBackend.GPU ->
                            Backend.GPU()
                    },
                maxNumTokens =
                    4096,
                cacheDir =
                    File(
                        appContext.cacheDir,
                        "litertlm"
                    ).apply {
                        mkdirs()
                    }.absolutePath
            )

        val created =
            Engine(
                config
            )

        try {
            withContext(
                Dispatchers.Default
            ) {
                created
                    .initialize()
            }

            engine =
                created

            conversation =
                created
                    .createConversation(
                        conversationConfig()
                    )
        } catch (
            t: Throwable
        ) {
            runCatching {
                created.close()
            }

            throw t
        }
    }

    private fun conversationConfig() =
        ConversationConfig(
            systemInstruction =
                Contents.of(
                    """
                    Sen AppForge Studio içindeki yerel yardım asistanısın.
                    AppForge Studio, Android build, HTML/WebView, Preview, Test Lab,
                    imzalama, sürümleme, Pro planları ve proje ayarları konusunda
                    uygulama içi yardım sağla.

                    AppForge yerel bağlamıyla çelişme ve bilmediğin AppForge özelliğini uydurma.
                    Güncel internet bilgisine erişemediğin durumlarda bunu açıkça belirt.
                    Parola, API anahtarı ve keystore şifresi isteme veya yanıt içinde tekrarlama.

                    Kullanıcıya yalnızca nihai cevabı göster.
                    İç düşünme, reasoning, chain-of-thought veya <think> bölümü gösterme.
                    Türkçe soruya düzgün UTF-8 Türkçe ile cevap ver.
                    Gereksiz Markdown işaretleri kullanma.
                    Kısa paragraflar ve okunabilir maddeler kullan.
                    """.trimIndent()
                ),
            samplerConfig =
                SamplerConfig(
                    topK =
                        40,
                    topP =
                        0.9,
                    temperature =
                        0.35
                )
        )

    override fun close() {
        closeInternal()
    }

    private fun closeInternal() {
        runCatching {
            conversation
                ?.close()
        }

        conversation =
            null

        runCatching {
            engine
                ?.close()
        }

        engine =
            null
    }
}
