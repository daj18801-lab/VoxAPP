package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.DirectThread
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

/**
 * Tracks whether the app UI is currently visible and which private chat thread (if any)
 * is open in the foreground so we never buzz the user for the exact chat they are reading.
 */
object VoxStageNotificationState {
    @Volatile
    var isAppInForeground: Boolean = false

    @Volatile
    var activeDmThreadId: String? = null
}

/**
 * Persists notification preferences and per-thread deduplication keys so background workers
 * and process restarts never spam duplicate notifications for messages already notified.
 */
object DirectMessageNotificationPrefs {
    private const val PREFS_NAME = "voxstage_dm_notifications"
    private const val KEY_ENABLED = "dm_notifications_enabled"
    private const val KEY_SEEDED_PREFIX = "dm_seeded_"
    private const val KEY_LAST_NOTIFIED_PREFIX = "dm_last_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDmNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setDmNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isBaselineSeeded(context: Context, uid: String): Boolean =
        prefs(context).getBoolean("${KEY_SEEDED_PREFIX}$uid", false)

    fun markBaselineSeeded(context: Context, uid: String) {
        prefs(context).edit().putBoolean("${KEY_SEEDED_PREFIX}$uid", true).apply()
    }

    fun getLastNotifiedKey(context: Context, uid: String, threadId: String): String =
        prefs(context).getString("${KEY_LAST_NOTIFIED_PREFIX}${uid}_$threadId", "").orEmpty()

    fun setLastNotifiedKey(context: Context, uid: String, threadId: String, key: String) {
        prefs(context).edit().putString("${KEY_LAST_NOTIFIED_PREFIX}${uid}_$threadId", key).apply()
    }

    fun buildMessageKey(thread: DirectThread): String {
        val ts = thread.updatedAt?.toDate()?.time ?: 0L
        return "${ts}|${thread.lastMessageSenderId}|${thread.lastMessageText.hashCode()}"
    }
}

object DirectMessageNotificationHelper {
    const val CHANNEL_ID = "voxstage_private_messages"
    const val EXTRA_DM_THREAD_ID = "extra_dm_thread_id"
    private const val GROUP_KEY_DM = "com.aistudio.voxstage.PRIVATE_MESSAGES"
    private const val NOTIFICATION_ID_BASE = 41_000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_dm_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_dm_desc)
                    enableVibration(true)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun notificationIdForThread(threadId: String): Int =
        NOTIFICATION_ID_BASE + (threadId.hashCode() and 0x0FFF)

    fun cancelThreadNotification(context: Context, threadId: String) {
        if (threadId.isBlank()) return
        try {
            NotificationManagerCompat.from(context).cancel(notificationIdForThread(threadId))
        } catch (_: SecurityException) {
            // Ignored if permission was revoked
        }
    }

    fun cancelAllNotifications(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancelAll()
        } catch (_: SecurityException) {
            // Ignored
        }
    }

    /**
     * Posts a heads-up Android notification for an incoming unread private message.
     * Automatically skips if the user is actively viewing that exact DM in the foreground,
     * if DM notifications are disabled, if permission is missing, or if already notified.
     */
    fun notifyPrivateMessageIfNew(
        context: Context,
        thread: DirectThread,
        currentUserId: String
    ) {
        if (thread.id.isBlank()) return
        if (thread.lastMessageSenderId.isBlank() || thread.lastMessageSenderId == currentUserId) return
        if (!thread.isUnreadFor(currentUserId)) return

        val msgKey = DirectMessageNotificationPrefs.buildMessageKey(thread)
        val lastNotified = DirectMessageNotificationPrefs.getLastNotifiedKey(
            context = context,
            uid = currentUserId,
            threadId = thread.id
        )
        if (msgKey == lastNotified) return

        // Always record the message key once observed; suppress alert only if reading this exact chat right now
        val readingThisChatNow = VoxStageNotificationState.isAppInForeground &&
            VoxStageNotificationState.activeDmThreadId == thread.id
        if (readingThisChatNow) {
            DirectMessageNotificationPrefs.setLastNotifiedKey(context, currentUserId, thread.id, msgKey)
            return
        }

        if (!DirectMessageNotificationPrefs.isDmNotificationsEnabled(context)) return
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val senderName = thread.otherName(currentUserId).ifBlank { "New Private Message" }
        val messagePreview = thread.lastMessageText.ifBlank { "Sent you a private message" }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DM_THREAD_ID, thread.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationIdForThread(thread.id),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(0xFF0088FF.toInt())
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(senderName)
                    .bigText(messagePreview)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_DM)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                notificationIdForThread(thread.id),
                notification
            )
            DirectMessageNotificationPrefs.setLastNotifiedKey(context, currentUserId, thread.id, msgKey)
        } catch (sec: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission when posting DM notification", sec)
        }
    }

    private const val TAG = "DMNotificationHelper"
}

