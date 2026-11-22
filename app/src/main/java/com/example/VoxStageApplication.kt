package com.example

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationMonitor
import com.example.notifications.VoxStageNotificationState

class VoxStageApplication : Application() {

    private var startedActivityCount = 0
    private lateinit var dmNotificationMonitor: DirectMessageNotificationMonitor

    override fun onCreate() {
        super.onCreate()
        DirectMessageNotificationHelper.ensureChannel(this)

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                startedActivityCount += 1
                VoxStageNotificationState.isAppInForeground = startedActivityCount > 0
            }

            override fun onActivityResumed(activity: Activity) {
                VoxStageNotificationState.isAppInForeground = true
            }

            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                VoxStageNotificationState.isAppInForeground = startedActivityCount > 0
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        dmNotificationMonitor = DirectMessageNotificationMonitor(this)
        dmNotificationMonitor.start()
    }
}
