package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.DmPolicy
import com.example.data.Friendship
import com.example.data.PresenceStatus
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.QuickCreatePostSheet
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TalkStreakBadge
import com.example.ui.components.TopBarSafetyShieldButton
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsHomeScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    staffCapabilities: StaffCapabilities,
    directThreadsState: UiState<List<DirectThread>>,
    friendships: List<Friendship>,
    directoryEntries: List<DirectoryEntry>,
    liveRoomsCount: Int,
    isPublishingPost: Boolean,
    viewerIsAdultVerified: Boolean,
    onOpenThread: (String) -> Unit,
    onStartChatWithUser: (userId: String, name: String, colorHex: String, tier: StaffTier) -> Unit,
    onCreateQuickPost: (
        text: String,
        imageBase64: String,
        locked: Boolean,
        boostToTop: Boolean,
        onInsufficientPoints: () -> Unit,
        onPosted: () -> Unit
    ) -> Unit,
    onRequestAgeVerification: () -> Unit,
    onOpenBuyPoints: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenFriendsTab: () -> Unit,
    onOpenRoomsTab: () -> Unit,
    onOpenProfileTab: () -> Unit,
    safetyBadgeCount: Int = 0,
    onOpenSafety: () -> Unit = {},
    canManageCompany: Boolean = false,
    isCompanySupervisor: Boolean = false,
    pendingWorkOrdersCount: Int = 0,
    onOpenCompanyConsole: () -> Unit = {},
    onOpenWorkHub: () -> Unit = {},
    onToggleDnd: (Boolean) -> Unit = {}
) {
    val isSubAccount = userProfile?.companySubAccount == true
    val isDndOn = userProfile?.doNotDisturb == true
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableIntStateOf(0) }
    var showNewChatSheet by remember { mutableStateOf(false) }
    var showQuickCreateSheet by remember { mutableStateOf(false) }
    val nowMillis = rememberPresenceClockMillis()
    val directoryByUid = remember(directoryEntries) {
        directoryEntries.associateBy { it.userId }
    }
    val directoryPhotoByUid = remember(directoryEntries) {
        directoryEntries.associate { it.userId to it.photoBase64 }
    }

    val acceptedFriends = remember(friendships) { friendships.filter { it.isAccepted() } }
    val acceptedFriendIds = remember(acceptedFriends, currentUserId) {
        acceptedFriends.map { it.otherUserId(currentUserId) }.toSet()
    }
    val incomingRequestCount = remember(friendships, currentUserId) {
        friendships.count { it.isIncomingFor(currentUserId) }
    }

    val rawThreads = (directThreadsState as? UiState.Success)?.data.orEmpty()
    val allThreads = remember(rawThreads, isSubAccount, userProfile?.companyThreadId, userProfile?.parentCompanyOwnerId, currentUserId) {
        if (isSubAccount) {
            val targetId = userProfile?.companyThreadId.orEmpty()
            val ownerId = userProfile?.parentCompanyOwnerId.orEmpty()
            rawThreads
                .filter { thread ->
                    (targetId.isNotBlank() && thread.id == targetId) ||
                        (ownerId.isNotBlank() &&
                            !thread.isGroup &&
                            thread.companyOwnerId == ownerId &&
                            thread.participantIds.contains(currentUserId))
                }
                .sortedByDescending { thread ->
                    if (thread.id == targetId) Long.MAX_VALUE
                    else (thread.updatedAt?.toDate()?.time ?: 0L)
                }
        } else {
            rawThreads
        }
    }
    val unreadCount = remember(allThreads, currentUserId) {
        allThreads.count { it.isUnreadFor(currentUserId) }
    }

    val filteredThreads = remember(allThreads, searchQuery, selectedFilter, acceptedFriendIds, currentUserId, isSubAccount) {
        if (isSubAccount) return@remember allThreads
        val q = searchQuery.trim()
        allThreads.filter { thread ->
            val otherUid = thread.otherUserId(currentUserId)
            val title = thread.displayTitle(currentUserId)
            val membersSummary = thread.memberNamesSummary()
            val matchesQuery = q.isEmpty() ||
                title.contains(q, ignoreCase = true) ||
                membersSummary.contains(q, ignoreCase = true) ||
                thread.lastMessageText.contains(q, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                1 -> thread.isUnreadFor(currentUserId)
                2 -> if (thread.isGroup) {
                    thread.participantIds.any { it != currentUserId && acceptedFriendIds.contains(it) }
                } else {
                    acceptedFriendIds.contains(otherUid)
                }
                else -> true
            }
            matchesQuery && matchesFilter
        }
    }

    val otherDirectoryUsers = remember(directoryEntries, currentUserId) {
        directoryEntries.filter { it.userId != currentUserId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onOpenProfileTab)
                                    .testTag("chats_avatar_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                SpeakingAvatarCircle(
                                    displayName = userProfile?.displayName ?: "Me",
                                    colorHex = userProfile?.avatarColorHex ?: "#0088FF",
                                    speakingLevel = 0f,
                                    micActive = false,
                                    size = 42.dp,
                                    photoBase64 = userProfile?.photoBase64.orEmpty(),
                                    userId = currentUserId
                                )
                                if (staffCapabilities.effectiveCanEnterInvisible && userProfile?.invisibleModePreferred == true) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VisibilityOff,
                                            contentDescription = "Ghost entry active",
                                            tint = androidx.compose.ui.graphics.Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }

                            if (!isSubAccount) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            searchOpen = !searchOpen
                                            if (!searchOpen) searchQuery = ""
                                        },
                                        modifier = Modifier.testTag("chats_search_toggle")
                                    ) {
                                        Icon(
                                            imageVector = if (searchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                                            contentDescription = "Search chats",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = if (isSubAccount) {
                                    userProfile?.companyName?.ifBlank { "Company DM" } ?: "Company DM"
                                } else {
                                    "Chat"
                                },
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!isSubAccount) {
                                StaffTierBadge(tier = staffCapabilities.tier)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isSubAccount) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (isDndOn) LiveCoral.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { onToggleDnd(!isDndOn) }
                                        .testTag("sub_account_chats_dnd_toggle")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isDndOn) Icons.Rounded.NotificationsOff else Icons.Rounded.NotificationsActive,
                                            contentDescription = if (isDndOn) "Do Not Disturb On" else "Do Not Disturb Off",
                                            tint = if (isDndOn) LiveCoral else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (isDndOn) "DND ON" else "DND",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isDndOn) LiveCoral else MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            } else {
                                Box {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        IconButton(
                                            onClick = onOpenFriendsTab,
                                            modifier = Modifier.testTag("chats_add_friend_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PersonAdd,
                                                contentDescription = "Friends and requests",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    if (incomingRequestCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$incomingRequestCount",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = androidx.compose.ui.graphics.Color.White
                                            )
                                        }
                                    }
                                }
                                TopBarSafetyShieldButton(
                                    badgeCount = safetyBadgeCount,
                                    onClick = onOpenSafety
                                )
                            }
                        }
                    }

                    if (!isSubAccount && searchOpen) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it.take(40) },
                            placeholder = { Text("Search private chats…") },
                            leadingIcon = {
                                Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(999.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(bottom = 10.dp)
                                .testTag("chats_search_input")
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                if (isSubAccount) {
                    item(key = "sub_account_company_banner") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Business,
                                        contentDescription = "Company workspace",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Company Sub-Account · 60s PTT Enabled",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isDndOn) {
                                                "Do Not Disturb is ON — URGENT work alerts still come through."
                                            } else {
                                                "Scoped to your Company Group DM, Work Orders, and Team Roster."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isDndOn) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = onOpenWorkHub,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("sub_account_open_work_hub_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Assignment,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (pendingWorkOrdersCount > 0) "Orders ($pendingWorkOrdersCount)" else "Work Orders",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = onOpenFriendsTab,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("sub_account_message_teammate_button")
                                    ) {
                                        Text(
                                            text = "1-on-1 DM",
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (canManageCompany || isCompanySupervisor) {
                    item(key = "company_console_entry_card") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onOpenCompanyConsole)
                                .testTag("chats_company_console_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Business,
                                        contentDescription = "Company Team Console",
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (canManageCompany) "Company Team & Sub-Accounts" else "Supervisor Work Orders & Requests",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (canManageCompany) {
                                            "Create employee sub-names, appoint supervisors, and dispatch work orders."
                                        } else {
                                            "Send work orders to team members and review employee requests."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                                OutlinedButton(
                                    onClick = onOpenCompanyConsole,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Open")
                                }
                            }
                        }
                    }
                }

                if (!isSubAccount) {
                    item(key = "quick_friends_strip") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    item(key = "add_friend_circle") {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier
                                                .width(66.dp)
                                                .clickable(onClick = onOpenFriendsTab)
                                                .testTag("quick_strip_add_friend")
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PersonAdd,
                                                    contentDescription = "Add Friends",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(5.dp))
                                            Text(
                                                text = "Add Friend",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    itemsIndexed(
                                        acceptedFriends,
                                        key = { idx, friendship -> "friend_circle_${idx}_${friendship.id}" }
                                    ) { _, friendship ->
                                        val otherId = friendship.otherUserId(currentUserId)
                                        val otherName = friendship.otherName(currentUserId)
                                        val otherColor = friendship.otherColorHex(currentUserId)
                                        val otherTier = friendship.otherTier(currentUserId)
                                        QuickFriendCircleItem(
                                            userId = otherId,
                                            name = otherName,
                                            colorHex = otherColor,
                                            photoBase64 = directoryPhotoByUid[otherId].orEmpty(),
                                            tier = otherTier,
                                            presenceStatus = directoryByUid[otherId]?.displayedStatusFor(
                                                viewerIsStaff = staffCapabilities.isStaff,
                                                nowMillis = nowMillis
                                            ) ?: PresenceStatus.OFFLINE,
                                            onClick = {
                                                onStartChatWithUser(otherId, otherName, otherColor, otherTier)
                                            }
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val tabs = listOf(
                                        "All" to allThreads.size,
                                        "Unread" to unreadCount,
                                        "Friends" to acceptedFriends.size
                                    )
                                    tabs.forEachIndexed { idx, (label, count) ->
                                        FilterChip(
                                            selected = selectedFilter == idx,
                                            onClick = { selectedFilter = idx },
                                            label = {
                                                Text(if (count > 0) "$label ($count)" else label)
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = androidx.compose.ui.graphics.Color.White
                                            ),
                                            modifier = Modifier.testTag("chats_filter_$idx")
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                when (directThreadsState) {
                    is UiState.Loading -> {
                        item(key = "chats_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(key = "chats_error") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = directThreadsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (filteredThreads.isEmpty()) {
                            item(key = "chats_empty") {
                                Surface(
                                    shape = RoundedCornerShape(22.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ChatBubble,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Text(
                                            text = stringResource(R.string.empty_chats_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = stringResource(R.string.empty_chats_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = { showNewChatSheet = true },
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier.testTag("empty_new_chat_button")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("New Chat")
                                        }
                                    }
                                }
                            }
                        } else {
                            val priorityThreads = filteredThreads.filter { it.hasActivePriorityChirpFor(currentUserId) }
                            val regularThreads = filteredThreads.filterNot { it.hasActivePriorityChirpFor(currentUserId) }

                            if (priorityThreads.isNotEmpty()) {
                                itemsIndexed(
                                    priorityThreads,
                                    key = { idx, thread -> "priority_chirp_${idx}_${thread.id}" }
                                ) { _, thread ->
                                    com.example.ui.components.PriorityChirpPinnedInboxCard(
                                        thread = thread,
                                        onOpenThread = { onOpenThread(thread.id) }
                                    )
                                }
                            }

                            itemsIndexed(
                                regularThreads,
                                key = { idx, thread -> "regular_thread_${idx}_${thread.id}" }
                            ) { _, thread ->
                                val otherUid = thread.otherUserId(currentUserId)
                                SnapChatFeedRow(
                                    thread = thread,
                                    currentUserId = currentUserId,
                                    otherPhotoBase64 = if (thread.isGroup) "" else directoryPhotoByUid[otherUid].orEmpty(),
                                    otherStatus = if (thread.isGroup) {
                                        PresenceStatus.OFFLINE
                                    } else {
                                        directoryByUid[otherUid]?.displayedStatusFor(
                                            viewerIsStaff = staffCapabilities.isStaff,
                                            nowMillis = nowMillis
                                        ) ?: PresenceStatus.OFFLINE
                                    },
                                    onClick = { onOpenThread(thread.id) },
                                    onAvatarClick = {
                                        if (thread.isGroup) onOpenThread(thread.id)
                                        else onOpenUserProfile(otherUid)
                                    }
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    modifier = Modifier.padding(start = 76.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!isSubAccount) {
            FloatingActionButton(
                onClick = { showQuickCreateSheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = androidx.compose.ui.graphics.Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .size(62.dp)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                    .testTag("chats_quick_create_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Quick post, snap, or picture to profile",
                    modifier = Modifier.size(30.dp)
                )
            }

            FloatingActionButton(
                onClick = { showNewChatSheet = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 18.dp)
                    .size(54.dp)
                    .testTag("fab_new_private_chat")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChatBubble,
                    contentDescription = "Start new private chat",
                    modifier = Modifier.size(23.dp)
                )
            }
        }
    }

    if (showNewChatSheet) {
        ModalBottomSheet(
            onDismissRequest = { showNewChatSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            NewPrivateChatSheetContent(
                friends = acceptedFriends,
                directoryUsers = otherDirectoryUsers,
                directoryByUid = directoryByUid,
                currentUserId = currentUserId,
                viewerIsStaff = staffCapabilities.isStaff,
                nowMillis = nowMillis,
                onSelectUser = { uid, name, color, tier ->
                    showNewChatSheet = false
                    onStartChatWithUser(uid, name, color, tier)
                }
            )
        }
    }

    if (showQuickCreateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showQuickCreateSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QuickCreatePostSheet(
                isPublishingPost = isPublishingPost,
                viewerIsAdultVerified = viewerIsAdultVerified,
                onRequestAgeVerification = onRequestAgeVerification,
                onCreatePost = onCreateQuickPost,
                onInsufficientPointsForBoost = onOpenBuyPoints,
                onDismiss = { showQuickCreateSheet = false }
            )
        }
    }
}

@Composable
private fun QuickFriendCircleItem(
    userId: String,
    name: String,
    colorHex: String,
    photoBase64: String,
    tier: StaffTier,
    presenceStatus: PresenceStatus,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(2.5.dp, SnapBlue, CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.Center
        ) {
            SpeakingAvatarCircle(
                displayName = name,
                colorHex = colorHex,
                speakingLevel = 0f,
                micActive = false,
                size = 50.dp,
                photoBase64 = photoBase64,
                userId = userId
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PresenceStatusDot(
                status = presenceStatus,
                size = 8.dp
            )
            Text(
                text = name.substringBefore(" "),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = usernameColor(tier, userId = userId),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SnapChatFeedRow(
    thread: DirectThread,
    currentUserId: String,
    otherPhotoBase64: String,
    otherStatus: PresenceStatus,
    onClick: () -> Unit,
    onAvatarClick: () -> Unit
) {
    val isGroup = thread.isGroup
    val isVoxOfficial = thread.voxOfficial
    val title = thread.displayTitle(currentUserId)
    val otherColorHex = thread.otherColorHex(currentUserId)
    val otherTier = if (isGroup) StaffTier.USER else thread.otherTier(currentUserId)
    val isUnread = thread.isUnreadFor(currentUserId)
    val lastSentByMe = thread.lastMessageSenderId == currentUserId

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_thread_row_${thread.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isVoxOfficial) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(SnapBlue)
                        .border(
                            width = if (isUnread) 2.dp else 1.dp,
                            color = if (isUnread) ModAmber else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = "Official VOX Message",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else if (isGroup) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(
                            width = if (isUnread) 2.dp else 1.dp,
                            color = if (isUnread) SnapBlue else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            shape = CircleShape
                        )
                        .clickable(onClick = onAvatarClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Groups,
                        contentDescription = "Group chat",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            } else {
                val otherUidAvatar = thread.otherUserId(currentUserId)
                SpeakingAvatarCircle(
                    displayName = title,
                    colorHex = otherColorHex,
                    speakingLevel = if (isUnread) 0.5f else 0f,
                    micActive = isUnread,
                    size = 50.dp,
                    photoBase64 = otherPhotoBase64,
                    userId = otherUidAvatar,
                    modifier = Modifier.clickable(onClick = onAvatarClick)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!isGroup && !isVoxOfficial) {
                        PresenceStatusDot(
                            status = otherStatus,
                            modifier = Modifier.testTag("chat_row_presence_${thread.id}")
                        )
                    }
                    val otherUid = if (isGroup || isVoxOfficial) null else thread.otherUserId(currentUserId)
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (isUnread || isVoxOfficial) FontWeight.ExtraBold else FontWeight.SemiBold
                        ),
                        color = when {
                            isVoxOfficial -> SnapBlue
                            isGroup -> MaterialTheme.colorScheme.onSurface
                            else -> usernameColor(otherTier, userId = otherUid)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVoxOfficial) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = SnapBlue.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "Official • No Reply",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = SnapBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    } else if (isGroup) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Group · ${thread.participantIds.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        if (thread.isBannedInGroup(currentUserId)) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = LiveCoral.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Banned",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = LiveCoral,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        } else if (thread.isBouncedInGroup(currentUserId)) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ModAmber.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "Bounced",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ModAmber,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        } else if (thread.isGroupAdmin(currentUserId)) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ModAmber.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Admin",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = ModAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (thread.isGroupSupport(currentUserId)) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SafetyEmerald.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "Support",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = SafetyEmerald,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        StaffTierBadge(tier = otherTier)
                        val talkStreak = thread.activeTalkStreakCount()
                        if (talkStreak >= 3) {
                            TalkStreakBadge(
                                streakDays = talkStreak,
                                expiringToday = thread.isTalkStreakExpiringToday(),
                                compact = true,
                                modifier = Modifier.testTag("chat_talk_streak_${thread.id}")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(SnapBlue)
                        )
                        Text(
                            text = "New Chat",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SnapBlue
                        )
                    } else if (lastSentByMe && thread.lastMessageText.isNotBlank()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Delivered",
                            tint = SnapBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Delivered",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .border(1.8.dp, SnapBlue, RoundedCornerShape(3.dp))
                        )
                        Text(
                            text = if (thread.lastMessageText.isBlank()) "Tap to chat" else "Received",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "· ${formatTimestampShort(thread.updatedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (thread.lastMessageText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = thread.lastMessageText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = if (isUnread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (isUnread) Icons.Rounded.MarkChatUnread else Icons.Rounded.ChatBubble,
                    contentDescription = "Open chat with $title",
                    tint = if (isUnread) SnapBlue else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun NewPrivateChatSheetContent(
    friends: List<Friendship>,
    directoryUsers: List<DirectoryEntry>,
    directoryByUid: Map<String, DirectoryEntry>,
    currentUserId: String,
    viewerIsStaff: Boolean,
    nowMillis: Long,
    onSelectUser: (userId: String, name: String, colorHex: String, tier: StaffTier) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val friendIds = remember(friends, currentUserId) {
        friends.map { it.otherUserId(currentUserId) }.toSet()
    }
    val filteredFriends = remember(friends, query, currentUserId) {
        val q = query.trim()
        friends.filter {
            val otherTier = it.otherTier(currentUserId)
            q.isEmpty() ||
                it.otherName(currentUserId).contains(q, ignoreCase = true) ||
                (otherTier != StaffTier.ADMIN && it.otherHandle(currentUserId).contains(q, ignoreCase = true))
        }
    }
    val filteredOthers = remember(directoryUsers, friendIds, query) {
        val q = query.trim()
        directoryUsers.filter {
            !friendIds.contains(it.userId) &&
                (q.isEmpty() ||
                    it.displayName.contains(q, ignoreCase = true) ||
                    (it.tierEnum() != StaffTier.ADMIN && it.handle.contains(q, ignoreCase = true)))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "New Private Chat",
            style = MaterialTheme.typography.headlineSmall
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(40) },
            placeholder = { Text("Search friends or @handles…") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("new_chat_search_input")
        )

        LazyColumn(
            modifier = Modifier.height(340.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredFriends.isNotEmpty()) {
                item(key = "sheet_friends_header") {
                    Text(
                        text = "Your Friends",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                itemsIndexed(filteredFriends, key = { idx, f -> "sf_${idx}_${f.id}" }) { _, f ->
                    val uid = f.otherUserId(currentUserId)
                    val name = f.otherName(currentUserId)
                    val handle = f.otherHandle(currentUserId)
                    val color = f.otherColorHex(currentUserId)
                    val tier = f.otherTier(currentUserId)
                    NewChatPickerRow(
                        userId = uid,
                        name = name,
                        handle = handle,
                        colorHex = color,
                        tier = tier,
                        presenceStatus = directoryByUid[uid]?.displayedStatusFor(
                            viewerIsStaff = viewerIsStaff,
                            nowMillis = nowMillis
                        ) ?: PresenceStatus.OFFLINE,
                        dmPolicy = directoryByUid[uid]?.dmPolicyEnum() ?: DmPolicy.OPEN,
                        onClick = { onSelectUser(uid, name, color, tier) }
                    )
                }
            }

            if (filteredOthers.isNotEmpty()) {
                item(key = "sheet_everyone_header") {
                    Text(
                        text = "People on VOX",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                itemsIndexed(filteredOthers, key = { idx, entry -> "sd_${idx}_${entry.userId}" }) { _, entry ->
                    NewChatPickerRow(
                        userId = entry.userId,
                        name = entry.displayName,
                        handle = entry.handle,
                        colorHex = entry.avatarColorHex,
                        tier = entry.tierEnum(),
                        presenceStatus = entry.displayedStatusFor(
                            viewerIsStaff = viewerIsStaff,
                            nowMillis = nowMillis
                        ),
                        dmPolicy = entry.dmPolicyEnum(),
                        onClick = {
                            onSelectUser(entry.userId, entry.displayName, entry.avatarColorHex, entry.tierEnum())
                        }
                    )
                }
            }

            if (filteredFriends.isEmpty() && filteredOthers.isEmpty()) {
                item(key = "sheet_none") {
                    Text(
                        text = "No matching users found yet. Invite friends to sign in with Google!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NewChatPickerRow(
    userId: String,
    name: String,
    handle: String,
    colorHex: String,
    tier: StaffTier,
    presenceStatus: PresenceStatus = PresenceStatus.OFFLINE,
    dmPolicy: DmPolicy = DmPolicy.OPEN,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeakingAvatarCircle(
                displayName = name,
                colorHex = colorHex,
                speakingLevel = 0f,
                micActive = false,
                size = 42.dp,
                userId = userId
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PresenceStatusDot(status = presenceStatus)
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleSmall,
                        color = usernameColor(tier, userId = userId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    StaffTierBadge(tier = tier)
                }
                if (tier != StaffTier.ADMIN && handle.isNotBlank()) {
                    Text(
                        text = "@$handle",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when (dmPolicy) {
                DmPolicy.PAID -> {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ModAmber.copy(alpha = 0.16f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = "Paid private messages",
                                tint = ModAmber,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "\$5 DM",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ModAmber
                            )
                        }
                    }
                }
                DmPolicy.DISABLED -> {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = "Private messages off",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "DMs Off",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                DmPolicy.OPEN -> {
                    Icon(
                        imageVector = Icons.Rounded.ChatBubble,
                        contentDescription = "Start chat",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
