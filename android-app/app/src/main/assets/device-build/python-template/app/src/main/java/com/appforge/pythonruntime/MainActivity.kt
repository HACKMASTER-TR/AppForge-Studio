package com.appforge.pythonruntime

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
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

        setContentView(
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
        )

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
