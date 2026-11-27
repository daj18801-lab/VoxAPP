package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.PresenceStatus
import com.example.data.StaffTier
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue
import com.example.ui.theme.VoiceCyan

private data class DmMemberRowModel(
    val uid: String,
    val displayName: String,
    val colorHex: String,
    val photoBase64: String,
    val staffTier: StaffTier,
    val status: PresenceStatus,
    val isSelf: Boolean,
    val isGroupAdmin: Boolean,
    val isSupervisor: Boolean,
    val isSupport: Boolean,
    val isMuted: Boolean,
    val isOnMic: Boolean,
    val isPttEnabled: Boolean
)

/**
 * Right-side slide-over User List drawer for Private & Group DMs,
 * styled to match the Chat Room right-side User List drawer.
 */
@Composable
fun DmUserListDrawerContent(
    thread: DirectThread,
    currentUserId: String,
    myDisplayName: String,
    myColorHex: String,
    myPhotoBase64: String,
    myStaffTier: StaffTier,
    directoryByUid: Map<String, DirectoryEntry>,
    isLocalOnMic: Boolean,
    remoteSpeakingName: String?,
    canManageGroup: Boolean,
    nowMillis: Long,
    onSelectUser: (String) -> Unit,
    onOpenGroupControls: () -> Unit,
    onReportUser: ((uid: String, displayName: String) -> Unit)? = null,
    onClose: () -> Unit
) {
    val viewerIsStaff = myStaffTier.isStaff()
    val members = remember(
        thread,
        currentUserId,
        myDisplayName,
        myColorHex,
        myPhotoBase64,
        myStaffTier,
        directoryByUid,
        isLocalOnMic,
        remoteSpeakingName,
        nowMillis
    ) {
        val ids = thread.participantIds.filter { it.isNotBlank() }.distinct()
        val remoteSpeakerLower = remoteSpeakingName?.trim()?.lowercase().orEmpty()
        ids.map { uid ->
            val isSelf = uid == currentUserId
            val dir = directoryByUid[uid]
            val name = when {
                isSelf -> myDisplayName.ifBlank { thread.participantNamesById[uid].orEmpty().ifBlank { "You" } }
                !dir?.effectiveDisplayName().isNullOrBlank() -> dir!!.effectiveDisplayName()
                !thread.participantNamesById[uid].isNullOrBlank() -> thread.participantNamesById[uid].orEmpty()
                uid == thread.userAId -> thread.userAName.ifBlank { "Member" }
                uid == thread.userBId -> thread.userBName.ifBlank { "Member" }
                else -> "Member"
            }
            val colorHex = when {
                isSelf -> myColorHex
                !dir?.avatarColorHex.isNullOrBlank() -> dir!!.avatarColorHex
                uid == thread.userAId -> thread.userAColorHex
                uid == thread.userBId -> thread.userBColorHex
                else -> "#0088FF"
            }
            val photoBase64 = when {
                isSelf -> myPhotoBase64
                else -> dir?.photoBase64.orEmpty()
            }
            val tier = when {
                isSelf -> myStaffTier
                dir != null -> dir.tierEnum()
                uid == thread.userAId -> StaffTier.fromRaw(thread.userATier)
                uid == thread.userBId -> StaffTier.fromRaw(thread.userBTier)
                else -> StaffTier.USER
            }
            val status = if (isSelf) {
                PresenceStatus.ONLINE
            } else {
                dir?.displayedStatusFor(viewerIsStaff = viewerIsStaff, nowMillis = nowMillis)
                    ?: PresenceStatus.OFFLINE
            }
            val isGroupAdmin = thread.isGroupAdmin(uid)
            val isSupervisor = thread.isGroup && thread.isGroupSupervisor(uid)
            val isSupport = thread.isGroup && thread.isGroupSupport(uid)
            val isMuted = thread.isMutedInGroup(uid)
            val matchesRemoteSpeaker = !isSelf && remoteSpeakerLower.isNotBlank() &&
                name.trim().lowercase() == remoteSpeakerLower
            val isOnMic = when {
                isSelf -> isLocalOnMic
                isGroupAdmin && thread.groupAdminTalking -> true
                matchesRemoteSpeaker -> true
                else -> false
            }
            val isPttEnabled = thread.isPttEnabledFor(uid)
            DmMemberRowModel(
                uid = uid,
                displayName = name,
                colorHex = colorHex,
                photoBase64 = photoBase64,
                staffTier = tier,
                status = status,
                isSelf = isSelf,
                isGroupAdmin = isGroupAdmin,
                isSupervisor = isSupervisor,
                isSupport = isSupport,
                isMuted = isMuted,
                isOnMic = isOnMic,
                isPttEnabled = isPttEnabled
            )
        }.sortedWith(
            compareByDescending<DmMemberRowModel> { it.isOnMic }
                .thenByDescending { it.isGroupAdmin }
                .thenByDescending { it.isSupervisor || it.isSupport }
                .thenByDescending { it.status == PresenceStatus.ONLINE }
                .thenBy { it.displayName.lowercase() }
        )
    }

    val leaders = remember(members, thread.isGroup) {
        if (thread.isGroup) members.filter { it.isGroupAdmin || it.isSupervisor || it.isSupport }
        else emptyList()
    }
    val regularMembers = remember(members, thread.isGroup) {
        if (thread.isGroup) members.filter { !it.isGroupAdmin && !it.isSupervisor && !it.isSupport }
        else members
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (thread.isGroup) "Users (${members.size})" else "Chat (${members.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = VoiceCyan,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "On Mic",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = null,
                            tint = SafetyEmerald,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "PTT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(LiveCoral)
                        )
                        Text(
                            text = "Muted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("dm_user_list_close_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close user list"
                )
            }
        }

        if (canManageGroup && thread.isGroup) {
            OutlinedButton(
                onClick = {
                    onClose()
                    onOpenGroupControls()
                },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ModAmber),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dm_user_list_manage_group_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Group Admin Controls",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (leaders.isNotEmpty()) {
            Text(
                text = "Group Admins & Staff (${leaders.size})",
                style = MaterialTheme.typography.labelLarge,
                color = ModAmber
            )
            leaders.forEach { member ->
                DmUserListRow(
                    member = member,
                    showReportAction = thread.isGroup && !member.isSelf && onReportUser != null,
                    onReport = {
                        onClose()
                        onReportUser?.invoke(member.uid, member.displayName)
                    },
                    onClick = {
                        if (!member.isSelf) {
                            onClose()
                            onSelectUser(member.uid)
                        }
                    }
                )
            }
        }

        if (regularMembers.isNotEmpty()) {
            Text(
                text = if (thread.isGroup) "Members (${regularMembers.size})" else "Participants (${regularMembers.size})",
                style = MaterialTheme.typography.labelLarge,
                color = VoiceCyan
            )
            regularMembers.forEach { member ->
                DmUserListRow(
                    member = member,
                    showReportAction = thread.isGroup && !member.isSelf && onReportUser != null,
                    onReport = {
                        onClose()
                        onReportUser?.invoke(member.uid, member.displayName)
                    },
                    onClick = {
                        if (!member.isSelf) {
                            onClose()
                            onSelectUser(member.uid)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DmUserListRow(
    member: DmMemberRowModel,
    showReportAction: Boolean = false,
    onReport: () -> Unit = {},
    onClick: () -> Unit
) {
    val isLeader = member.isGroupAdmin || member.isSupervisor || member.isSupport
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !member.isSelf, onClick = onClick)
            .testTag("dm_user_list_row_${member.uid}")
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeakingAvatarCircle(
                displayName = member.displayName,
                colorHex = member.colorHex,
                speakingLevel = if (member.isOnMic) 0.85f else 0f,
                micActive = member.isOnMic,
                size = 34.dp,
                photoBase64 = member.photoBase64,
                userId = member.uid
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    PresenceStatusDot(status = member.status)
                    Text(
                        text = if (member.isSelf) "${member.displayName} (You)" else member.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = usernameColor(member.staffTier, isRoomLeader = isLeader, userId = member.uid),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Little mic icon directly next to name when user is on the microphone
                    if (member.isOnMic) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "On microphone",
                            tint = if (member.isGroupAdmin) ModAmber else VoiceCyan,
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("dm_roster_mic_icon_${member.uid}")
                        )
                    }
                    // Red dot directly next to name when user is muted
                    if (member.isMuted) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(LiveCoral)
                                .semantics { contentDescription = "Muted" }
                                .testTag("dm_roster_muted_dot_${member.uid}")
                        )
                    }
                    if (member.isPttEnabled && !member.isMuted) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = "PTT enabled",
                            tint = SafetyEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    StaffTierBadge(tier = member.staffTier)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val roleLabel = when {
                        member.isGroupAdmin -> "Group Admin"
                        member.isSupervisor -> "Supervisor"
                        member.isSupport -> "Group Support"
                        else -> null
                    }
                    if (roleLabel != null) {
                        val badgeColor = when {
                            member.isGroupAdmin -> ModAmber
                            member.isSupervisor -> SnapBlue
                            else -> SafetyEmerald
                        }
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = badgeColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = roleLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                    }
                    val statusLabel = when {
                        member.isOnMic && member.isGroupAdmin -> "Priority Mic Live (Talk-Over)"
                        member.isOnMic -> "On microphone"
                        member.isMuted -> "Muted"
                        member.isPttEnabled && member.status == PresenceStatus.ONLINE -> "PTT Ready · Online"
                        member.isPttEnabled -> "PTT Ready"
                        else -> member.status.label
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            member.isOnMic && member.isGroupAdmin -> ModAmber
                            member.isOnMic -> VoiceCyan
                            member.isMuted -> LiveCoral
                            member.status == PresenceStatus.ONLINE -> PresenceOnlineGreen
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (showReportAction) {
                IconButton(
                    onClick = onReport,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("dm_roster_report_user_${member.uid}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = "Report ${member.displayName}",
                        tint = LiveCoral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DmTopDropdownChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

