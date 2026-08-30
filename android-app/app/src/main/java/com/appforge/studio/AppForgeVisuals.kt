package com.appforge.studio

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AppForgeMotionBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val transition =
        rememberInfiniteTransition(
            label = "appforge-motion"
        )

    val phase by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation =
                        tween(
                            durationMillis = 12_000,
                            easing = FastOutSlowInEasing
                        ),
                    repeatMode = RepeatMode.Reverse
                ),
            label = "appforge-phase"
        )

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Color(
                        0xFF060810
                    )
                )
    ) {
        Canvas(
            Modifier.fillMaxSize()
        ) {
            val w = size.width
            val h = size.height
            val a =
                phase *
                    (2f * PI.toFloat())

            val p1 =
                Offset(
                    x =
                        w *
                            (
                                0.18f +
                                    0.12f *
                                    cos(a)
                            ),
                    y =
                        h *
                            (
                                0.30f +
                                    0.08f *
                                    sin(a)
                            )
                )

            val p2 =
                Offset(
                    x =
                        w *
                            (
                                0.78f -
                                    0.12f *
                                    sin(a)
                            ),
                    y =
                        h *
                            (
                                0.62f +
                                    0.10f *
                                    cos(a)
                            )
                )

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(
                                    0x553400FF
                                ),
                                Color.Transparent
                            ),
                        center = p1,
                        radius =
                            maxOf(
                                w,
                                h
                            ) *
                                0.52f
                    ),
                radius =
                    maxOf(
                        w,
                        h
                    ) *
                        0.52f,
                center = p1
            )

            drawCircle(
                brush =
                    Brush.radialGradient(
                        colors =
                            listOf(
                                Color(
                                    0x5533E6D1
                                ),
                                Color.Transparent
                            ),
                        center = p2,
                        radius =
                            maxOf(
                                w,
                                h
                            ) *
                                0.46f
                    ),
                radius =
                    maxOf(
                        w,
                        h
                    ) *
                        0.46f,
                center = p2
            )

            val streakY =
                h *
                    (
                        0.18f +
                            0.04f *
                            sin(a)
                    )

            drawLine(
                brush =
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color(
                                0x553CA8FF
                            ),
                            Color(
                                0x55705CFF
                            ),
                            Color.Transparent
                        )
                    ),
                start =
                    Offset(
                        -w * 0.1f,
                        streakY
                    ),
                end =
                    Offset(
                        w * 1.1f,
                        streakY +
                            h *
                            0.05f
                    ),
                strokeWidth =
                    3.dp.toPx()
            )
        }

        content()
    }
}

private data class AppForgeOnboardingPage(
    val icon: String,
    val title: String,
    val description: String
)

@Composable
fun AppForgeOnboardingScreen(
    onDone: () -> Unit
) {
    val pages =
        remember {
            listOf(
                AppForgeOnboardingPage(
                    icon = "✎",
                    title =
                        "Projeyi Uygulamaya Dönüştürün",
                    description =
                        "Web ve kaynak projelerinizi analiz edin; uygun Android veya Windows çıktısını AppForge otomatik hazırlasın."
                ),
                AppForgeOnboardingPage(
                    icon = "🛠",
                    title =
                        "Her Şeyi Otomatik Yapılandırın",
                    description =
                        "İzinler, WebView ve Native Bridge özellikleri kaynak koddan otomatik algılansın. İsterseniz seçimleri değiştirebilirsiniz."
                ),
                AppForgeOnboardingPage(
                    icon = "↑",
                    title =
                        "Derleyin ve Yayınlayın",
                    description =
                        "APK, AAB veya desteklenen Windows çıktısını oluşturun ve yayınlama adımlarına geçin."
                )
            )
        }

    var page by
        remember {
            mutableIntStateOf(
                0
            )
        }

    val current =
        pages[
            page.coerceIn(
                pages.indices
            )
        ]

    AppForgeMotionBackground {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 28.dp,
                        vertical = 18.dp
                    ),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.End
            ) {
                TextButton(
                    onClick = onDone
                ) {
                    Text(
                        "Atla"
                    )
                }
            }

            Spacer(
                Modifier.weight(
                    1f
                )
            )

            Surface(
                shape =
                    RoundedCornerShape(
                        30.dp
                    ),
                color =
                    Color(
                        0xCC0C4160
                    ),
                modifier =
                    Modifier.size(
                        150.dp
                    )
            ) {
                Box(
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        current.icon,
                        fontSize =
                            54.sp,
                        fontWeight =
                            FontWeight.Bold
                    )
                }
            }

            Spacer(
                Modifier.height(
                    32.dp
                )
            )

            Text(
                current.title,
                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,
                fontWeight =
                    FontWeight.Bold,
                textAlign =
                    TextAlign.Center
            )

            Spacer(
                Modifier.height(
                    12.dp
                )
            )

            Text(
                current.description,
                color =
                    Color(
                        0xFFCAD0DA
                    ),
                fontSize =
                    15.sp,
                lineHeight =
                    22.sp,
                textAlign =
                    TextAlign.Center
            )

            Spacer(
                Modifier.weight(
                    1f
                )
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {
                pages.indices
                    .forEach {
                        index ->

                        Surface(
                            modifier =
                                Modifier.size(
                                    if (
                                        index ==
                                        page
                                    ) {
                                        10.dp
                                    } else {
                                        7.dp
                                    }
                                ),
                            shape =
                                CircleShape,
                            color =
                                if (
                                    index ==
                                    page
                                ) {
                                    Color(
                                        0xFF8CC9F6
                                    )
                                } else {
                                    Color(
                                        0xFF515967
                                    )
                                }
                        ) {}
                    }
            }

            Spacer(
                Modifier.height(
                    24.dp
                )
            )

            if (
                page <
                pages.lastIndex
            ) {
                OutlinedButton(
                    onClick = {
                        page += 1
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                52.dp
                            )
                ) {
                    Text(
                        "İleri  →"
                    )
                }
            } else {
                Button(
                    onClick =
                        onDone,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                52.dp
                            )
                ) {
                    Text(
                        "✓  Başlayın"
                    )
                }
            }

            Spacer(
                Modifier.height(
                    14.dp
                )
            )
        }
    }
}
