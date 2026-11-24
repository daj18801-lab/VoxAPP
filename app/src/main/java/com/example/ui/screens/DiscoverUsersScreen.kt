package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.AgeFilterRange
import com.example.data.DirectoryEntry
import com.example.data.DmPolicy
import com.example.data.Friendship
import com.example.data.PostComment
import com.example.data.PresenceStatus
import com.example.data.ProfilePost
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.SupportTicket
import com.example.data.SupportTicketCategory
import com.example.data.SupportTicketStatus
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.UserSex
import com.example.ui.components.DiscoveryPhotoGridRow
import com.example.ui.components.DiscoveryPhotoPostDialog
import com.example.ui.components.DiscoveryPhotoPreviewDialog
import com.example.ui.components.DiscoveryRecentPhotosQuotaCard
import com.example.ui.components.PostCommentsDialog
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.ProfilePostCard
import com.example.ui.components.SharePostDialog
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverUsersScreen(
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    directoryEntries: List<DirectoryEntry>,
    friendships: List<Friendship>,
    recentPostsState: UiState<List<ProfilePost>>,
    myPointsBalance: Long,
    subscribedCreatorIds: Set<String>,
    viewerIsAdultVerified: Boolean,
    isProcessingWallet: Boolean,
    onRequestAgeVerification: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onSendFriendRequest: (DirectoryEntry) -> Unit,
    onAcceptFriendRequest: (Friendship) -> Unit,
    onRemoveOrDeclineFriendship: (Friendship) -> Unit,
    onMessageUser: (userId: String, name: String, colorHex: String, tier: StaffTier) -> Unit,
    onToggleLikePost: (ProfilePost) -> Unit,
    onDeletePost: (ProfilePost) -> Unit,
    onUnlockPost: (ProfilePost, onInsufficientPoints: () -> Unit) -> Unit,
    onBoostPost: (ProfilePost, onInsufficientPoints: () -> Unit) -> Unit,
    onObservePostComments: (String) -> Flow<List<PostComment>>,
    onAddPostComment: (ProfilePost, String, () -> Unit) -> Unit,
    onDeletePostComment: (String, String) -> Unit,
    onSharePostToFeed: (ProfilePost, String, () -> Unit) -> Unit,
    onExternalSharePost: (ProfilePost) -> Unit,
    onSubscribeCreator: (creatorId: String, creatorName: String) -> Unit,
    onOpenBuyPoints: () -> Unit,
    blockedUserIds: Set<String> = emptySet(),
    supportTickets: List<SupportTicket> = emptyList(),
    onCreateSupportTicket: (
        category: SupportTicketCategory,
        subject: String,
        details: String,
        assignedStaffId: String,
        assignedStaffName: String,
        onCreated: () -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> },
    onRespondToSupportTicket: (
        ticketId: String,
        status: SupportTicketStatus,
        staffResponse: String,
        onUpdated: () -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onCloseSupportTicket: (ticketId: String) -> Unit = {},
    myUserProfile: UserProfile? = null,
    isPublishingPost: Boolean = false,
    onCreateDiscoveryPhotoPost: (
        text: String,
        imageBase64: String,
        locked: Boolean,
        boostToTop: Boolean,
        onInsufficientPoints: () -> Unit,
        onPosted: () -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> },
    followerCountsByUserId: Map<String, Int> = emptyMap()
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0=People, 1=Recent Photos, 2=Posts, 3=Suggested, 4=Friends, 5=Support, 6=Top 10
    var showBuyPointsDialog by remember { mutableStateOf(false) }
    var buyPointsNeededCount by remember { mutableIntStateOf(2) }
    var activeCommentsPost by remember { mutableStateOf<ProfilePost?>(null) }
    var activeSharePost by remember { mutableStateOf<ProfilePost?>(null) }
    var showCreateTicketDialog by remember { mutableStateOf(false) }
    var ticketTargetStaff by remember { mutableStateOf<DirectoryEntry?>(null) }
    var ticketBeingReplied by remember { mutableStateOf<SupportTicket?>(null) }
    var showPostDiscoveryPhotoDialog by remember { mutableStateOf(false) }
    var previewPhotoPostId by remember { mutableStateOf<String?>(null) }

    // People tab filters (Age, Sex, Online/Offline)
    var peopleSexFilter by remember { mutableStateOf<UserSex?>(null) }
    var peopleAgeFilter by remember { mutableStateOf(AgeFilterRange.ALL) }
    var peoplePresenceFilter by remember { mutableStateOf("ALL") } // "ALL", "ONLINE", "OFFLINE"

    // Posts tab filters (Friends, Male, Female)
    var postsFriendsOnly by remember { mutableStateOf(false) }
    var postsSexFilter by remember { mutableStateOf<UserSex?>(null) }

    // Recent Photos tab filters (Age, Sex)
    var photosSexFilter by remember { mutableStateOf<UserSex?>(null) }
    var photosAgeFilter by remember { mutableStateOf(AgeFilterRange.ALL) }

    val nowMillis = rememberPresenceClockMillis()

    val directoryByUid = remember(directoryEntries) {
        directoryEntries.associateBy { it.userId }
    }
    val friendshipByOtherUid = remember(friendships, currentUserId) {
        friendships.associateBy { it.otherUserId(currentUserId) }
    }
    val incomingRequests = remember(friendships, currentUserId) {
        friendships.filter { it.isIncomingFor(currentUserId) }
    }
    val acceptedFriends = remember(friendships) {
        friendships.filter { it.isAccepted() }
    }
    val acceptedFriendUidSet = remember(acceptedFriends, currentUserId) {
        acceptedFriends.map { it.otherUserId(currentUserId) }.toSet()
    }
    val outgoingRequests = remember(friendships, currentUserId) {
        friendships.filter { it.isOutgoingFor(currentUserId) }
    }

    val browseUsers = remember(
        directoryEntries,
        currentUserId,
        searchQuery,
        peopleSexFilter,
        peopleAgeFilter,
        peoplePresenceFilter,
        nowMillis
    ) {
        val q = searchQuery.trim()
        directoryEntries
            .filter { it.userId != currentUserId }
            .filter { entry ->
                q.isEmpty() ||
                    entry.displayName.contains(q, ignoreCase = true) ||
                    (entry.tierEnum() != StaffTier.ADMIN && entry.handle.contains(q, ignoreCase = true)) ||
                    entry.bio.contains(q, ignoreCase = true)
            }
            .filter { entry ->
                peopleSexFilter == null || entry.sexEnum() == peopleSexFilter
            }
            .filter { entry ->
                peopleAgeFilter.matches(entry.effectiveAge())
            }
            .filter { entry ->
                val shown = entry.displayedStatusFor(
                    viewerIsStaff = staffCapabilities.isStaff,
                    nowMillis = nowMillis
                )
                when (peoplePresenceFilter) {
                    "ONLINE" -> shown == PresenceStatus.ONLINE || shown == PresenceStatus.IDLE
                    "OFFLINE" -> shown == PresenceStatus.OFFLINE
                    else -> true
                }
            }
            .sortedWith(
                compareByDescending<DirectoryEntry> { it.isProfileBoosted(nowMillis) }
                    .thenByDescending { it.profileBoostedUntilMillis }
                    .thenByDescending {
                        it.displayedStatusFor(viewerIsStaff = staffCapabilities.isStaff, nowMillis = nowMillis) == PresenceStatus.ONLINE
                    }
                    .thenByDescending {
                        it.displayedStatusFor(viewerIsStaff = staffCapabilities.isStaff, nowMillis = nowMillis) == PresenceStatus.IDLE
                    }
                    .thenByDescending { it.photoBase64.isNotBlank() }
                    .thenByDescending { it.postCount }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    val suggestedFriends = remember(
        acceptedFriends,
        friendshipByOtherUid,
        directoryByUid,
        blockedUserIds,
        currentUserId,
        searchQuery
    ) {
        val q = searchQuery.trim()
        val candidateToMutuals = linkedMapOf<String, MutableList<String>>()
        acceptedFriends.forEach { friend ->
            val friendId = friend.otherUserId(currentUserId)
            val friendEntry = directoryByUid[friendId] ?: return@forEach
            val friendName = friendEntry.displayName.ifBlank { friend.otherName(currentUserId) }
            friendEntry.acceptedFriendIds.forEach { fofId ->
                if (fofId.isNotBlank() &&
                    fofId != currentUserId &&
                    fofId !in friendshipByOtherUid.keys &&
                    fofId !in blockedUserIds
                ) {
                    val list = candidateToMutuals.getOrPut(fofId) { mutableListOf() }
                    if (friendName.isNotBlank() && friendName !in list) {
                        list.add(friendName)
                    }
                }
            }
        }
        candidateToMutuals.entries
            .mapNotNull { (candidateUid, mutualNames) ->
                val entry = directoryByUid[candidateUid] ?: return@mapNotNull null
                SuggestedFriend(entry = entry, mutualNames = mutualNames.toList())
            }
            .filter { suggestion ->
                val entry = suggestion.entry
                q.isEmpty() ||
                    entry.displayName.contains(q, ignoreCase = true) ||
                    (entry.tierEnum() != StaffTier.ADMIN && entry.handle.contains(q, ignoreCase = true)) ||
                    suggestion.mutualNames.any { it.contains(q, ignoreCase = true) }
            }
            .sortedWith(
                compareByDescending<SuggestedFriend> { it.mutualNames.size }
                    .thenBy { it.entry.displayName.lowercase() }
            )
    }

    val staffMembers = remember(directoryEntries, searchQuery, nowMillis) {
        val q = searchQuery.trim()
        directoryEntries
            .filter { it.tierEnum() == StaffTier.ADMIN || it.tierEnum() == StaffTier.SUPPORT }
            .filter { entry ->
                q.isEmpty() ||
                    entry.displayName.contains(q, ignoreCase = true) ||
                    (entry.tierEnum() != StaffTier.ADMIN && entry.handle.contains(q, ignoreCase = true)) ||
                    entry.bio.contains(q, ignoreCase = true)
            }
            .sortedWith(
                compareBy<DirectoryEntry> { if (it.tierEnum() == StaffTier.ADMIN) 0 else 1 }
                    .thenByDescending {
                        it.displayedStatusFor(viewerIsStaff = staffCapabilities.isStaff, nowMillis = nowMillis) == PresenceStatus.ONLINE
                    }
                    .thenBy { it.displayName.lowercase() }
            )
    }

    val filteredSupportTickets = remember(supportTickets, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) supportTickets
        else supportTickets.filter { ticket ->
            ticket.subject.contains(q, ignoreCase = true) ||
                ticket.details.contains(q, ignoreCase = true) ||
                ticket.categoryEnum().label.contains(q, ignoreCase = true) ||
                ticket.requesterName.contains(q, ignoreCase = true) ||
                ticket.assignedStaffName.contains(q, ignoreCase = true)
        }
    }
    val openTicketCount = remember(supportTickets) {
        supportTickets.count { it.statusEnum() != SupportTicketStatus.RESOLVED }
    }

    val allRecentPosts = (recentPostsState as? UiState.Success)?.data.orEmpty()
    val filteredPosts = remember(
        allRecentPosts,
        searchQuery,
        postsFriendsOnly,
        postsSexFilter,
        acceptedFriendUidSet,
        directoryByUid
    ) {
        val q = searchQuery.trim()
        allRecentPosts
            .filter { post ->
                q.isEmpty() ||
                    post.authorName.contains(q, ignoreCase = true) ||
                    (post.authorTierEnum() != StaffTier.ADMIN && post.authorHandle.contains(q, ignoreCase = true)) ||
                    post.text.contains(q, ignoreCase = true)
            }
            .filter { post ->
                !postsFriendsOnly || post.authorId in acceptedFriendUidSet
            }
            .filter { post ->
                postsSexFilter == null || directoryByUid[post.authorId]?.sexEnum() == postsSexFilter
            }
    }

    val allRecentPhotoPosts = remember(allRecentPosts) {
        allRecentPosts
            .filter { it.imageBase64.isNotBlank() }
            .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
    }
    val filteredRecentPhotoPosts = remember(
        allRecentPhotoPosts,
        searchQuery,
        photosSexFilter,
        photosAgeFilter,
        directoryByUid
    ) {
        val q = searchQuery.trim()
        allRecentPhotoPosts
            .filter { post ->
                q.isEmpty() ||
                    post.authorName.contains(q, ignoreCase = true) ||
                    (post.authorTierEnum() != StaffTier.ADMIN && post.authorHandle.contains(q, ignoreCase = true)) ||
                    post.text.contains(q, ignoreCase = true)
            }
            .filter { post ->
                photosSexFilter == null || directoryByUid[post.authorId]?.sexEnum() == photosSexFilter
            }
            .filter { post ->
                photosAgeFilter.matches(directoryByUid[post.authorId]?.effectiveAge() ?: 0)
            }
    }
    val filteredRecentPhotoRows = remember(filteredRecentPhotoPosts) {
        filteredRecentPhotoPosts.chunked(2)
    }

    val top10FollowedUsers = remember(directoryEntries, followerCountsByUserId, searchQuery) {
        val q = searchQuery.trim()
        directoryEntries
            .filter { entry ->
                q.isEmpty() ||
                    entry.displayName.contains(q, ignoreCase = true) ||
                    (entry.tierEnum() != StaffTier.ADMIN && entry.handle.contains(q, ignoreCase = true)) ||
                    entry.bio.contains(q, ignoreCase = true)
            }
            .sortedWith(
                compareByDescending<DirectoryEntry> { followerCountsByUserId[it.userId] ?: 0 }
                    .thenByDescending { it.postCount }
                    .thenBy { it.displayName.lowercase() }
            )
            .take(10)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Search + Segment Chips
            item(key = "discover_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Discover",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Browse ${browseUsers.size} people & community posts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Explore,
                                    contentDescription = "Discover users",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(40) },
                        placeholder = { Text(stringResource(R.string.hint_search_discover)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search users",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(999.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("discover_search_input")
                    )

                    val tabs = listOf(
                        "People (${browseUsers.size})",
                        "Recent Photos (${allRecentPhotoPosts.size})",
                        "Posts (${allRecentPosts.size})",
                        "Suggested (${suggestedFriends.size})",
                        if (incomingRequests.isNotEmpty()) {
                            "Friends (${acceptedFriends.size} · +${incomingRequests.size})"
                        } else {
                            "Friends (${acceptedFriends.size})"
                        },
                        if (openTicketCount > 0) "Support ($openTicketCount)" else "Support",
                        "Top 10"
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        tabs.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingIcon = when (idx) {
                                    1 -> {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoLibrary,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    6 -> {
                                        {
                                            Icon(
                                                imageVector = Icons.Rounded.EmojiEvents,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    else -> null
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (idx == 6) ModAmber else MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier.testTag(
                                    if (idx == 6) "discover_tab_top10" else "discover_tab_$idx"
                                )
                            )
                        }
                    }
                }
            }

            when (selectedTab) {
                // TAB 0: BROWSE USERS (Filter by Age, Male, Female, Online, Offline)
                0 -> {
                    item(key = "discover_people_filters") {
                        val hasActivePeopleFilter = peopleSexFilter != null ||
                            peopleAgeFilter != AgeFilterRange.ALL ||
                            peoplePresenceFilter != "ALL"
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Filter People (${browseUsers.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasActivePeopleFilter) {
                                        TextButton(
                                            onClick = {
                                                peopleSexFilter = null
                                                peopleAgeFilter = AgeFilterRange.ALL
                                                peoplePresenceFilter = "ALL"
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.testTag("discover_people_clear_filters")
                                        ) {
                                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                // Sex + Online/Offline Chips
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = peopleSexFilter == UserSex.MALE,
                                        onClick = {
                                            peopleSexFilter = if (peopleSexFilter == UserSex.MALE) null else UserSex.MALE
                                        },
                                        label = { Text("Male") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_people_filter_male")
                                    )
                                    FilterChip(
                                        selected = peopleSexFilter == UserSex.FEMALE,
                                        onClick = {
                                            peopleSexFilter = if (peopleSexFilter == UserSex.FEMALE) null else UserSex.FEMALE
                                        },
                                        label = { Text("Female") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_people_filter_female")
                                    )
                                    FilterChip(
                                        selected = peoplePresenceFilter == "ONLINE",
                                        onClick = {
                                            peoplePresenceFilter = if (peoplePresenceFilter == "ONLINE") "ALL" else "ONLINE"
                                        },
                                        label = { Text("Online") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_people_filter_online")
                                    )
                                    FilterChip(
                                        selected = peoplePresenceFilter == "OFFLINE",
                                        onClick = {
                                            peoplePresenceFilter = if (peoplePresenceFilter == "OFFLINE") "ALL" else "OFFLINE"
                                        },
                                        label = { Text("Offline") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_people_filter_offline")
                                    )
                                }

                                // Age Range Chips
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AgeFilterRange.entries.forEach { range ->
                                        FilterChip(
                                            selected = peopleAgeFilter == range,
                                            onClick = { peopleAgeFilter = range },
                                            label = { Text(range.label) },
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier.testTag("discover_people_age_${range.name}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (browseUsers.isEmpty()) {
                        item(key = "discover_people_empty") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = "No matching people found",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Try adjusting your Age, Male/Female, or Online/Offline filters. Members can set their Age and Sex in Profile Settings.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(browseUsers, key = { "browse_${it.userId}" }) { person ->
                            DiscoverUserCard(
                                entry = person,
                                presenceStatus = person.displayedStatusFor(
                                    viewerIsStaff = staffCapabilities.isStaff,
                                    nowMillis = nowMillis
                                ),
                                staffTrueOnlineHiddenLabel = if (staffCapabilities.isStaff && person.isHidingTrueOnlineStatus(nowMillis)) {
                                    "True: Online (Appearing ${person.appearanceStatusEnum().label})"
                                } else null,
                                currentUserId = currentUserId,
                                friendship = friendshipByOtherUid[person.userId],
                                onClickProfile = { onOpenUserProfile(person.userId) },
                                onSendFriendRequest = { onSendFriendRequest(person) },
                                onAcceptFriendRequest = { f -> onAcceptFriendRequest(f) },
                                onMessageUser = {
                                    onMessageUser(
                                        person.userId,
                                        person.displayName,
                                        person.avatarColorHex,
                                        person.tierEnum()
                                    )
                                }
                            )
                        }
                    }
                }

                // TAB 1: RECENTLY ADDED MEMBER PHOTOS (Filter by Age & Sex + Free 2/week quota)
                1 -> {
                    item(key = "discover_recent_photos_quota") {
                        DiscoveryRecentPhotosQuotaCard(
                            myUserProfile = myUserProfile,
                            myStaffTier = staffCapabilities.tier,
                            isPublishingPost = isPublishingPost,
                            onPostPhotoClick = { showPostDiscoveryPhotoDialog = true },
                            onOpenBuyPoints = onOpenBuyPoints
                        )
                    }

                    item(key = "discover_recent_photos_filters") {
                        val hasPhotoFilter = photosSexFilter != null || photosAgeFilter != AgeFilterRange.ALL
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Filter Recent Photos (${filteredRecentPhotoPosts.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasPhotoFilter) {
                                        TextButton(
                                            onClick = {
                                                photosSexFilter = null
                                                photosAgeFilter = AgeFilterRange.ALL
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.testTag("discover_photos_clear_filters")
                                        ) {
                                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                // Sex Chips
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = photosSexFilter == null,
                                        onClick = { photosSexFilter = null },
                                        label = { Text("All") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_photos_filter_all")
                                    )
                                    FilterChip(
                                        selected = photosSexFilter == UserSex.MALE,
                                        onClick = {
                                            photosSexFilter = if (photosSexFilter == UserSex.MALE) null else UserSex.MALE
                                        },
                                        label = { Text("Male") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_photos_filter_male")
                                    )
                                    FilterChip(
                                        selected = photosSexFilter == UserSex.FEMALE,
                                        onClick = {
                                            photosSexFilter = if (photosSexFilter == UserSex.FEMALE) null else UserSex.FEMALE
                                        },
                                        label = { Text("Female") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_photos_filter_female")
                                    )
                                }

                                // Age Range Chips
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    AgeFilterRange.entries.forEach { range ->
                                        FilterChip(
                                            selected = photosAgeFilter == range,
                                            onClick = { photosAgeFilter = range },
                                            label = { Text(range.label) },
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier.testTag("discover_photos_age_${range.name}")
                                        )
                                    }
                                }
                            }
                        }
                    }

                    when (recentPostsState) {
                        is UiState.Loading -> {
                            item(key = "discover_photos_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 36.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        is UiState.Error -> {
                            item(key = "discover_photos_error") {
                                Text(
                                    text = recentPostsState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        is UiState.Success -> {
                            if (filteredRecentPhotoRows.isEmpty()) {
                                item(key = "discover_photos_empty") {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoLibrary,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Text(
                                                text = "No matching recent photos",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Tap \"Post Photo to Discovery\" above to share a photo, or adjust your Age and Sex filters.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = filteredRecentPhotoRows,
                                    key = { row -> "photo_row_${row.joinToString("_") { it.id }}" }
                                ) { pair ->
                                    DiscoveryPhotoGridRow(
                                        pair = pair,
                                        directoryByUid = directoryByUid,
                                        currentUserId = currentUserId,
                                        subscribedCreatorIds = subscribedCreatorIds,
                                        viewerIsAdultVerified = viewerIsAdultVerified,
                                        onSelectPost = { selected -> previewPhotoPostId = selected.id }
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 2: COMMUNITY POSTS FEED (Filter by Friends, Male, Female)
                2 -> {
                    item(key = "discover_posts_filters") {
                        val hasPostFilter = postsFriendsOnly || postsSexFilter != null
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Filter Posts (${filteredPosts.size})",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (hasPostFilter) {
                                        TextButton(
                                            onClick = {
                                                postsFriendsOnly = false
                                                postsSexFilter = null
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.testTag("discover_posts_clear_filters")
                                        ) {
                                            Text("Clear", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    FilterChip(
                                        selected = !postsFriendsOnly && postsSexFilter == null,
                                        onClick = {
                                            postsFriendsOnly = false
                                            postsSexFilter = null
                                        },
                                        label = { Text("All") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_posts_filter_all")
                                    )
                                    FilterChip(
                                        selected = postsFriendsOnly,
                                        onClick = { postsFriendsOnly = !postsFriendsOnly },
                                        label = { Text("Friends") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_posts_filter_friends")
                                    )
                                    FilterChip(
                                        selected = postsSexFilter == UserSex.MALE,
                                        onClick = {
                                            postsSexFilter = if (postsSexFilter == UserSex.MALE) null else UserSex.MALE
                                        },
                                        label = { Text("Male") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_posts_filter_male")
                                    )
                                    FilterChip(
                                        selected = postsSexFilter == UserSex.FEMALE,
                                        onClick = {
                                            postsSexFilter = if (postsSexFilter == UserSex.FEMALE) null else UserSex.FEMALE
                                        },
                                        label = { Text("Female") },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier.testTag("discover_posts_filter_female")
                                    )
                                }
                            }
                        }
                    }

                    when (recentPostsState) {
                        is UiState.Loading -> {
                            item(key = "discover_posts_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        is UiState.Error -> {
                            item(key = "discover_posts_error") {
                                Text(
                                    text = recentPostsState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }

                        is UiState.Success -> {
                            if (filteredPosts.isEmpty()) {
                                item(key = "discover_posts_empty") {
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.DynamicFeed,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Text(
                                                text = "No matching posts yet",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Try clearing your Friends/Male/Female filters, or visit your Profile tab to share a new post!",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                items(filteredPosts, key = { "feed_${it.id}" }) { post ->
                                    val unlocked = post.isUnlockedFor(
                                        uid = currentUserId,
                                        subscribedCreatorIds = subscribedCreatorIds,
                                        viewerIsAdultVerified = viewerIsAdultVerified
                                    )
                                    ProfilePostCard(
                                        post = post,
                                        currentUserId = currentUserId,
                                        canDelete = post.authorId == currentUserId || staffCapabilities.isStaff,
                                        latestAuthorPhotoBase64 = directoryByUid[post.authorId]?.photoBase64,
                                        isUnlocked = unlocked,
                                        viewerIsAdultVerified = viewerIsAdultVerified,
                                        isProcessingUnlock = isProcessingWallet,
                                        onAuthorClick = { onOpenUserProfile(post.authorId) },
                                        onOriginalAuthorClick = onOpenUserProfile,
                                        onRequestAgeVerification = onRequestAgeVerification,
                                        onUnlockPost = {
                                            if (!viewerIsAdultVerified) {
                                                onRequestAgeVerification()
                                            } else {
                                                onUnlockPost(post) {
                                                    buyPointsNeededCount = 2
                                                    showBuyPointsDialog = true
                                                }
                                            }
                                        },
                                        onSubscribeCreator = {
                                            if (!viewerIsAdultVerified) {
                                                onRequestAgeVerification()
                                            } else {
                                                onSubscribeCreator(post.authorId, post.authorName)
                                            }
                                        },
                                        onBoostPost = if (post.authorId == currentUserId) {
                                            {
                                                onBoostPost(post) {
                                                    buyPointsNeededCount = 5
                                                    showBuyPointsDialog = true
                                                }
                                            }
                                        } else {
                                            null
                                        },
                                        onOpenComments = { activeCommentsPost = post },
                                        onSharePost = if (!post.locked) {
                                            { activeSharePost = post }
                                        } else {
                                            null
                                        },
                                        onToggleLike = { onToggleLikePost(post) },
                                        onDelete = { onDeletePost(post) }
                                    )
                                }
                            }
                        }
                    }
                }

                // TAB 3: SUGGESTED FRIENDS (FRIENDS OF YOUR FRIENDS)
                3 -> {
                    item(key = "discover_suggested_header") {
                        Text(
                            text = "Suggested Friends (${suggestedFriends.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    if (suggestedFriends.isEmpty()) {
                        item(key = "discover_suggested_empty") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Group,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Text(
                                        text = "No suggested friends yet",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Friends of your accepted friends will appear here automatically so you can connect through mutual friends.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(suggestedFriends, key = { "suggested_${it.entry.userId}" }) { suggestion ->
                            SuggestedFriendCard(
                                suggestion = suggestion,
                                presenceStatus = suggestion.entry.displayedStatusFor(
                                    viewerIsStaff = staffCapabilities.isStaff,
                                    nowMillis = nowMillis
                                ),
                                onClickProfile = { onOpenUserProfile(suggestion.entry.userId) },
                                onSendFriendRequest = { onSendFriendRequest(suggestion.entry) },
                                onMessageUser = {
                                    onMessageUser(
                                        suggestion.entry.userId,
                                        suggestion.entry.displayName,
                                        suggestion.entry.avatarColorHex,
                                        suggestion.entry.tierEnum()
                                    )
                                }
                            )
                        }
                    }
                }

                // TAB 4: FRIENDS & REQUESTS
                4 -> {
                    if (incomingRequests.isNotEmpty()) {
                        item(key = "disc_in_header") {
                            Text(
                                text = "Friend Requests (${incomingRequests.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(incomingRequests, key = { "disc_in_${it.id}" }) { req ->
                            val otherId = req.otherUserId(currentUserId)
                            val name = req.otherName(currentUserId)
                            val handle = req.otherHandle(currentUserId)
                            val color = req.otherColorHex(currentUserId)
                            val tier = req.otherTier(currentUserId)
                            val photo = directoryByUid[otherId]?.photoBase64.orEmpty()

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenUserProfile(otherId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SpeakingAvatarCircle(
                                        displayName = name,
                                        colorHex = color,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 46.dp,
                                        photoBase64 = photo,
                                        userId = otherId
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val presenceStatus = directoryByUid[otherId]?.displayedStatusFor(
                                        viewerIsStaff = staffCapabilities.isStaff,
                                        nowMillis = nowMillis
                                    ) ?: PresenceStatus.OFFLINE
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            PresenceStatusDot(
                                                status = presenceStatus,
                                                modifier = Modifier.testTag("discover_request_presence_$otherId")
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = usernameColor(tier, userId = otherId),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            StaffTierBadge(tier = tier)
                                        }
                                        Text(
                                            text = if (tier == StaffTier.ADMIN) {
                                                "Wants to be friends"
                                            } else {
                                                "@$handle wants to be friends"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { onAcceptFriendRequest(req) },
                                        shape = RoundedCornerShape(999.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text("Accept", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { onRemoveOrDeclineFriendship(req) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Decline", tint = LiveCoral)
                                    }
                                }
                            }
                        }
                    }

                    item(key = "disc_friends_header") {
                        Text(
                            text = "My Friends (${acceptedFriends.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (acceptedFriends.isEmpty()) {
                        item(key = "disc_friends_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "You haven't added friends yet. Switch to People to browse profiles and send friend requests!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(acceptedFriends, key = { "disc_fr_${it.id}" }) { friend ->
                            val otherId = friend.otherUserId(currentUserId)
                            val name = friend.otherName(currentUserId)
                            val handle = friend.otherHandle(currentUserId)
                            val color = friend.otherColorHex(currentUserId)
                            val tier = friend.otherTier(currentUserId)
                            val photo = directoryByUid[otherId]?.photoBase64.orEmpty()

                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenUserProfile(otherId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SpeakingAvatarCircle(
                                        displayName = name,
                                        colorHex = color,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 48.dp,
                                        photoBase64 = photo,
                                        userId = otherId
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    val presenceStatus = directoryByUid[otherId]?.displayedStatusFor(
                                        viewerIsStaff = staffCapabilities.isStaff,
                                        nowMillis = nowMillis
                                    ) ?: PresenceStatus.OFFLINE
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            PresenceStatusDot(
                                                status = presenceStatus,
                                                modifier = Modifier.testTag("discover_friend_presence_$otherId")
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = usernameColor(tier, userId = otherId),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            StaffTierBadge(tier = tier)
                                        }
                                        Text(
                                            text = if (tier == StaffTier.ADMIN) {
                                                presenceStatus.label
                                            } else {
                                                "@$handle · ${presenceStatus.label}"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = when (presenceStatus) {
                                                PresenceStatus.ONLINE -> PresenceOnlineGreen
                                                PresenceStatus.IDLE -> ModAmber
                                                PresenceStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                    Button(
                                        onClick = { onMessageUser(otherId, name, color, tier) },
                                        shape = RoundedCornerShape(999.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Chat", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { onRemoveOrDeclineFriendship(friend) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonRemove,
                                            contentDescription = "Remove friend",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (outgoingRequests.isNotEmpty()) {
                        item(key = "disc_out_header") {
                            Text(
                                text = "Sent Requests (${outgoingRequests.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        items(outgoingRequests, key = { "disc_out_${it.id}" }) { req ->
                            val otherId = req.otherUserId(currentUserId)
                            val name = req.otherName(currentUserId)
                            val handle = req.otherHandle(currentUserId)
                            val color = req.otherColorHex(currentUserId)
                            val tier = req.otherTier(currentUserId)
                            val photo = directoryByUid[otherId]?.photoBase64.orEmpty()

                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenUserProfile(otherId) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    SpeakingAvatarCircle(
                                        displayName = name,
                                        colorHex = color,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 42.dp,
                                        photoBase64 = photo,
                                        userId = otherId
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    val presenceStatus = directoryByUid[otherId]?.displayedStatusFor(
                                        viewerIsStaff = staffCapabilities.isStaff,
                                        nowMillis = nowMillis
                                    ) ?: PresenceStatus.OFFLINE
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            PresenceStatusDot(
                                                status = presenceStatus,
                                                modifier = Modifier.testTag("discover_outgoing_presence_$otherId")
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = usernameColor(tier, userId = otherId),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = if (tier == StaffTier.ADMIN) "Pending" else "@$handle · Pending",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onRemoveOrDeclineFriendship(req) },
                                        shape = RoundedCornerShape(999.dp)
                                    ) {
                                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }

                // TAB 5: ADMIN / SUPPORT DIRECTORY & SUPPORT TICKETS
                5 -> {
                    item(key = "discover_support_banner") {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.SupportAgent,
                                                contentDescription = "Support team",
                                                tint = Color.White,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Admin & Support Desk",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Reach verified Admin & Support team members or open a tracked support ticket.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Button(
                                    onClick = {
                                        ticketTargetStaff = null
                                        showCreateTicketDialog = true
                                    },
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("discover_create_support_ticket_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ConfirmationNumber,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Create Support Ticket")
                                }
                            }
                        }
                    }

                    item(key = "discover_staff_team_header") {
                        Text(
                            text = "Admin & Support Team (${staffMembers.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (staffMembers.isEmpty()) {
                        item(key = "discover_staff_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No Admin or Support members matched your search. You can still open a support ticket above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(staffMembers, key = { "staff_${it.userId}" }) { member ->
                            SupportStaffMemberCard(
                                entry = member,
                                presenceStatus = member.displayedStatusFor(
                                    viewerIsStaff = staffCapabilities.isStaff,
                                    nowMillis = nowMillis
                                ),
                                isSelf = member.userId == currentUserId,
                                onClickProfile = { onOpenUserProfile(member.userId) },
                                onMessageStaff = {
                                    onMessageUser(
                                        member.userId,
                                        member.displayName,
                                        member.avatarColorHex,
                                        member.tierEnum()
                                    )
                                },
                                onOpenTicketWithStaff = {
                                    ticketTargetStaff = member
                                    showCreateTicketDialog = true
                                }
                            )
                        }
                    }

                    item(key = "discover_tickets_header") {
                        Text(
                            text = if (staffCapabilities.isStaff) {
                                "All Support Tickets (${filteredSupportTickets.size})"
                            } else {
                                "My Support Tickets (${filteredSupportTickets.size})"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (filteredSupportTickets.isEmpty()) {
                        item(key = "discover_tickets_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (staffCapabilities.isStaff) {
                                        "No support tickets submitted yet."
                                    } else {
                                        "You haven't created any support tickets yet. Tap \"Create Support Ticket\" above anytime you need help."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredSupportTickets, key = { "ticket_${it.id}" }) { ticket ->
                            SupportTicketCard(
                                ticket = ticket,
                                currentUserId = currentUserId,
                                isStaff = staffCapabilities.isStaff,
                                onReplyTicket = { ticketBeingReplied = ticket },
                                onCloseTicket = { onCloseSupportTicket(ticket.id) }
                            )
                        }
                    }
                }

                // TAB 6: TOP 10 MOST FOLLOWED USERS
                6 -> {
                    item(key = "discover_top10_header") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = ModAmber.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("discover_top10_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ModAmber,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.EmojiEvents,
                                            contentDescription = "Top 10 leaderboard",
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Top 10 Most Followed",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Members ranked live by total followers across VoxStage.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (top10FollowedUsers.isEmpty()) {
                        item(key = "discover_top10_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No members found yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(
                            items = top10FollowedUsers,
                            key = { _, entry -> "top10_${entry.userId}" }
                        ) { index, entry ->
                            val rank = index + 1
                            val followerCount = followerCountsByUserId[entry.userId] ?: 0
                            val tier = entry.tierEnum()
                            val presenceStatus = entry.displayedStatusFor(
                                viewerIsStaff = staffCapabilities.isStaff,
                                nowMillis = nowMillis
                            )
                            val rankBadgeColor = when (rank) {
                                1 -> Color(0xFFD97706) // Gold
                                2 -> Color(0xFF64748B) // Silver
                                3 -> Color(0xFFB45309) // Bronze
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    width = if (rank <= 3) 1.5.dp else 1.dp,
                                    color = if (rank <= 3) rankBadgeColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenUserProfile(entry.userId) }
                                    .testTag("discover_top10_card_$rank")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = rankBadgeColor,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "#$rank",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = Color.White
                                            )
                                        }
                                    }

                                    SpeakingAvatarCircle(
                                        displayName = entry.displayName,
                                        colorHex = entry.avatarColorHex,
                                        speakingLevel = 0f,
                                        micActive = false,
                                        size = 48.dp,
                                        photoBase64 = entry.photoBase64,
                                        userId = entry.userId
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            PresenceStatusDot(
                                                status = presenceStatus,
                                                modifier = Modifier.testTag("discover_top10_presence_${entry.userId}")
                                            )
                                            Text(
                                                text = entry.displayName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = usernameColor(tier, isPro = entry.isProActive(), userId = entry.userId),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            StaffTierBadge(tier = tier)
                                        }
                                        val handleLine = buildList {
                                            if (tier != StaffTier.ADMIN && entry.handle.isNotBlank()) {
                                                add("@${entry.handle}")
                                            }
                                            if (entry.userId == currentUserId) {
                                                add("You")
                                            }
                                        }.joinToString(" · ")
                                        if (handleLine.isNotBlank()) {
                                            Text(
                                                text = handleLine,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = rankBadgeColor.copy(alpha = 0.14f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Group,
                                                    contentDescription = null,
                                                    tint = rankBadgeColor,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Text(
                                                    text = if (followerCount == 1) "1 follower" else "$followerCount followers",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                    color = rankBadgeColor
                                                )
                                            }
                                        }
                                    }

                                    if (entry.userId != currentUserId) {
                                        Button(
                                            onClick = {
                                                onMessageUser(
                                                    entry.userId,
                                                    entry.displayName,
                                                    entry.avatarColorHex,
                                                    tier
                                                )
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                            modifier = Modifier.testTag("discover_top10_chat_${entry.userId}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ChatBubble,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Chat", style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "discover_bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showBuyPointsDialog) {
        AlertDialog(
            onDismissRequest = { showBuyPointsDialog = false },
            title = { Text("Need More Points") },
            text = {
                Text(
                    if (buyPointsNeededCount >= 5) {
                        "You currently have $myPointsBalance pts. Boosting a post to the top of Discovery costs 5 points (\$5) for 24 hours. Buy points in Profile Settings."
                    } else {
                        "You currently have $myPointsBalance pts. Unlocking a locked photo costs 2 points (\$2 — \$1 to the photo owner, \$1 to the app owner). Buy points in Profile Settings or subscribe for \$15/month."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBuyPointsDialog = false
                        onOpenBuyPoints()
                    },
                    modifier = Modifier.testTag("discover_dialog_buy_points")
                ) {
                    Text("Buy Points")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuyPointsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    activeCommentsPost?.let { post ->
        val commentsFlow = remember(post.id) { onObservePostComments(post.id) }
        PostCommentsDialog(
            post = post,
            commentsFlow = commentsFlow,
            currentUserId = currentUserId,
            isStaff = staffCapabilities.isStaff,
            onAuthorClick = { uid ->
                activeCommentsPost = null
                onOpenUserProfile(uid)
            },
            onAddComment = { text, onSent ->
                onAddPostComment(post, text, onSent)
            },
            onDeleteComment = { commentId ->
                onDeletePostComment(post.id, commentId)
            },
            onDismiss = { activeCommentsPost = null }
        )
    }

    activeSharePost?.let { post ->
        SharePostDialog(
            post = post,
            onRepostToFeed = { note, onShared ->
                onSharePostToFeed(post, note, onShared)
            },
            onRecordExternalShare = {
                onExternalSharePost(post)
            },
            onDismiss = { activeSharePost = null }
        )
    }

    if (showCreateTicketDialog) {
        CreateSupportTicketDialog(
            initialStaffMember = ticketTargetStaff,
            onDismiss = {
                showCreateTicketDialog = false
                ticketTargetStaff = null
            },
            onSubmit = { category, subject, details, assignedStaffId, assignedStaffName ->
                onCreateSupportTicket(
                    category,
                    subject,
                    details,
                    assignedStaffId,
                    assignedStaffName
                ) {
                    showCreateTicketDialog = false
                    ticketTargetStaff = null
                }
            }
        )
    }

    ticketBeingReplied?.let { ticket ->
        RespondSupportTicketDialog(
            ticket = ticket,
            onDismiss = { ticketBeingReplied = null },
            onSubmit = { status, response ->
                onRespondToSupportTicket(ticket.id, status, response) {
                    ticketBeingReplied = null
                }
            }
        )
    }

    if (showPostDiscoveryPhotoDialog) {
        DiscoveryPhotoPostDialog(
            myUserProfile = myUserProfile,
            myStaffTier = staffCapabilities.tier,
            viewerIsAdultVerified = viewerIsAdultVerified,
            isPublishingPost = isPublishingPost,
            onRequestAgeVerification = onRequestAgeVerification,
            onOpenBuyPoints = onOpenBuyPoints,
            onSubmitPhotoPost = { caption, imageBase64, locked, onDone ->
                onCreateDiscoveryPhotoPost(
                    caption,
                    imageBase64,
                    locked,
                    false,
                    {
                        buyPointsNeededCount = 5
                        showBuyPointsDialog = true
                    },
                    onDone
                )
            },
            onDismiss = { showPostDiscoveryPhotoDialog = false }
        )
    }

    val previewPost = remember(previewPhotoPostId, allRecentPosts) {
        previewPhotoPostId?.let { id -> allRecentPosts.firstOrNull { it.id == id } }
    }
    if (previewPost != null) {
        val unlocked = previewPost.isUnlockedFor(
            uid = currentUserId,
            subscribedCreatorIds = subscribedCreatorIds,
            viewerIsAdultVerified = viewerIsAdultVerified
        )
        DiscoveryPhotoPreviewDialog(
            post = previewPost,
            currentUserId = currentUserId,
            canDelete = previewPost.authorId == currentUserId || staffCapabilities.isStaff,
            latestAuthorPhotoBase64 = directoryByUid[previewPost.authorId]?.photoBase64,
            isUnlocked = unlocked,
            viewerIsAdultVerified = viewerIsAdultVerified,
            isProcessingUnlock = isProcessingWallet,
            onAuthorClick = { onOpenUserProfile(previewPost.authorId) },
            onOriginalAuthorClick = onOpenUserProfile,
            onRequestAgeVerification = onRequestAgeVerification,
            onUnlockPost = {
                if (!viewerIsAdultVerified) {
                    onRequestAgeVerification()
                } else {
                    onUnlockPost(previewPost) {
                        buyPointsNeededCount = 2
                        showBuyPointsDialog = true
                    }
                }
            },
            onSubscribeCreator = {
                if (!viewerIsAdultVerified) {
                    onRequestAgeVerification()
                } else {
                    onSubscribeCreator(previewPost.authorId, previewPost.authorName)
                }
            },
            onBoostPost = if (previewPost.authorId == currentUserId) {
                {
                    onBoostPost(previewPost) {
                        buyPointsNeededCount = 5
                        showBuyPointsDialog = true
                    }
                }
            } else null,
            onOpenComments = { activeCommentsPost = previewPost },
            onSharePost = if (!previewPost.locked) {
                { activeSharePost = previewPost }
            } else null,
            onToggleLike = { onToggleLikePost(previewPost) },
            onDelete = { onDeletePost(previewPost) },
            onDismiss = { previewPhotoPostId = null }
        )
    }
}

@Composable
private fun DiscoverUserCard(
    entry: DirectoryEntry,
    presenceStatus: PresenceStatus,
    staffTrueOnlineHiddenLabel: String? = null,
    currentUserId: String,
    friendship: Friendship?,
    onClickProfile: () -> Unit,
    onSendFriendRequest: () -> Unit,
    onAcceptFriendRequest: (Friendship) -> Unit,
    onMessageUser: () -> Unit
) {
    val tier = entry.tierEnum()
    val isPromoted = entry.isProfileBoosted()
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isPromoted) 1.5.dp else 1.dp,
            color = if (isPromoted) ModAmber.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClickProfile)
            .testTag("discover_user_card_${entry.userId}")
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
                    size = 56.dp,
                    photoBase64 = entry.photoBase64,
                    userId = entry.userId
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresenceStatusDot(
                            status = presenceStatus,
                            modifier = Modifier.testTag("discover_user_presence_${entry.userId}")
                        )
                        Text(
                            text = entry.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = usernameColor(tier, isPro = entry.isProActive(), userId = entry.userId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isPromoted) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = ModAmber.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "PROMOTED",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = ModAmber,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        StaffTierBadge(tier = tier)
                    }
                    if (!staffTrueOnlineHiddenLabel.isNullOrBlank()) {
                        Text(
                            text = staffTrueOnlineHiddenLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = PresenceOnlineGreen
                        )
                    }
                    val demoParts = buildList {
                        if (entry.effectiveAge() >= 18) add("${entry.effectiveAge()}")
                        if (entry.sexEnum() != UserSex.UNSPECIFIED) add(entry.sexEnum().label)
                    }
                    val demoSuffix = if (demoParts.isNotEmpty()) " · ${demoParts.joinToString(" · ")}" else ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (tier == StaffTier.ADMIN) {
                                "${entry.postCount} ${if (entry.postCount == 1L) "post" else "posts"}$demoSuffix"
                            } else {
                                "@${entry.handle} · ${entry.postCount} ${if (entry.postCount == 1L) "post" else "posts"}$demoSuffix"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        when (entry.dmPolicyEnum()) {
                            DmPolicy.PAID -> {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = ModAmber.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Paid private messages",
                                            tint = ModAmber,
                                            modifier = Modifier.size(11.dp)
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
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Block,
                                            contentDescription = "Private messages off",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "DMs Off",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            DmPolicy.OPEN -> Unit
                        }
                    }
                    if (entry.bio.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onClickProfile,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discover_view_profile_${entry.userId}")
                ) {
                    Text("View Profile", style = MaterialTheme.typography.labelMedium)
                }

                when {
                    friendship == null -> {
                        Button(
                            onClick = onSendFriendRequest,
                            shape = RoundedCornerShape(999.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("discover_add_friend_${entry.userId}")
                        ) {
                            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Friend", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    friendship.isAccepted() -> {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = "Friends",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Friends",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    friendship.isIncomingFor(currentUserId) -> {
                        Button(
                            onClick = { onAcceptFriendRequest(friendship) },
                            shape = RoundedCornerShape(999.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Accept", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    else -> {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.HourglassTop,
                                    contentDescription = "Pending",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Pending",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = onMessageUser,
                        modifier = Modifier.testTag("discover_message_${entry.userId}")
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
