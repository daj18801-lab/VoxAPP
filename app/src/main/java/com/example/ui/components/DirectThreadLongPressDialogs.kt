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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VoiceOverOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.StaffTier
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue

/**
 * Popup menu and action confirmation dialogs shown when long-pressing a Private DM row
 * in the main Chats list.
 */
@Composable
fun DirectThreadLongPressDialog(
    thread: DirectThread,
    currentUserId: String,
    isAppAdmin: Boolean,
    activePttThreadId: String?,
    onDismiss: () -> Unit,
    onAddUserToGroup: () -> Unit,
    onRemoveUser: () -> Unit,
    onRemoveDm: () -> Unit,
    onConnectPtt: () -> Unit,
    onDisconnectPtt: () -> Unit
) {
    val title = thread.displayTitle(currentUserId)
    val isOfficial = thread.voxOfficial
    val pttEnabledForMe = thread.isPttEnabledFor(currentUserId)
    val pttLiveConnected = activePttThreadId == thread.id
    val canManageMembers = !thread.isGroup || thread.isGroupAdmin(currentUserId) || isAppAdmin

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dm_longpress_options_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (thread.isGroup) {
                        "Group DM · ${thread.participantIds.size} members"
                    } else {
                        "Private DM options"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isOfficial) {
                    LongPressOptionRow(
                        icon = Icons.Rounded.GroupAdd,
                        title = "Add User to Group",
                        subtitle = if (thread.isGroup) {
                            "Add another member to this group DM"
                        } else {
                            "Add someone to turn this DM into a group"
                        },
                        tint = SnapBlue,
                        tag = "dm_longpress_add_user",
                        onClick = onAddUserToGroup
                    )

                    LongPressOptionRow(
                        icon = Icons.Rounded.PersonRemove,
                        title = "Remove User",
                        subtitle = when {
                            !thread.isGroup -> "Remove ${thread.otherName(currentUserId)} from this DM"
                            canManageMembers -> "Pick a member to remove from this group"
                            else -> "Only Group DM Admins can remove members"
                        },
                        tint = ModAmber,
                        enabled = canManageMembers,
                        tag = "dm_longpress_remove_user",
                        onClick = onRemoveUser
                    )
                }

                LongPressOptionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Remove DM",
                    subtitle = if (thread.isGroup && !canManageMembers) {
                        "Leave and remove this group DM from your list"
                    } else {
                        "Delete this conversation from your inbox"
                    },
                    tint = LiveCoral,
                    tag = "dm_longpress_remove_dm",
                    onClick = onRemoveDm
                )

                if (!isOfficial) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    LongPressOptionRow(
                        icon = Icons.Rounded.RecordVoiceOver,
                        title = "Connect PTT",
                        subtitle = when {
                            pttLiveConnected -> "Live Push-to-Talk is currently active"
                            pttEnabledForMe -> "PTT enabled — tap to connect live audio"
                            else -> "Enable and connect Push-to-Talk on this DM"
                        },
                        tint = SafetyEmerald,
                        badge = if (pttLiveConnected) "LIVE" else if (pttEnabledForMe) "ON" else null,
                        tag = "dm_longpress_connect_ptt",
                        onClick = onConnectPtt
                    )

                    LongPressOptionRow(
                        icon = Icons.Rounded.VoiceOverOff,
                        title = "Disconnect PTT",
                        subtitle = if (pttLiveConnected || pttEnabledForMe) {
                            "End live audio stream and turn off PTT"
                        } else {
                            "PTT is currently off for this DM"
                        },
                        tint = LiveCoral,
                        tag = "dm_longpress_disconnect_ptt",
                        onClick = onDisconnectPtt
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dm_longpress_cancel")
            ) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun LongPressOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    tag: String,
    enabled: Boolean = true,
    badge: String? = null,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.45f
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.55f else 0.28f),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f * contentAlpha)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint.copy(alpha = contentAlpha),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badge.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = tint.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = tint,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AddUserToDirectThreadDialog(
    thread: DirectThread,
    currentUserId: String,
    directoryUsers: List<DirectoryEntry>,
    onDismiss: () -> Unit,
    onSelectUser: (DirectoryEntry) -> Unit
) {
    var query by remember(thread.id) { mutableStateOf("") }
    val candidates = remember(directoryUsers, thread.participantIds, query) {
        val q = query.trim()
        directoryUsers.filter { entry ->
            !thread.participantIds.contains(entry.userId) &&
                (q.isEmpty() ||
                    entry.effectiveDisplayName().contains(q, ignoreCase = true) ||
                    entry.displayName.contains(q, ignoreCase = true) ||
                    (entry.tierEnum() != StaffTier.ADMIN && entry.handle.contains(q, ignoreCase = true)))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dm_longpress_add_user_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Add User to Group",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = if (thread.isGroup) {
                        "Pick someone to add to ${thread.displayTitle(currentUserId)}"
                    } else {
                        "Pick someone to start a group DM with ${thread.displayTitle(currentUserId)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    placeholder = { Text("Search people to add…") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dm_longpress_add_user_search")
                )

                if (candidates.isEmpty()) {
                    Text(
                        text = "No matching users available to add.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            candidates,
                            key = { idx, item -> "add_cand_${idx}_${item.userId}" }
                        ) { _, candidate ->
                            val label = candidate.effectiveDisplayName().ifBlank { candidate.displayName }
                            val tier = candidate.tierEnum()
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectUser(candidate) }
                                    .testTag("dm_longpress_add_candidate_${candidate.userId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SpeakingAvatarCircle(
                                        displayName = label,
                                        colorHex = candidate.avatarColorHex,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 38.dp,
                                        photoBase64 = candidate.photoBase64,
                                        userId = candidate.userId
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = usernameColor(tier, userId = candidate.userId),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (tier != StaffTier.ADMIN && candidate.handle.isNotBlank()) {
                                            Text(
                                                text = "@${candidate.handle}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    Icon(
                                        imageVector = Icons.Rounded.GroupAdd,
                                        contentDescription = "Add $label",
                                        tint = SnapBlue,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dm_longpress_add_user_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RemoveUserFromDirectThreadDialog(
    thread: DirectThread,
    currentUserId: String,
    directoryByUid: Map<String, DirectoryEntry>,
    onDismiss: () -> Unit,
    onConfirmRemoveUser: (targetUserId: String, targetName: String) -> Unit
) {
    if (!thread.isGroup) {
        val otherUid = thread.otherUserId(currentUserId)
        val otherName = directoryByUid[otherUid]?.effectiveDisplayName()?.ifBlank { null }
            ?: thread.otherName(currentUserId)
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("dm_longpress_remove_user_dialog"),
            title = { Text("Remove $otherName?") },
            text = {
                Text(
                    "Remove $otherName from this private DM? They will no longer be able to send messages or use PTT in this conversation."
                )
            },
            confirmButton = {
                Button(
                    onClick = { onConfirmRemoveUser(otherUid, otherName) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("dm_longpress_remove_user_confirm")
                ) {
                    Text("Remove User")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dm_longpress_remove_user_cancel")
                ) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    val removableMembers = remember(thread.participantIds, thread.companyOwnerId, currentUserId) {
        thread.participantIds.filter { uid ->
            uid != currentUserId && (thread.companyOwnerId.isBlank() || uid != thread.companyOwnerId)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dm_longpress_remove_user_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Remove Member",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Select a user to remove from ${thread.displayTitle(currentUserId)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            if (removableMembers.isEmpty()) {
                Text(
                    text = "There are no other removable members in this group DM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        removableMembers,
                        key = { idx, uid -> "rm_mem_${idx}_$uid" }
                    ) { _, memberUid ->
                        val entry = directoryByUid[memberUid]
                        val memberName = entry?.effectiveDisplayName()?.ifBlank { null }
                            ?: thread.participantNamesById[memberUid]?.ifBlank { null }
                            ?: "Member"
                        val colorHex = entry?.avatarColorHex?.ifBlank { null } ?: "#0088FF"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onConfirmRemoveUser(memberUid, memberName) }
                                .testTag("dm_longpress_remove_member_$memberUid")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SpeakingAvatarCircle(
                                    displayName = memberName,
                                    colorHex = colorHex,
                                    speakingLevel = 0f,
                                    micActive = false,
                                    size = 38.dp,
                                    photoBase64 = entry?.photoBase64.orEmpty(),
                                    userId = memberUid
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = memberName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (thread.isGroupAdmin(memberUid)) {
                                        Text(
                                            text = "Group Admin",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ModAmber
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Rounded.PersonRemove,
                                    contentDescription = "Remove $memberName",
                                    tint = LiveCoral,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dm_longpress_remove_user_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmRemoveDirectThreadDialog(
    thread: DirectThread,
    currentUserId: String,
    isAppAdmin: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val label = thread.displayTitle(currentUserId)
    val isLeavingGroup = thread.isGroup && !thread.isGroupAdmin(currentUserId) && !isAppAdmin
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dm_longpress_remove_dm_confirm_dialog"),
        title = {
            Text(if (isLeavingGroup) "Leave & Remove Group DM?" else "Remove DM?")
        },
        text = {
            Text(
                if (isLeavingGroup) {
                    "You will leave \"$label\", disconnect any active PTT stream on it, and remove it from your inbox."
                } else {
                    "Remove \"$label\" from your inbox? Any active Push-to-Talk stream on this DM will also be disconnected."
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveCoral,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("dm_longpress_remove_dm_confirm")
            ) {
                Text(if (isLeavingGroup) "Leave & Remove" else "Remove DM")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dm_longpress_remove_dm_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}
