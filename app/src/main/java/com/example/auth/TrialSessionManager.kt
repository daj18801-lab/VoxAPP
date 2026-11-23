package com.example.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages the local 1-day (24-hour) Trial Login session.
 * Persists the original trial start timestamp so users get a strict 24-hour trial window
 * before being required to sign in with their Google email.
 */
data class TrialSession(
    val displayName: String,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
    val active: Boolean
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        startedAtMillis > 0L && nowMillis >= expiresAtMillis

    fun remainingMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        (expiresAtMillis - nowMillis).coerceAtLeast(0L)

    fun remainingLabel(nowMillis: Long = System.currentTimeMillis()): String {
        val rem = remainingMillis(nowMillis)
        if (rem <= 0L) return "Expired"
        val totalMinutes = (rem + 59_999L) / 60_000L
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        return when {
            hours > 0L -> "${hours}h ${mins}m left"
            else -> "${mins}m left"
        }
    }
}

object TrialSessionManager {
    private const val PREFS_NAME = "voxstage_trial_session_prefs"
    private const val KEY_DISPLAY_NAME = "trial_display_name"
    private const val KEY_STARTED_AT = "trial_started_at_millis"
    private const val KEY_EXPIRES_AT = "trial_expires_at_millis"
    private const val KEY_ACTIVE = "trial_active"

    const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSession(context: Context): TrialSession? {
        val p = prefs(context)
        val started = p.getLong(KEY_STARTED_AT, 0L)
        val expires = p.getLong(KEY_EXPIRES_AT, 0L)
        if (started <= 0L || expires <= 0L) return null
        return TrialSession(
            displayName = p.getString(KEY_DISPLAY_NAME, "Trial Guest").orEmpty().ifBlank { "Trial Guest" },
            startedAtMillis = started,
            expiresAtMillis = expires,
            active = p.getBoolean(KEY_ACTIVE, false)
        )
    }

    fun hasTrialExpired(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val session = loadSession(context) ?: return false
        return session.isExpired(nowMillis)
    }

    fun getActiveValidSession(context: Context, nowMillis: Long = System.currentTimeMillis()): TrialSession? {
        val session = loadSession(context) ?: return null
        if (!session.active) return null
        if (session.isExpired(nowMillis)) {
            deactivateTrial(context)
            return null
        }
        return session
    }

    /**
     * Starts or resumes the device's 1-day trial session.
     * If a previous trial already started, preserves the original 24-hour deadline so users
     * cannot reset the 1-day limit by logging out and re-entering a trial name.
     */
    fun startOrResumeTrial(
        context: Context,
        trialName: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<TrialSession> {
        val cleanName = trialName.trim().take(30)
        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Enter a trial name (at least 2 characters)."))
        }
        val existing = loadSession(context)
        if (existing != null && existing.isExpired(nowMillis)) {
            return Result.failure(
                IllegalStateException("Your 1-day trial has ended. Please sign in with your Google email to continue.")
            )
        }
        val startedAt = existing?.startedAtMillis ?: nowMillis
        val expiresAt = existing?.expiresAtMillis ?: (nowMillis + ONE_DAY_MILLIS)

        prefs(context).edit()
            .putString(KEY_DISPLAY_NAME, cleanName)
            .putLong(KEY_STARTED_AT, startedAt)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putBoolean(KEY_ACTIVE, true)
            .apply()

        return Result.success(
            TrialSession(
                displayName = cleanName,
                startedAtMillis = startedAt,
                expiresAtMillis = expiresAt,
                active = true
            )
        )
    }

    fun deactivateTrial(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .apply()
    }
}
