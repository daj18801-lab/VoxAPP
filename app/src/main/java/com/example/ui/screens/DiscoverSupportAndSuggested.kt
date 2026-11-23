package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Reply
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DirectoryEntry
import com.example.data.StaffTier
import com.example.data.SupportTicket
import com.example.data.SupportTicketCategory
import com.example.data.SupportTicketStatus
import com.example.ui.components.OnlinePresenceDot
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
import com.example.ui.theme.SafetyEmerald
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

internal data class SuggestedFriend(
    val entry: DirectoryEntry,
    val mutualNames: List<String>
)

@Composable
internal fun SuggestedFriendCard(
    suggestion: SuggestedFriend,
    isOnline: Boolean,
    onClickProfile: () -> Unit,
    onSendFriendRequest: () -> Unit,
    onMessageUser: () -> Unit
) {
    val entry = suggestion.entry
    val tier = entry.tierEnum()
    val mutualCount = suggestion.mutualNames.size
    val previewNames = suggestion.mutualNames.take(2).joinToString(", ")
    val extraCount = (mutualCount - 2).coerceAtLeast(0)
    val mutualSummary = buildString {
        append(if (mutualCount == 1) "1 mutual friend" else "$mutualCount mutual friends")
        if (previewNames.isNotBlank()) {
            append(" · ")
            append(previewNames)
            if (extraCount > 0) append(" +$extraCount")
        }
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickProfile)
            .testTag("discover_suggested_card_${entry.userId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakingAvatarCircle(
                    displayName = entry.displayName,
                    colorHex = entry.avatarColorHex,
                    speakingLevel = 0f,
                    micActive = false,
                    size = 54.dp,
                    photoBase64 = entry.photoBase64
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OnlinePresenceDot(
                            isOnline = isOnline,
                            modifier = Modifier.testTag("discover_suggested_presence_${entry.userId}")
                        )
                        Text(
                            text = entry.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = usernameColor(tier),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        StaffTierBadge(tier = tier)
                    }
                    if (tier != StaffTier.ADMIN && entry.handle.isNotBlank()) {
                        Text(
                            text = "@${entry.handle}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = mutualSummary,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onClickProfile,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discover_suggested_profile_${entry.userId}")
                ) {
                    Text("View Profile", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = onSendFriendRequest,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discover_suggested_add_${entry.userId}")
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Friend", style = MaterialTheme.typography.labelMedium)
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    IconButton(
                        onClick = onMessageUser,
                        modifier = Modifier.testTag("discover_suggested_message_${entry.userId}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ChatBubble,
                            contentDescription = "Message ${entry.displayName}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SupportStaffMemberCard(
    entry: DirectoryEntry,
    isOnline: Boolean,
    isSelf: Boolean,
    onClickProfile: () -> Unit,
    onMessageStaff: () -> Unit,
    onOpenTicketWithStaff: () -> Unit
) {
    val tier = entry.tierEnum()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (tier == StaffTier.ADMIN) {
                LiveCoral.copy(alpha = 0.35f)
            } else {
                SafetyEmerald.copy(alpha = 0.35f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickProfile)
            .testTag("discover_staff_card_${entry.userId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SpeakingAvatarCircle(
                    displayName = entry.displayName,
                    colorHex = entry.avatarColorHex,
                    speakingLevel = 0f,
                    micActive = false,
                    size = 52.dp,
                    photoBase64 = entry.photoBase64
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OnlinePresenceDot(
                            isOnline = isOnline,
                            modifier = Modifier.testTag("discover_staff_presence_${entry.userId}")
                        )
                        Text(
                            text = entry.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = usernameColor(tier),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        StaffTierBadge(tier = tier)
                    }
                    Text(
                        text = buildString {
                            if (tier != StaffTier.ADMIN && entry.handle.isNotBlank()) {
                                append("@${entry.handle} · ")
                            }
                            append(if (isOnline) "Online now" else "Offline")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOnline) PresenceOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (entry.bio.isNotBlank()) {
                        Text(
                            text = entry.bio,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!isSelf) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onMessageStaff,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("discover_staff_message_${entry.userId}")
                    ) {
                        Icon(Icons.Rounded.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Message", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = onOpenTicketWithStaff,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("discover_staff_ticket_${entry.userId}")
                    ) {
                        Icon(Icons.Rounded.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Ticket", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
internal fun SupportTicketCard(
    ticket: SupportTicket,
    currentUserId: String,
    isStaff: Boolean,
    onReplyTicket: () -> Unit,
    onCloseTicket: () -> Unit
) {
    val status = ticket.statusEnum()
    val category = ticket.categoryEnum()
    val statusColor = when (status) {
        SupportTicketStatus.OPEN -> LiveCoral
        SupportTicketStatus.IN_PROGRESS -> ModAmber
        SupportTicketStatus.RESOLVED -> SafetyEmerald
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_ticket_card_${ticket.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = category.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = status.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = ticket.subject.ifBlank { "Support Request" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = buildString {
                    append("By ${ticket.requesterName.ifBlank { "Member" }}")
                    if (ticket.assignedStaffName.isNotBlank()) {
                        append(" · Assigned to ${ticket.assignedStaffName}")
                    }
                    val dateLabel = formatSupportTicketTime(ticket.createdAt)
                    if (dateLabel.isNotBlank()) {
                        append(" · $dateLabel")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (ticket.details.isNotBlank()) {
                Text(
                    text = ticket.details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (ticket.staffResponse.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SafetyEmerald.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.30f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SupportAgent,
                                contentDescription = null,
                                tint = SafetyEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = if (ticket.assignedStaffName.isNotBlank()) {
                                    "Staff Reply · ${ticket.assignedStaffName}"
                                } else {
                                    "Staff Reply"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = SafetyEmerald
                            )
                        }
                        Text(
                            text = ticket.staffResponse,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            val canCloseMine = ticket.requesterId == currentUserId && status != SupportTicketStatus.RESOLVED
            if (isStaff || canCloseMine) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isStaff) {
                        Button(
                            onClick = onReplyTicket,
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("support_ticket_reply_${ticket.id}")
                        ) {
                            Icon(Icons.Rounded.Reply, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reply / Update", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (canCloseMine) {
                        OutlinedButton(
                            onClick = onCloseTicket,
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("support_ticket_resolve_${ticket.id}")
                        ) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Mark Resolved", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CreateSupportTicketDialog(
    initialStaffMember: DirectoryEntry?,
    onDismiss: () -> Unit,
    onSubmit: (
        category: SupportTicketCategory,
        subject: String,
        details: String,
        assignedStaffId: String,
        assignedStaffName: String
    ) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(SupportTicketCategory.GENERAL) }
    var subject by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ConfirmationNumber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Create Support Ticket")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (initialStaffMember != null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SupportAgent,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Directed to: ${initialStaffMember.displayName}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportTicketCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("support_category_${cat.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it.take(120) },
                    label = { Text("Subject") },
                    placeholder = { Text("Brief summary of your question or issue") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_ticket_subject_input")
                )

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(1500) },
                    label = { Text("Details") },
                    placeholder = { Text("Describe what happened and how we can help…") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_ticket_details_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSubmit(
                        selectedCategory,
                        subject.trim(),
                        details.trim(),
                        initialStaffMember?.userId.orEmpty(),
                        initialStaffMember?.displayName.orEmpty()
                    )
                },
                enabled = subject.trim().length >= 3 && details.trim().length >= 5,
                modifier = Modifier.testTag("support_ticket_submit_button")
            ) {
                Text("Submit Ticket")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RespondSupportTicketDialog(
    ticket: SupportTicket,
    onDismiss: () -> Unit,
    onSubmit: (status: SupportTicketStatus, response: String) -> Unit
) {
    var selectedStatus by remember(ticket.id) {
        mutableStateOf(
            if (ticket.statusEnum() == SupportTicketStatus.OPEN) {
                SupportTicketStatus.IN_PROGRESS
            } else {
                ticket.statusEnum()
            }
        )
    }
    var responseText by remember(ticket.id) { mutableStateOf(ticket.staffResponse) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Respond to Ticket") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = ticket.subject.ifBlank { "Support Request" },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "From ${ticket.requesterName.ifBlank { "Member" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Ticket Status",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportTicketStatus.entries.forEach { st ->
                        FilterChip(
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st },
                            label = { Text(st.label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("support_reply_status_${st.name}")
                        )
                    }
                }
                OutlinedTextField(
                    value = responseText,
                    onValueChange = { responseText = it.take(1500) },
                    label = { Text("Staff Response") },
                    placeholder = { Text("Write your reply to the member…") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_ticket_reply_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedStatus, responseText.trim()) },
                enabled = responseText.trim().isNotEmpty() || selectedStatus != ticket.statusEnum(),
                modifier = Modifier.testTag("support_ticket_reply_save_button")
            ) {
                Text("Save Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatSupportTicketTime(ts: Timestamp?): String {
    if (ts == null) return ""
    return SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(ts.toDate())
}
