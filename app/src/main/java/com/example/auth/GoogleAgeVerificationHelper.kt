package com.example.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.play.agesignals.AgeSignalsAccessRequest
import com.google.android.play.agesignals.AgeSignalsManagerFactory
import com.google.android.play.agesignals.AgeSignalsRequest
import com.google.android.play.agesignals.model.AgeSignalsStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

private const val TAG_AGE_VERIFY = "GoogleAgeVerify"
private const val SCOPE_USER_BIRTHDAY_READ = "https://www.googleapis.com/auth/user.birthday.read"
private const val PEOPLE_API_BIRTHDAY_URL =
    "https://people.googleapis.com/v1/people/me?personFields=birthdays"

sealed interface GoogleAgeCheckOutcome {
    data class VerifiedAdult(
        val birthYear: Int,
        val ageYears: Int,
        val sourceLabel: String
    ) : GoogleAgeCheckOutcome

    data class UnderageBlocked(
        val ageYears: Int?,
        val sourceLabel: String,
        val reason: String
    ) : GoogleAgeCheckOutcome

    data class NeedsConsent(
        val intentSender: IntentSender
    ) : GoogleAgeCheckOutcome

    data class Unavailable(
        val reason: String
    ) : GoogleAgeCheckOutcome

    data object Cancelled : GoogleAgeCheckOutcome
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/**
 * Initiates Google Account birthday authorization via Google Identity Services
 * AuthorizationClient (`user.birthday.read` scope). If already authorized, fetches
 * the birthday immediately from the Google People API; otherwise returns [GoogleAgeCheckOutcome.NeedsConsent]
 * so the caller can launch Google's OAuth consent screen.
 */
suspend fun requestGoogleAccountAgeCheck(
    context: Context,
    userEmail: String?
): GoogleAgeCheckOutcome {
    val activity = context.findHostActivity()
        ?: return GoogleAgeCheckOutcome.Unavailable("Unable to launch Google Account check from this screen.")

    return try {
        val builder = AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(SCOPE_USER_BIRTHDAY_READ)))

        val cleanEmail = userEmail?.trim().orEmpty()
        if (cleanEmail.isNotBlank()) {
            builder.setAccount(Account(cleanEmail, "com.google"))
        }

        val authResult = Identity.getAuthorizationClient(activity)
            .authorize(builder.build())
            .await()

        val pendingIntent = authResult.pendingIntent
        if (authResult.hasResolution() && pendingIntent != null) {
            GoogleAgeCheckOutcome.NeedsConsent(pendingIntent.intentSender)
        } else {
            val accessToken = authResult.accessToken
            if (accessToken.isNullOrBlank()) {
                GoogleAgeCheckOutcome.Unavailable(
                    "Google did not return an authorization token. Please try again."
                )
            } else {
                fetchAndEvaluateGoogleBirthday(accessToken)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG_AGE_VERIFY, "Google Account birthday authorization failed", e)
        GoogleAgeCheckOutcome.Unavailable(
            formatGoogleAuthError(e.localizedMessage)
        )
    }
}

/**
 * Completes the Google Account birthday check after the user finishes Google's
 * OAuth consent IntentSender flow.
 */
suspend fun completeGoogleAccountAgeConsent(
    context: Context,
    resultCode: Int,
    data: Intent?
): GoogleAgeCheckOutcome {
    if (resultCode != Activity.RESULT_OK || data == null) {
        return GoogleAgeCheckOutcome.Cancelled
    }
    val activity = context.findHostActivity()
        ?: return GoogleAgeCheckOutcome.Unavailable("Unable to complete Google Account check.")

    return try {
        val authResult = Identity.getAuthorizationClient(activity)
            .getAuthorizationResultFromIntent(data)
        val accessToken = authResult.accessToken
        if (accessToken.isNullOrBlank()) {
            GoogleAgeCheckOutcome.Unavailable("Google Account authorization did not grant access.")
        } else {
            fetchAndEvaluateGoogleBirthday(accessToken)
        }
    } catch (e: Exception) {
        Log.w(TAG_AGE_VERIFY, "Failed to read Google Account authorization result", e)
        GoogleAgeCheckOutcome.Unavailable(formatGoogleAuthError(e.localizedMessage))
    }
}

