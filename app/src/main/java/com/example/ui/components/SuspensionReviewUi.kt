package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.SupportTicket
import com.example.data.SupportTicketStatus
import com.example.data.UserModerationFile
import com.example.data.UserProfile
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

/**
 * Full-screen blocking suspension popup shown when a suspended user logs in or opens the app.
 * - Details WHY the user was suspended and time remaining.
 * - Allows requesting a review and submitting ONE message (strictly locked after submission).
 * - Displays any Staff/Admin reply prominently whenever the user tries to log in.
 */
@Composable
fun SuspendedAccountLoginPopup(
    suspensionReason: String,
    remainingTimeLabel: String?,
    existingAppeal: SupportTicket?,
    onSubmitReviewMessage: (message: String, onFinished: (Boolean, String) -> Unit) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showReviewComposer by remember { mutableStateOf(false) }
    var draftMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var submittedLocally by remember(existingAppeal?.id) { mutableStateOf(false) }
    var localSubmittedText by remember(existingAppeal?.id) { mutableStateOf("") }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val hasSubmittedAppeal = existingAppeal != null || submittedLocally
    val displayedUserMessage = existingAppeal?.effectiveMessage()?.ifBlank { localSubmittedText }
        ?: localSubmittedText
    val staffReplyText = existingAppeal?.staffResponse?.trim().orEmpty()
    val staffResponder = existingAppeal?.assignedStaffName?.trim()?.ifBlank { "VOX Staff" } ?: "VOX Staff"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(20.dp)
            .testTag("suspended_login_popup"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 460.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.5.dp, LiveCoral.copy(alpha = 0.55f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(LiveCoral.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = "Account Suspended",
                        tint = LiveCoral,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Account Suspended",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                if (!remainingTimeLabel.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ModAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = "Time remaining",
                                tint = ModAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Time Remaining: $remainingTimeLabel",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Reason Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = LiveCoral.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.28f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Reason for Suspension",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = LiveCoral
                        )
                        Text(
                            text = suspensionReason.ifBlank {
                                "Your account has been suspended for violating VOX community guidelines."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Prominent Staff / Admin Reply Banner (shown whenever Staff or Admin replies)
                if (staffReplyText.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suspension_staff_reply_card"),
                        shape = RoundedCornerShape(14.dp),
                        color = VoiceCyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.5.dp, VoiceCyan.copy(alpha = 0.55f))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MarkEmailRead,
                                    contentDescription = "Staff reply",
                                    tint = VoiceCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Reply from $staffResponder",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = staffReplyText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                HorizontalDivider()

                // One-Time Review Request Section
                if (hasSubmittedAppeal) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suspension_appeal_submitted_card"),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Message locked",
                                    tint = SafetyEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Review Message Submitted (1 of 1)",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SafetyEmerald
                                )
                            }
                            if (displayedUserMessage.isNotBlank()) {
                                Text(
                                    text = displayedUserMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = if (staffReplyText.isNotBlank()) {
                                    "Staff has replied above. Additional review messages cannot be sent for this suspension."
                                } else {
                                    "Your review request has been sent to Staff & Admin. You cannot send another message while this review is active."
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (!showReviewComposer) {
                    OutlinedButton(
                        onClick = { showReviewComposer = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("request_suspension_review_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Request review",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Request Review / Send Message")
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Send a Review Message (One-Time Only)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Explain why you believe this suspension should be reviewed. Once submitted, you cannot send another message.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = draftMessage,
                            onValueChange = { draftMessage = it.take(600) },
                            label = { Text("Your message for Staff / Admin") },
                            placeholder = { Text("Enter your explanation here...") },
                            minLines = 3,
                            maxLines = 5,
                            enabled = !isSubmitting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("suspension_review_message_input")
                        )
                        if (!feedbackMessage.isNullOrBlank()) {
                            Text(
                                text = feedbackMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = LiveCoral
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showReviewComposer = false
                                    feedbackMessage = null
                                },
                                enabled = !isSubmitting,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    val clean = draftMessage.trim()
                                    if (clean.length < 4) {
                                        feedbackMessage = "Please write at least 4 characters."
                                        return@Button
                                    }
                                    isSubmitting = true
                                    feedbackMessage = null
                                    onSubmitReviewMessage(clean) { success, msg ->
                                        isSubmitting = false
                                        if (success) {
                                            submittedLocally = true
                                            localSubmittedText = clean
                                            showReviewComposer = false
                                        } else {
                                            feedbackMessage = msg
                                        }
                                    }
                                },
                                enabled = !isSubmitting && draftMessage.trim().length >= 4,
                                modifier = Modifier
                                    .weight(1.3f)
                                    .testTag("submit_suspension_review_button")
                            ) {
                                if (isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Text("Submit Review")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("suspended_sign_out_button")
                ) {
                    Text("Sign Out / Switch Account")
                }
            }
        }
    }
}

/**
 * Staff & Admin panel in SafetyScreen showing:
 * 1. Currently Suspended Users (Admin can one-tap "Remove Suspension").
 * 2. Suspension Review Appeals submitted by suspended users (Staff/Admin can reply; Admin can remove suspension).
 */
@Composable
fun SuspensionAppealsAndActiveCard(
    allUsers: List<UserProfile>,
    moderationFiles: List<UserModerationFile>,
    supportTickets: List<SupportTicket>,
    isAdmin: Boolean,
    nowMillis: Long,
    onReplyToAppeal: (ticketId: String, status: SupportTicketStatus, replyText: String, onUpdated: () -> Unit) -> Unit,
    onAdminRemoveSuspension: (targetUserId: String, targetName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appeals = remember(supportTickets) {
        supportTickets.filter { it.isSuspensionAppeal() }
    }
    val suspendedUsers = remember(allUsers, moderationFiles, nowMillis) {
        val byUid = LinkedHashMap<String, Triple<String, String, String>>() // uid -> (name, reason, timeLabel)
        allUsers.forEach { user ->
            val activeTimed = user.currentlySuspended(nowMillis)
            if (activeTimed || user.globallyBanned) {
                val reason = user.suspensionReason.ifBlank {
                    if (user.globallyBanned) "Globally banned" else "Suspended"
                }
                val timeLabel = if (activeTimed) user.remainingSuspensionLabel(nowMillis) else "Permanent"
                byUid[user.userId] = Triple(user.displayName.ifBlank { "Member" }, reason, timeLabel)
            }
        }
        moderationFiles.forEach { file ->
            if (file.isCurrentlySuspended(nowMillis) && !byUid.containsKey(file.userId)) {
                val timeLabel = if (file.suspendedUntilMillis > nowMillis) {
                    val remMin = ((file.suspendedUntilMillis - nowMillis) / 60_000L).coerceAtLeast(1L)
                    if (remMin >= 1440L) "${remMin / 1440L}d ${(remMin % 1440L) / 60L}h"
                    else if (remMin >= 60L) "${remMin / 60L}h ${remMin % 60L}m"
                    else "${remMin}m"
                } else {
                    "Pending Review"
                }
                byUid[file.userId] = Triple(
                    file.targetName.ifBlank { "Member" },
                    file.suspensionReason.ifBlank { "Suspended via moderation file" },
                    timeLabel
                )
            }
        }
        byUid.entries.toList()
    }

    var replyingAppeal by remember { mutableStateOf<SupportTicket?>(null) }
    var replyDraft by remember { mutableStateOf("") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("suspension_appeals_and_active_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (appeals.any { it.staffResponse.isBlank() } || suspendedUsers.isNotEmpty()) {
                LiveCoral.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Gavel,
                    contentDescription = "Suspensions & Review Appeals",
                    tint = LiveCoral
                )
                Text(
                    text = "Active Suspensions & Review Appeals",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                val badgeTotal = appeals.count { it.staffResponse.isBlank() } + suspendedUsers.size
                if (badgeTotal > 0) {
                    Surface(
                        color = LiveCoral,
                        shape = RoundedCornerShape(999.dp)
                    ) {
                        Text(
                            text = "$badgeTotal",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            if (isAdmin && suspendedUsers.isNotEmpty()) {
                Text(
                    text = "Currently Suspended Accounts (${suspendedUsers.size})",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = LiveCoral
                )
                suspendedUsers.forEach { (uid, info) ->
                    val (name, reason, timeLabel) = info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = LiveCoral.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.30f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = ModAmber.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = timeLabel,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = reason,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = { onAdminRemoveSuspension(uid, name) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyEmerald,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("remove_suspension_btn_$uid")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Remove suspension",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Remove Suspension")
                            }
                        }
                    }
                }
            }

            Text(
                text = "Suspension Review Messages (${appeals.size})",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            if (appeals.isEmpty()) {
                Text(
                    text = "No suspension review messages submitted yet. When a suspended user requests a review on login, it will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                appeals.take(15).forEach { appeal ->
                    val hasReply = appeal.staffResponse.isNotBlank()
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (!hasReply) VoiceCyan.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (!hasReply) VoiceCyan.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = appeal.requesterName.ifBlank { "Suspended Member" },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (hasReply) SafetyEmerald.copy(alpha = 0.16f) else ModAmber.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = if (hasReply) "Replied" else "Needs Reply",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (hasReply) SafetyEmerald else ModAmber,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = appeal.subject,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "\"${appeal.effectiveMessage()}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (hasReply) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = VoiceCyan.copy(alpha = 0.10f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text(
                                            text = "Reply by ${appeal.assignedStaffName.ifBlank { "Staff" }}:",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = VoiceCyan
                                        )
                                        Text(
                                            text = appeal.staffResponse,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        replyingAppeal = appeal
                                        replyDraft = appeal.staffResponse
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("reply_appeal_btn_${appeal.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                                        contentDescription = "Reply",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(if (hasReply) "Edit Reply" else "Reply")
                                }
                                if (isAdmin) {
                                    Button(
                                        onClick = {
                                            onAdminRemoveSuspension(
                                                appeal.requesterId,
                                                appeal.requesterName.ifBlank { "Member" }
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SafetyEmerald,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .weight(1.2f)
                                            .testTag("appeal_remove_suspension_btn_${appeal.id}")
                                    ) {
                                        Text("Remove Suspension")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val activeAppeal = replyingAppeal
    if (activeAppeal != null) {
        AlertDialog(
            onDismissRequest = { replyingAppeal = null },
            title = {
                Text(
                    text = "Reply to ${activeAppeal.requesterName.ifBlank { "Member" }}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "User's Review Message:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = activeAppeal.effectiveMessage(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = replyDraft,
                        onValueChange = { replyDraft = it.take(500) },
                        label = { Text("Staff / Admin Reply (shown on user login)") },
                        placeholder = { Text("Write your response to this user...") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suspension_appeal_reply_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = replyDraft.trim()
                        if (clean.isNotEmpty()) {
                            onReplyToAppeal(
                                activeAppeal.id,
                                SupportTicketStatus.RESOLVED,
                                clean
                            ) {
                                replyingAppeal = null
                            }
                        }
                    },
                    enabled = replyDraft.trim().isNotEmpty(),
                    modifier = Modifier.testTag("confirm_appeal_reply_button")
                ) {
                    Text("Send Reply")
                }
            },
            dismissButton = {
                TextButton(onClick = { replyingAppeal = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
