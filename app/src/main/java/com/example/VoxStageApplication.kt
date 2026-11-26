package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationMonitor
import com.example.notifications.VoxStageNotificationState
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class VoxStageApplication : Application() {

    private var startedActivityCount = 0
    private var dmNotificationMonitor: DirectMessageNotificationMonitor? = null
    private var isMonitorStarted = false

    override fun onCreate() {
        super.onCreate()

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.e("VoxStageApp", "Firebase init failed", e)
        }

        try {
            DirectMessageNotificationHelper.ensureChannel(this)
        } catch (e: Exception) {
            Log.e("VoxStageApp", "Notification channel setup failed", e)
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) {
                startedActivityCount += 1
                VoxStageNotificationState.isAppInForeground = startedActivityCount > 0
                startDmMonitorIfSignedIn()
            }

            override fun onActivityResumed(activity: Activity) {
                VoxStageNotificationState.isAppInForeground = true
                startDmMonitorIfSignedIn()
            }

            override fun onActivityPaused(activity: Activity) = Unit

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                VoxStageNotificationState.isAppInForeground = startedActivityCount > 0
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun startDmMonitorIfSignedIn() {
        if (isMonitorStarted) return
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (!uid.isNullOrBlank()) {
                val monitor = dmNotificationMonitor ?: DirectMessageNotificationMonitor(this).also {
                    dmNotificationMonitor = it
                }
                monitor.start()
                isMonitorStarted = true
            }
        } catch (e: Exception) {
            Log.e("VoxStageApp", "Deferred DM monitor start failed", e)
        }
    }
}
