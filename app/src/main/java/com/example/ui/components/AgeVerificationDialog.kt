package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.auth.GoogleAgeCheckOutcome
import com.example.auth.completeGoogleAccountAgeConsent
import com.example.auth.requestGoogleAccountAgeCheck
import com.example.auth.requestPlayAgeSignalsCheck
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun AgeVerificationDialog(
    userEmail: String? = null,
    onConfirmBirthYear: (Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val maxAllowedBirthYear = currentYear - 18

    var isCheckingGoogle by remember { mutableStateOf(false) }
    var activeCheckLabel by remember { mutableStateOf("") }
    var googleNotice by remember { mutableStateOf<String?>(null) }
    var underageBlockedReason by remember { mutableStateOf<String?>(null) }
    var showManualFallback by remember { mutableStateOf(false) }

    var birthYearInput by remember { mutableStateOf("") }
    var confirmedCheckbox by remember { mutableStateOf(false) }

    val parsedYear = birthYearInput.trim().toIntOrNull()
    val hasFourDigits = birthYearInput.trim().length == 4
    val calculatedAge = if (parsedYear != null && hasFourDigits) currentYear - parsedYear else null
    val isInvalidRange = hasFourDigits && (parsedYear == null || parsedYear < 1900 || parsedYear > currentYear)
    val isUnderage = hasFourDigits && parsedYear != null && parsedYear > maxAllowedBirthYear && parsedYear <= currentYear
    val isEligible = hasFourDigits && parsedYear != null && parsedYear in 1900..maxAllowedBirthYear

    fun handleOutcome(outcome: GoogleAgeCheckOutcome) {
        isCheckingGoogle = false
        activeCheckLabel = ""
        when (outcome) {
            is GoogleAgeCheckOutcome.VerifiedAdult -> {
                googleNotice = null
                underageBlockedReason = null
                onConfirmBirthYear(outcome.birthYear, outcome.sourceLabel)
            }
            is GoogleAgeCheckOutcome.UnderageBlocked -> {
                underageBlockedReason = outcome.reason
                googleNotice = null
                showManualFallback = false
            }
            is GoogleAgeCheckOutcome.Unavailable -> {
                googleNotice = outcome.reason
            }
            GoogleAgeCheckOutcome.Cancelled -> {
                googleNotice = "Google Account verification was cancelled. You can try again anytime."
            }
            is GoogleAgeCheckOutcome.NeedsConsent -> {
                // Handled directly by launcher below
            }
        }
    }

    val consentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            isCheckingGoogle = true
            activeCheckLabel = "Reading birthday from Google Account…"
            val outcome = completeGoogleAccountAgeConsent(
                context = context,
                resultCode = result.resultCode,
                data = result.data
            )
            handleOutcome(outcome)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isCheckingGoogle) onDismiss() },
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (underageBlockedReason != null) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (underageBlockedReason != null) Icons.Rounded.Block else Icons.Rounded.VerifiedUser,
                    contentDescription = "18+ Age Verification",
                    tint = if (underageBlockedReason != null) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "18+ Age Verification",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Verify you are 18+ using your Google Account birthday or Google Play Age Signals to lock or view locked photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (underageBlockedReason != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "18+ Access Restricted",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = underageBlockedReason.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            if (isCheckingGoogle) return@Button
                            googleNotice = null
                            isCheckingGoogle = true
                            activeCheckLabel = "Connecting to Google Account…"
                            scope.launch {
                                val outcome = requestGoogleAccountAgeCheck(
                                    context = context,
                                    userEmail = userEmail
                                )
                                if (outcome is GoogleAgeCheckOutcome.NeedsConsent) {
                                    isCheckingGoogle = false
                                    activeCheckLabel = ""
                                    consentLauncher.launch(
                                        IntentSenderRequest.Builder(outcome.intentSender).build()
                                    )
                                } else {
                                    handleOutcome(outcome)
                                }
                            }
                        },
                        enabled = !isCheckingGoogle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_age_google_account_button")
                    ) {
                        if (isCheckingGoogle && activeCheckLabel.contains("Google Account", ignoreCase = true)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking Google Account…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AccountCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify with Google Account")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isCheckingGoogle) return@OutlinedButton
                            googleNotice = null
                            isCheckingGoogle = true
                            activeCheckLabel = "Checking Google Play Age Signals…"
                            scope.launch {
                                val outcome = requestPlayAgeSignalsCheck(context = context)
                                handleOutcome(outcome)
                            }
                        },
                        enabled = !isCheckingGoogle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("verify_age_play_signals_button")
                    ) {
                        if (isCheckingGoogle && activeCheckLabel.contains("Play", ignoreCase = true)) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Checking Play Age Signals…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Verify with Google Play Age Signals")
                        }
                    }

                    if (!googleNotice.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = googleNotice.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isCheckingGoogle) {
                                showManualFallback = !showManualFallback
                            }
                            .testTag("toggle_manual_age_fallback_button")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Birthday hidden on Google? Manual fallback",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showManualFallback) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (showManualFallback) "Hide manual fallback" else "Show manual fallback",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (showManualFallback) {
                        OutlinedTextField(
                            value = birthYearInput,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(4)
                                birthYearInput = digits
                            },
                            label = { Text("Birth Year (YYYY)") },
                            placeholder = { Text("e.g. 1996") },
                            singleLine = true,
                            isError = isUnderage || isInvalidRange,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            supportingText = {
                                when {
                                    isInvalidRange -> Text(
                                        text = "Enter a valid 4-digit birth year (1900–$currentYear).",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    isUnderage -> Text(
                                        text = "You must be 18 or older (born $maxAllowedBirthYear or earlier).",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    isEligible && calculatedAge != null -> Text(
                                        text = "Verified age range: $calculatedAge+ years old",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    else -> Text("Must be born in $maxAllowedBirthYear or earlier")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("age_verification_birth_year_input")
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { confirmedCheckbox = !confirmedCheckbox }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = confirmedCheckbox,
                                    onCheckedChange = { confirmedCheckbox = it },
                                    modifier = Modifier.testTag("age_verification_checkbox")
                                )
                                Text(
                                    text = "I confirm that I am at least 18 years of age.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (underageBlockedReason == null && showManualFallback) {
                Button(
                    onClick = {
                        if (isEligible && confirmedCheckbox && parsedYear != null) {
                            onConfirmBirthYear(parsedYear, "Birth Year Confirmation")
                        }
                    },
                    enabled = !isCheckingGoogle && isEligible && confirmedCheckbox,
                    modifier = Modifier.testTag("confirm_age_18_button")
                ) {
                    Text("Verify 18+")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCheckingGoogle,
                modifier = Modifier.testTag("cancel_age_18_button")
            ) {
                Text(if (underageBlockedReason != null) "Close" else "Cancel")
            }
        }
    )
}
