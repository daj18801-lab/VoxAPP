package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ChatMessage
import com.example.data.ChatRoom
import com.example.data.ModActionType
import com.example.data.ModerationLog
import com.example.data.ParticipantRole
import com.example.data.ReportCategory
import com.example.data.RoomParticipant
import com.example.data.RoomStatus
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.ui.components.ConfigureRoomPinDialog
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
    onRemoveBounce: (userId: String, userName: String) -> Unit = { _, _ -> },
    onBanBouncedUser: (userId: String, userName: String) -> Unit = { _, _ -> },
    onUpdateRoomPinLock: (pinLocked: Boolean, pinCode: String) -> Unit = { _, _ -> },
    onCloseRoom: () -> Unit
) {
    val isMod = room.hasModPrivileges(currentUserId, staffCapabilities.tier)
    val canBan = room.canBanInRoom(currentUserId, staffCapabilities.tier)
    val canManageBansAndBounces = room.canManageBansAndBounces(currentUserId, staffCapabilities.tier)
    val canManagePin = room.canManagePinLock(currentUserId, staffCapabilities.tier)
    val canEditOrClose = room.canEditOrCloseRoom(currentUserId, staffCapabilities.tier)
    val activeBounces = remember(room.bouncedUntilByUserId, room.bouncedNamesByUserId) {
        room.activeBounces()
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var newWord by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }

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
                            room.isRoomAdmin(currentUserId) -> "Room Administrator · Manage PIN, Bounces, Bans & Co-Admins"
                            staffCapabilities.isSupport -> "Support Staff · Bounce, Kick, Mute & Request Suspension"
                            room.isCoAdmin(currentUserId) -> "Room Co-Admin · Bounce, Kick & Mute Controls"
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
                text = { Text("Restricted (${room.mutedUserIds.size + activeBounces.size + room.bannedUserIds.size})") },
                modifier = Modifier.testTag("mod_tab_restricted")
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

                    if (canManagePin) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (room.pinLocked) ModAmber.copy(alpha = 0.14f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (room.pinLocked) ModAmber.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (room.pinLocked) Icons.Rounded.Pin else Icons.Rounded.LockOpen,
                                        contentDescription = "Room PIN lock status",
                                        tint = if (room.pinLocked) ModAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column {
                                        Text(
                                            text = if (room.pinLocked) "4-Digit PIN Locked (${room.roomPinCode})" else "4-Digit PIN Lock Off",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (room.pinLocked) ModAmber else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (room.pinLocked) {
                                                "Users must enter PIN ${room.roomPinCode} to enter this room"
                                            } else {
                                                "Protect room entry with a 4-digit PIN code"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showPinDialog = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("mod_sheet_pin_lock_button")
                                ) {
                                    Text(if (room.pinLocked) "Edit PIN" else "Set PIN")
                                }
                            }
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
                    Text(
                        text = "Bounce List — 24h Lockout (${activeBounces.size})",
                        style = MaterialTheme.typography.titleSmall,
                        color = ModAmber
                    )
                    if (activeBounces.isEmpty()) {
                        Text(
                            text = "No users are currently on the 24-hour bounce list.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        activeBounces.forEach { (bouncedUid, bouncedName, _) ->
                            val remaining = room.remainingBounceLabel(bouncedUid)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ModAmber.copy(alpha = 0.10f),
                                border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("bounce_list_row_$bouncedUid")
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = bouncedName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "BOUNCED · $remaining remaining (24h lockout)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ModAmber
                                            )
                                        }
                                    }
                                    if (canManageBansAndBounces || canBan) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            if (canManageBansAndBounces) {
                                                OutlinedButton(
                                                    onClick = { onRemoveBounce(bouncedUid, bouncedName) },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 48.dp)
                                                        .testTag("remove_bounce_$bouncedUid")
                                                ) {
                                                    Text("Remove Bounce")
                                                }
                                            }
                                            if (canBan) {
                                                Button(
                                                    onClick = { onBanBouncedUser(bouncedUid, bouncedName) },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = LiveCoral,
                                                        contentColor = Color.White
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("ban_bounced_user_$bouncedUid")
                                                ) {
                                                    Text("Ban")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
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
                                displayName = room.bannedDisplayName(bannedUid),
                                badge = "PERMANENTLY BANNED",
                                badgeColor = LiveCoral,
                                canLift = canBan,
                                actionLabel = "Unban",
                                onLift = { onUnbanUser(bannedUid) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
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
                                displayName = "User ${mutedUid.take(12)}…",
                                badge = "MUTED",
                                badgeColor = ModAmber,
                                canLift = isMod,
                                actionLabel = "Unmute",
                                onLift = { onUnmuteUser(mutedUid) }
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

    if (showPinDialog && canManagePin) {
        ConfigureRoomPinDialog(
            currentlyLocked = room.pinLocked,
            currentPin = room.roomPinCode,
            onSave = { locked, pin ->
                onUpdateRoomPinLock(locked, pin)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false }
        )
    }
}

@Composable
private fun RestrictedUserRow(
    displayName: String,
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
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor
                )
            }
            if (canLift) {
                OutlinedButton(
                    onClick = onLift,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
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
    onApproveSpeaker: (RoomParticipant) -> Unit,
    onClose: (() -> Unit)? = null
) {
    val isMod = room.hasModPrivileges(currentUserId, staffCapabilities.tier)
    val visibleParticipants = participants.filter { !it.invisible }
    // App Admins see all invisible participants; Support Staff see invisible Room Admins (and themselves);
    // regular users & Co-Admins cannot see invisible Room Admins or invisible Staff.
    val invisibleVisibleToViewer = when {
        staffCapabilities.isAdmin -> participants.filter { it.invisible }
        staffCapabilities.isSupport -> participants.filter {
            it.invisible && (it.userId == room.hostId || it.userId == currentUserId)
        }
        else -> emptyList()
    }

    val onStage = visibleParticipants.filter { it.onStage() }
    val handRaised = visibleParticipants.filter { !it.onStage() && it.handRaised }
    val listeners = visibleParticipants.filter { !it.onStage() && !it.handRaised }

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
                    text = "Users (${visibleParticipants.size})",
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
                            imageVector = Icons.Rounded.WavingHand,
                            contentDescription = null,
                            tint = ModAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Hand",
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
            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("room_user_list_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close user list"
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        if (invisibleVisibleToViewer.isNotEmpty()) {
            val invisibleHeader = if (staffCapabilities.isAdmin) {
                "Invisible Staff & Room Admins (${invisibleVisibleToViewer.size} · Admin View)"
            } else {
                "Invisible Room Admin (${invisibleVisibleToViewer.size} · Support Staff View)"
            }
            Text(
                text = invisibleHeader,
                style = MaterialTheme.typography.labelLarge,
                color = usernameColor(staffCapabilities.tier)
            )
            invisibleVisibleToViewer.forEach { p ->
                ParticipantRosterRow(
                    participant = p,
                    isSelf = p.userId == currentUserId,
                    isMuted = p.mutedByMod || room.mutedUserIds.contains(p.userId),
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
                    isMuted = p.mutedByMod || room.mutedUserIds.contains(p.userId),
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
                isMuted = p.mutedByMod || room.mutedUserIds.contains(p.userId),
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
                    isMuted = p.mutedByMod || room.mutedUserIds.contains(p.userId),
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
    isMuted: Boolean,
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
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeakingAvatarCircle(
                displayName = participant.displayName,
                colorHex = participant.avatarColorHex,
                speakingLevel = participant.speakingLevel.toFloat(),
                micActive = participant.micActive,
                size = 34.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val isLeader = participant.roleEnum() == ParticipantRole.HOST ||
                        participant.roleEnum() == ParticipantRole.MODERATOR
                    Text(
                        text = if (isSelf) "${participant.displayName} (You)" else participant.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = usernameColor(tier, isRoomLeader = isLeader, userId = participant.userId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Little mic icon directly next to name when user is on the microphone
                    if (participant.micActive) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "On microphone",
                            tint = VoiceCyan,
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("roster_mic_icon_${participant.userId}")
                        )
                    }
                    // Little raised hand icon directly next to name when user raises their hand
                    if (participant.handRaised) {
                        Icon(
                            imageVector = Icons.Rounded.WavingHand,
                            contentDescription = "Hand raised",
                            tint = ModAmber,
                            modifier = Modifier
                                .size(16.dp)
                                .testTag("roster_hand_icon_${participant.userId}")
                        )
                    }
                    // Red dot directly next to name when user is muted
                    if (isMuted) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(LiveCoral)
                                .semantics { contentDescription = "Muted" }
                                .testTag("roster_muted_dot_${participant.userId}")
                        )
                    }
                    StaffTierBadge(tier = tier)
                    if (!participant.invisible) {
                        RolePillBadge(role = participant.roleEnum())
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Invisible",
                            tint = usernameColor(tier, isRoomLeader = isLeader, userId = participant.userId),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                val statusLabel = when {
                    participant.invisible && participant.micActive -> "Invisible · Speaking Live (Admin Priority)"
                    participant.invisible -> "Invisible in Room"
                    isMuted -> "Muted"
                    participant.camActive && participant.micActive -> "Live Video & Voice"
                    participant.camActive -> "Live Video"
                    participant.micActive -> "On microphone"
                    participant.handRaised -> "Hand raised"
                    else -> "Listening"
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMuted) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showApprove) {
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = onApprove,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.WavingHand, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Invite", style = MaterialTheme.typography.labelMedium)
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
    onRequestSupportSuspension: () -> Unit = {},
    onSendAlert: () -> Unit,
    onSetStaffTier: (StaffTier) -> Unit,
    onEditUserAdmin: () -> Unit,
    onSendToRoom: (() -> Unit)? = null,
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
                color = usernameColor(targetTier, isRoomLeader = targetIsHost || targetIsCoAdmin, userId = targetUserId)
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
                label = "Bounce from Room (24h Lockout)",
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

        // Staff-only actions: Suspend (Admin) / Request Suspension (Support) & Send Direct Alert
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
                if (staffCapabilities.isAdmin) {
                    SheetActionRow(
                        icon = Icons.Rounded.Timer,
                        label = "Suspend for X Time",
                        tint = ModAmber,
                        onClick = { onDismiss(); onSuspend() }
                    )
                } else if (staffCapabilities.isSupport) {
                    SheetActionRow(
                        icon = Icons.Rounded.Gavel,
                        label = "Request Suspension (Admin Review)",
                        tint = LiveCoral,
                        onClick = { onDismiss(); onRequestSupportSuspension() }
                    )
                }
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
                if (onSendToRoom != null) {
                    SheetActionRow(
                        icon = Icons.Rounded.SwapHoriz,
                        label = "Send User to Different Room",
                        tint = VoiceCyan,
                        onClick = { onDismiss(); onSendToRoom() }
                    )
                }
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

@Composable
fun SendUserToRoomDialog(
    targetName: String,
    availableRooms: List<ChatRoom>,
    currentRoomId: String = "",
    onSendToRoom: (targetRoom: ChatRoom, reason: String) -> Unit,
    onDismiss: () -> Unit
) {
    val destinationRooms = remember(availableRooms, currentRoomId) {
        availableRooms.filter { it.id != currentRoomId && it.statusEnum() == RoomStatus.LIVE }
    }
    var selectedRoom by remember(destinationRooms) { mutableStateOf(destinationRooms.firstOrNull()) }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send $targetName to Another Room") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (destinationRooms.isEmpty()) {
                    Text(
                        text = "No other live rooms are currently available to move $targetName into.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Pick a live destination room. $targetName will be navigated there automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        destinationRooms.forEach { candidate ->
                            val isSelected = selectedRoom?.id == candidate.id
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRoom = candidate }
                                    .testTag("select_transfer_room_${candidate.id}")
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        text = candidate.displayTitle(),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Host: ${candidate.hostName} · ${candidate.participantCount} online",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(160) },
                        label = { Text("Reason (optional)") },
                        placeholder = { Text("e.g., Moving to music lounge") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transfer_room_reason_input")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dest = selectedRoom ?: return@Button
                    onSendToRoom(dest, reason.trim())
                    onDismiss()
                },
                enabled = selectedRoom != null,
                modifier = Modifier.testTag("confirm_send_user_to_room_button")
            ) {
                Text("Send to Room")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

