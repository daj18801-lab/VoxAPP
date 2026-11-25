package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
    private const val KEY_QUICK_REPLY_POPUP_ENABLED = "dm_quick_reply_popup_enabled"
    private const val KEY_FRIEND_ATTENTION_ALERTS_ENABLED = "dm_friend_attention_alerts_enabled"
    private const val KEY_READ_RECEIPTS_ENABLED = "dm_read_receipts_enabled"
    private const val KEY_VOLUME_DOWN_PTT_ENABLED = "ptt_volume_down_enabled"
    private const val KEY_LOCK_SCREEN_PTT_ENABLED = "ptt_lock_screen_enabled"
    private const val KEY_SEEDED_PREFIX = "dm_seeded_"
    private const val KEY_LAST_NOTIFIED_PREFIX = "dm_last_"
    private const val KEY_LAST_FRIEND_ALERT_PREFIX = "dm_last_alert_"
    private const val KEY_LAST_SENT_ALERT_PREFIX = "dm_last_sent_alert_"
    private const val KEY_DISMISSED_BROADCASTS_PREFIX = "dismissed_app_broadcasts_"

    const val FRIEND_ALERT_COOLDOWN_MS = 5 * 60 * 1000L

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isDmNotificationsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setDmNotificationsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isQuickReplyPopupEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_QUICK_REPLY_POPUP_ENABLED, true)

    fun setQuickReplyPopupEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_QUICK_REPLY_POPUP_ENABLED, enabled).apply()
    }

    fun isFriendAttentionAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FRIEND_ATTENTION_ALERTS_ENABLED, true)

    fun setFriendAttentionAlertsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FRIEND_ATTENTION_ALERTS_ENABLED, enabled).apply()
    }

    fun isReadReceiptsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_READ_RECEIPTS_ENABLED, true)

    fun setReadReceiptsEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_READ_RECEIPTS_ENABLED, enabled).apply()
    }

    fun isVolumeDownPttEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VOLUME_DOWN_PTT_ENABLED, true)

    fun setVolumeDownPttEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VOLUME_DOWN_PTT_ENABLED, enabled).apply()
    }

    fun isLockScreenPttEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_SCREEN_PTT_ENABLED, true)

    fun setLockScreenPttEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN_PTT_ENABLED, enabled).apply()
    }

    fun getLastFriendAlertMillis(context: Context, uid: String, threadId: String): Long =
        prefs(context).getLong("${KEY_LAST_FRIEND_ALERT_PREFIX}${uid}_$threadId", 0L)

    fun setLastFriendAlertMillis(context: Context, uid: String, threadId: String, alertMillis: Long) {
        prefs(context).edit().putLong("${KEY_LAST_FRIEND_ALERT_PREFIX}${uid}_$threadId", alertMillis).apply()
    }

    fun getLastAlertSentAtMillis(context: Context, uid: String): Long =
        prefs(context).getLong("${KEY_LAST_SENT_ALERT_PREFIX}$uid", 0L)

    fun recordAlertSentNow(context: Context, uid: String, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong("${KEY_LAST_SENT_ALERT_PREFIX}$uid", nowMs).apply()
    }

    fun remainingAlertCooldownSeconds(context: Context, uid: String, nowMs: Long = System.currentTimeMillis()): Int {
        val lastSent = getLastAlertSentAtMillis(context, uid)
        if (lastSent <= 0L) return 0
        val remainingMs = (lastSent + FRIEND_ALERT_COOLDOWN_MS) - nowMs
        return if (remainingMs <= 0L) 0 else ((remainingMs + 999L) / 1000L).toInt()
    }

    fun isBaselineSeeded(context: Context, uid: String): Boolean =
        prefs(context).getBoolean("${KEY_SEEDED_PREFIX}$uid", false)

    fun markBaselineSeeded(context: Context, uid: String) {
        prefs(context).edit().putBoolean("${KEY_SEEDED_PREFIX}$uid", true).apply()
    }

    fun getDismissedBroadcastIds(context: Context, uid: String): Set<String> {
        if (uid.isBlank()) return emptySet()
        return prefs(context)
            .getStringSet("${KEY_DISMISSED_BROADCASTS_PREFIX}$uid", emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun markBroadcastDismissed(context: Context, uid: String, broadcastId: String) {
        if (uid.isBlank() || broadcastId.isBlank()) return
        val updated = getDismissedBroadcastIds(context, uid) + broadcastId
        prefs(context).edit()
            .putStringSet("${KEY_DISMISSED_BROADCASTS_PREFIX}$uid", updated)
            .apply()
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
    const val CHANNEL_ID_STANDARD = "voxstage_private_messages_standard"
    const val CHANNEL_ID_FRIEND_ALERT = "voxstage_friend_attention_alerts"
    const val EXTRA_DM_THREAD_ID = "extra_dm_thread_id"
    const val EXTRA_QUICK_REPLY_THREAD_ID = "extra_quick_reply_thread_id"
    const val EXTRA_QUICK_REPLY_SENDER_NAME = "extra_quick_reply_sender_name"
    const val KEY_QUICK_REPLY_TEXT = "key_voxstage_quick_reply_text"
    private const val GROUP_KEY_DM = "com.aistudio.voxstage.PRIVATE_MESSAGES"
    private const val NOTIFICATION_ID_BASE = 41_000
    private const val FRIEND_ALERT_NOTIFICATION_ID_BASE = 46_000
    private const val QUICK_REPLY_REQ_BASE = 51_000
    private const val ALERT_FRESHNESS_WINDOW_MS = 3 * 60 * 1000L

    private val ALERT_VIBRATION_PATTERN = longArrayOf(0L, 420L, 180L, 420L, 180L, 620L)

    private val _inAppPopupEvents = MutableSharedFlow<DirectThread>(extraBufferCapacity = 8)
    val inAppPopupEvents: SharedFlow<DirectThread> = _inAppPopupEvents.asSharedFlow()

    private val _friendAlertEvents = MutableSharedFlow<DirectThread>(extraBufferCapacity = 8)
    val friendAlertEvents: SharedFlow<DirectThread> = _friendAlertEvents.asSharedFlow()

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val popupChannel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_dm_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_dm_desc)
                    enableVibration(true)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(popupChannel)
            }
            if (manager.getNotificationChannel(CHANNEL_ID_STANDARD) == null) {
                val standardChannel = NotificationChannel(
                    CHANNEL_ID_STANDARD,
                    context.getString(R.string.notif_channel_dm_standard_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = context.getString(R.string.notif_channel_dm_standard_desc)
                    enableVibration(false)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(standardChannel)
            }
            if (manager.getNotificationChannel(CHANNEL_ID_FRIEND_ALERT) == null) {
                val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val alertChannel = NotificationChannel(
                    CHANNEL_ID_FRIEND_ALERT,
                    context.getString(R.string.notif_channel_friend_alert_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_friend_alert_desc)
                    enableVibration(true)
                    vibrationPattern = ALERT_VIBRATION_PATTERN
                    setSound(ringtoneUri, audioAttrs)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(alertChannel)
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

    fun alertNotificationIdForThread(threadId: String): Int =
        FRIEND_ALERT_NOTIFICATION_ID_BASE + (threadId.hashCode() and 0x0FFF)

    fun cancelThreadNotification(context: Context, threadId: String) {
        if (threadId.isBlank()) return
        try {
            NotificationManagerCompat.from(context).cancel(notificationIdForThread(threadId))
            NotificationManagerCompat.from(context).cancel(alertNotificationIdForThread(threadId))
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
     * Triggers an unmistakable phone ring + vibration alert when an accepted friend sends an
     * Attention Alert in a private message. Respects the user's opt-in preference.
     */
    fun notifyFriendAttentionAlertIfNew(
        context: Context,
        thread: DirectThread,
        currentUserId: String
    ) {
        if (thread.id.isBlank() || thread.isGroup) return
        if (thread.lastAlertAtMillis <= 0L) return
        if (thread.lastAlertSenderId.isBlank() || thread.lastAlertSenderId == currentUserId) return
        if (thread.hasAnyBlock() || thread.isUserKicked(currentUserId)) return

        val lastSeenAlert = DirectMessageNotificationPrefs.getLastFriendAlertMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id
        )
        if (thread.lastAlertAtMillis <= lastSeenAlert) return

        // Record immediately so we never ring twice for the same alert
        DirectMessageNotificationPrefs.setLastFriendAlertMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id,
            alertMillis = thread.lastAlertAtMillis
        )

        if (!DirectMessageNotificationPrefs.isFriendAttentionAlertsEnabled(context)) return

        val ageMs = System.currentTimeMillis() - thread.lastAlertAtMillis
        if (ageMs > ALERT_FRESHNESS_WINDOW_MS) return

        // Vibrate phone directly (covers both foreground & background with crisp waveform)
        vibrateAttentionPattern(context)

        // Emit in-app pulsing dialog when the app is open
        if (VoxStageNotificationState.isAppInForeground) {
            playShortRingSound(context)
            _friendAlertEvents.tryEmit(thread)
        }

        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val senderName = thread.lastAlertSenderName.ifBlank {
            thread.otherName(currentUserId).ifBlank { "Your friend" }
        }
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DM_THREAD_ID, thread.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            alertNotificationIdForThread(thread.id),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_FRIEND_ALERT)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(0xFFFF6D00.toInt())
            .setContentTitle("$senderName wants your attention!")
            .setContentText("Tap to jump into your private chat with $senderName.")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(ringtoneUri)
            .setVibrate(ALERT_VIBRATION_PATTERN)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.action_open_chat), pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                alertNotificationIdForThread(thread.id),
                notification
            )
        } catch (sec: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission for friend alert", sec)
        }
    }

    private fun vibrateAttentionPattern(context: Context) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(ALERT_VIBRATION_PATTERN, -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(ALERT_VIBRATION_PATTERN, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(ALERT_VIBRATION_PATTERN, -1)
                }
            }
        }
    }

    private fun playShortRingSound(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context.applicationContext, uri)
            ringtone?.play()
        }
    }

    /**
     * Posts a private message notification or interactive heads-up quick-reply popup for an incoming
     * unread private message. Automatically skips if the user is reading that exact chat,
     * if either user blocked the thread, if notifications are disabled, or if already notified.
     */
    fun notifyPrivateMessageIfNew(
        context: Context,
        thread: DirectThread,
        currentUserId: String
    ) {
        if (thread.id.isBlank()) return
        if (thread.lastMessageSenderId.isBlank() || thread.lastMessageSenderId == currentUserId) return
        if (!thread.isUnreadFor(currentUserId)) return
        if (thread.hasAnyBlock() || thread.isUserKicked(currentUserId)) return

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

        val popupEnabled = DirectMessageNotificationPrefs.isQuickReplyPopupEnabled(context)

        // If the app is in foreground on another tab and the user enabled message popups,
        // pop open the in-app Quick Reply popup card right away.
        if (VoxStageNotificationState.isAppInForeground && popupEnabled) {
            DirectMessageNotificationPrefs.setLastNotifiedKey(context, currentUserId, thread.id, msgKey)
            _inAppPopupEvents.tryEmit(thread)
            return
        }

        if (!DirectMessageNotificationPrefs.isDmNotificationsEnabled(context) && !popupEnabled) return
        if (!hasNotificationPermission(context)) return

        ensureChannel(context)

        val senderName = if (thread.isGroup) {
            val who = thread.participantNamesById[thread.lastMessageSenderId].orEmpty()
            val groupTitle = thread.displayTitle(currentUserId)
            if (who.isNotBlank()) "$who · $groupTitle" else groupTitle
        } else {
            thread.otherName(currentUserId).ifBlank { "New Private Message" }
        }
        val messagePreview = thread.lastMessageText.ifBlank { "Sent you a private message" }
        val messageTimestamp = thread.updatedAt?.toDate()?.time ?: System.currentTimeMillis()

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

        val channelId = if (popupEnabled) CHANNEL_ID else CHANNEL_ID_STANDARD
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(0xFF0088FF.toInt())
            .setContentTitle(senderName)
            .setContentText(messagePreview)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY_DM)
            .setContentIntent(pendingIntent)

        if (popupEnabled) {
            // High-priority heads-up popup with full message details + inline Quick Reply
            val senderPerson = Person.Builder()
                .setName(senderName)
                .setKey(thread.lastMessageSenderId)
                .build()
            val messagingStyle = NotificationCompat.MessagingStyle(
                Person.Builder().setName("You").setKey(currentUserId).build()
            )
                .setConversationTitle(if (thread.isGroup) thread.displayTitle(currentUserId) else null)
                .addMessage(messagePreview, messageTimestamp, senderPerson)

            val remoteInput = RemoteInput.Builder(KEY_QUICK_REPLY_TEXT)
                .setLabel(context.getString(R.string.hint_quick_reply))
                .build()
            val replyIntent = Intent(context, DirectMessageQuickReplyReceiver::class.java).apply {
                putExtra(EXTRA_QUICK_REPLY_THREAD_ID, thread.id)
                putExtra(EXTRA_QUICK_REPLY_SENDER_NAME, senderName)
            }
            val replyFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                QUICK_REPLY_REQ_BASE + (thread.id.hashCode() and 0x0FFF),
                replyIntent,
                replyFlags
            )
            val quickReplyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_stat_message,
                context.getString(R.string.action_quick_reply),
                replyPendingIntent
            )
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

            builder
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .addAction(quickReplyAction)
                .addAction(0, context.getString(R.string.action_open_chat), pendingIntent)
        } else {
            builder
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .setBigContentTitle(senderName)
                        .bigText(messagePreview)
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        }

        try {
            NotificationManagerCompat.from(context).notify(
                notificationIdForThread(thread.id),
                builder.build()
            )
            DirectMessageNotificationPrefs.setLastNotifiedKey(context, currentUserId, thread.id, msgKey)
        } catch (sec: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission when posting DM notification", sec)
        }
    }

    private const val TAG = "DMNotificationHelper"
}

/**
 * Application-scoped real-time monitor for incoming private messages and friend attention alerts.
 * Stays attached even when the user backgrounds the app so incoming private messages and alerts
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
                            // Seed historical friend alert timestamps unless brand new (< 45s old)
                            if (thread.lastAlertAtMillis > 0L && thread.lastAlertSenderId != uid) {
                                val isFreshAlert = (nowMs - thread.lastAlertAtMillis) <= RECENT_ARRIVAL_WINDOW_MS
                                if (wasBaselineSeeded && isFreshAlert) {
                                    DirectMessageNotificationHelper.notifyFriendAttentionAlertIfNew(
                                        context = appContext,
                                        thread = thread,
                                        currentUserId = uid
                                    )
                                } else {
                                    DirectMessageNotificationPrefs.setLastFriendAlertMillis(
                                        context = appContext,
                                        uid = uid,
                                        threadId = thread.id,
                                        alertMillis = thread.lastAlertAtMillis
                                    )
                                }
                            }
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
                            DirectMessageNotificationHelper.notifyFriendAttentionAlertIfNew(
                                context = appContext,
                                thread = thread,
                                currentUserId = uid
                            )
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

