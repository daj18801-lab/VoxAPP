package com.example.auth

import android.content.Context

/**
 * Stores a pending Company Employee Sub-Name login claim entered on AuthScreen
 * before completing Google Sign-In via Jetpack Credential Manager.
 */
object SubAccountLoginPrefs {
    private const val PREFS_NAME = "vox_sub_account_login_prefs"
    private const val KEY_SUB_NAME = "pending_sub_name"
    private const val KEY_ACCESS_CODE = "pending_access_code"

    data class PendingSubAccountClaim(
        val subName: String,
        val accessCode: String
    )

    fun savePendingClaim(context: Context, subName: String, accessCode: String) {
        val cleanCode = normalizeCode(accessCode)
        val cleanName = subName.trim()
        if (cleanCode.isBlank()) {
            clear(context)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SUB_NAME, cleanName)
            .putString(KEY_ACCESS_CODE, cleanCode)
            .apply()
    }

    fun getPendingClaim(context: Context): PendingSubAccountClaim? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_ACCESS_CODE, "").orEmpty().trim()
        if (code.isBlank()) return null
        val name = prefs.getString(KEY_SUB_NAME, "").orEmpty().trim()
        return PendingSubAccountClaim(subName = name, accessCode = code)
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SUB_NAME)
            .remove(KEY_ACCESS_CODE)
            .apply()
    }

    fun normalizeCode(raw: String): String {
        val upper = raw.trim().uppercase().replace(" ", "").replace("-", "_")
        if (upper.isBlank()) return ""
        return if (upper.startsWith("EMP_")) upper else "EMP_$upper"
    }
}