/**
 * Queries the Google People API (`people/me?personFields=birthdays`) using the
 * authorized OAuth access token and evaluates whether the account holder is 18+.
 */
private suspend fun fetchAndEvaluateGoogleBirthday(
    accessToken: String
): GoogleAgeCheckOutcome = withContext(Dispatchers.IO) {
    var connection: HttpURLConnection? = null
    try {
        connection = (URL(PEOPLE_API_BIRTHDAY_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (statusCode !in 200..299) {
            Log.w(TAG_AGE_VERIFY, "People API returned HTTP $statusCode: $body")
            val apiErrorMessage = runCatching {
                JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            val friendlyReason = when {
                statusCode == 403 && apiErrorMessage.contains("People API", ignoreCase = true) ->
                    "Google People API is not enabled on this Cloud project yet. Enable People API in Google Cloud Console or use Google Play Age Signals / fallback below."
                statusCode == 401 || statusCode == 403 ->
                    "Google Account birthday access was declined or is restricted on this account."
                else ->
                    "Could not read birthday from Google Account (HTTP $statusCode)."
            }
            return@withContext GoogleAgeCheckOutcome.Unavailable(friendlyReason)
        }

        parsePeopleApiBirthdays(body)
    } catch (e: Exception) {
        Log.w(TAG_AGE_VERIFY, "Failed fetching birthday from People API", e)
        GoogleAgeCheckOutcome.Unavailable(
            e.localizedMessage ?: "Network error while checking your Google Account birthday."
        )
    } finally {
        connection?.disconnect()
    }
}

private fun parsePeopleApiBirthdays(jsonBody: String): GoogleAgeCheckOutcome {
    val root = JSONObject(jsonBody)
    val birthdays = root.optJSONArray("birthdays")
        ?: return GoogleAgeCheckOutcome.Unavailable(
            "Your Google Account does not have a shared birth year on file. Add or share your birthday in Google Account settings, or use another option below."
        )

    var bestYear: Int? = null
    var bestMonth: Int? = null
    var bestDay: Int? = null
    var bestIsAccountSource = false

    for (i in 0 until birthdays.length()) {
        val entry = birthdays.optJSONObject(i) ?: continue
        val date = entry.optJSONObject("date") ?: continue
        val year = date.optInt("year", 0)
        if (year < 1900) continue

        val month = date.optInt("month", 0).takeIf { it in 1..12 }
        val day = date.optInt("day", 0).takeIf { it in 1..31 }
        val sourceType = entry
            .optJSONObject("metadata")
            ?.optJSONObject("source")
            ?.optString("type")
            .orEmpty()
        val isAccountSource = sourceType.equals("ACCOUNT", ignoreCase = true)

        if (bestYear == null || (isAccountSource && !bestIsAccountSource)) {
            bestYear = year
            bestMonth = month
            bestDay = day
            bestIsAccountSource = isAccountSource
        }
    }

    val verifiedYear = bestYear
        ?: return GoogleAgeCheckOutcome.Unavailable(
            "Your Google Account did not include a birth year (it may be hidden or unset in Google privacy settings)."
        )

    val exactAge = computeExactAgeYears(
        birthYear = verifiedYear,
        birthMonth = bestMonth,
        birthDay = bestDay
    )

    return if (exactAge >= 18) {
        GoogleAgeCheckOutcome.VerifiedAdult(
            birthYear = verifiedYear,
            ageYears = exactAge,
            sourceLabel = "Google Account"
        )
    } else {
        GoogleAgeCheckOutcome.UnderageBlocked(
            ageYears = exactAge,
            sourceLabel = "Google Account",
            reason = "Your Google Account birthday indicates you are $exactAge years old. You must be 18 or older to lock or view locked photos."
        )
    }
}

/**
 * Checks 18+ status via Google Play Age Signals API (`com.google.android.play:age-signals`),
 * which returns privacy-preserving age ranges (`ageLower` / `ageUpper`) managed by Google Play
 * and Family Link without exposing exact birthdates.
 */
suspend fun requestPlayAgeSignalsCheck(
    context: Context
): GoogleAgeCheckOutcome {
    val activity = context.findHostActivity()
        ?: return GoogleAgeCheckOutcome.Unavailable("Unable to launch Google Play Age Signals check.")

    return try {
        val manager = AgeSignalsManagerFactory.create(context.applicationContext)
        val accessRequest = AgeSignalsAccessRequest.builder()
            .setActivity(activity)
            .build()
        val accessResult = manager.requestAgeSignalsAccess(accessRequest).await()

        when (accessResult.ageSignalsStatus()) {
            AgeSignalsStatus.SHARED -> {
                val signals = manager.checkAgeSignals(AgeSignalsRequest.builder().build()).await()
                val ageLower = signals.ageLower()
                val ageUpper = signals.ageUpper()
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)

                when {
                    ageLower != null && ageLower >= 18 -> {
                        GoogleAgeCheckOutcome.VerifiedAdult(
                            birthYear = currentYear - ageLower,
                            ageYears = ageLower,
                            sourceLabel = "Google Play Age Signals"
                        )
                    }
                    ageUpper != null && ageUpper < 18 -> {
                        GoogleAgeCheckOutcome.UnderageBlocked(
                            ageYears = ageUpper,
                            sourceLabel = "Google Play Age Signals",
                            reason = "Google Play Age Signals indicates this account is under 18 (${ageLower ?: 0}–$ageUpper). Locked 18+ photos are unavailable."
                        )
                    }
                    else -> {
                        GoogleAgeCheckOutcome.Unavailable(
                            "Google Play did not share an 18+ age band for this account or region."
                        )
                    }
                }
            }
            AgeSignalsStatus.VERIFICATION_REQUIRED -> {
                GoogleAgeCheckOutcome.Unavailable(
                    "Google Play requires you to verify your age in the Play Store app first."
                )
            }
            AgeSignalsStatus.NOT_SHARED -> {
                GoogleAgeCheckOutcome.Unavailable(
                    "Age range sharing is turned off in your Google Play settings. Enable age sharing in Play Store or verify with your Google Account birthday."
                )
            }
            else -> {
                GoogleAgeCheckOutcome.Unavailable(
                    "Google Play Age Signals did not return a verified status for this device."
                )
            }
        }
    } catch (e: Exception) {
        Log.w(TAG_AGE_VERIFY, "Play Age Signals check failed", e)
        GoogleAgeCheckOutcome.Unavailable(
            "Google Play Age Signals is unavailable on this build (apps must be installed via Google Play in a supported region). Try Google Account verification or the fallback below."
        )
    }
}

private fun computeExactAgeYears(
    birthYear: Int,
    birthMonth: Int?,
    birthDay: Int?
): Int {
    val today = Calendar.getInstance()
    val currentYear = today.get(Calendar.YEAR)
    val currentMonth = today.get(Calendar.MONTH) + 1 // 1..12
    val currentDay = today.get(Calendar.DAY_OF_MONTH)

    var age = currentYear - birthYear
    if (birthMonth != null) {
        if (currentMonth < birthMonth || (currentMonth == birthMonth && birthDay != null && currentDay < birthDay)) {
            age -= 1
        }
    }
    return age.coerceAtLeast(0)
}

private fun formatGoogleAuthError(raw: String?): String {
    val msg = raw.orEmpty()
    return when {
        msg.contains("10:") || msg.contains("DEVELOPER_ERROR", ignoreCase = true) ->
            "Google Account birthday scope is not configured on this OAuth consent screen yet. Add the 'user.birthday.read' scope and enable Google People API in Google Cloud Console."
        msg.contains("16:") || msg.contains("CANCELED", ignoreCase = true) ->
            "Google Account verification was cancelled."
        msg.isNotBlank() ->
            "Could not check Google Account age: $msg"
        else ->
            "Could not reach Google Account services right now."
    }
}
