package com.appforge.studio.ai

import com.appforge.studio.model.ProjectDraft
import com.appforge.studio.model.SigningMode
import com.appforge.studio.model.SourceMode

enum class AdvisorSeverity {
    INFO,
    WARNING,
    ERROR
}

data class AdvisorIssue(
    val severity: AdvisorSeverity,
    val title: String,
    val detail: String,
    val autoFixable: Boolean = false
)

data class AdvisorReport(
    val score: Int,
    val readyForPlay: Boolean,
    val issues: List<AdvisorIssue>
)

data class AdvisorFixResult(
    val draft: ProjectDraft,
    val changes: List<String>
)

object AppForgeProjectAdvisor {

    private val packagePattern =
        Regex(
            "^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$"
        )

    fun analyze(
        draft: ProjectDraft
    ): AdvisorReport {
        val issues =
            mutableListOf<AdvisorIssue>()

        fun error(
            title: String,
            detail: String,
            autoFixable: Boolean = false
        ) {
            issues +=
                AdvisorIssue(
                    AdvisorSeverity.ERROR,
                    title,
                    detail,
                    autoFixable
                )
        }

        fun warning(
            title: String,
            detail: String,
            autoFixable: Boolean = false
        ) {
            issues +=
                AdvisorIssue(
                    AdvisorSeverity.WARNING,
                    title,
                    detail,
                    autoFixable
                )
        }

        fun info(
            title: String,
            detail: String
        ) {
            issues +=
                AdvisorIssue(
                    AdvisorSeverity.INFO,
                    title,
                    detail
                )
        }

        if (
            draft.appName
                .trim()
                .isBlank()
        ) {
            error(
                "Uygulama adı eksik",
                "Yayınlanabilir bir proje için uygulama adı belirlenmeli."
            )
        }

        val packageName =
            draft.packageName
                .trim()

        if (
            packageName.isBlank() ||
            !packagePattern.matches(
                packageName
            )
        ) {
            error(
                "Package name geçersiz",
                "com.sirket.uygulama biçiminde geçerli ve benzersiz bir Android package name kullan."
            )
        } else if (
            packageName ==
                "com.example.myapp"
        ) {
            error(
                "Varsayılan package name",
                "com.example.myapp yayın için kullanılmamalı. Benzersiz bir package name seç."
            )
        }

        when (
            draft.sourceMode
        ) {
            SourceMode.LOCAL -> {
                if (
                    draft.startPage
                        .isNullOrBlank() &&
                    draft.importedFolder
                        .isNullOrBlank()
                ) {
                    error(
                        "Yerel kaynak eksik",
                        "HTML/ZIP projesi için başlangıç dosyası veya içe aktarılmış proje klasörü seç."
                    )
                }
            }

            SourceMode.URL -> {
                val url =
                    draft.webUrl
                        .trim()

                if (
                    url.isBlank()
                ) {
                    error(
                        "Web URL eksik",
                        "URL kaynak modunda uygulamanın açacağı adresi gir."
                    )
                } else if (
                    !url.startsWith(
                        "https://",
                        ignoreCase = true
                    )
                ) {
                    warning(
                        "HTTPS önerilir",
                        "Uzak web içeriğinde HTTPS kullanmak güvenlik ve Play Store hazırlığı açısından daha uygundur."
                    )
                }
            }
        }

        if (
            draft.versionCode <=
                0
        ) {
            error(
                "versionCode geçersiz",
                "versionCode pozitif tam sayı olmalı.",
                autoFixable = true
            )
        }

        if (
            draft.versionName
                .trim()
                .isBlank()
        ) {
            error(
                "versionName eksik",
                "Kullanıcıya gösterilecek sürüm adı boş olamaz.",
                autoFixable = true
            )
        }

        if (
            draft.buildOutput
                .lowercase() !in
            setOf(
                "aab",
                "both"
            )
        ) {
            warning(
                "AAB çıktısı seçili değil",
                "Google Play için AAB gerekir. Production öncesinde AAB veya BOTH çıktısı seç."
            )
        }

        if (
            draft.signingMode ==
                SigningMode.DEBUG
        ) {
            error(
                "Release imza gerekli",
                "Google Play production için Debug signing yerine Release/Custom keystore kullan."
            )
        } else {
            if (
                draft.keystoreName
                    .isBlank()
            ) {
                error(
                    "Keystore eksik",
                    "Release imzalama için keystore seç."
                )
            }

            if (
                draft.keyAlias
                    .isBlank()
            ) {
                error(
                    "Key alias eksik",
                    "Release keystore alias bilgisini gir."
                )
            }

            if (
                draft.storePassword
                    .isBlank()
            ) {
                error(
                    "Store password eksik",
                    "Release keystore store password bilgisi gerekli."
                )
            }

            if (
                draft.keyPassword
                    .isBlank()
            ) {
                error(
                    "Key password eksik",
                    "Release keystore key password bilgisi gerekli."
                )
            }
        }

        if (
            draft.webMixedContentAllowed
        ) {
            warning(
                "Mixed Content açık",
                "HTTPS sayfada HTTP alt kaynaklara izin vermek güvenliği azaltır. Gerekmiyorsa kapat.",
                autoFixable = true
            )
        }

        if (
            draft.remoteBridgeAllowed &&
            !draft.javascriptBridge
        ) {
            warning(
                "Remote Bridge gereksiz açık",
                "Native Bridge kapalıyken Remote Bridge açık bırakılmamalı.",
                autoFixable = true
            )
        }

        if (
            draft.remoteBridgeAllowed &&
            draft.sourceMode ==
                SourceMode.URL &&
            !draft.webUrl
                .trim()
                .startsWith(
                    "https://",
                    ignoreCase = true
                )
        ) {
            error(
                "Remote Bridge güvenli değil",
                "Remote Native Bridge yalnız güvenilir HTTPS originlerinde açık olmalı.",
                autoFixable = true
            )
        }

        if (
            draft.qrScanner &&
            !draft.camera
        ) {
            warning(
                "QR için kamera kapalı",
                "QR/Barkod tarayıcı kamera erişimine ihtiyaç duyar.",
                autoFixable = true
            )
        }

        if (
            draft.deepLinkEnabled &&
            (
                draft.deepLinkScheme
                    .isBlank() ||
                draft.deepLinkHost
                    .isBlank()
            )
        ) {
            warning(
                "Deep Link eksik",
                "Deep Link açıksa scheme ve host alanlarını tamamla."
            )
        }

        if (
            draft.admobEnabled &&
            draft.admobAppId
                .isBlank()
        ) {
            error(
                "AdMob App ID eksik",
                "AdMob açıksa geçerli AdMob App ID gerekli."
            )
        }

        if (
            draft.billingEnabled &&
            draft.billingProductIds
                .isBlank() &&
            draft.billingSubscriptionIds
                .isBlank()
        ) {
            warning(
                "Billing ürünleri eksik",
                "Google Play Billing açıksa en az bir ürün veya abonelik kimliği tanımla."
            )
        }

        if (
            (
                draft.firebaseAnalyticsEnabled ||
                draft.firebaseCrashlyticsEnabled
            ) &&
            draft.firebaseConfigName
                .isBlank() &&
            draft.firebaseConfigUri
                .isNullOrBlank()
        ) {
            error(
                "Firebase yapılandırması eksik",
                "Firebase özelliği açıksa uygun google-services yapılandırmasını seç."
            )
        }

        if (
            draft.iconUri
                .isNullOrBlank()
        ) {
            info(
                "Özel ikon seçilmedi",
                "Yayın öncesinde uygulamaya özel kare PNG ikon kullanılması önerilir."
            )
        }

        if (
            !draft.autoVersionCode
        ) {
            info(
                "Otomatik versionCode kapalı",
                "Play güncellemelerinde versionCode'u elle artırmayı unutma."
            )
        }

        val penalty =
            issues.sumOf {
                when (
                    it.severity
                ) {
                    AdvisorSeverity.ERROR ->
                        16

                    AdvisorSeverity.WARNING ->
                        7

                    AdvisorSeverity.INFO ->
                        2
                }
            }

        val score =
            (
                100 -
                    penalty
            ).coerceIn(
                0,
                100
            )

        val ready =
            issues.none {
                it.severity ==
                    AdvisorSeverity.ERROR
            } &&
            draft.buildOutput
                .lowercase() in
                setOf(
                    "aab",
                    "both"
                ) &&
            draft.signingMode !=
                SigningMode.DEBUG

        return AdvisorReport(
            score =
                score,
            readyForPlay =
                ready,
            issues =
                issues
        )
    }

