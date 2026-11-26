package com.example.auth

import android.content.Context

/**
 * Stores pending Company Employee logins (Company Code + PIN + First/Last Name or legacy Access Code)
 * AND pending Business Account registration ($99.99/mo) entered on AuthScreen before Google Sign-In.
 */
object SubAccountLoginPrefs {
    private const val PREFS_NAME = "vox_sub_account_login_prefs"
    private const val KEY_SUB_NAME = "pending_sub_name"
    private const val KEY_ACCESS_CODE = "pending_access_code"

    // Company Code + PIN sub-account login
    private const val KEY_COMPANY_CODE = "pending_company_code"
    private const val KEY_COMPANY_PIN = "pending_company_pin"
    private const val KEY_EMP_FIRST_NAME = "pending_emp_first_name"
    private const val KEY_EMP_LAST_NAME = "pending_emp_last_name"

    // Business Account registration ($99.99/mo)
    private const val KEY_BIZ_EMAIL = "pending_biz_email"
    private const val KEY_BIZ_COMPANY_NAME = "pending_biz_company_name"
    private const val KEY_BIZ_CODE = "pending_biz_code"
    private const val KEY_BIZ_PIN = "pending_biz_pin"
    private const val KEY_BIZ_PENDING_CHECKOUT = "pending_biz_checkout"

    data class PendingSubAccountClaim(
        val subName: String,
        val accessCode: String
    )

    data class PendingCompanyCodeLogin(
        val companyCode: String,
        val pin: String,
        val firstName: String,
        val lastName: String
    ) {
        fun fullName(): String = "$firstName $lastName".trim()
    }

    data class PendingBusinessRegistration(
        val businessEmail: String,
        val companyName: String,
        val companyCode: String,
        val pin: String
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
            .remove(KEY_COMPANY_CODE)
            .remove(KEY_COMPANY_PIN)
            .remove(KEY_EMP_FIRST_NAME)
            .remove(KEY_EMP_LAST_NAME)
            .apply()
    }

    fun getPendingClaim(context: Context): PendingSubAccountClaim? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_ACCESS_CODE, "").orEmpty().trim()
        if (code.isBlank()) return null
        val name = prefs.getString(KEY_SUB_NAME, "").orEmpty().trim()
        return PendingSubAccountClaim(subName = name, accessCode = code)
    }

    fun savePendingCompanyCodeLogin(
        context: Context,
        companyCode: String,
        pin: String,
        firstName: String,
        lastName: String
    ) {
        val cleanCode = normalizeCompanyCode(companyCode)
        val cleanPin = normalizePin(pin)
        if (cleanCode.isBlank() || cleanPin.isBlank()) {
            clear(context)
            return
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COMPANY_CODE, cleanCode)
            .putString(KEY_COMPANY_PIN, cleanPin)
            .putString(KEY_EMP_FIRST_NAME, firstName.trim().take(20))
            .putString(KEY_EMP_LAST_NAME, lastName.trim().take(20))
            .remove(KEY_SUB_NAME)
            .remove(KEY_ACCESS_CODE)
            .putBoolean(KEY_BIZ_PENDING_CHECKOUT, false)
            .apply()
    }

    fun getPendingCompanyCodeLogin(context: Context): PendingCompanyCodeLogin? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val code = prefs.getString(KEY_COMPANY_CODE, "").orEmpty().trim()
        val pin = prefs.getString(KEY_COMPANY_PIN, "").orEmpty().trim()
        if (code.isBlank() || pin.isBlank()) return null
        val first = prefs.getString(KEY_EMP_FIRST_NAME, "").orEmpty().trim()
        val last = prefs.getString(KEY_EMP_LAST_NAME, "").orEmpty().trim()
        return PendingCompanyCodeLogin(
            companyCode = code,
            pin = pin,
            firstName = first,
            lastName = last
        )
    }

    fun savePendingBusinessRegistration(
        context: Context,
        businessEmail: String,
        companyName: String,
        companyCode: String,
        pin: String
    ) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BIZ_EMAIL, businessEmail.trim().take(120))
            .putString(KEY_BIZ_COMPANY_NAME, companyName.trim().take(60))
            .putString(KEY_BIZ_CODE, normalizeCompanyCode(companyCode))
            .putString(KEY_BIZ_PIN, normalizePin(pin))
            .putBoolean(KEY_BIZ_PENDING_CHECKOUT, true)
            .remove(KEY_SUB_NAME)
            .remove(KEY_ACCESS_CODE)
            .remove(KEY_COMPANY_CODE)
            .remove(KEY_COMPANY_PIN)
            .apply()
    }

    fun getPendingBusinessRegistration(context: Context): PendingBusinessRegistration? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_BIZ_PENDING_CHECKOUT, false)) return null
        return PendingBusinessRegistration(
            businessEmail = prefs.getString(KEY_BIZ_EMAIL, "").orEmpty().trim(),
            companyName = prefs.getString(KEY_BIZ_COMPANY_NAME, "").orEmpty().trim(),
            companyCode = prefs.getString(KEY_BIZ_CODE, "").orEmpty().trim(),
            pin = prefs.getString(KEY_BIZ_PIN, "").orEmpty().trim()
        )
    }

    fun clearPendingBusinessRegistration(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIZ_PENDING_CHECKOUT, false)
            .remove(KEY_BIZ_EMAIL)
            .remove(KEY_BIZ_COMPANY_NAME)
            .remove(KEY_BIZ_CODE)
            .remove(KEY_BIZ_PIN)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_SUB_NAME)
            .remove(KEY_ACCESS_CODE)
            .remove(KEY_COMPANY_CODE)
            .remove(KEY_COMPANY_PIN)
            .remove(KEY_EMP_FIRST_NAME)
            .remove(KEY_EMP_LAST_NAME)
            .apply()
    }

    fun clearAll(context: Context) {
        clear(context)
        clearPendingBusinessRegistration(context)
    }

    fun normalizeCode(raw: String): String {
        val upper = raw.trim().uppercase().replace(" ", "").replace("-", "_")
        if (upper.isBlank()) return ""
        return if (upper.startsWith("EMP_")) upper else "EMP_$upper"
    }

    fun normalizeCompanyCode(raw: String): String =
        raw.trim().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' }.take(24)

    fun normalizePin(raw: String): String =
        raw.trim().filter { it in '0'..'9' || it in 'A'..'Z' || it in 'a'..'z' }.take(12)
}
