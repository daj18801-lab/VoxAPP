package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.TimerOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDmAdminControlsSheet(
    thread: DirectThread,
    currentUserId: String,
    isGroupAdminOrAppAdmin: Boolean,
    isAppStaff: Boolean = false,
    directoryByUid: Map<String, DirectoryEntry> = emptyMap(),
    canManageWorkOrders: Boolean = false,
    onDismiss: () -> Unit,
    onToggleMute: (userId: String, displayName: String, mute: Boolean) -> Unit,
    onBounce24h: (userId: String, displayName: String) -> Unit,
    onRemoveBounce: (userId: String, displayName: String) -> Unit,
    onBanUser: (userId: String, displayName: String) -> Unit,
    onUnbanUser: (userId: String, displayName: String) -> Unit,
    onEditGroupName: (newName: String) -> Unit = {},
    onRemoveMember: (userId: String, displayName: String) -> Unit = { _, _ -> },
    onSetGroupDisabled: (disabled: Boolean, reason: String) -> Unit = { _, _ -> },
    onToggleSupportStaff: (userId: String, displayName: String, promote: Boolean) -> Unit = { _, _, _ -> },
    onToggleSupervisor: (userId: String, displayName: String, makeSupervisor: Boolean) -> Unit = { _, _, _ -> },
    onSendWorkOrderClick: (userId: String, displayName: String) -> Unit = { _, _ -> },
    onTogglePttAllowed: (userId: String, displayName: String, allowed: Boolean) -> Unit = { _, _, _ -> },
    onToggleAnonymousModeDisabled: (disabled: Boolean) -> Unit = {},
    onOpenSendGroupAlert: () -> Unit = {}
) {
    val nowMs = rememberPresenceClockMillis()
    val adminUid = thread.effectiveGroupAdminId()
    val requiresPttSelection = thread.requiresAdminPttSelection()
    val canFullModerate = isGroupAdminOrAppAdmin || isAppStaff
    var groupNameDraft by remember(thread.id, thread.groupName) { mutableStateOf(thread.groupName) }
    var disableReasonDraft by remember(thread.id, thread.groupDisabledReason) {
        mutableStateOf(thread.groupDisabledReason)
    }
    val manageableMembers = thread.participantIds.filter { memberUid ->
        if (memberUid == currentUserId) {
            false
        } else if (isAppStaff) {
            // App Admin & Support Staff can moderate any member in the Group DM
            true
        } else if (memberUid == adminUid) {
            false
        } else if (!isGroupAdminOrAppAdmin && thread.isGroupSupport(memberUid)) {
            // Group DM Support Staff can only moderate regular members, not other Support Staff
            false
        } else {
            true
        }
    }
    val activeBounces = thread.activeGroupBounces(nowMs)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("group_dm_admin_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (canFullModerate) Icons.Rounded.AdminPanelSettings else Icons.Rounded.SupportAgent,
                    contentDescription = if (isAppStaff) "VOX Staff Group Controls" else if (isGroupAdminOrAppAdmin) "Group DM Administrator" else "Group DM Support Staff",
                    tint = if (isAppStaff) LiveCoral else if (isGroupAdminOrAppAdmin) VoiceCyan else SafetyEmerald
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isAppStaff -> "VOX Staff Group DM Controls"
                            isGroupAdminOrAppAdmin -> "Group DM Admin Controls"
                            else -> "Group DM Support Controls"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when {
                            isAppStaff -> "Edit group name, mute, remove or ban members, or disable this Group DM for violations."
                            isGroupAdminOrAppAdmin -> "Mute, bounce (24h), ban, appoint Support Staff, or manage PTT speakers."
                            else -> "Group DM Support Staff can mute or bounce (24h) regular members."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (canFullModerate) {
                // Urgent Group Alert — Sends high-priority phone notification to every member
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = LiveCoral.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Urgent Group Alert",
                                tint = LiveCoral,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Urgent Group Alert (All Phones)",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = LiveCoral
                            )
                        }
                        Text(
                            text = "Send a high-priority urgent alert message that pushes a loud phone notification with vibration to everyone in this Group DM.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                onDismiss()
                                onOpenSendGroupAlert()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LiveCoral,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_dm_admin_send_alert_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Send Urgent Alert to Group", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Disable Anonymous Mode ("Hide Name") Toggle Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (thread.anonymousModeDisabled) ModAmber.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (thread.anonymousModeDisabled) ModAmber else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (thread.anonymousModeDisabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = "Anonymous Mode setting",
                            tint = if (thread.anonymousModeDisabled) ModAmber else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Disable Anonymous Mode",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (thread.anonymousModeDisabled) {
                                    "ON — Members cannot hide their name or send anonymous messages."
                                } else {
                                    "OFF — Members can toggle Hide Name to send anonymous messages."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = thread.anonymousModeDisabled,
                            onCheckedChange = { disableAnon ->
                                onToggleAnonymousModeDisabled(disableAnon)
                            },
                            modifier = Modifier.testTag("group_dm_disable_anonymous_switch")
                        )
                    }
                }
            }

            if (isAppStaff) {
                // Edit Group Name Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, VoiceCyan.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Edit Group DM Name",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = VoiceCyan
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = groupNameDraft,
                                onValueChange = { groupNameDraft = it.take(60) },
                                placeholder = { Text("Group DM name…") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("staff_edit_group_name_input")
                            )
                            Button(
                                onClick = {
                                    val clean = groupNameDraft.trim()
                                    if (clean.isNotEmpty()) {
                                        onEditGroupName(clean)
                                    }
                                },
                                enabled = groupNameDraft.trim().isNotEmpty() && groupNameDraft.trim() != thread.groupName,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                modifier = Modifier.testTag("staff_save_group_name_button")
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }

                // Disable / Re-enable Group DM Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (thread.groupDisabled) LiveCoral.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, if (thread.groupDisabled) LiveCoral else LiveCoral.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (thread.groupDisabled) "Group DM Currently DISABLED" else "Disable Group DM (Violations)",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = LiveCoral
                        )
                        Text(
                            text = if (thread.groupDisabled) {
                                "Members cannot send messages or PTT. Disabled by ${thread.groupDisabledByName.ifBlank { "VOX Staff" }}."
                            } else {
                                "Disabling locks messaging and PTT for all members in this Group DM if policy violations occurred."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!thread.groupDisabled) {
                            OutlinedTextField(
                                value = disableReasonDraft,
                                onValueChange = { disableReasonDraft = it.take(240) },
                                label = { Text("Violation Reason") },
                                placeholder = { Text("e.g., Harassment, spam, or policy violation…") },
                                maxLines = 2,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("staff_disable_group_reason_input")
                            )
                        }
                        Button(
                            onClick = {
                                onSetGroupDisabled(!thread.groupDisabled, disableReasonDraft.trim())
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (thread.groupDisabled) SafetyEmerald else LiveCoral,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("staff_toggle_group_disabled_button")
                        ) {
                            Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (thread.groupDisabled) "Re-enable Group DM" else "Disable Group DM",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (isGroupAdminOrAppAdmin && requiresPttSelection) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("group_dm_ptt_control_banner"),
                    shape = RoundedCornerShape(14.dp),
                    color = VoiceCyan.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, VoiceCyan.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RecordVoiceOver,
                            contentDescription = "PTT Speaker Selection Active",
                            tint = VoiceCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "PTT Speaker Control Active (${thread.participantIds.size} members)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = VoiceCyan
                            )
                            Text(
                                text = "Groups with more than 15 users restrict Push-to-Talk to the Group Admin and selected speakers (${thread.pttAllowedUserIds.size} selected).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "Group Members (${manageableMembers.size})",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            if (manageableMembers.isEmpty()) {
                Text(
                    text = "No other members to moderate in this group DM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                manageableMembers.forEach { memberUid ->
                    val name = thread.participantNamesById[memberUid]?.takeIf { it.isNotBlank() } ?: "Member"
                    val isMuted = thread.isMutedInGroup(memberUid)
                    val isBounced = thread.isBouncedInGroup(memberUid, nowMs)
                    val isBanned = thread.isBannedInGroup(memberUid)
                    val isSupport = thread.isGroupSupport(memberUid)
                    val isSupervisor = thread.isGroupSupervisor(memberUid)
                    val isDnd = directoryByUid[memberUid]?.doNotDisturb == true
                    val isPttSpeaker = thread.pttAllowedUserIds.contains(memberUid)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSupervisor) {
                                    GroupModStatusPill("SUPERVISOR", ModAmber)
                                }
                                if (isDnd) {
                                    GroupModStatusPill("DND", LiveCoral)
                                }
                                if (isSupport) {
                                    GroupModStatusPill("SUPPORT", SafetyEmerald)
                                }
                                if (requiresPttSelection && isPttSpeaker) {
                                    GroupModStatusPill("PTT", VoiceCyan)
                                }
                                if (isBanned) {
                                    GroupModStatusPill("BANNED", LiveCoral)
                                } else if (isBounced) {
                                    GroupModStatusPill("BOUNCED", ModAmber)
                                }
                                if (isMuted) {
                                    GroupModStatusPill("MUTED", VoiceCyan)
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onToggleMute(memberUid, name, !isMuted) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("group_dm_mute_$memberUid"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                        contentDescription = if (isMuted) "Unmute" else "Mute",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isMuted) "Unmute" else "Mute", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = {
                                        if (isBounced) onRemoveBounce(memberUid, name)
                                        else onBounce24h(memberUid, name)
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("group_dm_bounce_$memberUid"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.TimerOff,
                                        contentDescription = "Bounce 24h",
                                        tint = ModAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (isBounced) "Unbounce" else "Bounce", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                if (canFullModerate) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isBanned) onUnbanUser(memberUid, name)
                                            else onBanUser(memberUid, name)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("group_dm_ban_$memberUid"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Block,
                                            contentDescription = if (isBanned) "Unban" else "Ban",
                                            tint = if (isBanned) SafetyEmerald else LiveCoral,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (isBanned) "Unban" else "Ban", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }

                            if (isAppStaff && (thread.companyOwnerId.isBlank() || memberUid != thread.companyOwnerId)) {
                                OutlinedButton(
                                    onClick = { onRemoveMember(memberUid, name) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.6f)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("group_dm_remove_$memberUid"),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PersonRemove,
                                        contentDescription = "Remove $name from Group DM",
                                        tint = LiveCoral,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Remove Member from Group DM", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            if (isGroupAdminOrAppAdmin) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { onToggleSupportStaff(memberUid, name, !isSupport) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSupport) SafetyEmerald else MaterialTheme.colorScheme.outline
                                        ),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSupport) SafetyEmerald.copy(alpha = 0.14f) else Color.Transparent,
                                            contentColor = if (isSupport) SafetyEmerald else MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("group_dm_support_$memberUid"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SupportAgent,
                                            contentDescription = if (isSupport) "Remove Support Staff" else "Make Support Staff",
                                            tint = SafetyEmerald,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = if (isSupport) "Remove Support" else "Make Support",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    if (requiresPttSelection) {
                                        OutlinedButton(
                                            onClick = { onTogglePttAllowed(memberUid, name, !isPttSpeaker) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isPttSpeaker) VoiceCyan else MaterialTheme.colorScheme.outline
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isPttSpeaker) VoiceCyan.copy(alpha = 0.16f) else Color.Transparent,
                                                contentColor = if (isPttSpeaker) VoiceCyan else MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("group_dm_ptt_$memberUid"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.RecordVoiceOver,
                                                contentDescription = if (isPttSpeaker) "Revoke PTT Speaker" else "Allow PTT Speaker",
                                                tint = VoiceCyan,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isPttSpeaker) "PTT Allowed" else "Allow PTT",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            if (isGroupAdminOrAppAdmin || canManageWorkOrders) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isGroupAdminOrAppAdmin) {
                                        OutlinedButton(
                                            onClick = { onToggleSupervisor(memberUid, name, !isSupervisor) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            border = BorderStroke(
                                                1.dp,
                                                if (isSupervisor) ModAmber else MaterialTheme.colorScheme.outline
                                            ),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (isSupervisor) ModAmber.copy(alpha = 0.14f) else Color.Transparent,
                                                contentColor = if (isSupervisor) ModAmber else MaterialTheme.colorScheme.onSurface
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("group_dm_supervisor_$memberUid"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Badge,
                                                contentDescription = if (isSupervisor) "Remove Supervisor" else "Appoint Supervisor",
                                                tint = ModAmber,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isSupervisor) "Remove Supervisor" else "Make Supervisor",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    if (canManageWorkOrders) {
                                        OutlinedButton(
                                            onClick = { onSendWorkOrderClick(memberUid, name) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                                contentColor = MaterialTheme.colorScheme.primary
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("group_dm_work_order_$memberUid"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                                contentDescription = "Send Work Order or Note to $name",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = "Work Order",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (activeBounces.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Active Bounce List (${activeBounces.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ModAmber
                )
                activeBounces.forEach { (bUid, _) ->
                    val name = thread.bouncedGroupDisplayName(bUid)
                    val remaining = thread.remainingGroupBounceLabel(bUid, nowMs)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = ModAmber.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Bounced · $remaining",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { onRemoveBounce(bUid, name) },
                                modifier = Modifier.testTag("remove_group_bounce_$bUid")
                            ) {
                                Text("Remove Bounce")
                            }
                        }
                    }
                }
            }

            if (canFullModerate && thread.bannedUserIds.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Banned From Group DM (${thread.bannedUserIds.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = LiveCoral
                )
                thread.bannedUserIds.forEach { bannedUid ->
                    val name = thread.bannedGroupDisplayName(bannedUid)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = LiveCoral.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { onUnbanUser(bannedUid, name) },
                                modifier = Modifier.testTag("unban_group_user_$bannedUid")
                            ) {
                                Text("Unban")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GroupModStatusPill(
    label: String,
    color: Color
) {
    Surface(
        color = color.copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