    fun projectAnalysisText(
        draft: ProjectDraft
    ): String =
        reportText(
            title =
                "🔍 PROJE ANALİZİ",
            draft =
                draft,
            includePlaySummary =
                false
        )

    fun playStoreText(
        draft: ProjectDraft
    ): String =
        reportText(
            title =
                "🚀 PLAY STORE HAZIRLIK DENETİMİ",
            draft =
                draft,
            includePlaySummary =
                true
        )

    private fun reportText(
        title: String,
        draft: ProjectDraft,
        includePlaySummary: Boolean
    ): String {
        val report =
            analyze(
                draft
            )

        return buildString {
            appendLine(
                title
            )
            appendLine()
            appendLine(
                "Hazırlık puanı: ${report.score}/100"
            )

            if (
                includePlaySummary
            ) {
                appendLine(
                    if (
                        report.readyForPlay
                    ) {
                        "Durum: ✅ Temel Play Store kontrolleri hazır."
                    } else {
                        "Durum: ⚠ Production öncesinde düzeltilmesi gereken noktalar var."
                    }
                )
            }

            if (
                report.issues
                    .isEmpty()
            ) {
                appendLine()
                appendLine(
                    "✅ Kontrol edilen proje ayarlarında sorun bulunamadı."
                )
                return@buildString
            }

            appendLine()

            report.issues
                .forEach {
                    issue ->
                    val icon =
                        when (
                            issue.severity
                        ) {
                            AdvisorSeverity.ERROR ->
                                "❌"

                            AdvisorSeverity.WARNING ->
                                "⚠"

                            AdvisorSeverity.INFO ->
                                "ℹ"
                        }

                    appendLine(
                        "$icon ${issue.title}"
                    )
                    appendLine(
                        "   ${issue.detail}"
                    )

                    if (
                        issue.autoFixable
                    ) {
                        appendLine(
                            "   ⚡ Güvenli Düzelt ile otomatik düzeltilebilir."
                        )
                    }
                }

            appendLine()
            append(
                "Not: Keystore parolaları ve API anahtarları AI raporuna eklenmez."
            )
        }
    }

