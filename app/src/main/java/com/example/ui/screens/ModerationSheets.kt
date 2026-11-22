package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.ModActionType
import com.example.data.ModerationLog
import com.example.data.ParticipantRole
import com.example.data.ReportCategory
import com.example.data.RoomParticipant
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.ui.components.RolePillBadge
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@Composable
fun ModerationCenterSheetContent(
    room: ChatRoom,
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    modLogs: List<ModerationLog>,
    onUpdateSettings: (
        autoModEnabled: Boolean?,
        autoModBlockMode: Boolean?,
        linksBlocked: Boolean?,
        roomLocked: Boolean?,
        stageMicLocked: Boolean?,
        slowModeSeconds: Long?,
        videoEnabled: Boolean?,
        blockedWords: List<String>?,
        actionType: ModActionType,
        reason: String
    ) -> Unit,
    onOpenEditRoom: () -> Unit,
    onUnmuteUser: (String) -> Unit,
    onUnbanUser: (String) -> Unit,
    onCloseRoom: () -> Unit
) {
    val isMod = room.hasModPrivileges(currentUserId, staffCapabilities.tier)
    val canBan = room.canBanInRoom(currentUserId, staffCapabilities.tier)
    val canEditOrClose = room.canEditOrCloseRoom(currentUserId, staffCapabilities.tier)
    var selectedTab by remember { mutableIntStateOf(0) }
    var newWord by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Moderation shield",
                    tint = SafetyEmerald,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Room Safety & Moderation",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = when {
                            staffCapabilities.isAdmin -> "App Owner Administrator · Full Control"
                            room.isRoomAdmin(currentUserId) -> "Room Administrator · Manage Room & Co-Admins"
                            staffCapabilities.isSupport -> "Support Staff · Bounce, Kick, Mute & Suspend"
                            room.isCoAdmin(currentUserId) -> "Room Co-Admin · Kick & Mute Controls"
                            else -> "View-Only Safety Status"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Controls") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Restricted (${room.mutedUserIds.size + room.bannedUserIds.size})") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Audit (${modLogs.size})") }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    if (canEditOrClose) {
                        OutlinedButton(
                            onClick = onOpenEditRoom,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("mod_sheet_edit_room_name")
                        ) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Change or Delete Room Name")
                        }
                    }

                    // Panic / Emergency controls
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = LiveCoral.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Emergency Stage Controls",
                                style = MaterialTheme.typography.titleSmall,
                                color = LiveCoral
                            )
                            ToggleSettingRow(
                                title = "Panic Mute Stage Mics",
                                subtitle = "Immediately silence all non-moderator microphones",
                                checked = room.stageMicLocked,
                                enabled = isMod,
                                onCheckedChange = { locked ->
                                    onUpdateSettings(
                                        null, null, null, null, locked, null, null, null,
                                        ModActionType.STAGE_MUTE,
                                        if (locked) "Locked all non-mod stage microphones" else "Unlocked stage microphones"
                                    )
                                }
                            )
                            ToggleSettingRow(
                                title = "Lock Room Entry",
                                subtitle = "Prevent new users from joining this room",
                                checked = room.roomLocked,
                                enabled = isMod,
                                onCheckedChange = { locked ->
                                    onUpdateSettings(
                                        null, null, null, locked, null, null, null, null,
                                        ModActionType.LOCK_ROOM,
                                        if (locked) "Locked room to new entrants" else "Unlocked room entry"
                                    )
                                }
                            )
                        }
                    }

                    // Chat & Media safety switches
                    ToggleSettingRow(
                        title = "Auto-Mod Profanity & Harassment Filter",
                        subtitle = "Screen messages against built-in + custom blocked terms",
                        checked = room.autoModEnabled,
                        enabled = isMod,
                        onCheckedChange = { enabled ->
                            onUpdateSettings(
                                enabled, null, null, null, null, null, null, null,
                                ModActionType.SETTINGS,
                                if (enabled) "Enabled Auto-Mod filter" else "Disabled Auto-Mod filter"
                            )
                        }
                    )

                    ToggleSettingRow(
                        title = "Strict Block Mode",
                        subtitle = "Block flagged messages outright (off = mask with ***)",
                        checked = room.autoModBlockMode,
                        enabled = isMod && room.autoModEnabled,
                        onCheckedChange = { strict ->
                            onUpdateSettings(
                                null, strict, null, null, null, null, null, null,
                                ModActionType.SETTINGS,
                                if (strict) "Set Auto-Mod to Strict Block" else "Set Auto-Mod to Mask & Flag"
                            )
                        }
                    )

                    ToggleSettingRow(
                        title = "Block External Links",
                        subtitle = "Stop spam & phishing URLs in room chat",
                        checked = room.linksBlocked,
                        enabled = isMod,
                        onCheckedChange = { block ->
                            onUpdateSettings(
                                null, null, block, null, null, null, null, null,
                                ModActionType.SETTINGS,
                                if (block) "Blocked links in chat" else "Allowed links in chat"
                            )
                        }
                    )

                    ToggleSettingRow(
                        title = "Allow Live Video Broadcasting",
                        subtitle = "Toggle CameraX streaming capability for the stage",
                        checked = room.videoEnabled,
                        enabled = isMod,
                        onCheckedChange = { enabled ->
                            onUpdateSettings(
                                null, null, null, null, null, null, enabled, null,
                                ModActionType.SETTINGS,
                                if (enabled) "Enabled stage video streaming" else "Disabled stage video streaming"
                            )
                        }
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Slow Mode Cooldown",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0L to "Off", 3L to "3s", 10L to "10s", 30L to "30s").forEach { (sec, label) ->
                                FilterChip(
                                    selected = room.slowModeSeconds == sec,
                                    enabled = isMod,
                                    onClick = {
                                        onUpdateSettings(
                                            null, null, null, null, null, sec, null, null,
                                            ModActionType.SETTINGS,
                                            if (sec == 0L) "Disabled Slow Mode" else "Set Slow Mode to ${sec}s"
                                        )
                                    },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Custom Blocked Keywords (${room.blockedWords.size}/50)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (isMod) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newWord,
                                    onValueChange = { newWord = it.take(30) },
                                    placeholder = { Text("Add custom blocked term…") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("custom_blocked_word_input")
                                )
                                IconButton(
                                    onClick = {
                                        val clean = newWord.trim().lowercase()
                                        if (clean.length >= 2) {
                                            val updated = (room.blockedWords + clean).distinct().take(50)
                                            onUpdateSettings(
                                                null, null, null, null, null, null, null, updated,
                                                ModActionType.SETTINGS,
                                                "Added blocked keyword '$clean'"
                                            )
                                            newWord = ""
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("add_blocked_word_button")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add blocked word", tint = VoiceCyan)
                                }
                            }
                        }

                        if (room.blockedWords.isEmpty()) {
                            Text(
                                text = "Baseline safety dictionary is active. Add room-specific terms above.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                room.blockedWords.forEach { word ->
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = word, style = MaterialTheme.typography.labelMedium)
                                            if (isMod) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Rounded.Close,
                                                    contentDescription = "Remove $word",
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable {
                                                            val updated = room.blockedWords.filterNot { it == word }
                                                            onUpdateSettings(
                                                                null, null, null, null, null, null, null, updated,
                                                                ModActionType.SETTINGS,
                                                                "Removed blocked keyword '$word'"
                                                            )
                                                        }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (canEditOrClose) {
                        HorizontalDivider()
                        Button(
                            onClick = onCloseRoom,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiveCoral,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("close_room_button")
                        ) {
                            Text("End Live Room for Everyone")
                        }
                    }
                }

                1 -> {
                    Text("Muted Users (${room.mutedUserIds.size})", style = MaterialTheme.typography.titleSmall)
                    if (room.mutedUserIds.isEmpty()) {
                        Text(
                            text = "No users are currently muted.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        room.mutedUserIds.forEach { mutedUid ->
                            RestrictedUserRow(
                                userId = mutedUid,
                                badge = "MUTED",
                                badgeColor = ModAmber,
                                canLift = isMod,
                                actionLabel = "Unmute",
                                onLift = { onUnmuteUser(mutedUid) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Banned Users (${room.bannedUserIds.size})", style = MaterialTheme.typography.titleSmall)
                    if (room.bannedUserIds.isEmpty()) {
                        Text(
                            text = "No users are banned from this room.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        room.bannedUserIds.forEach { bannedUid ->
                            RestrictedUserRow(
                                userId = bannedUid,
                                badge = "BANNED",
                                badgeColor = LiveCoral,
                                canLift = canBan,
                                actionLabel = "Unban",
                                onLift = { onUnbanUser(bannedUid) }
                            )
                        }
                    }
                }

                2 -> {
                    if (modLogs.isEmpty()) {
                        Text(
                            text = "No moderation events recorded yet in this session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        modLogs.forEach { log ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = log.actionTypeEnum().label,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = ModAmber
                                        )
                                        Text(
                                            text = formatTimestampShort(log.createdAt),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = log.reason.ifBlank { "${log.actorName} -> ${log.targetName}" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Actor: ${log.actorName}" + if (log.targetName.isNotBlank()) " · Target: ${log.targetName}" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun RestrictedUserRow(
    userId: String,
    badge: String,
    badgeColor: Color,
    canLift: Boolean,
    actionLabel: String,
    onLift: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "User ${userId.take(12)}…",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor
                )
            }
            if (canLift) {
                OutlinedButton(onClick = onLift) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ParticipantsSheetContent(
    room: ChatRoom,
    participants: List<RoomParticipant>,
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    onSelectParticipant: (RoomParticipant) -> Unit,
    onApproveSpeaker: (RoomParticipant) -> Unit
) {
    val isMod = room.hasModPrivileges(currentUserId, staffCapabilities.tier)
    val visibleParticipants = participants.filter { !it.invisible }
    val invisibleStaff = if (staffCapabilities.isAdmin) participants.filter { it.invisible } else emptyList()

    val onStage = visibleParticipants.filter { it.onStage() }
    val handRaised = visibleParticipants.filter { !it.onStage() && it.handRaised }
    val listeners = visibleParticipants.filter { !it.onStage() && !it.handRaised }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Room Roster (${visibleParticipants.size})",
            style = MaterialTheme.typography.headlineSmall
        )

        if (invisibleStaff.isNotEmpty()) {
            Text(
                text = "Invisible Staff (${invisibleStaff.size} · Admin View Only)",
                style = MaterialTheme.typography.labelLarge,
                color = usernameColor(StaffTier.ADMIN)
            )
            invisibleStaff.forEach { p ->
                ParticipantRosterRow(
                    participant = p,
                    isSelf = p.userId == currentUserId,
                    showApprove = false,
                    onApprove = {},
                    onClick = { onSelectParticipant(p) }
                )
            }
        }

        if (handRaised.isNotEmpty()) {
            Text(
                text = "Raised Hands (${handRaised.size})",
                style = MaterialTheme.typography.labelLarge,
                color = ModAmber
            )
            handRaised.forEach { p ->
                ParticipantRosterRow(
                    participant = p,
                    isSelf = p.userId == currentUserId,
                    showApprove = isMod,
                    onApprove = { onApproveSpeaker(p) },
                    onClick = { onSelectParticipant(p) }
                )
            }
        }

        Text(
            text = "On Stage (${onStage.size})",
            style = MaterialTheme.typography.labelLarge,
            color = VoiceCyan
        )
        onStage.forEach { p ->
            ParticipantRosterRow(
                participant = p,
                isSelf = p.userId == currentUserId,
                showApprove = false,
                onApprove = {},
                onClick = { onSelectParticipant(p) }
            )
        }

        if (listeners.isNotEmpty()) {
            Text(
                text = "Audience (${listeners.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            listeners.forEach { p ->
                ParticipantRosterRow(
                    participant = p,
                    isSelf = p.userId == currentUserId,
                    showApprove = false,
                    onApprove = {},
                    onClick = { onSelectParticipant(p) }
                )
            }
        }
    }
}

@Composable
private fun ParticipantRosterRow(
    participant: RoomParticipant,
    isSelf: Boolean,
    showApprove: Boolean,
    onApprove: () -> Unit,
    onClick: () -> Unit
) {
    val tier = participant.staffTierEnum()
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeakingAvatarCircle(
                displayName = participant.displayName,
                colorHex = participant.avatarColorHex,
                speakingLevel = participant.speakingLevel.toFloat(),
                micActive = participant.micActive,
                size = 42.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (isSelf) "${participant.displayName} (You)" else participant.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = usernameColor(tier)
                    )
                    StaffTierBadge(tier = tier)
                    if (!participant.invisible) {
                        RolePillBadge(role = participant.roleEnum())
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Invisible",
                            tint = usernameColor(tier),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                val statusLabel = when {
                    participant.invisible && participant.micActive -> "Invisible · Speaking Live (Admin Priority)"
                    participant.invisible -> "Invisible in Room"
                    participant.mutedByMod -> "Muted by moderator"
                    participant.camActive && participant.micActive -> "Live Video & Voice"
                    participant.camActive -> "Live Video"
                    participant.micActive -> "Speaking live"
                    participant.handRaised -> "Raised hand to speak"
                    else -> "Listening"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (participant.mutedByMod) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showApprove) {
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.WavingHand, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Invite")
                }
            }
        }
    }
}

@Composable
fun UserModerationActionSheet(
    room: ChatRoom,
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    targetUserId: String,
    targetName: String,
    targetTier: StaffTier,
    targetParticipant: RoomParticipant?,
    selectedMessage: ChatMessage?,
    onMuteToggle: (Boolean) -> Unit,
    onChangeRole: (ParticipantRole) -> Unit,
    onBounce: () -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onSuspend: () -> Unit,
    onSendAlert: () -> Unit,
    onSetStaffTier: (StaffTier) -> Unit,
    onEditUserAdmin: () -> Unit,
    onRedactMessage: () -> Unit,
    onReport: () -> Unit,
    onBlockUser: () -> Unit,
    onDismiss: () -> Unit
) {
    val isSelf = targetUserId == currentUserId
    val isMod = room.hasModPrivileges(currentUserId, staffCapabilities.tier)
    val canBan = room.canBanInRoom(currentUserId, staffCapabilities.tier)
    val canManageCoAdmins = room.canManageCoAdmins(currentUserId, staffCapabilities.tier)
    val targetIsHost = targetUserId == room.hostId
    val targetIsMuted = room.mutedUserIds.contains(targetUserId) || (targetParticipant?.mutedByMod == true)
    val targetIsCoAdmin = room.isCoAdmin(targetUserId)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = targetName,
                style = MaterialTheme.typography.headlineSmall,
                color = usernameColor(targetTier)
            )
            StaffTierBadge(tier = targetTier)
        }
        Text(
            text = when {
                targetTier == StaffTier.ADMIN -> "App Owner Administrator"
                targetTier == StaffTier.SUPPORT -> "Support Staff"
                targetIsHost -> "Room Administrator"
                targetIsCoAdmin -> "Room Co-Admin"
                else -> "Room Member"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (selectedMessage != null && isMod && !selectedMessage.deletedByMod) {
            SheetActionRow(
                icon = Icons.Rounded.Delete,
                label = "Redact Message from Chat",
                tint = ModAmber,
                onClick = { onRedactMessage(); onDismiss() }
            )
        }

        // Mute / Bounce / Kick / Stage controls for Room Admin, Co-Admin, Support & App Admin
        val canModerateTarget = isMod && !isSelf && targetTier != StaffTier.ADMIN && (!targetIsHost || staffCapabilities.isStaff)
        if (canModerateTarget) {
            SheetActionRow(
                icon = if (targetIsMuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.MicOff,
                label = if (targetIsMuted) "Unmute User" else "Mute User (Voice & Chat)",
                tint = ModAmber,
                onClick = { onMuteToggle(!targetIsMuted); onDismiss() }
            )

            if (targetParticipant != null && !targetIsHost) {
                val moveLabel = if (targetParticipant.onStage()) "Move to Audience" else "Invite to Voice Stage"
                val nextRole = if (targetParticipant.onStage()) ParticipantRole.LISTENER else ParticipantRole.SPEAKER
                SheetActionRow(
                    icon = Icons.Rounded.RecordVoiceOver,
                    label = moveLabel,
                    tint = VoiceCyan,
                    onClick = { onChangeRole(nextRole); onDismiss() }
                )
            }

            if (canManageCoAdmins && !targetIsHost) {
                SheetActionRow(
                    icon = Icons.Rounded.VerifiedUser,
                    label = if (targetIsCoAdmin) "Remove Room Co-Admin" else "Add as Room Co-Admin",
                    tint = SafetyEmerald,
                    onClick = {
                        onChangeRole(if (targetIsCoAdmin) ParticipantRole.SPEAKER else ParticipantRole.MODERATOR)
                        onDismiss()
                    }
                )
            }

            SheetActionRow(
                icon = Icons.Rounded.Eject,
                label = "Bounce from Room (Can Rejoin)",
                tint = ModAmber,
                onClick = { onBounce(); onDismiss() }
            )

            if (!targetIsHost || staffCapabilities.isAdmin) {
                SheetActionRow(
                    icon = Icons.Rounded.PersonRemove,
                    label = "Kick from Room",
                    tint = LiveCoral,
                    onClick = { onKick(); onDismiss() }
                )
            }

            if (canBan && (!targetIsHost || staffCapabilities.isAdmin)) {
                SheetActionRow(
                    icon = Icons.Rounded.Gavel,
                    label = "Ban Permanently from Room",
                    tint = LiveCoral,
                    onClick = { onBan(); onDismiss() }
                )
            }
        }

        // Staff-only actions: Suspend for X Time & Send Direct Alert
        if (staffCapabilities.isStaff && !isSelf) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = if (staffCapabilities.isAdmin) "Administrator Controls" else "Support Staff Controls",
                style = MaterialTheme.typography.labelLarge,
                color = usernameColor(staffCapabilities.tier)
            )

            SheetActionRow(
                icon = Icons.Rounded.NotificationsActive,
                label = "Send Live Alert to $targetName",
                tint = VoiceCyan,
                onClick = { onDismiss(); onSendAlert() }
            )

            if (targetTier != StaffTier.ADMIN) {
                SheetActionRow(
                    icon = Icons.Rounded.Timer,
                    label = "Suspend for X Time",
                    tint = ModAmber,
                    onClick = { onDismiss(); onSuspend() }
                )
            }

            if (staffCapabilities.isAdmin) {
                val isCurrentlyAdmin = targetTier == StaffTier.ADMIN
                val isCurrentlySupport = targetTier == StaffTier.SUPPORT
                SheetActionRow(
                    icon = Icons.Rounded.AdminPanelSettings,
                    label = if (isCurrentlyAdmin) "Remove App Admin Role" else "Make App Admin",
                    tint = usernameColor(StaffTier.ADMIN),
                    onClick = {
                        onSetStaffTier(if (isCurrentlyAdmin) StaffTier.USER else StaffTier.ADMIN)
                        onDismiss()
                    }
                )
                SheetActionRow(
                    icon = Icons.Rounded.SupportAgent,
                    label = if (isCurrentlySupport) "Remove Support Staff Role" else "Make Support Staff",
                    tint = usernameColor(StaffTier.SUPPORT),
                    onClick = {
                        onSetStaffTier(if (isCurrentlySupport) StaffTier.USER else StaffTier.SUPPORT)
                        onDismiss()
                    }
                )
                SheetActionRow(
                    icon = Icons.Rounded.Edit,
                    label = "Change User's Name & Restrictions",
                    tint = usernameColor(StaffTier.ADMIN),
                    onClick = { onDismiss(); onEditUserAdmin() }
                )
            }
        }

        if (!isSelf) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            SheetActionRow(
                icon = Icons.Rounded.Flag,
                label = "Report Safety Violation",
                tint = ModAmber,
                onClick = { onDismiss(); onReport() }
            )
            SheetActionRow(
                icon = Icons.Rounded.Block,
                label = "Block User (Hide Their Messages)",
                tint = LiveCoral,
                onClick = { onBlockUser(); onDismiss() }
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun ReportSafetyDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onSubmit: (ReportCategory, String) -> Unit
) {
    var category by remember { mutableStateOf(ReportCategory.HARASSMENT) }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = "Safety report",
                tint = ModAmber
            )
        },
        title = { Text("Report $targetName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select the safety concern and add brief context for moderators:",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ReportCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it.take(400) },
                    label = { Text("Incident Details") },
                    placeholder = { Text("Describe what happened…") },
                    minLines = 3,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_details_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (details.trim().isNotEmpty()) {
                        onSubmit(category, details.trim())
                    }
                },
                enabled = details.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = LiveCoral, contentColor = Color.White),
                modifier = Modifier.testTag("submit_report_button")
            ) {
                Text("Submit Report")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
