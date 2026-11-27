package com.example.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.DirectoryEntry
import com.example.data.ProTier
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue
import com.example.ui.theme.VoiceCyan

private fun DirectoryEntry.isEligibleForProGift(nowMs: Long): Boolean {
    if (proLifetime) return false
    val tier = effectiveProTier(nowMs)
    return tier == ProTier.FREE || tier == ProTier.PRO
}

/**
 * Points Store card allowing users who stack up 15 points (via streaks, referrals,
 * creator earnings, or wallet points) to gift a 1-Month VOX Pro Pass to a friend
 * or favorite live room host.
 */
@Composable
fun GiftedProPassStoreCard(
    currentUserId: String,
    userProfile: UserProfile?,
    directoryEntries: List<DirectoryEntry>,
    acceptedFriendIds: Set<String>,
    liveRoomHostIds: Set<String>,
    isProcessingWallet: Boolean,
    onGiftProPass: (
        targetUserId: String,
        targetName: String,
        giftNote: String,
        onFinished: () -> Unit
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMs = rememberPresenceClockMillis()
    val pointsBalance = userProfile?.pointsBalance ?: 0L
    val costPts = UserProfile.PRO_GIFT_POINTS
    val hasEnoughPoints = pointsBalance >= costPts
    val progress = (pointsBalance.toFloat() / costPts.toFloat()).coerceIn(0f, 1f)
    val earnedTotal = (userProfile?.creatorEarnedPoints ?: 0L) + (userProfile?.referralEarnedPoints ?: 0L)

    var showGiftDialog by remember { mutableStateOf(false) }
    var preselectedEntry by remember { mutableStateOf<DirectoryEntry?>(null) }

    val quickSuggestions = remember(directoryEntries, acceptedFriendIds, liveRoomHostIds, currentUserId, nowMs) {
        directoryEntries
            .asSequence()
            .filter { it.userId.isNotBlank() && it.userId != currentUserId && it.isEligibleForProGift(nowMs) }
            .filter { it.userId in acceptedFriendIds || it.userId in liveRoomHostIds }
            .sortedByDescending { entry ->
                (if (entry.userId in liveRoomHostIds) 2 else 0) + (if (entry.userId in acceptedFriendIds) 1 else 0)
            }
            .take(6)
            .toList()
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (hasEnoughPoints) 1.5.dp else 1.dp,
            color = if (hasEnoughPoints) ModAmber.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("gifted_pro_pass_store_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(ModAmber.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CardGiftcard,
                        contentDescription = "Gifted VOX Pro Passes",
                        tint = ModAmber,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gift a 1-Month VOX Pro Pass",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Points Store · Support a friend or favorite room host",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (hasEnoughPoints) ModAmber else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "$costPts PTS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (hasEnoughPoints) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Stack up $costPts points from streaks, referrals, or creator earnings and gift a full 30-day VOX Pro pass (1-min PTT, 15-member Group DMs, 50-user Private Rooms & Blue Pro badge) to any friend or live room host.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Progress toward 15 pts
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasEnoughPoints) {
                            "Ready to gift! ($pointsBalance pts available)"
                        } else {
                            "${costPts - pointsBalance} more pts needed (${pointsBalance}/$costPts pts)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (hasEnoughPoints) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (earnedTotal > 0L) {
                        Text(
                            text = "$earnedTotal earned pts lifetime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(999.dp)),
                    color = if (hasEnoughPoints) SafetyEmerald else ModAmber,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // Quick-pick Friends & Live Room Hosts
            if (quickSuggestions.isNotEmpty()) {
                Text(
                    text = "Quick Gift to Friends & Live Hosts",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quickSuggestions, key = { it.userId }) { entry ->
                        val isHost = entry.userId in liveRoomHostIds
                        Surface(
                            onClick = {
                                preselectedEntry = entry
                                showGiftDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (isHost) VoiceCyan.copy(alpha = 0.12f) else SnapBlue.copy(alpha = 0.10f),
                            border = BorderStroke(
                                1.dp,
                                if (isHost) VoiceCyan.copy(alpha = 0.45f) else SnapBlue.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("quick_gift_pro_${entry.userId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isHost) Icons.Rounded.Mic else Icons.Rounded.People,
                                    contentDescription = if (isHost) "Live Room Host" else "Friend",
                                    tint = if (isHost) VoiceCyan else SnapBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Column {
                                    Text(
                                        text = entry.effectiveDisplayName(),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isHost) "Live Host" else "Friend",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    preselectedEntry = null
                    showGiftDialog = true
                },
                enabled = !isProcessingWallet,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (hasEnoughPoints) ModAmber else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (hasEnoughPoints) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_gift_pro_pass_dialog_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = "Choose recipient",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = if (hasEnoughPoints) {
                        "Gift 1-Month VOX Pro Pass ($costPts pts)"
                    } else {
                        "Browse Friends & Hosts ($pointsBalance/$costPts pts)"
                    },
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }

    if (showGiftDialog) {
        GiftProPassRecipientDialog(
            currentUserId = currentUserId,
            pointsBalance = pointsBalance,
            directoryEntries = directoryEntries,
            acceptedFriendIds = acceptedFriendIds,
            liveRoomHostIds = liveRoomHostIds,
            initialRecipient = preselectedEntry,
            isProcessingWallet = isProcessingWallet,
            onConfirmGift = { targetUid, targetName, note ->
                onGiftProPass(targetUid, targetName, note) {
                    showGiftDialog = false
                    preselectedEntry = null
                }
            },
            onDismiss = {
                showGiftDialog = false
                preselectedEntry = null
            }
        )
    }
}

@Composable
fun GiftProPassRecipientDialog(
    currentUserId: String,
    pointsBalance: Long,
    directoryEntries: List<DirectoryEntry>,
    acceptedFriendIds: Set<String>,
    liveRoomHostIds: Set<String>,
    initialRecipient: DirectoryEntry? = null,
    isProcessingWallet: Boolean = false,
    onConfirmGift: (targetUserId: String, targetName: String, giftNote: String) -> Unit,
    onDismiss: () -> Unit
) {
    val nowMs = rememberPresenceClockMillis()
    val costPts = UserProfile.PRO_GIFT_POINTS
    val hasEnoughPoints = pointsBalance >= costPts
    var searchQuery by remember { mutableStateOf("") }
    var filterTab by remember { mutableIntStateOf(0) } // 0 = All, 1 = Friends, 2 = Room Hosts
    var selectedRecipient by remember(initialRecipient?.userId) { mutableStateOf(initialRecipient) }
    var giftNote by remember { mutableStateOf("") }

    val filteredEntries = remember(
        directoryEntries,
        acceptedFriendIds,
        liveRoomHostIds,
        searchQuery,
        filterTab,
        currentUserId
    ) {
        val q = searchQuery.trim().lowercase()
        directoryEntries
            .filter { it.userId.isNotBlank() && it.userId != currentUserId }
            .filter { entry ->
                when (filterTab) {
                    1 -> entry.userId in acceptedFriendIds
                    2 -> entry.userId in liveRoomHostIds
                    else -> true
                }
            }
            .filter { entry ->
                q.isEmpty() ||
                    entry.effectiveDisplayName().lowercase().contains(q) ||
                    entry.handle.lowercase().contains(q)
            }
            .sortedByDescending { entry ->
                (if (entry.userId in liveRoomHostIds) 4 else 0) +
                    (if (entry.userId in acceptedFriendIds) 2 else 0) +
                    (if (entry.isEligibleForProGift(nowMs)) 1 else 0)
            }
            .take(40)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CardGiftcard,
                    contentDescription = "Gift VOX Pro Pass",
                    tint = ModAmber
                )
                Text(
                    text = "Gift 1-Month VOX Pro",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Cost: $costPts Points · Balance: $pointsBalance pts. Gives or extends VOX Pro by 30 days!",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasEnoughPoints) SafetyEmerald else ModAmber,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filterTab == 0,
                        onClick = { filterTab = 0 },
                        label = { Text("All") },
                        modifier = Modifier.testTag("gift_pro_filter_all")
                    )
                    FilterChip(
                        selected = filterTab == 1,
                        onClick = { filterTab = 1 },
                        label = { Text("Friends") },
                        modifier = Modifier.testTag("gift_pro_filter_friends")
                    )
                    FilterChip(
                        selected = filterTab == 2,
                        onClick = { filterTab = 2 },
                        label = { Text("Room Hosts") },
                        modifier = Modifier.testTag("gift_pro_filter_hosts")
                    )
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it.take(40) },
                    label = { Text("Search friend or host") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search users"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("gift_pro_search_input")
                )

                if (filteredEntries.isEmpty()) {
                    Text(
                        text = when (filterTab) {
                            1 -> "No friends found matching your search."
                            2 -> "No active live room hosts right now."
                            else -> "No members found matching your search."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredEntries, key = { it.userId }) { entry ->
                            val eligible = entry.isEligibleForProGift(nowMs)
                            val isSelected = selectedRecipient?.userId == entry.userId
                            val isHost = entry.userId in liveRoomHostIds
                            val isFriend = entry.userId in acceptedFriendIds
                            val currentTier = entry.effectiveProTier(nowMs)
                            val tierNote = when {
                                entry.proLifetime -> "Lifetime Pro (ineligible)"
                                currentTier == ProTier.VIP_PRO -> "VIP Pro (ineligible)"
                                currentTier == ProTier.BUSINESS -> "Business (ineligible)"
                                currentTier == ProTier.PRO -> "VOX Pro (+30 days)"
                                else -> "Free → 30d VOX Pro"
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = when {
                                    isSelected -> ModAmber.copy(alpha = 0.16f)
                                    !eligible -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                                },
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) ModAmber else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(enabled = eligible) { selectedRecipient = entry }
                                    .testTag("gift_pro_recipient_${entry.userId}")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = entry.effectiveDisplayName(),
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (eligible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (isHost) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = VoiceCyan.copy(alpha = 0.18f)
                                                ) {
                                                    Text(
                                                        text = "HOST",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = VoiceCyan,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else if (isFriend) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = SnapBlue.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = "FRIEND",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = SnapBlue,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "@${entry.handle} · $tierNote",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Selected recipient",
                                            tint = ModAmber,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedRecipient != null) {
                    OutlinedTextField(
                        value = giftNote,
                        onValueChange = { giftNote = it.take(100) },
                        label = { Text("Appreciation note (optional)") },
                        placeholder = { Text("e.g. Thanks for hosting awesome rooms!") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gift_pro_note_input")
                    )
                }
            }
        },
        confirmButton = {
            val chosen = selectedRecipient
            Button(
                onClick = {
                    if (chosen != null) {
                        onConfirmGift(
                            chosen.userId,
                            chosen.effectiveDisplayName(),
                            giftNote.trim()
                        )
                    }
                },
                enabled = chosen != null && hasEnoughPoints && !isProcessingWallet,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ModAmber,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("confirm_gift_pro_pass_button")
            ) {
                if (isProcessingWallet) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Gift Pro · $costPts pts")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
