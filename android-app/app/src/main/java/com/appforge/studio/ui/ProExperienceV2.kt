package com.appforge.studio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.appforge.studio.R

private val ProBlack =
    Color(
        0xFF000000
    )

private val ProPanel =
    Color(
        0xFF0C0C0C
    )

private val ProYellow =
    Color(
        0xFFFFD400
    )

private val ProBlue =
    Color(
        0xFF43BFF4
    )

private val ProMuted =
    Color(
        0xFFA9A9A9
    )

private data class ProConfettiParticle(
    val xFraction: Float,
    val phaseOffset: Float,
    val speed: Float,
    val sizePx: Float,
    val color: Color
)

private val ProConfettiParticles =
    listOf(
        ProConfettiParticle(0.05f, 0.02f, 1.12f, 8f, ProYellow),
        ProConfettiParticle(0.12f, 0.46f, 0.92f, 11f, ProBlue),
        ProConfettiParticle(0.19f, 0.24f, 1.34f, 7f, Color(0xFFFF6B9E)),
        ProConfettiParticle(0.27f, 0.68f, 1.05f, 10f, Color(0xFF68E1A2)),
        ProConfettiParticle(0.34f, 0.12f, 0.84f, 6f, Color.White),
        ProConfettiParticle(0.41f, 0.57f, 1.27f, 12f, ProYellow),
        ProConfettiParticle(0.48f, 0.32f, 0.98f, 9f, ProBlue),
        ProConfettiParticle(0.55f, 0.79f, 1.41f, 7f, Color(0xFFFF8A65)),
        ProConfettiParticle(0.62f, 0.18f, 1.08f, 11f, Color(0xFFAB8BFF)),
        ProConfettiParticle(0.69f, 0.52f, 0.89f, 8f, Color.White),
        ProConfettiParticle(0.76f, 0.07f, 1.31f, 10f, Color(0xFF68E1A2)),
        ProConfettiParticle(0.83f, 0.63f, 1.16f, 6f, ProYellow),
        ProConfettiParticle(0.90f, 0.28f, 0.96f, 12f, Color(0xFFFF6B9E)),
        ProConfettiParticle(0.96f, 0.84f, 1.38f, 8f, ProBlue)
    )

@Composable
fun ProWelcomeScreenV1(
    onBack: () -> Unit,
    onStart: () -> Unit,
    onSupport: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    ProBlack
                )
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal =
                        20.dp,
                    vertical =
                        12.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick =
                    onBack
            ) {
                Text(
                    "←",
                    color =
                        Color.White,
                    fontSize =
                        28.sp
                )
            }

            Text(
                "AppForge PRO",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    20.sp
            )
        }

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        ProCelebrationHero()

        Spacer(
            Modifier.height(
                20.dp
            )
        )

        Text(
            "Tebrikler!",
            color =
                ProYellow,
            fontWeight =
                FontWeight.Black,
            fontSize =
                28.sp
        )

        Text(
            "PRO Dünyasına Hoşgeldin",
            modifier =
                Modifier.padding(
                    top =
                        8.dp
                ),
            color =
                Color.White,
            fontWeight =
                FontWeight.Black,
            fontSize =
                21.sp
        )

        Spacer(
            Modifier.height(
                28.dp
            )
        )

        Button(
            onClick =
                onStart,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        58.dp
                    ),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            ProYellow,
                        contentColor =
                            Color.Black
                    ),
            shape =
                RoundedCornerShape(
                    20.dp
                )
        ) {
            Text(
                "Hadi Başlayalım",
                fontWeight =
                    FontWeight.Black
            )
        }

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        Button(
            onClick =
                onSupport,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(
                        58.dp
                    ),
            colors =
                ButtonDefaults
                    .buttonColors(
                        containerColor =
                            ProBlack,
                        contentColor =
                            ProYellow
                    ),
            border =
                BorderStroke(
                    2.dp,
                    ProYellow
                ),
            shape =
                RoundedCornerShape(
                    20.dp
                )
        ) {
            Text(
                "☕  Geliştiriciye Destek Ol  ☕",
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            Modifier.height(
                32.dp
            )
        )
    }
}

/*
 * PRO_CELEBRATION_CONFETTI_V1_2
 *
 * Pure Compose visual layer only: it does not read or change entitlement,
 * billing, owner access, support products or navigation security.
 */
@Composable
private fun ProCelebrationHero() {
    val transition =
        rememberInfiniteTransition()

    val phase =
        transition.animateFloat(
            initialValue =
                0f,
            targetValue =
                1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis =
                                2_800,
                            easing =
                                LinearEasing
                        )
                )
        ).value

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(
                    250.dp
                )
    ) {
        Canvas(
            modifier =
                Modifier.fillMaxSize()
        ) {
            val travel =
                size.height +
                    56f

            ProConfettiParticles.forEach {
                particle ->

                val normalizedY =
                    (
                        phase *
                            particle.speed +
                            particle.phaseOffset
                    ) % 1f

                val y =
                    normalizedY *
                        travel -
                        28f

                val x =
                    size.width *
                        particle.xFraction

                drawRect(
                    color =
                        particle.color,
                    topLeft =
                        Offset(
                            x,
                            y
                        ),
                    size =
                        Size(
                            particle.sizePx,
                            particle.sizePx *
                                1.55f
                        )
                )
            }
        }

        Surface(
            modifier =
                Modifier
                    .size(
                        156.dp
                    )
                    .align(
                        Alignment.Center
                    ),
            color =
                Color(
                    0xFF0B1830
                ),
            shape =
                RoundedCornerShape(
                    36.dp
                ),
            border =
                BorderStroke(
                    1.dp,
                    ProYellow.copy(
                        alpha =
                            0.45f
                    )
                )
        ) {
            Image(
                painter =
                    painterResource(
                        id =
                            R.drawable
                                .ic_launcher_foreground
                    ),
                contentDescription =
                    "AppForge",
                modifier =
                    Modifier.padding(
                        18.dp
                    )
            )
        }
    }
}

