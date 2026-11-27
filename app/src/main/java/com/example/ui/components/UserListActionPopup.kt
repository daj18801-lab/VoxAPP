package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.Friendship
import com.example.data.StaffTier
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue
import com.example.ui.theme.VoiceCyan

/**
 * Shared popup menu displayed when long-pressing a username in either the
 * Chat Room User List or the Private / Group DM User List.
 */
@Composable
fun UserListLongPressMenuDialog(
    targetUserId: String,
    targetDisplayName: String,
    targetColorHex: String,
    targetPhotoBase64: String,
    targetTier: StaffTier,
    targetRoleSubtitle: String,
    isTargetRoomLeader: Boolean,
    currentUserId: String,
    friendship: Friendship?,
    onAddFriend: () -> Unit,
    onAcceptFriendRequest: (Friendship) -> Unit,
    onPrivateMessage: () -> Unit,
    onViewProfile: () -> Unit,
    isTargetMuted: Boolean = false,
    isTargetRoomAdmin: Boolean = false,
    modSectionTitle: String = "Admin, Staff & Room Admin Controls",
    banActionLabel: String = "Ban from Room",
    makeRoomAdminLabel: String = "Make Room Admin",
    removeRoomAdminLabel: String = "Remove Room Admin",
    onMuteToggle: ((Boolean) -> Unit)? = null,
    onBounce: (() -> Unit)? = null,
    onBan: (() -> Unit)? = null,
    onToggleRoomAdmin: (() -> Unit)? = null,
    isTargetBlocked: Boolean = false,
    onBlockToggle: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val hasModControls = onMuteToggle != null ||
        onBounce != null ||
        onBan != null ||
        onToggleRoomAdmin != null

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("user_list_long_press_popup")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: avatar + colored username + badges + close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SpeakingAvatarCircle(
                        displayName = targetDisplayName,
                        colorHex = targetColorHex,
                        speakingLevel = 0f,
                        micActive = false,
                        size = 42.dp,
                        photoBase64 = targetPhotoBase64,
                        userId = targetUserId
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = targetDisplayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = usernameColor(
                                    targetTier,
                                    isRoomLeader = isTargetRoomLeader,
                                    userId = targetUserId
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            StaffTierBadge(tier = targetTier)
                        }
                        if (targetRoleSubtitle.isNotBlank()) {
                            Text(
                                text = targetRoleSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("user_list_popup_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close user menu"
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 1. Add Friend (friendship-aware)
                val friendAccepted = friendship?.isAccepted() == true
                val friendIncoming = friendship?.isIncomingFor(currentUserId) == true
                val friendOutgoing = friendship?.isOutgoingFor(currentUserId) == true
                val friendLabel = when {
                    friendAccepted -> "Already Friends"
                    friendIncoming -> "Accept Friend Request"
                    friendOutgoing -> "Friend Request Sent"
                    else -> "Add Friend"
                }
                val friendIcon = when {
                    friendAccepted -> Icons.Rounded.CheckCircle
                    friendOutgoing -> Icons.Rounded.HowToReg
                    else -> Icons.Rounded.PersonAdd
                }
                val friendEnabled = !friendAccepted && !friendOutgoing
                UserListPopupActionRow(
                    icon = friendIcon,
                    label = friendLabel,
                    tint = if (friendEnabled || friendAccepted) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = friendEnabled,
                    testTag = "user_list_popup_add_friend",
                    onClick = {
                        if (friendIncoming && friendship != null) {
                            onAcceptFriendRequest(friendship)
                        } else if (!friendAccepted && !friendOutgoing) {
                            onAddFriend()
                        }
                        onDismiss()
                    }
                )

                // 2. Private Message
                UserListPopupActionRow(
                    icon = Icons.Rounded.Mail,
                    label = "Private Message",
                    tint = VoiceCyan,
                    enabled = true,
                    testTag = "user_list_popup_private_message",
                    onClick = {
                        onDismiss()
                        onPrivateMessage()
                    }
                )

                // 3. View Profile
                UserListPopupActionRow(
                    icon = Icons.Rounded.Person,
                    label = "View Profile",
                    tint = SnapBlue,
                    enabled = true,
                    testTag = "user_list_popup_view_profile",
                    onClick = {
                        onDismiss()
                        onViewProfile()
                    }
                )

                // 4. Admin, Staff & Room Admin Controls
                if (hasModControls) {
                    Spacer(modifier = Modifier.height(2.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Text(
                        text = modSectionTitle,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = ModAmber,
                        modifier = Modifier.padding(top = 2.dp, start = 2.dp)
                    )

                    if (onMuteToggle != null) {
                        UserListPopupActionRow(
                            icon = if (isTargetMuted) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.MicOff,
                            label = if (isTargetMuted) "Unmute User" else "Mute User",
                            tint = ModAmber,
                            enabled = true,
                            testTag = "user_list_popup_mute",
                            onClick = {
                                onMuteToggle(!isTargetMuted)
                                onDismiss()
                            }
                        )
                    }

                    if (onBounce != null) {
                        UserListPopupActionRow(
                            icon = Icons.Rounded.Eject,
                            label = "Bounce (24h Lockout)",
                            tint = ModAmber,
                            enabled = true,
                            testTag = "user_list_popup_bounce",
                            onClick = {
                                onBounce()
                                onDismiss()
                            }
                        )
                    }

                    if (onBan != null) {
                        UserListPopupActionRow(
                            icon = Icons.Rounded.Gavel,
                            label = banActionLabel,
                            tint = LiveCoral,
                            enabled = true,
                            testTag = "user_list_popup_ban",
                            onClick = {
                                onBan()
                                onDismiss()
                            }
                        )
                    }

                    if (onToggleRoomAdmin != null) {
                        UserListPopupActionRow(
                            icon = Icons.Rounded.VerifiedUser,
                            label = if (isTargetRoomAdmin) removeRoomAdminLabel else makeRoomAdminLabel,
                            tint = SafetyEmerald,
                            enabled = true,
                            testTag = "user_list_popup_make_room_admin",
                            onClick = {
                                onToggleRoomAdmin()
                                onDismiss()
                            }
                        )
                    }
                }

                // Optional Block / Unblock for 1-on-1 DM
                if (onBlockToggle != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    UserListPopupActionRow(
                        icon = Icons.Rounded.Block,
                        label = if (isTargetBlocked) "Unblock User" else "Block User",
                        tint = if (isTargetBlocked) SafetyEmerald else LiveCoral,
                        enabled = true,
                        testTag = "user_list_popup_block",
                        onClick = {
                            onDismiss()
                            onBlockToggle()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserListPopupActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = if (enabled) 0.50f else 0.25f
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
