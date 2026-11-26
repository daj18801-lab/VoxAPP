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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.Friendship
import com.example.data.PresenceStatus
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen

@Composable
fun FriendsScreen(
    currentUserId: String,
    friendships: List<Friendship>,
    directoryEntries: List<DirectoryEntry>,
    viewerIsStaff: Boolean = false,
    onSendFriendRequest: (DirectoryEntry) -> Unit,
    onAcceptFriendRequest: (Friendship) -> Unit,
    onRemoveOrDeclineFriendship: (Friendship) -> Unit,
    onMessageUser: (userId: String, name: String, colorHex: String, tier: StaffTier) -> Unit,
    onOpenUserProfile: (String) -> Unit = {},
    userProfile: UserProfile? = null,
    companyThread: DirectThread? = null,
    onOpenCompanyDm: () -> Unit = {},
    onStartTeamOneOnOneDm: (userId: String, name: String, colorHex: String, tier: StaffTier) -> Unit = { _, _, _, _ -> }
) {
    val nowMillis = rememberPresenceClockMillis()
    val directoryByUid = remember(directoryEntries) {
        directoryEntries.associateBy { it.userId }
    }

    if (userProfile?.companySubAccount == true) {
        val teamMemberIds = remember(companyThread?.participantIds) {
            companyThread?.participantIds.orEmpty()
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
                item(key = "company_roster_header") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Business,
                                        contentDescription = "Company Team Friends List",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${userProfile.companyName.ifBlank { "Company" }} Team",
                                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${teamMemberIds.size} members in your company group chat",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Button(
                                onClick = onOpenCompanyDm,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("team_friends_open_company_dm_button")
                            ) {
                                Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Open Company Group DM")
                            }
                        }
                    }
                }

                if (teamMemberIds.isEmpty()) {
                    item(key = "company_roster_empty") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Syncing your company group members…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                } else {
                    items(teamMemberIds, key = { "team_member_$it" }) { memberUid ->
                        val entry = directoryByUid[memberUid]
                        val displayName = entry?.effectiveDisplayName()
                            ?: companyThread?.participantNamesById?.get(memberUid)?.takeIf { it.isNotBlank() }
                            ?: if (memberUid == currentUserId) userProfile.displayName else "Team Member"
                        val isOwner = companyThread?.companyOwnerId == memberUid || companyThread?.effectiveGroupAdminId() == memberUid
                        val isSupervisor = companyThread?.isGroupSupervisor(memberUid) == true
                        val isDnd = entry?.doNotDisturb == true
                        val status = entry?.displayedStatusFor(viewerIsStaff = false, nowMillis = nowMillis) ?: PresenceStatus.OFFLINE

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("company_team_member_$memberUid")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                SpeakingAvatarCircle(
                                    displayName = displayName,
                                    colorHex = entry?.avatarColorHex ?: "#0088FF",
                                    speakingLevel = 0f,
                                    micActive = false,
                                    size = 44.dp,
                                    photoBase64 = entry?.photoBase64.orEmpty(),
                                    userId = memberUid
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PresenceStatusDot(status = status)
                                        Text(
                                            text = if (memberUid == currentUserId) "$displayName (You)" else displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                    }
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val roleLabel = when {
                                            isOwner -> "OWNER"
                                            isSupervisor -> "SUPERVISOR"
                                            else -> "EMPLOYEE"
                                        }
                                        val roleColor = when {
                                            isOwner -> MaterialTheme.colorScheme.primary
                                            isSupervisor -> ModAmber
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = roleColor.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = roleLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = roleColor,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (isDnd) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = LiveCoral.copy(alpha = 0.15f)
                                            ) {
                                                Text(
                                                    text = "DND",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = LiveCoral,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                if (memberUid != currentUserId) {
                                    OutlinedButton(
                                        onClick = {
                                            onStartTeamOneOnOneDm(
                                                memberUid,
                                                displayName,
                                                entry?.avatarColorHex ?: "#0088FF",
                                                entry?.tierEnum() ?: StaffTier.USER
                                            )
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("team_member_dm_button_$memberUid")
                                    ) {
                                        Text(
                                            text = "DM",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return
    }

    var searchQuery by remember { mutableStateOf("") }
    val directoryPhotoByUid = remember(directoryEntries) {
        directoryEntries.associate { it.userId to it.photoBase64 }
    }

    val incomingRequests = remember(friendships, currentUserId) {
        friendships.filter { it.isIncomingFor(currentUserId) }
    }
    val acceptedFriends = remember(friendships, currentUserId, searchQuery) {
        val q = searchQuery.trim()
        friendships.filter { f ->
            val otherTier = f.otherTier(currentUserId)
            f.isAccepted() && (
                q.isEmpty() ||
                    f.otherName(currentUserId).contains(q, ignoreCase = true) ||
                    (otherTier != StaffTier.ADMIN && f.otherHandle(currentUserId).contains(q, ignoreCase = true))
                )
        }
    }
    val outgoingRequests = remember(friendships, currentUserId) {
        friendships.filter { it.isOutgoingFor(currentUserId) }
    }

    val connectedUserIds = remember(friendships, currentUserId) {
        friendships.map { it.otherUserId(currentUserId) }.toSet() + currentUserId
    }

    val discoverPeople = remember(directoryEntries, connectedUserIds, searchQuery) {
        val q = searchQuery.trim()
        directoryEntries.filter { entry ->
            !connectedUserIds.contains(entry.userId) && (
                q.isEmpty() ||
                    entry.displayName.contains(q, ignoreCase = true) ||
                    (entry.tierEnum() != StaffTier.ADMIN && entry.handle.contains(q, ignoreCase = true))
                )
        }
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
            item(key = "friends_header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Friends",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "${friendships.count { it.isAccepted() }} friends · ${incomingRequests.size} pending requests",
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
                                    imageVector = Icons.Rounded.Group,
                                    contentDescription = "Friends hub",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(40) },
                        placeholder = { Text(stringResource(R.string.hint_search_friends)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search friends",
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
                            .testTag("friends_search_input")
                    )
                }
            }

            // Incoming Friend Requests
            if (incomingRequests.isNotEmpty()) {
                item(key = "incoming_header") {
                    Text(
                        text = "Friend Requests (${incomingRequests.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(incomingRequests, key = { "in_${it.id}" }) { req ->
                    val otherId = req.otherUserId(currentUserId)
                    val name = req.otherName(currentUserId)
                    val handle = req.otherHandle(currentUserId)
                    val color = req.otherColorHex(currentUserId)
                    val tier = req.otherTier(currentUserId)
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
                                photoBase64 = directoryPhotoByUid[otherId].orEmpty()
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val presenceStatus = directoryByUid[otherId]?.displayedStatusFor(
                                    viewerIsStaff = viewerIsStaff,
                                    nowMillis = nowMillis
                                ) ?: PresenceStatus.OFFLINE
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresenceStatusDot(
                                        status = presenceStatus,
                                        modifier = Modifier.testTag("friend_request_presence_$otherId")
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
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onAcceptFriendRequest(req) },
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("accept_friend_${req.id}")
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = "Accept", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Accept", style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(
                                    onClick = { onRemoveOrDeclineFriendship(req) },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Decline request",
                                        tint = LiveCoral
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Accepted Friends List
            item(key = "my_friends_header") {
                Text(
                    text = "My Friends (${acceptedFriends.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            if (acceptedFriends.isEmpty()) {
                item(key = "friends_empty") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.empty_friends_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.empty_friends_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(acceptedFriends, key = { "fr_${it.id}" }) { friend ->
                    val otherId = friend.otherUserId(currentUserId)
                    val name = friend.otherName(currentUserId)
                    val handle = friend.otherHandle(currentUserId)
                    val color = friend.otherColorHex(currentUserId)
                    val tier = friend.otherTier(currentUserId)

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
                                photoBase64 = directoryPhotoByUid[otherId].orEmpty()
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val presenceStatus = directoryByUid[otherId]?.displayedStatusFor(
                                viewerIsStaff = viewerIsStaff,
                                nowMillis = nowMillis
                            ) ?: PresenceStatus.OFFLINE
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresenceStatusDot(
                                        status = presenceStatus,
                                        modifier = Modifier.testTag("friend_presence_$otherId")
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
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("chat_friend_$otherId")
                            ) {
                                Icon(Icons.Rounded.ChatBubble, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Chat", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(
                                onClick = { onRemoveOrDeclineFriendship(friend) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PersonRemove,
                                    contentDescription = "Remove $name from friends",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Outgoing Sent Requests
            if (outgoingRequests.isNotEmpty()) {
                item(key = "outgoing_header") {
                    Text(
                        text = "Sent Requests (${outgoingRequests.size})",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(outgoingRequests, key = { "out_${it.id}" }) { req ->
                    val otherId = req.otherUserId(currentUserId)
                    val name = req.otherName(currentUserId)
                    val handle = req.otherHandle(currentUserId)
                    val color = req.otherColorHex(currentUserId)
                    val tier = req.otherTier(currentUserId)
                    val presenceStatus = directoryByUid[otherId]?.displayedStatusFor(
                        viewerIsStaff = viewerIsStaff,
                        nowMillis = nowMillis
                    ) ?: PresenceStatus.OFFLINE

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
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
                                photoBase64 = directoryPhotoByUid[otherId].orEmpty()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresenceStatusDot(
                                        status = presenceStatus,
                                        modifier = Modifier.testTag("outgoing_friend_presence_$otherId")
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
                                    text = if (tier == StaffTier.ADMIN) {
                                        "Request pending"
                                    } else {
                                        "@$handle · Request pending"
                                    },
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

            // Find & Add Friends (Directory)
            item(key = "discover_header") {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Add Friends (${discoverPeople.size})",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (discoverPeople.isEmpty()) {
                item(key = "discover_empty") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No new people match your search right now. Anyone who signs into VOX appears here automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(discoverPeople, key = { "dir_${it.userId}" }) { person ->
                    val tier = person.tierEnum()
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenUserProfile(person.userId) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SpeakingAvatarCircle(
                                displayName = person.displayName,
                                colorHex = person.avatarColorHex,
                                speakingLevel = 0f,
                                micActive = false,
                                size = 46.dp,
                                photoBase64 = person.photoBase64
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            val presenceStatus = person.displayedStatusFor(
                                viewerIsStaff = viewerIsStaff,
                                nowMillis = nowMillis
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PresenceStatusDot(
                                        status = presenceStatus,
                                        modifier = Modifier.testTag("directory_presence_${person.userId}")
                                    )
                                    Text(
                                        text = person.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = usernameColor(tier, isPro = person.isProActive(), userId = person.userId),
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
                                        "@${person.handle}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (tier == StaffTier.ADMIN) {
                                        when (presenceStatus) {
                                            PresenceStatus.ONLINE -> PresenceOnlineGreen
                                            PresenceStatus.IDLE -> ModAmber
                                            PresenceStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            onMessageUser(
                                                person.userId,
                                                person.displayName,
                                                person.avatarColorHex,
                                                tier
                                            )
                                        },
                                        modifier = Modifier.testTag("message_person_${person.userId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ChatBubble,
                                            contentDescription = "Message ${person.displayName}",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onSendFriendRequest(person) },
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("add_friend_${person.userId}")
                                ) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "friends_bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}
