package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun AgeVerificationDialog(
    onConfirmBirthYear: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val maxAllowedBirthYear = currentYear - 18

    var birthYearInput by remember { mutableStateOf("") }
    var confirmedCheckbox by remember { mutableStateOf(false) }

    val parsedYear = birthYearInput.trim().toIntOrNull()
    val hasFourDigits = birthYearInput.trim().length == 4
    val calculatedAge = if (parsedYear != null && hasFourDigits) currentYear - parsedYear else null
    val isInvalidRange = hasFourDigits && (parsedYear == null || parsedYear < 1900 || parsedYear > currentYear)
    val isUnderage = hasFourDigits && parsedYear != null && parsedYear > maxAllowedBirthYear && parsedYear <= currentYear
    val isEligible = hasFourDigits && parsedYear != null && parsedYear in 1900..maxAllowedBirthYear

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = "18+ Age Verification",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "18+ Age Verification Required",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "You must be 18 years or older to lock photos on your profile or view and unlock locked photos.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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
                                text = "You must be 18 or older to lock or view locked photos (born $maxAllowedBirthYear or earlier).",
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
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isEligible && confirmedCheckbox && parsedYear != null) {
                        onConfirmBirthYear(parsedYear)
                    }
                },
                enabled = isEligible && confirmedCheckbox,
                modifier = Modifier.testTag("confirm_age_18_button")
            ) {
                Text("Verify 18+")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_age_18_button")
            ) {
                Text("Cancel")
            }
        }
    )
}
