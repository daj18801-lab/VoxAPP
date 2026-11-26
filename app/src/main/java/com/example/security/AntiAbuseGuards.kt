package com.example.security

import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.UUID

/**
 * Play-policy-compliant device fingerprint and local device-ban cache.
 * Uses app-scoped ANDROID_ID (zero permissions) hashed with SHA-256 so the same
 * physical device maps to a consistent hash across different Google email accounts.
 */
object DeviceBanManager {
    private const val PREFS_NAME = "vox_device_ban_prefs"
    private const val KEY_FALLBACK_UUID = "fallback_device_uuid"
    private const val KEY_IS_BANNED = "is_device_banned"
    private const val KEY_BAN_REASON = "device_ban_reason"

    @Volatile
    private var cachedHash: String? = null

    fun getDeviceHash(context: Context): String {
        cachedHash?.let { return it }
        val appContext = context.applicationContext
        val androidId = runCatching {
            Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim().orEmpty()

        val rawSeed = if (androidId.isNotBlank() && androidId != "9774d56d682e549c") {
            androidId
        } else {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.getString(KEY_FALLBACK_UUID, null)?.takeIf { it.isNotBlank() }
                ?: UUID.randomUUID().toString().also { generated ->
                    prefs.edit().putString(KEY_FALLBACK_UUID, generated).apply()
                }
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest("vox_device_v1:$rawSeed".toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        val result = "dev_${hex.take(48)}"
        cachedHash = result
        return result
    }

    fun isLocallyDeviceBanned(context: Context): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_BANNED, false)
    }

    fun getLocalDeviceBanReason(context: Context): String {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_BAN_REASON, "")
            .orEmpty()
    }

    fun markLocalDeviceBanned(context: Context, reason: String) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_BANNED, true)
            .putString(KEY_BAN_REASON, reason.trim().take(240))
            .apply()
    }

    fun clearLocalDeviceBan(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_BANNED, false)
            .remove(KEY_BAN_REASON)
            .apply()
    }
}

/**
 * Rolling 30-second message spam detector (> 20 messages in 30 seconds).
 * Triggers the 4-tier escalation:
 *  - 1st offense: Warning dialog
 *  - 2nd offense (in 24h): 1-hour suspension
 *  - 3rd offense (in 24h): 4-hour suspension + staff report
 *  - 4th+ offense (in 24h): 24-hour suspension + staff report for action
 */
object MessageSpamTracker {
    const val MAX_MESSAGES_IN_WINDOW = 20
    const val MESSAGE_WINDOW_MS = 30_000L
    const val ESCALATION_WINDOW_24H_MS = 24L * 60L * 60L * 1000L

    private val lock = Any()
    private val timestampsByUid = mutableMapOf<String, ArrayDeque<Long>>()

    private val _spamWarningDialogMessage = MutableStateFlow<String?>(null)
    val spamWarningDialogMessage: StateFlow<String?> = _spamWarningDialogMessage.asStateFlow()

    /**
     * Records a message send attempt. Returns true if the user just exceeded
     * 20 messages within 30 seconds (i.e. this is the 21st message in 30s).
     */
    fun checkAndRecordMessageAttempt(uid: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        synchronized(lock) {
            val deque = timestampsByUid.getOrPut(uid) { ArrayDeque() }
            val cutoff = nowMs - MESSAGE_WINDOW_MS
            while (deque.isNotEmpty() && deque.first() < cutoff) {
                deque.removeFirst()
            }
            if (deque.size >= MAX_MESSAGES_IN_WINDOW) {
                // Reset the 30s burst counter so a single violation is recorded cleanly;
                // a subsequent 20+ msg burst triggers the next strike.
                deque.clear()
                return true
            }
            deque.addLast(nowMs)
            return false
        }
    }

    fun postSpamWarning(message: String) {
        _spamWarningDialogMessage.value = message
    }

    fun dismissSpamWarning() {
        _spamWarningDialogMessage.value = null
    }
}
