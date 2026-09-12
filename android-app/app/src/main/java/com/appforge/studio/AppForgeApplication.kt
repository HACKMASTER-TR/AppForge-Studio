package com.appforge.studio

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.atomic.AtomicBoolean

internal object UpdateGateSession {
    private val approved = AtomicBoolean(false)

    fun approve() {
        approved.set(true)
    }

    fun revoke() {
        approved.set(false)
    }

    fun isApproved(): Boolean = approved.get()
}

class AppForgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        System.setProperty(
            "http.agent",
            "AppForge-Studio-Android/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )

        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                private fun enforceGate(activity: Activity) {
                    if (activity !is MainActivity) return

                    val original = activity.intent
                    val notificationEntry =
                        original?.getBooleanExtra("appforge_open_builds", false) == true &&
                            original.getBooleanExtra("appforge_gate_checked", false) != true

                    if (UpdateGateSession.isApproved() && !notificationEntry) return

                    val gate = Intent(
                        activity,
                        UpdateGateActivity::class.java
                    ).apply {
                        action = original?.action
                        data = original?.data
                        original?.extras?.let(::putExtras)
                        original?.categories?.forEach(::addCategory)
                        addFlags(
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    }

                    activity.startActivity(gate)
                    activity.finish()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?
                ) {
                    enforceGate(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) {
                    enforceGate(activity)
                }
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
