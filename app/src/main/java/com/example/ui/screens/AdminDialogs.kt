package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.AlertSeverity
import com.example.data.ChatRoom
import com.example.data.UserAlert
import com.example.data.UserProfile
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@Composable
fun EditRoomNameDialog(
    room: ChatRoom,
    onSave: (newTitle: String, newTopic: String) -> Unit,
    onDeleteName: (currentTopic: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(room.id) { mutableStateOf(room.title) }
    var topic by remember(room.id) { mutableStateOf(room.topic) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Edit room name",
                tint = VoiceCyan
            )
        },
        title = { Text("Change or Delete Room Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Room Name (leave blank or tap Delete Name)") },
                    placeholder = { Text("Untitled Room") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_room_title_input")
                )
                OutlinedTextField(
                    value = topic,
                    onValueChange = { topic = it.take(160) },
                    label = { Text("Room Topic / Rules") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_room_topic_input")
                )
                OutlinedButton(
                    onClick = {
                        onDeleteName(topic.trim())
                        onDismiss()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_room_name_button")
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete Room Name")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(title.trim(), topic.trim())
                    onDismiss()
                },
                modifier = Modifier.testTag("save_room_name_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SendOrEditAlertDialog(
    existingAlert: UserAlert?,
    targetName: String,
    onSave: (title: String, message: String, severity: AlertSeverity, reopenForUser: Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isEditing = existingAlert != null
    var title by remember(existingAlert?.id) { mutableStateOf(existingAlert?.title ?: "") }
    var message by remember(existingAlert?.id) { mutableStateOf(existingAlert?.message ?: "") }
    var severity by remember(existingAlert?.id) {
        mutableStateOf(existingAlert?.severityEnum() ?: AlertSeverity.WARNING)
    }
    var reopenForUser by remember(existingAlert?.id) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = "Alert",
                tint = when (severity) {
                    AlertSeverity.URGENT -> LiveCoral
                    AlertSeverity.WARNING -> ModAmber
                    AlertSeverity.INFO -> VoiceCyan
                }
            )
        },
        title = {
            Text(if (isEditing) "Edit Alert for $targetName" else "Send Alert to $targetName")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Severity", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AlertSeverity.entries.forEach { sev ->
                        FilterChip(
                            selected = severity == sev,
                            onClick = { severity = sev },
                            label = { Text(sev.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(60) },
                    label = { Text("Alert Headline") },
                    placeholder = { Text("Official Moderation Notice") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_title_input")
                )
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it.take(300) },
                    label = { Text("Alert Message") },
                    placeholder = { Text("Please follow community guidelines…") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("alert_message_input")
                )
                if (isEditing) {
                    ToggleSettingRow(
                        title = "Re-notify User",
                        subtitle = "Show this updated alert popup again immediately",
                        checked = reopenForUser,
                        onCheckedChange = { reopenForUser = it }
                    )
                }
                if (isEditing && onDelete != null) {
                    OutlinedButton(
                        onClick = {
                            onDelete()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_alert_button")
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Alert")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().isNotEmpty() && message.trim().isNotEmpty()) {
                        onSave(title.trim(), message.trim(), severity, reopenForUser)
                        onDismiss()
                    }
                },
                enabled = title.trim().isNotEmpty() && message.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (severity == AlertSeverity.URGENT) LiveCoral else MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_alert_button")
            ) {
                Text(if (isEditing) "Update Alert" else "Send Alert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SuspendUserDialog(
    targetName: String,
    onSuspend: (minutes: Long, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutes by remember { mutableLongStateOf(15L) }
    var customMinutesText by remember { mutableStateOf("15") }
    var reason by remember { mutableStateOf("") }
    val presets = listOf(5L to "5m", 15L to "15m", 30L to "30m", 60L to "1h", 360L to "6h", 1440L to "24h")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Timer,
                contentDescription = "Suspend user",
                tint = ModAmber
            )
        },
        title = { Text("Suspend $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Choose how long this member will be suspended from creating or entering rooms:",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { (mins, label) ->
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = {
                                selectedMinutes = mins
                                customMinutesText = mins.toString()
                            },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { raw ->
                        val digits = raw.filter { it.isDigit() }.take(5)
                        customMinutesText = digits
                        digits.toLongOrNull()?.let { selectedMinutes = it }
                    },
                    label = { Text("Duration (minutes, 1–43200)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suspend_minutes_input")
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(200) },
                    label = { Text("Reason") },
                    placeholder = { Text("Disruptive behavior on stage…") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("suspend_reason_input")
                )
            }
        },
        confirmButton = {
            val mins = customMinutesText.toLongOrNull() ?: 0L
            Button(
                onClick = {
                    if (mins in 1L..43_200L) {
                        onSuspend(mins, reason.trim())
                        onDismiss()
                    }
                },
                enabled = mins in 1L..43_200L,
                colors = ButtonDefaults.buttonColors(containerColor = ModAmber, contentColor = Color(0xFF2B1B00)),
                modifier = Modifier.testTag("confirm_suspend_button")
            ) {
                Text("Suspend")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditUserAdminDialog(
    profile: UserProfile,
    onSave: (
        displayName: String,
        handle: String,
        bio: String,
        globallyBanned: Boolean,
        disableCreateRoom: Boolean,
        disableEnterRoom: Boolean,
        disableMic: Boolean,
        disableVideo: Boolean,
        suspendedUntilMillis: Long,
        suspensionReason: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var displayName by remember(profile.userId) { mutableStateOf(profile.displayName) }
    var handle by remember(profile.userId) { mutableStateOf(profile.handle) }
    var bio by remember(profile.userId) { mutableStateOf(profile.bio) }
    var globallyBanned by remember(profile.userId) { mutableStateOf(profile.globallyBanned) }
    var disableCreateRoom by remember(profile.userId) { mutableStateOf(profile.disableCreateRoom) }
    var disableEnterRoom by remember(profile.userId) { mutableStateOf(profile.disableEnterRoom) }
    var disableMic by remember(profile.userId) { mutableStateOf(profile.disableMic) }
    var disableVideo by remember(profile.userId) { mutableStateOf(profile.disableVideo) }
    var suspendedUntilMillis by remember(profile.userId) { mutableLongStateOf(profile.suspendedUntilMillis) }
    var suspensionReason by remember(profile.userId) { mutableStateOf(profile.suspensionReason) }

    val isCurrentlySuspended = suspendedUntilMillis > System.currentTimeMillis()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.ManageAccounts,
                contentDescription = "Edit user",
                tint = LiveCoral
            )
        },
        title = { Text("Admin: Edit User & Restrictions") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it.take(40) },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_edit_user_name")
                )
                OutlinedTextField(
                    value = handle,
                    onValueChange = { handle = it.replace(" ", "_").take(30) },
                    label = { Text("Handle") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_edit_user_handle")
                )
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it.take(200) },
                    label = { Text("Bio") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_edit_user_bio")
                )

                HorizontalDivider()
                Text(
                    text = "Account Restrictions",
                    style = MaterialTheme.typography.titleSmall,
                    color = LiveCoral
                )

                ToggleSettingRow(
                    title = "Global Ban",
                    subtitle = "Block user from creating or entering any room",
                    checked = globallyBanned,
                    onCheckedChange = { globallyBanned = it }
                )
                ToggleSettingRow(
                    title = "Disable Creating Rooms",
                    subtitle = "Prevent user from hosting new rooms",
                    checked = disableCreateRoom,
                    onCheckedChange = { disableCreateRoom = it }
                )
                ToggleSettingRow(
                    title = "Disable Entering Rooms",
                    subtitle = "Prevent user from joining any live room",
                    checked = disableEnterRoom,
                    onCheckedChange = { disableEnterRoom = it }
                )
                ToggleSettingRow(
                    title = "Disable Microphone",
                    subtitle = "Lock user's microphone across all rooms",
                    checked = disableMic,
                    onCheckedChange = { disableMic = it }
                )
                ToggleSettingRow(
                    title = "Disable Video Streaming",
                    subtitle = "Lock user's camera broadcasting across all rooms",
                    checked = disableVideo,
                    onCheckedChange = { disableVideo = it }
                )

                if (isCurrentlySuspended) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ModAmber.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Active Suspension",
                                style = MaterialTheme.typography.labelLarge,
                                color = ModAmber
                            )
                            if (suspensionReason.isNotBlank()) {
                                Text(
                                    text = suspensionReason,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    suspendedUntilMillis = 0L
                                    suspensionReason = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Lift Suspension")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.trim().isNotEmpty() && handle.trim().length >= 2) {
                        onSave(
                            displayName.trim(),
                            handle.trim(),
                            bio.trim(),
                            globallyBanned,
                            disableCreateRoom,
                            disableEnterRoom,
                            disableMic,
                            disableVideo,
                            suspendedUntilMillis,
                            suspensionReason.trim()
                        )
                        onDismiss()
                    }
                },
                enabled = displayName.trim().isNotEmpty() && handle.trim().length >= 2,
                modifier = Modifier.testTag("confirm_edit_user_button")
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun IncomingAlertModalDialog(
    alert: UserAlert,
    onAcknowledge: () -> Unit
) {
    val severity = alert.severityEnum()
    val senderTier = alert.senderTierEnum()
    val accentColor = when (severity) {
        AlertSeverity.URGENT -> LiveCoral
        AlertSeverity.WARNING -> ModAmber
        AlertSeverity.INFO -> VoiceCyan
    }

    AlertDialog(
        onDismissRequest = onAcknowledge,
        icon = {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = "Staff alert",
                tint = accentColor,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${severity.label.uppercase()} ALERT",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = alert.title.ifBlank { "Staff Alert" },
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "From:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = alert.senderName.ifBlank { senderTier.label },
                        style = MaterialTheme.typography.labelLarge,
                        color = usernameColor(senderTier)
                    )
                    StaffTierBadge(tier = senderTier)
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = alert.message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (severity == AlertSeverity.URGENT) LiveCoral else SafetyEmerald,
                    contentColor = if (severity == AlertSeverity.URGENT) Color.White else Color(0xFF00261A)
                ),
                modifier = Modifier.testTag("acknowledge_alert_button")
            ) {
                Text("Acknowledge")
            }
        }
    )
}
