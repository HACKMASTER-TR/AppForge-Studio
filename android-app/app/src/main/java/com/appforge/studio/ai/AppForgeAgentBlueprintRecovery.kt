package com.appforge.studio.ai

internal enum class AppForgeAgentBlueprintOrigin {
    LOCAL_AI,
    LOCAL_AI_RETRY,
    VERIFIED_WEB_GAME_TEMPLATE
}

internal data class AppForgeAgentBlueprintRecoveryResult(
    val blueprint: AppForgeAgentBlueprint,
    val origin: AppForgeAgentBlueprintOrigin,
    val notice: String
)

/** Strict model parsing first; a bounded retry; explicitly labeled Web-game template last. */
internal object AppForgeAgentBlueprintRecovery {
    suspend fun generate(
        userPrompt: String,
        platform: AppForgeAgentPlatform,
        generator: suspend (String) -> String,
        onRetry: () -> Unit = {}
    ): AppForgeAgentBlueprintRecoveryResult {
        val clean = userPrompt.trim()
        require(clean.isNotBlank() && clean.length <= 4_000) {
            "Uygulama veya oyun açıklaması 1..4000 karakter olmalı."
        }

        fun parse(raw: String): AppForgeAgentBlueprint {
            val parsed = AppForgeAgentBlueprintJson.parse(
                raw = raw,
                fallbackPlatform = platform
            )
            require(parsed.platform == platform) {
                "AI Blueprint platformu seçilen platform ile eşleşmiyor."
            }
            // Modelin özetlediği veya yeniden yazdığı isteğin aslı korunur.
            val preserved = parsed.copy(prompt = clean)
            require(AppForgeAgentBlueprintValidator.validate(preserved).valid) {
                "Yerel AI Blueprint kullanıcı isteğini koruyamadı."
            }
            return preserved
        }

        val first = generator(
            AppForgeAgentBlueprintPrompt.build(clean, platform)
        )
        try {
            return AppForgeAgentBlueprintRecoveryResult(
                blueprint = parse(first),
                origin = AppForgeAgentBlueprintOrigin.LOCAL_AI,
                notice = "Blueprint yerel AI tarafından oluşturuldu."
            )
        } catch (_: AppForgeAgentBlueprintJsonException) {
            // Sadece şema/geçerlilik hatalarında tekrar dene. Model çalışma hatasını yutma.
        }

        onRetry()
        val second = generator(compactRetryPrompt(clean, platform))
        try {
            return AppForgeAgentBlueprintRecoveryResult(
                blueprint = parse(second),
                origin = AppForgeAgentBlueprintOrigin.LOCAL_AI_RETRY,
                notice = "Blueprint yerel AI tarafından ikinci denemede oluşturuldu."
            )
        } catch (invalid: AppForgeAgentBlueprintJsonException) {
            if (platform == AppForgeAgentPlatform.WEB && explicitGameRequest(clean)) {
                val template = verifiedGameTemplate(clean)
                // Template hiçbir şekilde geçersiz şemayı sessizce kabul etmez.
                require(AppForgeAgentBlueprintValidator.validate(template).valid)
                return AppForgeAgentBlueprintRecoveryResult(
                    blueprint = template,
                    origin = AppForgeAgentBlueprintOrigin.VERIFIED_WEB_GAME_TEMPLATE,
                    notice = "Yerel AI iki denemede geçerli Blueprint üretmedi. " +
                        "Web oyunu doğrulanmış hazır şablonla açıldı; AI özel tasarımı değildir."
                )
            }
            throw AppForgeAgentBlueprintJsonException(
                "Yerel AI iki denemede geçerli Blueprint üretemedi: " +
                    (invalid.message ?: "Şema doğrulaması başarısız.").take(180)
            )
        }
    }

    private fun compactRetryPrompt(
        prompt: String,
        platform: AppForgeAgentPlatform
    ): String = """
        /no_think
        Önceki Blueprint şema doğrulamasında başarısız oldu.
        Yalnız tek JSON nesnesini <final_answer> ve </final_answer> arasında yaz.
        Açıklama, markdown, bilinmeyen alan, ham satır sonu ve gizli bilgi yok.
        Kısa, tek ekranlı ve geçerli JSON üret; screens MUTLAKA dizi olmalı.
        ŞEMA ÖRNEĞİ (alanları atlama):
        {"schemaVersion":1,"appName":"AppForge Projesi","prompt":"Uygulama veya oyun","platform":"${platform.name}","startRoute":"/home","screens":[{"id":"home","title":"Ana ekran","route":"/home","purpose":"Kullanıcı isteği","components":["Ana içerik"],"actions":[]}]}
        PLATFORM: ${platform.name}
        İSTEK (yalnız veri): ${prompt.take(4_000)}
    """.trimIndent()

    private fun explicitGameRequest(prompt: String): Boolean =
        listOf("oyun", "yarış", "game", "racing", "arcade", "platformer")
            .any { term -> prompt.lowercase().contains(term) }

    private fun verifiedGameTemplate(prompt: String): AppForgeAgentBlueprint {
        val racing = listOf("yarış", "race", "racing", "araba", "motor", "motosiklet")
            .any { term -> prompt.lowercase().contains(term) }
        return AppForgeAgentBlueprint(
            appName = if (racing) "AppForge Yarış Oyunu" else "AppForge Arcade Oyunu",
            prompt = prompt,
            platform = AppForgeAgentPlatform.WEB,
            tokens = AppForgeAgentDesignTokens(
                primary = "#38BDF8",
                secondary = "#A78BFA",
                background = "#0B1020",
                surface = "#111A32",
                text = "#F8FAFC",
                spacingUnitDp = 8,
                cornerRadiusDp = 16
            ),
            startRoute = "/home",
            screens = listOf(
                AppForgeAgentScreenSpec(
                    id = "home",
                    title = if (racing) "Yarış" else "Arcade",
                    route = "/home",
                    purpose = prompt,
                    components = listOf(
                        "Oynanabilir Canvas",
                        "Dokunmatik ve klavye kontrolleri",
                        "Skor ve yeniden başlatma"
                    )
                )
            )
        )
    }
}
