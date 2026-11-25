package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.DirectoryEntry
import com.example.data.FileNoteAction
import com.example.data.InvestigationStatus
import com.example.data.StaffTier
import com.example.data.SuspensionRequest
import com.example.data.SuspensionRequestStatus
import com.example.data.UserFileNote
import com.example.data.UserModerationFile
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

/**
 * Top bar Safety Shield icon button (48dp touch target) with optional alert/report badge.
 */
@Composable
fun TopBarSafetyShieldButton(
    badgeCount: Int,
    onDarkBackground: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (onDarkBackground) {
        Color.Black.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val iconTint = if (onDarkBackground) {
        Color.White
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = containerColor,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("top_bar_safety_shield_button")
            ) {
                if (badgeCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = LiveCoral,
                                contentColor = Color.White
                            ) {
                                Text(if (badgeCount > 9) "9+" else "$badgeCount")
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = "Open Safety Center",
                            tint = iconTint,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = "Open Safety Center",
                        tint = iconTint,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        }
    }
}

/**
 * Mandatory 13+ Age Verification gate for using VOX and purchasing VOX Pro or Points.
 */
@Composable
fun Age13GateDialog(
    reasonMessage: String = "VOX is for users 13 and older. Confirm your birth year to use VOX and purchase VOX Pro membership or points.",
    allowDismiss: Boolean = false,
    onConfirmBirthYear: (Int) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val maxAllowedBirthYear = currentYear - 13

    var birthYearInput by remember { mutableStateOf("") }
    var confirmedCheckbox by remember { mutableStateOf(false) }

    val parsedYear = birthYearInput.trim().toIntOrNull()
    val hasFourDigits = birthYearInput.trim().length == 4
    val calculatedAge = if (parsedYear != null && hasFourDigits) currentYear - parsedYear else null
    val isInvalidRange = hasFourDigits && (parsedYear == null || parsedYear < 1900 || parsedYear > currentYear)
    val isUnder13 = hasFourDigits && parsedYear != null && parsedYear > maxAllowedBirthYear && parsedYear <= currentYear
    val isEligible = hasFourDigits && parsedYear != null && parsedYear in 1900..maxAllowedBirthYear

    AlertDialog(
        onDismissRequest = { if (allowDismiss) onDismiss() },
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isUnder13) MaterialTheme.colorScheme.errorContainer
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isUnder13) Icons.Rounded.Block else Icons.Rounded.VerifiedUser,
                    contentDescription = "13+ Age Verification",
                    tint = if (isUnder13) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "13+ Age Verification",
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
                    text = reasonMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = birthYearInput,
                    onValueChange = { raw ->
                        birthYearInput = raw.filter { it.isDigit() }.take(4)
                    },
                    label = { Text("Birth Year (YYYY)") },
                    placeholder = { Text("e.g. 2008") },
                    singleLine = true,
                    isError = isUnder13 || isInvalidRange,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        when {
                            isInvalidRange -> Text(
                                text = "Enter a valid 4-digit birth year (1900–$currentYear).",
                                color = MaterialTheme.colorScheme.error
                            )
                            isUnder13 -> Text(
                                text = "You must be 13 or older (born $maxAllowedBirthYear or earlier) to use VOX or buy Pro/Points.",
                                color = MaterialTheme.colorScheme.error
                            )
                            isEligible && calculatedAge != null -> Text(
                                text = if (calculatedAge >= 18) {
                                    "Verified 18+ ($calculatedAge years old) — full VOX & purchase access."
                                } else {
                                    "Verified 13+ ($calculatedAge years old) — VOX app, Pro & Points enabled."
                                },
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            else -> Text("Must be born in $maxAllowedBirthYear or earlier (13+)")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("age_13_birth_year_input")
                )

                if (isUnder13) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Sorry, VOX is only available for users aged 13 and older. You cannot use VOX or purchase Pro membership or points if you are under 13.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
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
                                modifier = Modifier.testTag("age_13_confirm_checkbox")
                            )
                            Text(
                                text = "I confirm that I am at least 13 years of age.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isUnder13) {
                Button(
                    onClick = {
                        if (isEligible && confirmedCheckbox && parsedYear != null) {
                            onConfirmBirthYear(parsedYear)
                        }
                    },
                    enabled = isEligible && confirmedCheckbox,
                    modifier = Modifier.testTag("confirm_age_13_button")
                ) {
                    Text("Verify 13+")
                }
            }
        },
        dismissButton = {
            if (allowDismiss) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("cancel_age_13_button")
                ) {
                    Text("Cancel")
                }
            } else {
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.testTag("age_13_sign_out_button")
                ) {
                    Text("Sign Out")
                }
            }
        }
    )
}

