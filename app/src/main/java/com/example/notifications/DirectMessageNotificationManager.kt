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
import android.os.VibrationAttributes
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

import com.example.media.LivePttSessionManager
import com.example.media.PttChirpSoundEngine

/**
 * Snapshot of the most recent incoming Private Message shown on the lock screen when enabled.
 */
data class LockScreenRecentDm(
    val threadId: String,
    val senderId: String,
    val senderName: String,
    val threadTitle: String,
    val messageText: String,
    val timestampMillis: Long,
    val isGroup: Boolean = false,
    val voxOfficial: Boolean = false
)

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
    private const val KEY_LOCK_SCREEN_RECENT_DM_ENABLED = "ptt_lock_screen_recent_dm_enabled"
    private const val KEY_RECENT_DM_THREAD_ID = "lock_recent_dm_thread_id"
    private const val KEY_RECENT_DM_SENDER_ID = "lock_recent_dm_sender_id"
    private const val KEY_RECENT_DM_SENDER_NAME = "lock_recent_dm_sender_name"
    private const val KEY_RECENT_DM_THREAD_TITLE = "lock_recent_dm_thread_title"
    private const val KEY_RECENT_DM_TEXT = "lock_recent_dm_text"
    private const val KEY_RECENT_DM_TIME = "lock_recent_dm_time"
    private const val KEY_RECENT_DM_IS_GROUP = "lock_recent_dm_is_group"
    private const val KEY_RECENT_DM_VOX_OFFICIAL = "lock_recent_dm_vox_official"
    private const val KEY_PTT_LOUDSPEAKER_ENABLED = "ptt_loudspeaker_enabled"
    private const val KEY_PTT_SILENT_MODE_ENABLED = "ptt_silent_mode_enabled"
    private const val KEY_PTT_BEEP_SILENCED = "ptt_beep_silenced"
    private const val KEY_FLOATING_PTT_ENABLED = "ptt_floating_overlay_enabled"
    private const val KEY_PTT_VOICE_TO_TEXT_ENABLED = "ptt_voice_to_text_enabled"
    private const val KEY_LOCK_SCREEN_DND_ENABLED = "ptt_lock_screen_dnd_enabled"
    private const val KEY_OVERLAY_FIRST_OPEN_PROMPTED = "overlay_first_open_prompted"
    private const val KEY_SEEDED_PREFIX = "dm_seeded_"
    private const val KEY_LAST_NOTIFIED_PREFIX = "dm_last_"
    private const val KEY_LAST_FRIEND_ALERT_PREFIX = "dm_last_alert_"
    private const val KEY_LAST_GROUP_ALERT_PREFIX = "dm_last_group_alert_"
    private const val KEY_LAST_PRIORITY_CHIRP_PREFIX = "dm_last_priority_chirp_"
    private const val KEY_LAST_PTT_BEEP_PREFIX = "dm_last_ptt_beep_"
    private const val KEY_SENT_PTT_BEEP_HISTORY_PREFIX = "dm_sent_ptt_beep_history_"
    private const val KEY_LAST_SENT_ALERT_PREFIX = "dm_last_sent_alert_"
    private const val KEY_DISMISSED_BROADCASTS_PREFIX = "dismissed_app_broadcasts_"
    private const val KEY_DISMISSED_NON_URGENT_ALERTS_PREFIX = "dismissed_non_urgent_alerts_"

    const val FRIEND_ALERT_COOLDOWN_MS = 5 * 60 * 1000L
    const val PTT_BEEP_MAX_PER_WINDOW = 5
    const val PTT_BEEP_WINDOW_MS = 3 * 60 * 1000L

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

    fun isLockScreenRecentDmEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_SCREEN_RECENT_DM_ENABLED, true)

    fun setLockScreenRecentDmEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN_RECENT_DM_ENABLED, enabled).apply()
    }

    fun isPttLoudspeakerEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PTT_LOUDSPEAKER_ENABLED, true)

    fun setPttLoudspeakerEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PTT_LOUDSPEAKER_ENABLED, enabled).apply()
    }

    fun isPttSilentModeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PTT_SILENT_MODE_ENABLED, false)

    fun setPttSilentModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PTT_SILENT_MODE_ENABLED, enabled).apply()
    }

    fun isPttBeepSilenced(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PTT_BEEP_SILENCED, false)

    fun setPttBeepSilenced(context: Context, silenced: Boolean) {
        prefs(context).edit().putBoolean(KEY_PTT_BEEP_SILENCED, silenced).apply()
    }

    fun isFloatingPttEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLOATING_PTT_ENABLED, true)

    fun setFloatingPttEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_FLOATING_PTT_ENABLED, enabled).apply()
    }

    fun isPttVoiceToTextEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PTT_VOICE_TO_TEXT_ENABLED, false)

    fun setPttVoiceToTextEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_PTT_VOICE_TO_TEXT_ENABLED, enabled).apply()
    }

    fun hasPromptedOverlayOnFirstOpen(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OVERLAY_FIRST_OPEN_PROMPTED, false)

    fun setPromptedOverlayOnFirstOpen(context: Context, prompted: Boolean) {
        prefs(context).edit().putBoolean(KEY_OVERLAY_FIRST_OPEN_PROMPTED, prompted).apply()
    }

    fun isLockScreenDndEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_SCREEN_DND_ENABLED, false)

    fun setLockScreenDndEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_SCREEN_DND_ENABLED, enabled).apply()
    }

    fun saveLastRecentDm(context: Context, dm: LockScreenRecentDm) {
        prefs(context).edit()
            .putString(KEY_RECENT_DM_THREAD_ID, dm.threadId)
            .putString(KEY_RECENT_DM_SENDER_ID, dm.senderId)
            .putString(KEY_RECENT_DM_SENDER_NAME, dm.senderName)
            .putString(KEY_RECENT_DM_THREAD_TITLE, dm.threadTitle)
            .putString(KEY_RECENT_DM_TEXT, dm.messageText)
            .putLong(KEY_RECENT_DM_TIME, dm.timestampMillis)
            .putBoolean(KEY_RECENT_DM_IS_GROUP, dm.isGroup)
            .putBoolean(KEY_RECENT_DM_VOX_OFFICIAL, dm.voxOfficial)
            .apply()
    }

    fun loadLastRecentDm(context: Context): LockScreenRecentDm? {
        val p = prefs(context)
        val threadId = p.getString(KEY_RECENT_DM_THREAD_ID, "").orEmpty()
        val messageText = p.getString(KEY_RECENT_DM_TEXT, "").orEmpty()
        if (threadId.isBlank() || messageText.isBlank()) return null
        return LockScreenRecentDm(
            threadId = threadId,
            senderId = p.getString(KEY_RECENT_DM_SENDER_ID, "").orEmpty(),
            senderName = p.getString(KEY_RECENT_DM_SENDER_NAME, "Private Message").orEmpty(),
            threadTitle = p.getString(KEY_RECENT_DM_THREAD_TITLE, "").orEmpty(),
            messageText = messageText,
            timestampMillis = p.getLong(KEY_RECENT_DM_TIME, 0L),
            isGroup = p.getBoolean(KEY_RECENT_DM_IS_GROUP, false),
            voxOfficial = p.getBoolean(KEY_RECENT_DM_VOX_OFFICIAL, false)
        )
    }

    fun clearLastRecentDm(context: Context) {
        prefs(context).edit()
            .remove(KEY_RECENT_DM_THREAD_ID)
            .remove(KEY_RECENT_DM_SENDER_ID)
            .remove(KEY_RECENT_DM_SENDER_NAME)
            .remove(KEY_RECENT_DM_THREAD_TITLE)
            .remove(KEY_RECENT_DM_TEXT)
            .remove(KEY_RECENT_DM_TIME)
            .remove(KEY_RECENT_DM_IS_GROUP)
            .remove(KEY_RECENT_DM_VOX_OFFICIAL)
            .apply()
    }

    fun getLastFriendAlertMillis(context: Context, uid: String, threadId: String): Long =
        prefs(context).getLong("${KEY_LAST_FRIEND_ALERT_PREFIX}${uid}_$threadId", 0L)

    fun setLastFriendAlertMillis(context: Context, uid: String, threadId: String, alertMillis: Long) {
        prefs(context).edit().putLong("${KEY_LAST_FRIEND_ALERT_PREFIX}${uid}_$threadId", alertMillis).apply()
    }

    fun getLastGroupAlertMillis(context: Context, uid: String, threadId: String): Long =
        prefs(context).getLong("${KEY_LAST_GROUP_ALERT_PREFIX}${uid}_$threadId", 0L)

    fun setLastGroupAlertMillis(context: Context, uid: String, threadId: String, alertMillis: Long) {
        prefs(context).edit().putLong("${KEY_LAST_GROUP_ALERT_PREFIX}${uid}_$threadId", alertMillis).apply()
    }

    fun getLastPriorityChirpMillis(context: Context, uid: String, threadId: String): Long =
        prefs(context).getLong("${KEY_LAST_PRIORITY_CHIRP_PREFIX}${uid}_$threadId", 0L)

    fun setLastPriorityChirpMillis(context: Context, uid: String, threadId: String, chirpMillis: Long) {
        prefs(context).edit().putLong("${KEY_LAST_PRIORITY_CHIRP_PREFIX}${uid}_$threadId", chirpMillis).apply()
    }

    fun getLastPttBeepMillis(context: Context, uid: String, threadId: String): Long =
        prefs(context).getLong("${KEY_LAST_PTT_BEEP_PREFIX}${uid}_$threadId", 0L)

    fun setLastPttBeepMillis(context: Context, uid: String, threadId: String, beepMillis: Long) {
        prefs(context).edit().putLong("${KEY_LAST_PTT_BEEP_PREFIX}${uid}_$threadId", beepMillis).apply()
    }

    fun getRecentSentPttBeepTimestamps(
        context: Context,
        uid: String,
        threadId: String,
        nowMs: Long = System.currentTimeMillis()
    ): List<Long> {
        val raw = prefs(context)
            .getString("${KEY_SENT_PTT_BEEP_HISTORY_PREFIX}${uid}_$threadId", "")
            .orEmpty()
        if (raw.isBlank()) return emptyList()
        val cutoff = nowMs - PTT_BEEP_WINDOW_MS
        return raw.split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > cutoff && it <= nowMs + 60_000L }
            .sorted()
    }

    fun remainingPttBeepsInWindow(
        context: Context,
        uid: String,
        threadId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val recent = getRecentSentPttBeepTimestamps(context, uid, threadId, nowMs)
        return (PTT_BEEP_MAX_PER_WINDOW - recent.size).coerceAtLeast(0)
    }

    fun pttBeepCooldownRemainingSeconds(
        context: Context,
        uid: String,
        threadId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val recent = getRecentSentPttBeepTimestamps(context, uid, threadId, nowMs)
        if (recent.size < PTT_BEEP_MAX_PER_WINDOW) return 0
        val oldestActive = recent[recent.size - PTT_BEEP_MAX_PER_WINDOW]
        val remainingMs = (oldestActive + PTT_BEEP_WINDOW_MS) - nowMs
        return if (remainingMs <= 0L) 0 else ((remainingMs + 999L) / 1000L).toInt()
    }

    fun recordSentPttBeep(
        context: Context,
        uid: String,
        threadId: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        val recent = getRecentSentPttBeepTimestamps(context, uid, threadId, nowMs)
        val updated = (recent + nowMs).takeLast(PTT_BEEP_MAX_PER_WINDOW)
        prefs(context).edit()
            .putString("${KEY_SENT_PTT_BEEP_HISTORY_PREFIX}${uid}_$threadId", updated.joinToString(","))
            .apply()
        return (PTT_BEEP_MAX_PER_WINDOW - updated.size).coerceAtLeast(0)
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

    fun getDismissedNonUrgentAlertKeys(context: Context, uid: String): Set<String> {
        if (uid.isBlank()) return emptySet()
        return prefs(context)
            .getStringSet("${KEY_DISMISSED_NON_URGENT_ALERTS_PREFIX}$uid", emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun markNonUrgentAlertDismissed(context: Context, uid: String, alertKey: String) {
        if (uid.isBlank() || alertKey.isBlank()) return
        val updated = getDismissedNonUrgentAlertKeys(context, uid) + alertKey
        prefs(context).edit()
            .putStringSet("${KEY_DISMISSED_NON_URGENT_ALERTS_PREFIX}$uid", updated)
            .apply()
    }

    fun getLastNotifiedKey(context: Context, uid: String, threadId: String): String =
        prefs(context).getString("${KEY_LAST_NOTIFIED_PREFIX}${uid}_$threadId", "").orEmpty()

    fun setLastNotifiedKey(context: Context, uid: String, threadId: String, key: String) {
        prefs(context).edit().putString("${KEY_LAST_NOTIFIED_PREFIX}${uid}_$threadId", key).apply()
    }

    fun buildMessageKey(thread: DirectThread): String {
        // Use sender + last message content + sender's latest message day instead of thread.updatedAt
        // so unrelated metadata updates (PTT toggles, read receipts, pins, reactions) never re-trigger
        // a popup alert for an unread message the user already saw.
        val senderDay = thread.lastMessageDayByUserId[thread.lastMessageSenderId].orEmpty()
        return "${thread.lastMessageSenderId}|${thread.lastMessageText.hashCode()}|$senderDay"
    }
}

object DirectMessageNotificationHelper {
    const val CHANNEL_ID = "voxstage_private_messages"
    const val CHANNEL_ID_STANDARD = "voxstage_private_messages_standard"
    const val CHANNEL_ID_FRIEND_ALERT = "voxstage_friend_attention_alerts"
    const val CHANNEL_ID_GROUP_URGENT_ALERT = "voxstage_group_urgent_alerts"
    const val EXTRA_DM_THREAD_ID = "extra_dm_thread_id"
    const val EXTRA_QUICK_REPLY_THREAD_ID = "extra_quick_reply_thread_id"
    const val EXTRA_QUICK_REPLY_SENDER_NAME = "extra_quick_reply_sender_name"
    const val KEY_QUICK_REPLY_TEXT = "key_voxstage_quick_reply_text"
    private const val GROUP_KEY_DM = "com.aistudio.voxstage.PRIVATE_MESSAGES"
    private const val NOTIFICATION_ID_BASE = 41_000
    private const val FRIEND_ALERT_NOTIFICATION_ID_BASE = 46_000
    private const val GROUP_ALERT_NOTIFICATION_ID_BASE = 48_000
    private const val QUICK_REPLY_REQ_BASE = 51_000
    private const val ALERT_FRESHNESS_WINDOW_MS = 3 * 60 * 1000L
    private const val GROUP_ALERT_FRESHNESS_WINDOW_MS = 20 * 60 * 1000L

    private val ALERT_VIBRATION_PATTERN = longArrayOf(0L, 420L, 180L, 420L, 180L, 620L)
    private val BEEP_VIBRATION_PATTERN = longArrayOf(0L, 240L, 110L, 300L)

    private val _inAppPopupEvents = MutableSharedFlow<DirectThread>(extraBufferCapacity = 8)
    val inAppPopupEvents: SharedFlow<DirectThread> = _inAppPopupEvents.asSharedFlow()

    private val _friendAlertEvents = MutableSharedFlow<DirectThread>(extraBufferCapacity = 8)
    val friendAlertEvents: SharedFlow<DirectThread> = _friendAlertEvents.asSharedFlow()

    private val _groupUrgentAlertEvents = MutableSharedFlow<DirectThread>(extraBufferCapacity = 8)
    val groupUrgentAlertEvents: SharedFlow<DirectThread> = _groupUrgentAlertEvents.asSharedFlow()

    private val _recentDmForLockScreen = MutableStateFlow<LockScreenRecentDm?>(null)
    val recentDmForLockScreen: StateFlow<LockScreenRecentDm?> = _recentDmForLockScreen.asStateFlow()

    fun ensureRecentDmLoaded(context: Context) {
        if (_recentDmForLockScreen.value == null) {
            _recentDmForLockScreen.value = DirectMessageNotificationPrefs.loadLastRecentDm(context)
        }
    }

    fun updateRecentDmFromThread(
        context: Context,
        thread: DirectThread,
        currentUserId: String
    ) {
        if (thread.id.isBlank()) return
        if (thread.lastMessageSenderId.isBlank() || thread.lastMessageSenderId == currentUserId) return
        if (thread.hasAnyBlock() || thread.isUserKicked(currentUserId)) return
        val messageText = thread.lastMessageText.trim()
        if (messageText.isBlank()) return

        val ts = thread.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
        val current = _recentDmForLockScreen.value ?: DirectMessageNotificationPrefs.loadLastRecentDm(context)
        if (current != null && ts < current.timestampMillis && current.threadId != thread.id) {
            return
        }

        val senderName = when {
            thread.voxOfficial -> "VOX"
            thread.isGroup -> thread.participantNamesById[thread.lastMessageSenderId].orEmpty()
                .ifBlank { thread.displayTitle(currentUserId) }
            else -> thread.otherName(currentUserId).ifBlank { "Private Message" }
        }
        val dm = LockScreenRecentDm(
            threadId = thread.id,
            senderId = thread.lastMessageSenderId,
            senderName = senderName,
            threadTitle = if (thread.voxOfficial) "VOX" else if (thread.isGroup) thread.displayTitle(currentUserId) else senderName,
            messageText = messageText.take(300),
            timestampMillis = ts,
            isGroup = thread.isGroup,
            voxOfficial = thread.voxOfficial
        )
        _recentDmForLockScreen.value = dm
        DirectMessageNotificationPrefs.saveLastRecentDm(context, dm)
    }

    fun clearRecentDm(context: Context) {
        _recentDmForLockScreen.value = null
        DirectMessageNotificationPrefs.clearLastRecentDm(context)
    }

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
            if (manager.getNotificationChannel(CHANNEL_ID_GROUP_URGENT_ALERT) == null) {
                val urgentUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val urgentAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val groupAlertChannel = NotificationChannel(
                    CHANNEL_ID_GROUP_URGENT_ALERT,
                    context.getString(R.string.notif_channel_group_urgent_alert_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_group_urgent_alert_desc)
                    enableVibration(true)
                    vibrationPattern = ALERT_VIBRATION_PATTERN
                    setSound(urgentUri, urgentAttrs)
                    setShowBadge(true)
                }
                manager.createNotificationChannel(groupAlertChannel)
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

    fun groupAlertNotificationIdForThread(threadId: String): Int =
        GROUP_ALERT_NOTIFICATION_ID_BASE + (threadId.hashCode() and 0x0FFF)

    fun cancelThreadNotification(context: Context, threadId: String) {
        if (threadId.isBlank()) return
        try {
            NotificationManagerCompat.from(context).cancel(notificationIdForThread(threadId))
            NotificationManagerCompat.from(context).cancel(alertNotificationIdForThread(threadId))
            NotificationManagerCompat.from(context).cancel(groupAlertNotificationIdForThread(threadId))
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

    /**
     * Triggers an urgent high-priority phone notification (with sound + vibration + heads-up card)
     * and foreground Urgent Alert dialog whenever a Group DM Admin sends an Urgent Group Alert.
     */
    fun notifyGroupUrgentAlertIfNew(
        context: Context,
        thread: DirectThread,
        currentUserId: String
    ) {
        if (thread.id.isBlank() || !thread.isGroup) return
        if (thread.lastGroupAlertAtMillis <= 0L) return
        if (thread.lastGroupAlertSenderId.isBlank() || thread.lastGroupAlertSenderId == currentUserId) return
        if (thread.isBannedInGroup(currentUserId) || thread.isBouncedInGroup(currentUserId) || thread.isUserKicked(currentUserId)) return

        val lastSeenAlert = DirectMessageNotificationPrefs.getLastGroupAlertMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id
        )
        if (thread.lastGroupAlertAtMillis <= lastSeenAlert) return

        // Record immediately so we never fire twice for the same urgent group alert
        DirectMessageNotificationPrefs.setLastGroupAlertMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id,
            alertMillis = thread.lastGroupAlertAtMillis
        )
        // Also mark the matching lastMessageText key so standard DM popup doesn't double-notify
        if (thread.lastMessageSenderId == thread.lastGroupAlertSenderId) {
            DirectMessageNotificationPrefs.setLastNotifiedKey(
                context = context,
                uid = currentUserId,
                threadId = thread.id,
                key = DirectMessageNotificationPrefs.buildMessageKey(thread)
            )
        }

        val ageMs = System.currentTimeMillis() - thread.lastGroupAlertAtMillis
        if (ageMs > GROUP_ALERT_FRESHNESS_WINDOW_MS) return

        // Always vibrate the phone with the urgent waveform
        vibrateAttentionPattern(context)

        // Emit in-app urgent alert dialog & play alert chime when app is open
        if (VoxStageNotificationState.isAppInForeground) {
            playShortRingSound(context)
            _groupUrgentAlertEvents.tryEmit(thread)
        }

        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val groupTitle = thread.displayTitle(currentUserId).ifBlank { "Group DM" }
        val senderName = thread.lastGroupAlertSenderName.ifBlank { "Group Admin" }
        val alertTitle = thread.lastGroupAlertTitle.ifBlank { "Urgent Group Alert" }
        val alertBody = thread.lastGroupAlertMessage.ifBlank { "Tap to open $groupTitle immediately." }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DM_THREAD_ID, thread.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            groupAlertNotificationIdForThread(thread.id),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val urgentSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val contentTitle = "🚨 $alertTitle · $groupTitle"
        val fullMessage = "$senderName: $alertBody"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_GROUP_URGENT_ALERT)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(0xFFFF3B5C.toInt())
            .setContentTitle(contentTitle)
            .setContentText(fullMessage)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(contentTitle)
                    .bigText(fullMessage)
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(urgentSoundUri)
            .setVibrate(ALERT_VIBRATION_PATTERN)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.action_open_chat), pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(
                groupAlertNotificationIdForThread(thread.id),
                notification
            )
        } catch (sec: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission for urgent group alert", sec)
        }
    }

    /**
     * Plays the distinct two-tone Priority Break-In Chirp radio alert and posts a high-priority
     * heads-up notification whenever an opted-in close friend sends a 1-2 point Priority Chirp.
     */
    fun notifyPriorityBreakInChirpIfNew(
        context: Context,
        thread: DirectThread,
        currentUserId: String
    ) {
        if (thread.id.isBlank() || thread.isGroup) return
        if (!thread.hasActivePriorityChirpFor(currentUserId)) return
        if (thread.hasAnyBlock() || thread.isUserKicked(currentUserId)) return

        val lastSeenChirp = DirectMessageNotificationPrefs.getLastPriorityChirpMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id
        )
        if (thread.lastPriorityChirpAtMillis <= lastSeenChirp) return

        DirectMessageNotificationPrefs.setLastPriorityChirpMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id,
            chirpMillis = thread.lastPriorityChirpAtMillis
        )
        if (thread.lastMessageSenderId == thread.lastPriorityChirpSenderId) {
            DirectMessageNotificationPrefs.setLastNotifiedKey(
                context = context,
                uid = currentUserId,
                threadId = thread.id,
                key = DirectMessageNotificationPrefs.buildMessageKey(thread)
            )
        }

        val ageMs = System.currentTimeMillis() - thread.lastPriorityChirpAtMillis
        if (ageMs > ALERT_FRESHNESS_WINDOW_MS) return

        val isUrgent = thread.lastPriorityChirpPoints >= 2
        vibrateAttentionPattern(context)
        // MainActivity handles foreground audio playback; play here when app is backgrounded/locked
        if (!VoxStageNotificationState.isAppInForeground) {
            com.example.media.PttChirpSoundEngine.playPriorityBreakInChirp(urgent = isUrgent)
        }

        if (!hasNotificationPermission(context)) return
        ensureChannel(context)

        val senderName = thread.lastPriorityChirpSenderName.ifBlank {
            thread.otherName(currentUserId).ifBlank { "Close Friend" }
        }
        val body = thread.lastPriorityChirpMessage.ifBlank { "Tap to jump in!" }
        val title = if (isUrgent) {
            "🚨 Urgent Break-In Chirp · $senderName"
        } else {
            "📡 Priority Chirp · $senderName"
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DM_THREAD_ID, thread.id)
        }
        val notifId = 8400 + (thread.id.hashCode() and 0x0FFF)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_FRIEND_ALERT)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setColor(if (isUrgent) 0xFFFF3B5C.toInt() else 0xFFFF9800.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(title)
                    .bigText(body)
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(ALERT_VIBRATION_PATTERN)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(0, context.getString(R.string.action_open_chat), pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (sec: SecurityException) {
            Log.w(TAG, "Missing POST_NOTIFICATIONS permission for priority chirp", sec)
        }
    }

    /**
     * Plays an incoming PTT Beep tone sent by another participant via the lock-screen Beep button.
     * Beeps ALWAYS come through even when PTT Silent Mode is enabled, unless the user explicitly
     * turned on the "Silence Beep" option.
     */
    fun notifyPttBeepIfNew(
        context: Context,
        thread: DirectThread,
        currentUserId: String
    ) {
        if (thread.id.isBlank() || thread.voxOfficial) return
        if (thread.lastBeepAtMillis <= 0L) return
        if (thread.lastBeepSenderId.isBlank() || thread.lastBeepSenderId == currentUserId) return
        if (thread.hasAnyBlock() || thread.isUserKicked(currentUserId)) return

        val lastSeenBeep = DirectMessageNotificationPrefs.getLastPttBeepMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id
        )
        if (thread.lastBeepAtMillis <= lastSeenBeep) return

        DirectMessageNotificationPrefs.setLastPttBeepMillis(
            context = context,
            uid = currentUserId,
            threadId = thread.id,
            beepMillis = thread.lastBeepAtMillis
        )

        val ageMs = System.currentTimeMillis() - thread.lastBeepAtMillis
        if (ageMs > BEEP_FRESHNESS_WINDOW_MS) return

        val senderName = thread.lastBeepSenderName.ifBlank {
            if (thread.isGroup) {
                thread.participantNamesById[thread.lastBeepSenderId].orEmpty().ifBlank { "Member" }
            } else {
                thread.otherName(currentUserId).ifBlank { "Friend" }
            }
        }
        val channelName = thread.displayTitle(currentUserId).ifBlank { senderName }
        LivePttSessionManager.recordIncomingBeep(
            threadId = thread.id,
            senderName = senderName,
            channelName = channelName,
            timestampMillis = thread.lastBeepAtMillis
        )

        // Always vibrate the peer's phone on incoming PTT Beep, and play the beep tone unless silenced
        vibrateBeepPattern(context)
        if (!DirectMessageNotificationPrefs.isPttBeepSilenced(context)) {
            PttChirpSoundEngine.playPttAttentionBeep()
        }

        // If the recipient's app is backgrounded or screen is locked, also post a brief high-priority
        // beep notification so Android reliably delivers the phone vibration & alert on the lock screen.
        if (!VoxStageNotificationState.isAppInForeground) {
            val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            if (canNotify) {
                ensureChannel(context)
                val tapIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_DM_THREAD_ID, thread.id)
                }
                val notifId = 8600 + (thread.id.hashCode() and 0x0FFF)
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    notifId,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_FRIEND_ALERT)
                    .setSmallIcon(R.drawable.ic_stat_message)
                    .setColor(0xFFF59E0B.toInt())
                    .setContentTitle("📡 PTT Beep · $senderName")
                    .setContentText("$senderName beeped you on $channelName")
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setVibrate(BEEP_VIBRATION_PATTERN)
                    .setSilent(DirectMessageNotificationPrefs.isPttBeepSilenced(context))
                    .setTimeoutAfter(12_000L)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()
                try {
                    NotificationManagerCompat.from(context).notify(notifId, notification)
                } catch (sec: SecurityException) {
                    Log.w(TAG, "Missing POST_NOTIFICATIONS permission for PTT beep alert", sec)
                }
            }
        }
    }

    private fun vibrateBeepPattern(context: Context) {
        runCatching {
            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect.createWaveform(BEEP_VIBRATION_PATTERN, -1)
            } else {
                null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && effect != null) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_COMMUNICATION_REQUEST)
                    .build()
                vm?.defaultVibrator?.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && effect != null) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && effect != null) {
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(BEEP_VIBRATION_PATTERN, -1)
                }
            }
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

        val senderName = when {
            thread.voxOfficial -> "VOX • Official Update"
            thread.isGroup -> {
                val who = thread.participantNamesById[thread.lastMessageSenderId].orEmpty()
                val groupTitle = thread.displayTitle(currentUserId)
                if (who.isNotBlank()) "$who · $groupTitle" else groupTitle
            }
            else -> thread.otherName(currentUserId).ifBlank { "New Private Message" }
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
            // High-priority heads-up popup with full message details (+ inline Quick Reply except for read-only VOX official DMs)
            val senderPerson = Person.Builder()
                .setName(senderName)
                .setKey(thread.lastMessageSenderId)
                .build()
            val messagingStyle = NotificationCompat.MessagingStyle(
                Person.Builder().setName("You").setKey(currentUserId).build()
            )
                .setConversationTitle(if (thread.isGroup) thread.displayTitle(currentUserId) else null)
                .addMessage(messagePreview, messageTimestamp, senderPerson)

            builder
                .setStyle(messagingStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            if (!thread.voxOfficial) {
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
                builder.addAction(quickReplyAction)
            }
            builder.addAction(0, context.getString(R.string.action_open_chat), pendingIntent)
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
    private const val BEEP_FRESHNESS_WINDOW_MS = 45_000L
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
            DirectMessageNotificationHelper.clearRecentDm(appContext)
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
                        var newestIncomingThread: DirectThread? = null
                        var newestIncomingMs = 0L
                        snapshot.documents.forEach { doc ->
                            val thread = doc.toObject(DirectThread::class.java) ?: return@forEach
                            if (thread.lastMessageSenderId.isNotBlank() &&
                                thread.lastMessageSenderId != uid &&
                                thread.lastMessageText.isNotBlank() &&
                                !thread.hasAnyBlock() &&
                                !thread.isUserKicked(uid)
                            ) {
                                val updatedMs = thread.updatedAt?.toDate()?.time ?: 0L
                                if (newestIncomingThread == null || updatedMs >= newestIncomingMs) {
                                    newestIncomingThread = thread
                                    newestIncomingMs = updatedMs
                                }
                            }
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
                            // Seed historical Group DM urgent alert timestamps unless brand new (< 45s old)
                            if (thread.isGroup && thread.lastGroupAlertAtMillis > 0L && thread.lastGroupAlertSenderId != uid) {
                                val isFreshGroupAlert = (nowMs - thread.lastGroupAlertAtMillis) <= RECENT_ARRIVAL_WINDOW_MS
                                if (wasBaselineSeeded && isFreshGroupAlert) {
                                    DirectMessageNotificationHelper.notifyGroupUrgentAlertIfNew(
                                        context = appContext,
                                        thread = thread,
                                        currentUserId = uid
                                    )
                                } else {
                                    DirectMessageNotificationPrefs.setLastGroupAlertMillis(
                                        context = appContext,
                                        uid = uid,
                                        threadId = thread.id,
                                        alertMillis = thread.lastGroupAlertAtMillis
                                    )
                                }
                            }
                            // Seed historical Priority Break-In Chirp timestamps unless brand new (< 45s old)
                            if (!thread.isGroup && thread.hasActivePriorityChirpFor(uid)) {
                                val isFreshChirp = (nowMs - thread.lastPriorityChirpAtMillis) <= RECENT_ARRIVAL_WINDOW_MS
                                if (wasBaselineSeeded && isFreshChirp) {
                                    DirectMessageNotificationHelper.notifyPriorityBreakInChirpIfNew(
                                        context = appContext,
                                        thread = thread,
                                        currentUserId = uid
                                    )
                                } else {
                                    DirectMessageNotificationPrefs.setLastPriorityChirpMillis(
                                        context = appContext,
                                        uid = uid,
                                        threadId = thread.id,
                                        chirpMillis = thread.lastPriorityChirpAtMillis
                                    )
                                }
                            }
                            // Seed historical PTT beep timestamps unless brand new (< 45s old)
                            if (thread.lastBeepAtMillis > 0L && thread.lastBeepSenderId != uid) {
                                val isFreshBeep = (nowMs - thread.lastBeepAtMillis) <= RECENT_ARRIVAL_WINDOW_MS
                                if (wasBaselineSeeded && isFreshBeep) {
                                    DirectMessageNotificationHelper.notifyPttBeepIfNew(
                                        context = appContext,
                                        thread = thread,
                                        currentUserId = uid
                                    )
                                } else {
                                    DirectMessageNotificationPrefs.setLastPttBeepMillis(
                                        context = appContext,
                                        uid = uid,
                                        threadId = thread.id,
                                        beepMillis = thread.lastBeepAtMillis
                                    )
                                }
                            }
                            // Keep LivePttSessionManager synced with Group DM Admin priority talk-over state
                            // so Floating PTT / Lock-Screen PTT / Hardware PTT also yield immediately.
                            if (thread.id == LivePttSessionManager.activeThreadId.value) {
                                LivePttSessionManager.setThreadGroupAdminState(
                                    threadId = thread.id,
                                    isGroupAdmin = thread.isGroupAdmin(uid),
                                    blockedByAdmin = thread.isBlockedByGroupAdminTalking(uid),
                                    context = appContext
                                )
                                LivePttSessionManager.syncActiveThreadPttFlags(
                                    context = appContext,
                                    thread = thread,
                                    currentUserId = uid
                                )
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
                        newestIncomingThread?.let { latest ->
                            DirectMessageNotificationHelper.updateRecentDmFromThread(
                                context = appContext,
                                thread = latest,
                                currentUserId = uid
                            )
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
                            if (thread.id == LivePttSessionManager.activeThreadId.value) {
                                LivePttSessionManager.setThreadGroupAdminState(
                                    threadId = thread.id,
                                    isGroupAdmin = thread.isGroupAdmin(uid),
                                    blockedByAdmin = thread.isBlockedByGroupAdminTalking(uid),
                                    context = appContext
                                )
                                LivePttSessionManager.syncActiveThreadPttFlags(
                                    context = appContext,
                                    thread = thread,
                                    currentUserId = uid
                                )
                            }
                            DirectMessageNotificationHelper.updateRecentDmFromThread(
                                context = appContext,
                                thread = thread,
                                currentUserId = uid
                            )
                            DirectMessageNotificationHelper.notifyFriendAttentionAlertIfNew(
                                context = appContext,
                                thread = thread,
                                currentUserId = uid
                            )
                            DirectMessageNotificationHelper.notifyGroupUrgentAlertIfNew(
                                context = appContext,
                                thread = thread,
                                currentUserId = uid
                            )
                            DirectMessageNotificationHelper.notifyPriorityBreakInChirpIfNew(
                                context = appContext,
                                thread = thread,
                                currentUserId = uid
                            )
                            DirectMessageNotificationHelper.notifyPttBeepIfNew(
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