    fun applySafeFixes(
        draft: ProjectDraft
    ): AdvisorFixResult {
        var updated =
            draft.copy()

        val changes =
            mutableListOf<String>()

        if (
            updated.versionCode <=
                0
        ) {
            updated =
                updated.copy(
                    versionCode = 1
                )

            changes +=
                "versionCode 1 yapıldı."
        }

        if (
            updated.versionName
                .trim()
                .isBlank()
        ) {
            updated =
                updated.copy(
                    versionName =
                        "1.0.0"
                )

            changes +=
                "versionName 1.0.0 yapıldı."
        }

        if (
            updated.webMixedContentAllowed
        ) {
            updated =
                updated.copy(
                    webMixedContentAllowed =
                        false
                )

            changes +=
                "Mixed Content kapatıldı."
        }

        if (
            updated.remoteBridgeAllowed &&
            (
                !updated.javascriptBridge ||
                (
                    updated.sourceMode ==
                        SourceMode.URL &&
                    !updated.webUrl
                        .trim()
                        .startsWith(
                            "https://",
                            ignoreCase =
                                true
                        )
                )
            )
        ) {
            updated =
                updated.copy(
                    remoteBridgeAllowed =
                        false
                )

            changes +=
                "Güvenli olmayan/gereksiz Remote Native Bridge kapatıldı."
        }

        if (
            updated.qrScanner &&
            !updated.camera
        ) {
            updated =
                updated.copy(
                    camera =
                        true
                )

            changes +=
                "QR/Barkod tarayıcı için kamera özelliği açıldı."
        }

        return AdvisorFixResult(
            draft =
                updated,
            changes =
                changes
        )
    }

    fun fixesText(
        result: AdvisorFixResult
    ): String =
        buildString {
            appendLine(
                "⚡ GÜVENLİ DÜZELT"
            )
            appendLine()

            if (
                result.changes
                    .isEmpty()
            ) {
                appendLine(
                    "Otomatik uygulanabilecek güvenli bir düzeltme bulunmadı."
                )
                appendLine(
                    "Release keystore, package name, URL ve ürün kimlikleri gibi kullanıcı kararı gerektiren alanlar otomatik değiştirilmedi."
                )
            } else {
                appendLine(
                    "${result.changes.size} güvenli düzeltme uygulandı:"
                )
                appendLine()

                result.changes
                    .forEach {
                        appendLine(
                            "✅ $it"
                        )
                    }

                appendLine()
                appendLine(
                    "Kullanıcı kararı gerektiren alanlara dokunulmadı."
                )
            }

            appendLine()
            append(
                "Yeni hazırlık puanı: ${analyze(result.draft).score}/100"
            )
        }
}
