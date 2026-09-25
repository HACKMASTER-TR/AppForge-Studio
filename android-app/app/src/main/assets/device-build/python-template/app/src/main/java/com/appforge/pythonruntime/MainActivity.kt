package com.appforge.pythonruntime

import android.app.Activity
import android.os.Bundle
import android.os.Build
import android.graphics.Color
import android.view.Gravity
import android.view.WindowInsets
import android.widget.ScrollView
import android.widget.TextView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainActivity : Activity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        val text =
            TextView(
                this
            ).apply {
                setTextColor(
                    Color.rgb(
                        235,
                        238,
                        242
                    )
                )
                setBackgroundColor(
                    Color.rgb(
                        8,
                        7,
                        13
                    )
                )
                textSize =
                    16f
                gravity =
                    Gravity.START
                setPadding(
                    32,
                    32,
                    32,
                    32
                )
                this.text =
                    "Python başlatılıyor..."
            }

        val root =
            ScrollView(
                this
            ).apply {
                setBackgroundColor(
                    Color.rgb(
                        8,
                        7,
                        13
                    )
                )

                addView(
                    text
                )
            }

        /*
         * Android 15+ edge-to-edge keeps generated content behind
         * system bars unless the runtime applies safe-area insets.
         * Keep this template dependency-free: use platform WindowInsets.
         */
        root.setOnApplyWindowInsetsListener {
            view,
            insets ->

            val left: Int
            val top: Int
            val right: Int
            val bottom: Int

            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
            ) {
                val bars =
                    insets.getInsets(
                        WindowInsets.Type.systemBars()
                    )

                left =
                    bars.left

                top =
                    bars.top

                right =
                    bars.right

                bottom =
                    bars.bottom
            } else {
                @Suppress(
                    "DEPRECATION"
                )
                left =
                    insets.systemWindowInsetLeft

                @Suppress(
                    "DEPRECATION"
                )
                top =
                    insets.systemWindowInsetTop

                @Suppress(
                    "DEPRECATION"
                )
                right =
                    insets.systemWindowInsetRight

                @Suppress(
                    "DEPRECATION"
                )
                bottom =
                    insets.systemWindowInsetBottom
            }

            view.setPadding(
                left,
                top,
                right,
                bottom
            )

            insets
        }

        setContentView(
            root
        )

        root.post {
            root.requestApplyInsets()
        }

        Thread {
            val result =
                runCatching {
                    if (
                        !Python.isStarted()
                    ) {
                        Python.start(
                            AndroidPlatform(
                                this
                            )
                        )
                    }

                    Python
                        .getInstance()
                        .getModule(
                            "appforge_entry"
                        )
                        .callAttr(
                            "run"
                        )
                        .toString()
                }
                    .getOrElse {
                        "Python çalışma hatası:\n" +
                            (
                                it.message
                                    ?: it.javaClass.simpleName
                            )
                    }

            runOnUiThread {
                text.text =
                    result
            }
        }.start()
    }
}
