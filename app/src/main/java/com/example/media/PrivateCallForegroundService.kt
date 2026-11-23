package com.example.media

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.notifications.DirectMessageNotificationHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Play-Policy-compliant microphone Foreground Service that keeps an active Private Message
 * voice call running uninterrupted when the user minimizes the app or locks the screen.
 */
class PrivateCallForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        val threadId = intent?.getStringExtra(EXTRA_THREAD_ID).orEmpty()
        val peerName = intent?.getStringExtra(EXTRA_PEER_NAME).orEmpty().ifBlank { "Private Chat" }

        if (action == ACTION_END_CALL) {
            if (threadId.isNotBlank()) {
                _endCallEvents.tryEmit(threadId)
            }
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        ensureCallChannel(this)

        val openIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (threadId.isNotBlank()) {
                putExtra(DirectMessageNotificationHelper.EXTRA_DM_THREAD_ID, threadId)
            }
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val endCallIntent = Intent(this, PrivateCallForegroundService::class.java).apply {
            this.action = ACTION_END_CALL
            putExtra(EXTRA_THREAD_ID, threadId)
        }
        val endCallPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 1,
            endCallIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(0xFF0088FF.toInt())
            .setContentTitle("Private Voice Call · $peerName")
            .setContentText("Microphone active in background — tap to return")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                0,
                getString(R.string.action_end_call),
                endCallPendingIntent
            )
            .build()

        try {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
            isRunning = true
            activeThreadId = threadId
        } catch (err: Exception) {
            Log.w(TAG, "Unable to enter microphone foreground service", err)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        activeThreadId = null
        super.onDestroy()
    }

    private fun stopForegroundCompat() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
            // Ignored
        }
        isRunning = false
        activeThreadId = null
    }

    companion object {
        private const val TAG = "PrivateCallFgService"
        private const val CHANNEL_ID = "voxstage_private_call"
        private const val NOTIFICATION_ID = 49_201
        private const val ACTION_START_CALL = "com.example.media.ACTION_START_PRIVATE_CALL"
        private const val ACTION_END_CALL = "com.example.media.ACTION_END_PRIVATE_CALL"
        private const val EXTRA_THREAD_ID = "extra_call_thread_id"
        private const val EXTRA_PEER_NAME = "extra_call_peer_name"

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var activeThreadId: String? = null
            private set

        private val _endCallEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
        val endCallEvents: SharedFlow<String> = _endCallEvents.asSharedFlow()

        fun ensureCallChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.notif_channel_call_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = context.getString(R.string.notif_channel_call_desc)
                        setShowBadge(false)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }

        fun start(context: Context, threadId: String, peerName: String) {
            try {
                val appContext = context.applicationContext
                ensureCallChannel(appContext)
                val intent = Intent(appContext, PrivateCallForegroundService::class.java).apply {
                    action = ACTION_START_CALL
                    putExtra(EXTRA_THREAD_ID, threadId)
                    putExtra(EXTRA_PEER_NAME, peerName)
                }
                ContextCompat.startForegroundService(appContext, intent)
            } catch (err: Exception) {
                Log.w(TAG, "Failed to start PrivateCallForegroundService", err)
            }
        }

        fun stop(context: Context) {
            try {
                val appContext = context.applicationContext
                appContext.stopService(Intent(appContext, PrivateCallForegroundService::class.java))
            } catch (err: Exception) {
                Log.w(TAG, "Failed to stop PrivateCallForegroundService", err)
            }
            isRunning = false
            activeThreadId = null
        }
    }
}