/**
 * Strike 2+ Formal Warning modal shown automatically to a reported user.
 */
@Composable
fun StrikeWarningModalDialog(
    moderationFile: UserModerationFile,
    onAcknowledge: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ModAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Warning,
                    contentDescription = "Account warning",
                    tint = ModAmber,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Safety Warning (Report #${moderationFile.reportCount})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("strike_warning_dialog")
            ) {
                Text(
                    text = moderationFile.lastWarningMessage.ifBlank {
                        "Your account has received 2 safety reports and has been issued a formal warning. A report note has been entered into your file."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ModAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Escalation Policy:\n• 3rd report: Automatic 1-hour suspension\n• 4th report: Automatic 2-hour suspension\n• 5th report: Suspended until Support or Admin resolves the investigation",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ModAmber,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("acknowledge_strike_warning_button")
            ) {
                Text("I Understand")
            }
        }
    )
}

/**
 * Full-screen Account Suspension gate for Strike 3 (1h), Strike 4 (2h), Strike 5+ (Pending Support/Admin Review),
 * or direct staff timed suspension / global ban.
 */
@Composable
fun AccountSuspendedGateScreen(
    moderationFile: UserModerationFile?,
    manualSuspendedUntilMillis: Long,
    manualSuspensionReason: String,
    globallyBanned: Boolean,
    nowMillis: Long,
    onSignOut: () -> Unit
) {
    val isPendingAdminReview = moderationFile?.pendingAdminReview == true
    val isPendingInvestigation = moderationFile?.suspendedUntilInvestigationResolved == true
    val modTimedActive = moderationFile?.isTimedSuspended(nowMillis) == true
    val manualTimedActive = manualSuspendedUntilMillis > nowMillis

    val headline = when {
        globallyBanned -> "Account Banned"
        isPendingAdminReview -> "Suspended Pending Admin Review"
        isPendingInvestigation -> "Suspended Pending Investigation"
        moderationFile != null && modTimedActive && moderationFile.reportCount == 3 -> "1-Hour Account Suspension"
        moderationFile != null && modTimedActive && moderationFile.reportCount == 4 -> "2-Hour Account Suspension"
        else -> "Account Temporarily Suspended"
    }

    val detailText = when {
        globallyBanned -> "Your VOX account has been globally banned by an Administrator."
        isPendingAdminReview -> moderationFile?.suspensionReason?.ifBlank {
            "Support staff submitted a suspension request for your account. Your account is suspended until further review by an App Administrator."
        } ?: "Your account is suspended until further review by an App Administrator."
        isPendingInvestigation -> moderationFile?.suspensionReason?.ifBlank {
            "Your account received 5 or more safety reports and is suspended until a Support member or Admin completes and resolves the investigation."
        } ?: "Your account is suspended until a Support member or Admin resolves the investigation."
        modTimedActive -> moderationFile?.suspensionReason?.ifBlank {
            "Your account is temporarily suspended due to repeated safety reports."
        } ?: "Your account is temporarily suspended."
        manualTimedActive -> manualSuspensionReason.ifBlank {
            "Your account has been temporarily suspended by VOX Safety staff."
        }
        else -> "Your account is currently suspended."
    }

    val remainingLabel = when {
        globallyBanned -> "Permanent restriction"
        isPendingAdminReview -> "Awaiting App Admin review"
        isPendingInvestigation -> "Awaiting Support or Admin resolution"
        modTimedActive -> moderationFile?.remainingSuspensionLabel(nowMillis).orEmpty()
        manualTimedActive -> {
            val diffMs = (manualSuspendedUntilMillis - nowMillis).coerceAtLeast(0L)
            val totalMinutes = ((diffMs + 59_999L) / 60_000L).coerceAtLeast(1L)
            val hours = totalMinutes / 60L
            val mins = totalMinutes % 60L
            if (hours > 0L) "${hours}h ${mins}m remaining" else "${mins}m remaining"
        }
        else -> ""
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .testTag("account_suspended_gate"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(LiveCoral.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Gavel,
                        contentDescription = "Account suspended",
                        tint = LiveCoral,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = headline,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (remainingLabel.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = LiveCoral.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = remainingLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = LiveCoral,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                if (moderationFile != null && moderationFile.reportCount > 0) {
                    Text(
                        text = "Reports on file: ${moderationFile.reportCount} · Report notes are reviewed by VOX Support & Admins.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("suspended_sign_out_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }
}

/**
 * Staff-only User Files & 5-Strike Investigations panel shown inside SafetyScreen for Support & Admins.
 */
@Composable
fun UserModerationFilesCard(
    moderationFiles: List<UserModerationFile>,
    nowMillis: Long,
    isAdmin: Boolean = true,
    onInspectFile: (UserModerationFile) -> Unit,
    onResolveFile: (UserModerationFile) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_moderation_files_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = "User files and investigations",
                    tint = SnapBlue,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "User Files & Report Investigations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "1st: Investigation · 2nd: Warning · 3rd: 1h · 4th: 2h · 5th: Suspended until resolved",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (moderationFiles.isEmpty()) {
                Text(
                    text = "No user report files have been created yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                moderationFiles.forEach { file ->
                    val statusColor = when (file.statusEnum()) {
                        InvestigationStatus.SUSPENDED_PENDING_ADMIN_REVIEW,
                        InvestigationStatus.SUSPENDED_PENDING_REVIEW -> LiveCoral
                        InvestigationStatus.SUSPENDED_2H, InvestigationStatus.SUSPENDED_1H -> LiveCoral
                        InvestigationStatus.WARNING_ISSUED -> ModAmber
                        InvestigationStatus.INVESTIGATING -> SnapBlue
                        InvestigationStatus.RESOLVED, InvestigationStatus.NONE -> SafetyEmerald
                    }
                    val canResolveThisFile = (file.investigationActive || file.isCurrentlySuspended(nowMillis)) &&
                        (!file.pendingAdminReview || isAdmin)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.targetName.ifBlank { file.userId.take(12) },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Reports: ${file.reportCount} · Warnings: ${file.warningCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = statusColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = file.statusEnum().label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (file.isCurrentlySuspended(nowMillis)) {
                                Text(
                                    text = "Active restriction: ${file.remainingSuspensionLabel(nowMillis)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LiveCoral
                                )
                            } else if (file.lastResolutionNote.isNotBlank()) {
                                Text(
                                    text = "Last resolved by ${file.lastResolvedByName.ifBlank { "Staff" }}: ${file.lastResolutionNote}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SafetyEmerald
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { onInspectFile(file) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("view_user_file_notes_${file.userId}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("File Notes")
                                }
                                if (canResolveThisFile) {
                                    Button(
                                        onClick = { onResolveFile(file) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SafetyEmerald,
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("resolve_investigation_${file.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Resolve")
                                    }
                                } else if (file.pendingAdminReview && !isAdmin) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = LiveCoral.copy(alpha = 0.12f),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = "Awaiting Admin Review",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = LiveCoral,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Staff-only dialog showing the full chronological private file notes for a user.
 */
@Composable
fun UserFileNotesDialog(
    file: UserModerationFile,
    notesFlow: Flow<List<UserFileNote>>,
    isAdmin: Boolean = true,
    onDismiss: () -> Unit,
    onResolveClick: () -> Unit
) {
    val notes by notesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val canResolve = (file.investigationActive || file.isCurrentlySuspended()) &&
        (!file.pendingAdminReview || isAdmin)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${file.targetName.ifBlank { "User" }}'s Moderation File",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${file.reportCount} report(s) · Status: ${file.statusEnum().label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (notes.isEmpty()) {
                Text(
                    text = "No file notes recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .testTag("user_file_notes_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.id }) { note ->
                        val noteColor = when (note.actionEnum()) {
                            FileNoteAction.INVESTIGATION_RESOLVED,
                            FileNoteAction.ADMIN_REVIEW_COMPLETED -> SafetyEmerald
                            FileNoteAction.SUPPORT_SUSPENSION_REQUESTED,
                            FileNoteAction.SUSPENDED_PENDING_REVIEW,
                            FileNoteAction.SUSPENDED_2H,
                            FileNoteAction.SUSPENDED_1H -> LiveCoral
                            FileNoteAction.WARNING_ISSUED -> ModAmber
                            FileNoteAction.INVESTIGATION_STARTED -> SnapBlue
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, noteColor.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = note.actionEnum().label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = noteColor
                                )
                                Text(
                                    text = note.reason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${note.category} · Logged by ${note.reporterName} (${note.actorRole}) · ${formatTimestampShort(note.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (canResolve) {
                Button(
                    onClick = onResolveClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyEmerald,
                        contentColor = Color.White
                    )
                ) {
                    Text("Resolve Investigation")
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("Close")
                }
            }
        },
        dismissButton = {
            if (canResolve) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

/**
 * Staff-only confirmation dialog for Support / Admin to resolve an active investigation and lift suspension.
 */
@Composable
fun ResolveInvestigationDialog(
    file: UserModerationFile,
    onConfirmResolve: (resolutionNote: String, resetReportCount: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var noteDraft by remember { mutableStateOf("") }
    var resetReportCount by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Resolve Investigation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Resolving ${file.targetName.ifBlank { "this user" }}'s investigation will lift any active report suspension and log an official resolution note to their file.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it.take(280) },
                    label = { Text("Resolution Note (required)") },
                    placeholder = { Text("e.g. Reviewed chat logs, issued guidance, cleared account") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("resolve_investigation_note_input")
                )
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { resetReportCount = !resetReportCount }
                ) {
                    Checkbox(
                        checked = resetReportCount,
                        onCheckedChange = { resetReportCount = it },
                        modifier = Modifier.testTag("resolve_investigation_reset_count_checkbox")
                    )
                    Text(
                        text = "Reset report count back to 0 (fresh slate)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = noteDraft.trim()
                    if (clean.isNotEmpty()) {
                        onConfirmResolve(clean, resetReportCount)
                    }
                },
                enabled = noteDraft.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyEmerald,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_resolve_investigation_button")
            ) {
                Text("Resolve & Unsuspend")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for Support Staff to submit a mandatory-reason Suspension Request.
 * Immediately suspends the user until an App Admin reviews the request.
 */
@Composable
fun RequestSuspensionDialog(
    targetName: String,
    onSubmit: (reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var reasonDraft by remember { mutableStateOf("") }
    val trimmed = reasonDraft.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(LiveCoral.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Gavel,
                    contentDescription = "Request suspension",
                    tint = LiveCoral,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Request Suspension for $targetName",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Submitting this request immediately suspends $targetName until further review by an App Administrator. You must explain why so the Admin can review.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = reasonDraft,
                    onValueChange = { reasonDraft = it.take(360) },
                    label = { Text("Why should this user be suspended? (Required)") },
                    placeholder = { Text("Describe the violation, evidence, or incident for Admin review…") },
                    minLines = 3,
                    maxLines = 5,
                    supportingText = {
                        Text("${trimmed.length}/360 characters (minimum 3)")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_suspension_reason_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (trimmed.length >= 3) {
                        onSubmit(trimmed)
                    }
                },
                enabled = trimmed.length >= 3,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveCoral,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_support_suspension_request_button")
            ) {
                Text("Suspend & Send to Admin")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Queue card shown in SafetyScreen for Admins (with Review actions) & Support (status tracker).
 */
@Composable
fun PendingSuspensionRequestsCard(
    requests: List<SuspensionRequest>,
    isAdmin: Boolean,
    onReviewRequest: (SuspensionRequest) -> Unit
) {
    val pendingCount = requests.count { it.statusEnum() == SuspensionRequestStatus.PENDING }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (pendingCount > 0) LiveCoral.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("suspension_requests_queue_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Gavel,
                    contentDescription = "Support suspension requests",
                    tint = LiveCoral,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAdmin) {
                            "Support Suspension Requests ($pendingCount Pending)"
                        } else {
                            "Submitted Suspension Requests ($pendingCount Pending)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isAdmin) {
                            "Users listed as Pending are immediately suspended until your review."
                        } else {
                            "Users stay suspended until an App Administrator completes review."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (requests.isEmpty()) {
                Text(
                    text = "No Support suspension requests have been submitted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                requests.take(20).forEach { req ->
                    val st = req.statusEnum()
                    val statusColor = when (st) {
                        SuspensionRequestStatus.PENDING -> LiveCoral
                        SuspensionRequestStatus.APPROVED_BAN -> LiveCoral
                        SuspensionRequestStatus.APPROVED_TIMED -> ModAmber
                        SuspensionRequestStatus.DISMISSED_LIFTED -> SafetyEmerald
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = req.targetName.ifBlank { req.targetUserId.take(12) },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Requested by ${req.requestedByName.ifBlank { "Support" }} · ${formatTimestampShort(req.createdAt)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = statusColor.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = st.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Reason: ${req.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (st != SuspensionRequestStatus.PENDING && req.adminDecisionNote.isNotBlank()) {
                                Text(
                                    text = "Admin (${req.reviewedByAdminName.ifBlank { "Admin" }}): ${req.adminDecisionNote}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = statusColor
                                )
                            }

                            if (isAdmin && st == SuspensionRequestStatus.PENDING) {
                                Button(
                                    onClick = { onReviewRequest(req) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LiveCoral,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("admin_review_suspension_${req.id}")
                                ) {
                                    Text("Review Suspension Request")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Quick user-picker card for Support Staff inside SafetyScreen to submit a Suspension Request.
 */
@Composable
fun SupportRequestSuspensionCard(
    directoryEntries: List<DirectoryEntry>,
    currentUserId: String,
    onSelectTarget: (DirectoryEntry) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val eligibleUsers = remember(directoryEntries, currentUserId, query) {
        val q = query.trim()
        directoryEntries
            .filter { it.userId != currentUserId && it.tierEnum() != StaffTier.ADMIN }
            .filter { entry ->
                q.isEmpty() ||
                    entry.displayName.contains(q, ignoreCase = true) ||
                    entry.handle.contains(q, ignoreCase = true)
            }
            .take(8)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, usernameColor(StaffTier.SUPPORT).copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_request_suspension_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SupportAgent,
                    contentDescription = "Support suspension request",
                    tint = usernameColor(StaffTier.SUPPORT),
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Request User Suspension",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select a user and submit why. They are suspended immediately until an Admin reviews.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(40) },
                placeholder = { Text("Search user by name or @handle…") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = "Search users")
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("support_suspension_user_search")
            )

            if (eligibleUsers.isEmpty()) {
                Text(
                    text = "No matching users found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                eligibleUsers.forEach { entry ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.displayName.ifBlank { entry.userId.take(10) },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (entry.handle.isNotBlank()) {
                                    Text(
                                        text = "@${entry.handle}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = { onSelectTarget(entry) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LiveCoral,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("support_request_suspend_${entry.userId}")
                            ) {
                                Text("Suspend")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Admin review dialog for a pending Support Suspension Request.
 */
@Composable
fun AdminReviewSuspensionRequestDialog(
    request: SuspensionRequest,
    onSubmitDecision: (decision: SuspensionRequestStatus, timedHours: Int, adminNote: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedDecision by remember { mutableStateOf(SuspensionRequestStatus.DISMISSED_LIFTED) }
    var selectedHours by remember { mutableIntStateOf(24) }
    var noteDraft by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Admin Review · ${request.targetName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = LiveCoral.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Submitted by ${request.requestedByName.ifBlank { "Support Staff" }}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = LiveCoral
                        )
                        Text(
                            text = request.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Text(
                    text = "Select Admin Decision",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                val decisions = listOf(
                    SuspensionRequestStatus.DISMISSED_LIFTED to "Dismiss & Lift Suspension",
                    SuspensionRequestStatus.APPROVED_TIMED to "Convert to Timed Suspension",
                    SuspensionRequestStatus.APPROVED_BAN to "Approve Permanent Ban"
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    decisions.forEach { (status, label) ->
                        val isSelected = selectedDecision == status
                        val accent = when (status) {
                            SuspensionRequestStatus.DISMISSED_LIFTED -> SafetyEmerald
                            SuspensionRequestStatus.APPROVED_TIMED -> ModAmber
                            else -> LiveCoral
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedDecision = status }
                                .testTag("admin_decision_${status.name.lowercase()}")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            )
                        }
                    }
                }

                if (selectedDecision == SuspensionRequestStatus.APPROVED_TIMED) {
                    Text(
                        text = "Suspension Duration",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 6, 24, 72).forEach { hrs ->
                            FilterChip(
                                selected = selectedHours == hrs,
                                onClick = { selectedHours = hrs },
                                label = { Text("${hrs}h") }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it.take(280) },
                    label = { Text("Admin Review Note (Required)") },
                    placeholder = { Text("Explain your decision for the user's moderation file…") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_review_decision_note_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = noteDraft.trim()
                    if (clean.isNotEmpty()) {
                        onSubmitDecision(selectedDecision, selectedHours, clean)
                    }
                },
                enabled = noteDraft.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (selectedDecision) {
                        SuspensionRequestStatus.DISMISSED_LIFTED -> SafetyEmerald
                        SuspensionRequestStatus.APPROVED_TIMED -> ModAmber
                        else -> LiveCoral
                    },
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_admin_review_decision_button")
            ) {
                Text("Save Admin Decision")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 4-Digit Room PIN Entry Dialog shown when joining a PIN-locked chat room.
 */
@Composable
fun EnterRoomPinDialog(
    roomTitle: String,
    expectedPin: String,
    onPinVerified: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(ModAmber.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "Room PIN required",
                    tint = ModAmber,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Enter 4-Digit Room PIN",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "\"$roomTitle\" is locked with a 4-digit PIN by the Room Admin.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { raw ->
                        pinInput = raw.filter { it.isDigit() }.take(4)
                        errorText = null
                    },
                    label = { Text("4-Digit PIN") },
                    placeholder = { Text("••••") },
                    singleLine = true,
                    isError = errorText != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = {
                        if (errorText != null) {
                            Text(errorText!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("${pinInput.length}/4 digits")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("enter_room_pin_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.length != 4) {
                        errorText = "Enter all 4 digits."
                    } else if (pinInput != expectedPin) {
                        errorText = "Incorrect 4-digit PIN. Please try again."
                    } else {
                        onPinVerified(pinInput)
                    }
                },
                enabled = pinInput.length == 4,
                modifier = Modifier.testTag("confirm_enter_room_pin_button")
            ) {
                Text("Unlock & Enter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Dialog for Room Admins & App Admins to enable, update, or remove a 4-digit Room PIN lock.
 */
@Composable
fun ConfigureRoomPinDialog(
    currentlyLocked: Boolean,
    currentPin: String,
    onSave: (pinLocked: Boolean, pinCode: String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinLocked by remember(currentlyLocked) { mutableStateOf(currentlyLocked) }
    var pinInput by remember(currentPin) { mutableStateOf(currentPin) }
    val validPin = pinInput.length == 4 && pinInput.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (pinLocked) ModAmber.copy(alpha = 0.16f)
                        else SafetyEmerald.copy(alpha = 0.16f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (pinLocked) Icons.Rounded.Pin else Icons.Rounded.LockOpen,
                    contentDescription = "Configure Room PIN",
                    tint = if (pinLocked) ModAmber else SafetyEmerald,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = "Room 4-Digit PIN Lock",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Require 4-Digit PIN to Enter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Room Admin, Co-Admins & Staff bypass the PIN automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = pinLocked,
                        onCheckedChange = { enabled ->
                            pinLocked = enabled
                            if (!enabled) pinInput = ""
                        },
                        modifier = Modifier.testTag("configure_room_pin_switch")
                    )
                }

                if (pinLocked) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { raw ->
                            pinInput = raw.filter { it.isDigit() }.take(4)
                        },
                        label = { Text("4-Digit PIN Code") },
                        placeholder = { Text("e.g. 2468") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = {
                            Text(
                                if (validPin) "Valid 4-digit room PIN ready"
                                else "Enter exactly 4 numeric digits (0000–9999)"
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("configure_room_pin_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinLocked) {
                        if (validPin) onSave(true, pinInput)
                    } else {
                        onSave(false, "")
                    }
                },
                enabled = !pinLocked || validPin,
                modifier = Modifier.testTag("save_room_pin_button")
            ) {
                Text(if (pinLocked) "Save 4-Digit PIN" else "Remove PIN Lock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