/**
 * Application-scoped real-time monitor for incoming private messages.
 * Stays attached even when the user backgrounds the app so incoming private messages
 * trigger immediate phone notifications, paired with [DirectMessageSyncWorker] for
 * periodic background checks if the OS suspends the app process.
 */
class DirectMessageNotificationMonitor(
    private val appContext: Context
) {
    private var activeUserId: String? = null
    private var threadsRegistration: ListenerRegistration? = null
    private var initialSnapshotProcessed: Boolean = false

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val uid = firebaseAuth.currentUser?.uid
        if (uid != activeUserId) {
            rebindForUser(uid)
        }
    }

    fun start() {
        DirectMessageNotificationHelper.ensureChannel(appContext)
        try {
            Firebase.auth.addAuthStateListener(authStateListener)
            rebindForUser(Firebase.auth.currentUser?.uid)
        } catch (err: Exception) {
            Log.w(TAG, "Failed to start DirectMessageNotificationMonitor", err)
        }
    }

    private fun rebindForUser(uid: String?) {
        threadsRegistration?.remove()
        threadsRegistration = null
        activeUserId = uid
        initialSnapshotProcessed = false

        if (uid.isNullOrBlank()) {
            DirectMessageNotificationHelper.cancelAllNotifications(appContext)
            DirectMessageSyncWorker.cancel(appContext)
            return
        }

        // Schedule resilient periodic background sync in case OS freezes the app process
        DirectMessageSyncWorker.schedule(appContext)

        try {
            val databaseId = appContext.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            threadsRegistration = db.collection("directThreads")
                .whereArrayContains("participantIds", uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        if (error != null) {
                            Log.w(TAG, "Background DM notification listener error", error)
                        }
                        return@addSnapshotListener
                    }

                    val wasBaselineSeeded = DirectMessageNotificationPrefs.isBaselineSeeded(appContext, uid)

                    if (!initialSnapshotProcessed) {
                        initialSnapshotProcessed = true
                        val nowMs = System.currentTimeMillis()
                        snapshot.documents.forEach { doc ->
                            val thread = doc.toObject(DirectThread::class.java) ?: return@forEach
                            if (thread.lastMessageSenderId.isBlank() ||
                                thread.lastMessageSenderId == uid ||
                                !thread.isUnreadFor(uid)
                            ) {
                                return@forEach
                            }
                            val updatedMs = thread.updatedAt?.toDate()?.time ?: 0L
                            val isBrandNew = updatedMs > 0L && (nowMs - updatedMs) <= RECENT_ARRIVAL_WINDOW_MS
                            if (wasBaselineSeeded || isBrandNew) {
                                DirectMessageNotificationHelper.notifyPrivateMessageIfNew(
                                    context = appContext,
                                    thread = thread,
                                    currentUserId = uid
                                )
                            } else {
                                // First-time sign-in baseline: remember existing historical unread threads
                                // without flooding the user with old notifications.
                                DirectMessageNotificationPrefs.setLastNotifiedKey(
                                    context = appContext,
                                    uid = uid,
                                    threadId = thread.id,
                                    key = DirectMessageNotificationPrefs.buildMessageKey(thread)
                                )
                            }
                        }
                        if (!wasBaselineSeeded) {
                            DirectMessageNotificationPrefs.markBaselineSeeded(appContext, uid)
                        }
                        return@addSnapshotListener
                    }

                    for (change in snapshot.documentChanges) {
                        if (change.type == DocumentChange.Type.ADDED ||
                            change.type == DocumentChange.Type.MODIFIED
                        ) {
                            val thread = change.document.toObject(DirectThread::class.java)
                            DirectMessageNotificationHelper.notifyPrivateMessageIfNew(
                                context = appContext,
                                thread = thread,
                                currentUserId = uid
                            )
                        }
                    }
                }
        } catch (err: Exception) {
            Log.w(TAG, "Unable to attach DM notification listener", err)
        }
    }

    companion object {
        private const val TAG = "DMNotificationMonitor"
        private const val RECENT_ARRIVAL_WINDOW_MS = 45_000L
    }
}