@Composable
fun DeveloperSupportScreenV1(
    onBack: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    ProBlack
                )
                .statusBarsPadding()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    20.dp
                )
    ) {
        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            TextButton(
                onClick =
                    onBack
            ) {
                Text(
                    "←",
                    color =
                        Color.White,
                    fontSize =
                        28.sp
                )
            }

            Text(
                "Geliştiriciye Destek Ol",
                color =
                    Color.White,
                fontWeight =
                    FontWeight.Black,
                fontSize =
                    20.sp
            )
        }

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        Surface(
            modifier =
                Modifier
                    .size(
                        112.dp
                    )
                    .align(
                        Alignment.CenterHorizontally
                    ),
            color =
                ProYellow,
            shape =
                RoundedCornerShape(
                    56.dp
                )
        ) {
            Box(
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    "☕",
                    fontSize =
                        48.sp
                )
            }
        }

        Text(
            "Beni Desteklemek İstersen ☕",
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top =
                            20.dp,
                        bottom =
                            14.dp
                    ),
            color =
                Color.White,
            textAlign =
                TextAlign.Center,
            fontWeight =
                FontWeight.Black,
            fontSize =
                21.sp
        )

        Card(
            colors =
                CardDefaults
                    .cardColors(
                        containerColor =
                            ProPanel
                    ),
            shape =
                RoundedCornerShape(
                    22.dp
                )
        ) {
            Column(
                modifier =
                    Modifier.padding(
                        18.dp
                    ),
                verticalArrangement =
                    Arrangement.spacedBy(
                        12.dp
                    )
            ) {
                Text(
                    "💗  Geliştiriciden Bir Not 👋",
                    color =
                        ProBlue,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "AppForge büyümeye devam ediyor 🚀\n\n" +
                        "Her sürümde yeni özellikler, daha güçlü derleme " +
                        "sistemleri ve daha iyi bir geliştirme deneyimi " +
                        "üzerinde çalışıyorum. AppForge'u kullanman ve geri " +
                        "bildirimlerin zaten büyük destek.\n\n" +
                        "Projeye ayrıca katkıda bulunmak istersen bana bir " +
                        "kahve, yemek veya geliştirme desteği bırakabilirsin. " +
                        "☕🍕🚀\n\n" +
                        "Destekler isteğe bağlıdır ve uygulamadaki özellik " +
                        "veya erişim haklarını değiştirmez.",
                    color =
                        Color(
                            0xFFD3D3D3
                        ),
                    fontSize =
                        14.sp,
                    lineHeight =
                        21.sp
                )
            }
        }

        Spacer(
            Modifier.height(
                24.dp
            )
        )

        SupportCard(
            icon = "☕",
            title = "Bir Kahve Ismarla",
            subtitle = "Gece kodlamaları için sıcak bir filtre kahve ☕",
            price = "₺5,89",
            accent =
                Color(
                    0xFF27414A
                )
        )

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        SupportCard(
            icon = "🍕",
            title = "Bir Yemek Ismarla",
            subtitle = "Projeye odaklanmak için güzel bir akşam yemeği 🍕",
            price = "₺294,99",
            accent =
                ProBlue
        )

        Spacer(
            Modifier.height(
                14.dp
            )
        )

        SupportCard(
            icon = "🚀",
            title = "Süper Sponsor",
            subtitle = "AppForge'un geleceğine doğrudan katkı 🚀",
            price = "₺1.169,99",
            accent =
                ProYellow
        )

        Spacer(
            Modifier.height(
                28.dp
            )
        )
    }
}

@Composable
private fun SupportCard(
    icon: String,
    title: String,
    subtitle: String,
    price: String,
    accent: Color
) {
    Card(
        colors =
            CardDefaults
                .cardColors(
                    containerColor =
                        ProPanel
                ),
        border =
            BorderStroke(
                2.dp,
                accent
            ),
        shape =
            RoundedCornerShape(
                24.dp
            )
    ) {
        Column(
            modifier =
                Modifier.padding(
                    18.dp
                ),
            verticalArrangement =
                Arrangement.spacedBy(
                    14.dp
                )
        ) {
            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                Surface(
                    modifier =
                        Modifier.size(
                            58.dp
                        ),
                    color =
                        accent.copy(
                            alpha =
                                0.18f
                        ),
                    shape =
                        RoundedCornerShape(
                            17.dp
                        )
                ) {
                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {
                        Text(
                            icon,
                            fontSize =
                                27.sp
                        )
                    }
                }

                Spacer(
                    Modifier.width(
                        14.dp
                    )
                )

                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {
                    Text(
                        title,
                        color =
                            Color.White,
                        fontWeight =
                            FontWeight.Black
                    )

                    Text(
                        subtitle,
                        color =
                            ProMuted,
                        fontSize =
                            12.sp
                    )
                }
            }

            Button(
                enabled =
                    false,
                onClick =
                    {},
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(
                            54.dp
                        ),
                colors =
                    ButtonDefaults
                        .buttonColors(
                            disabledContainerColor =
                                accent.copy(
                                    alpha =
                                        0.85f
                                ),
                            disabledContentColor =
                                if (
                                    accent ==
                                        ProYellow
                                ) {
                                    Color.Black
                                } else {
                                    Color.White
                                }
                        )
            ) {
                Text(
                    "$price — Yakında",
                    fontWeight =
                        FontWeight.Black
                )
            }
        }
    }
}
