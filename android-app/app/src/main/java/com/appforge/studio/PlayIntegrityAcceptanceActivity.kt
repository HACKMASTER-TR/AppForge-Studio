package com.appforge.studio

import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.appforge.studio.security.StudioSecurityClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayIntegrityAcceptanceActivity : ComponentActivity() {

    private lateinit var runButton: Button
    private lateinit var reportView: TextView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        check(
            BuildConfig
                .PLAY_INTEGRITY_ACCEPTANCE_HARNESS
        ) {
            "Physical acceptance harness is disabled."
        }

        val scroll =
            ScrollView(
                this
            )

        val root =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                gravity =
                    Gravity.CENTER_HORIZONTAL

                setPadding(
                    48,
                    72,
                    48,
                    72
                )
            }

        val title =
            TextView(
                this
            ).apply {
                text =
                    "Play Integrity Fiziksel Kabul"

                textSize =
                    24f

                gravity =
                    Gravity.CENTER
            }

        val description =
            TextView(
                this
            ).apply {
                text =
                    "Bu build yalnız Google Play Internal App Sharing üzerinden fiziksel Play Integrity kabulü içindir."

                textSize =
                    16f

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    24,
                    0,
                    32
                )
            }

        runButton =
            Button(
                this
            ).apply {
                text =
                    "Fiziksel kabul testini çalıştır"

                setOnClickListener {
                    runAcceptance()
                }
            }

        reportView =
            TextView(
                this
            ).apply {
                text =
                    "PHYSICAL_ACCEPTANCE=READY"

                textSize =
                    14f

                setTextIsSelectable(
                    true
                )

                setPadding(
                    0,
                    36,
                    0,
                    0
                )
            }

        root.addView(
            title
        )

        root.addView(
            description
        )

        root.addView(
            runButton
        )

        root.addView(
            reportView
        )

        scroll.addView(
            root
        )

        setContentView(
            scroll
        )
    }

    private fun runAcceptance() {
        if (
            !BuildConfig
                .PLAY_INTEGRITY_ACCEPTANCE_HARNESS
        ) {
            reportView.text =
                "PHYSICAL_ACCEPTANCE=DISABLED"

            return
        }

        val base =
            BuildConfig
                .PLAY_INTEGRITY_ACCEPTANCE_BASE_URL
                .trim()
                .trimEnd('/')

        val allowed =
            Regex(
                "^https://appforge-integrity-staging\\.[A-Za-z0-9.-]+\\.workers\\.dev$"
            )

        if (
            !allowed.matches(
                base
            )
        ) {
            reportView.text =
                """
                PHYSICAL_ACCEPTANCE=FAIL
                STAGING_BASE_URL_GUARD=FAIL
                """.trimIndent()

            return
        }

        runButton.isEnabled =
            false

        reportView.text =
            """
            PHYSICAL_ACCEPTANCE=RUNNING
            STAGING_BASE_URL_GUARD=PASS
            CONFIG=PENDING
            STANDARD_INTEGRITY_TOKEN=PENDING
            GOOGLE_DECODE_INTEGRITY_TOKEN=PENDING
            SERVER_VERDICT_POLICY=PENDING
            INTEGRITY_SESSION=PENDING
            """.trimIndent()

        lifecycleScope.launch {
            try {
                val result =
                    withContext(
                        Dispatchers.IO
                    ) {
                        val client =
                            StudioSecurityClient(
                                context =
                                    this@PlayIntegrityAcceptanceActivity,
                                baseUrl =
                                    base,
                                accessToken =
                                    ""
                            )

                        val config =
                            client.config()

                        check(
                            config
                                .integrityEnabled
                        ) {
                            "integrity_not_enabled"
                        }

                        check(
                            config
                                .cloudProjectNumber >
                                0L
                        ) {
                            "cloud_project_number_missing"
                        }

                        val session =
                            client.attest(
                                userId =
                                    "physical-acceptance",
                                action =
                                    "physical_acceptance"
                            )

                        check(
                            session
                                .isNotBlank()
                        ) {
                            "integrity_session_missing"
                        }

                        """
                        PHYSICAL_ACCEPTANCE=PASS
                        STAGING_BASE_URL_GUARD=PASS
                        CONFIG=PASS
                        CLOUD_PROJECT_NUMBER=PASS
                        STANDARD_INTEGRITY_TOKEN=PASS
                        GOOGLE_DECODE_INTEGRITY_TOKEN=PASS
                        SERVER_VERDICT_POLICY=PASS
                        INTEGRITY_SESSION=PASS
                        TOKEN_VALUE=NOT_PRINTED
                        SESSION_VALUE=NOT_PRINTED
                        PLAY_PRODUCTION=UNTOUCHED
                        """.trimIndent()
                    }

                reportView.text =
                    result

            } catch (
                error: Throwable
            ) {
                val raw =
                    error
                        .message
                        .orEmpty()

                val knownCode =
                    listOf(
                        "integrity_policy_denied",
                        "invalid_integrity_verdict",
                        "play_integrity_unavailable",
                        "integrity_request_binding_mismatch",
                        "integrity_request_stale",
                        "integrity_session_unavailable",
                        "integrity_not_enabled",
                        "cloud_project_number_missing"
                    )
                        .firstOrNull {
                            raw.contains(
                                it
                            )
                        }

                if (
                    knownCode ==
                    "integrity_policy_denied"
                ) {
                    /*
                     * The Worker can only return integrity_policy_denied
                     * after Google decodeIntegrityToken has completed and
                     * the decoded evaluation reached the local policy.
                     *
                     * This is therefore a real Standard Integrity +
                     * server decode acceptance even if optional verdicts
                     * prevent issuance of the final AppForge session.
                     */
                    reportView.text =
                        """
                        PHYSICAL_ACCEPTANCE=DECODE_PASS_POLICY_DENIED
                        STAGING_BASE_URL_GUARD=PASS
                        CONFIG=PASS
                        STANDARD_INTEGRITY_TOKEN=PASS
                        GOOGLE_DECODE_INTEGRITY_TOKEN=PASS
                        SERVER_VERDICT_POLICY=DENIED
                        INTEGRITY_SESSION=NOT_ISSUED
                        TOKEN_VALUE=NOT_PRINTED
                        SESSION_VALUE=NOT_PRINTED
                        PLAY_PRODUCTION=UNTOUCHED
                        """.trimIndent()
                } else {
                    reportView.text =
                        """
                        PHYSICAL_ACCEPTANCE=FAIL
                        STAGING_BASE_URL_GUARD=PASS
                        ERROR=${knownCode ?: error.javaClass.simpleName}
                        TOKEN_VALUE=NOT_PRINTED
                        SESSION_VALUE=NOT_PRINTED
                        PLAY_PRODUCTION=UNTOUCHED
                        """.trimIndent()
                }

            } finally {
                runButton.isEnabled =
                    true
            }
        }
    }
}
