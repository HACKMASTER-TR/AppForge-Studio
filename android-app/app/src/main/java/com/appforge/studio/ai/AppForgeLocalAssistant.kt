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

    /*
     * ÖNEMLİ:
     *
     * sendMessageAsync() cevabı streaming parçalar halinde verir.
     * Birçok parça başında gerçek bir boşluk taşır:
     *
     * "Merhaba"
     * " kullanıcı"
     * ", nasılsın?"
     *
     * Burada trimStart() kullanılırsa sonuç:
     * "Merhabakullanıcı,nasılsın?"
     *
     * olur. Bu nedenle streaming parçasının başındaki boşluğu
     * kesinlikle silmiyoruz.
     */
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
}

private fun extractFinalAssistantAnswer(
    raw: String,
    languageCode: String
): String {

    val cleaned =
        cleanAssistantOutput(
            raw
        ).trim()

    /*
     * Tercih edilen yöntem:
     * Model nihai cevabı özel etiket içine koyar.
     */
    val tagged =
        Regex(
            "(?is)<final_answer>\\s*(.*?)\\s*</final_answer>"
        )
            .findAll(
                cleaned
            )
            .lastOrNull()
            ?.groupValues
            ?.getOrNull(
                1
            )
            ?.trim()

    if (
        !tagged.isNullOrBlank()
    ) {
        return tagged
    }

    /*
     * Kapanış etiketi unutulursa başlangıçtan sonrasını al.
     */
    val openTag =
        cleaned.lastIndexOf(
            "<final_answer>",
            ignoreCase = true
        )

    if (
        openTag >= 0
    ) {
        return cleaned
            .substring(
                openTag +
                    "<final_answer>".length
            )
            .replace(
                "</final_answer>",
                "",
                ignoreCase = true
            )
            .trim()
    }

    /*
     * Bazı reasoning modelleri etikete uymayıp önce
     * İngilizce çalışma notlarını yazabiliyor.
     *
     * Türkçe seçiliyse sondaki gerçek Türkçe cevap
     * bölümünü cümle bazında buluyoruz.
     */
    if (
        languageCode == "tr" ||
        languageCode == "system"
    ) {
        val sentences =
            cleaned
                .split(
                    Regex(
                        "(?<=[.!?])\\s+|\\n+"
                    )
                )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }

        val englishMetaWords =
            setOf(
                "the",
                "user",
                "asking",
                "need",
                "answer",
                "should",
                "first",
                "looking",
                "following",
                "guidelines",
                "structure",
                "ensure",
                "clear",
                "meets",
                "requirements",
                "let",
                "think"
            )

        val turkishWords =
            setOf(
                "ve",
                "ile",
                "için",
                "bir",
                "bu",
                "daha",
                "olarak",
                "fark",
                "arasındaki",
                "proje",
                "projeler",
                "özellik",
                "özellikler",
                "hesap",
                "plan",
                "planı",
                "ücretsiz",
                "kullanıcı",
                "kullanabilir",
                "sunar",
                "oluşturma",
                "derleme",
                "uygulama"
            )

        fun words(
            value: String
        ): List<String> =
            Regex(
                "[\\p{L}]+"
            )
                .findAll(
                    value.lowercase()
                )
                .map {
                    it.value
                }
                .toList()

        fun isTurkishSentence(
            value: String
        ): Boolean {
            val lower =
                value.lowercase()

            val tokens =
                words(
                    value
                )

            val trScore =
                tokens.count {
                    it in turkishWords
                } +
                if (
                    lower.any {
                        it in
                            "çğıöşü"
                    }
                ) {
                    2
                } else {
                    0
                }

            val enScore =
                tokens.count {
                    it in englishMetaWords
                }

            return trScore >= 2 &&
                trScore > enScore
        }

        /*
         * Cevabın sonundan başlayarak kesintisiz Türkçe
         * nihai cevap bölümünü bul.
         */
        var start =
            sentences.size

        for (
            i in sentences.indices.reversed()
        ) {
            if (
                isTurkishSentence(
                    sentences[i]
                )
            ) {
                start =
                    i
            } else if (
                start <
                    sentences.size
            ) {
                break
            }
        }

        if (
            start <
                sentences.size
        ) {
            return sentences
                .subList(
                    start,
                    sentences.size
                )
                .joinToString(
                    " "
                )
                .trim()
        }
    }

    /*
     * Son güvenli fallback:
     * reasoning başlangıçlarını satır bazında temizle.
     */
    return cleaned
        .lineSequence()
        .filterNot {
            line ->
            val l =
                line
                    .trim()
                    .lowercase()

            l.startsWith(
                "okay,"
            ) ||
            l.startsWith(
                "the user"
            ) ||
            l.startsWith(
                "first, i need"
            ) ||
            l.startsWith(
                "i need to"
            ) ||
            l.startsWith(
                "looking at"
            ) ||
            l.startsWith(
                "let me"
            )
        }
        .joinToString(
            "\n"
        )
        .trim()
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

    private fun answerLanguageInstruction(): String {
        val selected =
            AppSettingsStore
                .load(
                    appContext
                )
                .languageCode

        /*
         * StudioI18n şu anda "system" seçimini Türkçe arayüz
         * olarak kullanıyor. Yerel AI de aynı davranışı izler.
         */
        return when (
            selected
        ) {
            "en" ->
                """
                RESPONSE LANGUAGE: ENGLISH.
                Answer entirely in English from the very first word.
                Do not output internal reasoning or a thinking preamble.
                Do not start with phrases such as "Okay, let's see",
                "The user is asking", "I need to", or "We need to".
                Show only the final answer.
                """.trimIndent()

            "de" ->
                """
                ANTWORTSPRACHE: DEUTSCH.
                Antworte vom ersten Wort an ausschließlich auf Deutsch.
                Zeige keine internen Überlegungen oder Denkprozesse.
                Zeige nur die endgültige Antwort.
                """.trimIndent()

            "ar" ->
                """
                لغة الإجابة: العربية.
                أجب باللغة العربية فقط من أول كلمة.
                لا تعرض التفكير الداخلي أو خطوات الاستدلال.
                اعرض الإجابة النهائية فقط.
                """.trimIndent()

            else ->
                """
                YANIT DİLİ KESİNLİKLE TÜRKÇEDİR.

                Yalnızca Türkçe yaz.
                Başka hiçbir dilde kelime veya cümle kurma.
                İlk kelimeden son kelimeye kadar Türkçe kal.

                Kullanıcıya yalnız nihai cevabı ver.
                Düşünme sürecini, analizini veya çalışma notlarını yazma.

                Türkçe karakterleri doğru kullan:
                ç, ğ, ı, İ, ö, ş, ü.

                Kısa, açık ve doğal cümleler kullan.
                Kelimeler arasındaki boşlukları koru.

                AppForge hakkında yalnız verilen yerel bilgi tabanındaki
                gerçekleri kullan. Bilgi tabanında bulunmayan özellik,
                avantaj veya kısıtlama uydurma.
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
        val clean =
            question.trim()

        require(
            clean.isNotBlank()
        ) {
            "Soru boş olamaz."
        }

        val selectedLanguage =
            AppSettingsStore
                .load(
                    appContext
                )
                .languageCode

        /*
         * Türkçe AppForge FAQ sorularında LLM çalıştırılmaz.
         * Bilgi tabanındaki doğrulanmış cevap doğrudan gösterilir.
         *
         * Bu yol milisaniyeler içinde cevap verir.
         */
        if (
            selectedLanguage == "tr" ||
            selectedLanguage == "system"
        ) {
            val directAnswer =
                AppForgeKnowledgeBase
                    .directTurkishAnswer(
                        clean
                    )

            if (
                !directAnswer.isNullOrBlank()
            ) {
                onPartial(
                    directAnswer
                )

                return
            }
        }

        val grounding =
            AppForgeKnowledgeBase
                .promptContext(
                    clean,
                    draft,
                    includeProjectContext,
                    runtimeContext
                )

        val languageInstruction =
            answerLanguageInstruction()

        val prompt =
            """
            /no_think

            $languageInstruction

            Aşağıdaki yerel AppForge bilgisini yalnızca soruyla ilgiliyse kullan.
            AppForge hakkında bağlamda olmayan hiçbir özellik uydurma.
            Bilgi tabanında yazmayan bir özelliği varmış veya yokmuş gibi söyleme.
            Proje özeti editördeki mevcut durumu gösterir.

            Cevabı gereksiz uzatma.
            Normal sorularda 2-6 kısa cümle yeterlidir.

            ÖNEMLİ:
            Yukarıdaki YANIT DİLİ talimatına bu mesajda mutlaka uy.
            Kullanıcıya yalnızca nihai cevabı göster.

            $grounding

            KULLANICI SORUSU:
            $clean

            ÇIKTI KURALI:
            Kullanıcıya gösterilecek cevabı yalnızca aşağıdaki biçimde üret:

            <final_answer>
            Nihai cevap
            </final_answer>

            <final_answer> etiketinden önce veya sonra hiçbir açıklama,
            düşünme metni, analiz, reasoning veya çalışma notu yazma.

            Şimdi yalnız nihai cevabı üret.
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
                /*
                 * Reasoning modellerinde ilk streaming parçaları
                 * iç çalışma notları olabilir.
                 *
                 * Kullanıcıya bunları canlı göstermiyoruz.
                 * Tam cevap cihaz RAM'inde toplanır ve yalnız
                 * nihai cevap ayıklanıp UI'ye gönderilir.
                 */
                val rawResult =
                    StringBuilder()

                val streamBuffer =
                    StringBuilder()

                val openTag =
                    "<final_answer>"

                val closeTag =
                    "</final_answer>"

                var finalStarted =
                    false

                var finalEmitted =
                    false

                current
                    .sendMessageAsync(
                        prompt
                    )
                    .collect {
                        part ->

                        val chunk =
                            part.toString()

                        rawResult.append(
                            chunk
                        )

                        if (
                            finalEmitted
                        ) {
                            return@collect
                        }

                        streamBuffer.append(
                            chunk
                        )

                        if (
                            !finalStarted
                        ) {
                            val start =
                                streamBuffer
                                    .indexOf(
                                        openTag,
                                        ignoreCase = true
                                    )

                            if (
                                start >= 0
                            ) {
                                streamBuffer.delete(
                                    0,
                                    start +
                                        openTag.length
                                )

                                finalStarted =
                                    true
                            } else {
                                /*
                                 * Etiket iki streaming parçasının
                                 * arasından bölünebilir.
                                 * Yalnız son karakterleri sakla.
                                 */
                                val keep =
                                    openTag.length +
                                        4

                                if (
                                    streamBuffer.length >
                                        keep
                                ) {
                                    streamBuffer.delete(
                                        0,
                                        streamBuffer.length -
                                            keep
                                    )
                                }

                                return@collect
                            }
                        }

                        val end =
                            streamBuffer
                                .indexOf(
                                    closeTag,
                                    ignoreCase = true
                                )

                        if (
                            end >= 0
                        ) {
                            val visible =
                                streamBuffer
                                    .substring(
                                        0,
                                        end
                                    )

                            val cleaned =
                                cleanAssistantOutput(
                                    visible
                                )

                            if (
                                cleaned.isNotEmpty()
                            ) {
                                onPartial(
                                    cleaned
                                )
                            }

                            streamBuffer.clear()

                            finalEmitted =
                                true

                            return@collect
                        }

                        /*
                         * Kapanış etiketi streaming sırasında
                         * bölünebileceği için son birkaç karakteri
                         * tamponda tut.
                         */
                        val safeLength =
                            streamBuffer.length -
                                (
                                    closeTag.length +
                                        2
                                )

                        if (
                            safeLength >
                                0
                        ) {
                            val visible =
                                streamBuffer
                                    .substring(
                                        0,
                                        safeLength
                                    )

                            streamBuffer.delete(
                                0,
                                safeLength
                            )

                            val cleaned =
                                cleanAssistantOutput(
                                    visible
                                )

                            if (
                                cleaned.isNotEmpty()
                            ) {
                                onPartial(
                                    cleaned
                                )
                            }
                        }
                    }

                /*
                 * Model final_answer etiketine uymazsa
                 * eski güvenli ayıklayıcı devreye girer.
                 */
                if (
                    !finalStarted
                ) {
                    val selectedLanguage =
                        AppSettingsStore
                            .load(
                                appContext
                            )
                            .languageCode

                    val finalAnswer =
                        extractFinalAssistantAnswer(
                            rawResult.toString(),
                            selectedLanguage
                        )

                    if (
                        finalAnswer.isNotBlank()
                    ) {
                        onPartial(
                            finalAnswer
                        )
                    } else {
                        error(
                            "Yerel AI geçerli bir nihai cevap üretemedi."
                        )
                    }
                } else if (
                    !finalEmitted &&
                    streamBuffer.isNotEmpty()
                ) {
                    val cleaned =
                        cleanAssistantOutput(
                            streamBuffer.toString()
                        )

                    if (
                        cleaned.isNotEmpty()
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
                /*
                 * Yerel yardım asistanı için 4096 token gereksizdi.
                 * Daha kısa sınır Qwen3'ün gereksiz uzun üretimini
                 * azaltır ve yanıt süresini iyileştirir.
                 */
                maxNumTokens =
                    1536,
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

                    Sana her soruda uygulamanın modül haritası verilir. Kullanıcının
                    sorusuyla ilgili ekranı, mevcut proje durumunu ve güvenli çalışma
                    bağlamını birlikte değerlendir. Bir ayarın nerede olduğunu sorarsa
                    açık ekran yolunu söyle. Mevcut build/proje durumu verilmişse genel
                    cevap yerine o duruma göre somut sonraki adımı öner.

                    AppForge yerel bağlamıyla çelişme ve bilmediğin AppForge özelliğini uydurma.
                    Güncel internet bilgisine erişemediğin durumlarda bunu açıkça belirt.
                    Parola, API anahtarı ve keystore şifresi isteme veya yanıt içinde tekrarlama.

                    Kullanıcıya yalnızca nihai cevabı göster.
                    İç düşünme, reasoning, chain-of-thought veya <think> bölümü gösterme.
                    Nihai cevabı <final_answer> ve </final_answer> etiketleri arasında üret.
                    Bu etiketlerin dışında hiçbir kullanıcıya dönük metin üretme.
                    Uygulamanın seçili yanıt diline kesin olarak uy.
                    Türkçe istendiğinde ilk kelimeden itibaren yalnız Türkçe cevap ver.
                    İngilizce iç düşünme veya "Okay, let's see" gibi girişler gösterme.
                    Düzgün UTF-8 Türkçe karakterleri kullan.
                    Kelimeler arasında doğal boşlukları koru.
                    Gereksiz Markdown işaretleri kullanma.
                    Kısa paragraflar ve okunabilir maddeler kullan.
                    """.trimIndent()
                ),
            samplerConfig =
                SamplerConfig(
                    topK =
                        20,
                    topP =
                        0.80,
                    temperature =
                        0.10
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
