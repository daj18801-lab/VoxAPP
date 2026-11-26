package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ChatRoom
import com.example.data.RoomCategory
import com.example.data.RoomCoverKey
import com.example.data.RoomStatus
import com.example.data.RoomVisibility
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.ui.components.EnterRoomPinDialog
import com.example.ui.components.LivePulseBadge
import com.example.ui.components.SafetyTagChip
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopBarSafetyShieldButton
import com.example.ui.components.coverDrawableRes
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.VoiceCyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    publicRoomsState: UiState<List<ChatRoom>>,
    myRoomsState: UiState<List<ChatRoom>>,
    userProfile: UserProfile?,
    staffCapabilities: StaffCapabilities,
    searchQuery: String,
    selectedCategory: RoomCategory?,
    showOnlyMyRooms: Boolean,
    isCreatingRoom: Boolean,
    currentUserId: String,
    onSearchChange: (String) -> Unit,
    onCategorySelected: (RoomCategory?) -> Unit,
    onToggleMyRooms: (Boolean) -> Unit,
    onToggleInvisiblePreference: (Boolean) -> Unit,
    onOpenRoom: (ChatRoom) -> Unit,
    onRoomPinVerified: (roomId: String, pin: String) -> Unit = { _, _ -> },
    onEditRoom: (room: ChatRoom, newTitle: String, newTopic: String) -> Unit,
    onCloseRoom: (ChatRoom) -> Unit,
    onCreateRoom: (
        title: String,
        topic: String,
        category: RoomCategory,
        coverKey: RoomCoverKey,
        visibility: RoomVisibility,
        videoEnabled: Boolean,
        autoModEnabled: Boolean,
        autoModBlockMode: Boolean,
        linksBlocked: Boolean,
        slowModeSeconds: Long,
        maxSpeakers: Long,
        pinLocked: Boolean,
        roomPinCode: String
    ) -> Unit,
    safetyBadgeCount: Int = 0,
    onOpenSafety: () -> Unit = {}
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var roomBeingEdited by remember { mutableStateOf<ChatRoom?>(null) }
    var roomPendingClose by remember { mutableStateOf<ChatRoom?>(null) }
    var roomPendingPin by remember { mutableStateOf<ChatRoom?>(null) }

    val canCreateRooms = staffCapabilities.isAdmin || (
        userProfile?.globallyBanned != true &&
            userProfile?.currentlySuspended() != true &&
            userProfile?.disableCreateRoom != true
        )
    val myLiveRoom = remember(myRoomsState, currentUserId) {
        (myRoomsState as? UiState.Success)?.data?.firstOrNull {
            it.hostId == currentUserId && it.statusEnum() == RoomStatus.LIVE
        }
    }
    val hasActiveRoomLimit = !staffCapabilities.isAdmin && myLiveRoom != null
    val invisibleActive = staffCapabilities.effectiveCanEnterInvisible && (userProfile?.invisibleModePreferred == true)

    val activeState = if (showOnlyMyRooms) myRoomsState else publicRoomsState
    val filteredRooms = remember(activeState, searchQuery, selectedCategory) {
        val list = (activeState as? UiState.Success)?.data.orEmpty()
        list.filter { room ->
            val matchesCategory = selectedCategory == null || room.categoryEnum() == selectedCategory
            val q = searchQuery.trim()
            val matchesQuery = q.isEmpty() ||
                room.displayTitle().contains(q, ignoreCase = true) ||
                room.topic.contains(q, ignoreCase = true) ||
                room.hostName.contains(q, ignoreCase = true)
            matchesCategory && matchesQuery
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "VOX",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                StaffTierBadge(tier = staffCapabilities.tier)
                            }
                            Text(
                                text = "Live voice, video & moderated chat lounges",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showCreateSheet = true },
                                enabled = canCreateRooms,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LiveCoral,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier.testTag("go_live_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Create live room",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_create_room),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                            TopBarSafetyShieldButton(
                                badgeCount = safetyBadgeCount,
                                onClick = onOpenSafety
                            )
                        }
                    }

                    // Stealth Ghost Entry toggle for Main Admin & Authorized Support Staff
                    if (staffCapabilities.effectiveCanEnterInvisible) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (invisibleActive) {
                                usernameColor(staffCapabilities.tier).copy(alpha = 0.14f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (invisibleActive) {
                                    usernameColor(staffCapabilities.tier).copy(alpha = 0.55f)
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleInvisiblePreference(!invisibleActive) }
                                .testTag("lobby_ghost_mode_toggle")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (invisibleActive) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Ghost entry",
                                        tint = if (invisibleActive) usernameColor(staffCapabilities.tier)
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (invisibleActive) "Ghost Entry Active (Invisible)" else "Ghost Entry Off (Visible)",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = if (invisibleActive) usernameColor(staffCapabilities.tier)
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (staffCapabilities.isAdmin) {
                                                "Enter any room unseen — Admin can still take exclusive mic control while invisible"
                                            } else {
                                                "Enter rooms unseen (switch to Visible inside a room to use mic)"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = invisibleActive,
                                    onCheckedChange = onToggleInvisiblePreference
                                )
                            }
                        }
                    }

                    // Active Account Restrictions Banner (for restricted regular/support users)
                    if (!staffCapabilities.isAdmin && userProfile != null) {
                        val restrictionNotes = buildList {
                            if (userProfile.globallyBanned) add("Account Banned")
                            if (userProfile.currentlySuspended()) {
                                add("Suspended (${userProfile.remainingSuspensionLabel()} left)")
                            }
                            if (userProfile.disableCreateRoom) add("Room Creation Disabled")
                            if (userProfile.disableEnterRoom) add("Room Entry Disabled")
                            if (userProfile.disableMic) add("Mic Disabled")
                            if (userProfile.disableVideo) add("Video Disabled")
                        }
                        if (restrictionNotes.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = LiveCoral.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "Active Account Restrictions",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = LiveCoral
                                    )
                                    Text(
                                        text = restrictionNotes.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (userProfile.currentlySuspended() && userProfile.suspensionReason.isNotBlank()) {
                                        Text(
                                            text = "Reason: ${userProfile.suspensionReason}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ModAmber
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "search_and_scope") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.hint_search_rooms),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search rooms",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("room_search_input")
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = !showOnlyMyRooms,
                            onClick = { onToggleMyRooms(false) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            modifier = Modifier.testTag("filter_all_rooms")
                        ) {
                            Text("Public Live")
                        }
                        SegmentedButton(
                            selected = showOnlyMyRooms,
                            onClick = { onToggleMyRooms(true) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            modifier = Modifier.testTag("filter_my_rooms")
                        ) {
                            Text("My Rooms")
                        }
                    }
                }
            }

            item(key = "category_chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text("All Stages") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("category_all")
                        )
                    }
                    items(RoomCategory.entries) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                onCategorySelected(if (selectedCategory == cat) null else cat)
                            },
                            label = { Text(cat.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("category_${cat.name.lowercase()}")
                        )
                    }
                }
            }

            when (activeState) {
                is UiState.Loading -> {
                    item(key = "loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VoiceCyan)
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "error") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = activeState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(18.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (filteredRooms.isEmpty()) {
                        item(key = "empty") {
                            EmptyRoomsCard(
                                isSearchActive = searchQuery.isNotBlank() || selectedCategory != null,
                                canCreate = canCreateRooms,
                                onCreateClick = { showCreateSheet = true }
                            )
                        }
                    } else {
                        items(filteredRooms, key = { it.id }) { room ->
                            val isBouncedOut = !staffCapabilities.isAdmin && room.isBouncedOut(currentUserId)
                            val isBannedFromRoom = !staffCapabilities.isAdmin && room.bannedUserIds.contains(currentUserId)
                            LiveRoomCard(
                                room = room,
                                isHost = room.hostId == currentUserId,
                                canManageRoom = room.canEditOrCloseRoom(currentUserId, staffCapabilities.tier),
                                isBouncedOut = isBouncedOut,
                                bounceRemainingLabel = room.remainingBounceLabel(currentUserId),
                                onClick = {
                                    if (!isBouncedOut && !isBannedFromRoom &&
                                        room.requiresPinForUser(currentUserId, staffCapabilities.tier)
                                    ) {
                                        roomPendingPin = room
                                    } else {
                                        onOpenRoom(room)
                                    }
                                },
                                onEditRoomClick = { roomBeingEdited = room },
                                onCloseRoomClick = { roomPendingClose = room }
                            )
                        }
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showCreateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { if (!isCreatingRoom) showCreateSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            CreateRoomSheetContent(
                isSubmitting = isCreatingRoom,
                existingLiveRoomTitle = if (hasActiveRoomLimit) myLiveRoom?.displayTitle() else null,
                onCancel = { showCreateSheet = false },
                onSubmit = { title, topic, category, coverKey, visibility, videoEnabled, autoModEnabled, autoModBlockMode, linksBlocked, slowModeSeconds, maxSpeakers, pinLocked, roomPinCode ->
                    onCreateRoom(
                        title,
                        topic,
                        category,
                        coverKey,
                        visibility,
                        videoEnabled,
                        autoModEnabled,
                        autoModBlockMode,
                        linksBlocked,
                        slowModeSeconds,
                        maxSpeakers,
                        pinLocked,
                        roomPinCode
                    )
                    showCreateSheet = false
                }
            )
        }
    }

    roomPendingPin?.let { lockedRoom ->
        EnterRoomPinDialog(
            roomTitle = lockedRoom.displayTitle(),
            expectedPin = lockedRoom.roomPinCode,
            onPinVerified = { enteredPin ->
                roomPendingPin = null
                onRoomPinVerified(lockedRoom.id, enteredPin)
                onOpenRoom(lockedRoom)
            },
            onDismiss = { roomPendingPin = null }
        )
    }

    roomBeingEdited?.let { editing ->
        EditRoomNameDialog(
            room = editing,
            onSave = { newTitle, newTopic -> onEditRoom(editing, newTitle, newTopic) },
            onDeleteName = { topic -> onEditRoom(editing, "", topic) },
            onDismiss = { roomBeingEdited = null }
        )
    }

    roomPendingClose?.let { target ->
        AlertDialog(
            onDismissRequest = { roomPendingClose = null },
            title = { Text("Close Live Room?") },
            text = {
                Text("This will immediately close \"${target.displayTitle()}\" and remove all participants.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCloseRoom(target)
                        roomPendingClose = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_close_room_lobby")
                ) {
                    Text("Close Room")
                }
            },
            dismissButton = {
                TextButton(onClick = { roomPendingClose = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun LiveRoomCard(
    room: ChatRoom,
    isHost: Boolean,
    canManageRoom: Boolean,
    isBouncedOut: Boolean,
    bounceRemainingLabel: String,
    onClick: () -> Unit,
    onEditRoomClick: () -> Unit,
    onCloseRoomClick: () -> Unit
) {
    val hostTier = room.hostTierEnum()

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isBouncedOut -> LiveCoral.copy(alpha = 0.55f)
                isHost -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else -> MaterialTheme.colorScheme.outlineVariant
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("room_card_${room.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Subtle Live Dot Indicator (no colored card background)
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (isBouncedOut) LiveCoral else SafetyEmerald)
            )

            // Compact Room Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = room.displayTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (room.videoEnabled) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = "Video enabled",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (room.pinLocked) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = ModAmber.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pin,
                                    contentDescription = "PIN locked room",
                                    tint = ModAmber,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "PIN",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ModAmber
                                )
                            }
                        }
                    }
                    if (room.roomLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Room locked",
                            tint = LiveCoral,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (room.adminMicPriorityActive) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Admin mic active",
                            tint = usernameColor(StaffTier.ADMIN),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = if (isHost) "You" else room.hostName,
                        style = MaterialTheme.typography.labelSmall,
                        color = usernameColor(hostTier, isRoomLeader = true, userId = room.hostId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "· ${room.categoryEnum().label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (room.topic.isNotBlank()) {
                        Text(
                            text = "· ${room.topic}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }

                if (isBouncedOut) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = "Bounced lockout",
                            tint = LiveCoral,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Bounced · Locked out ($bounceRemainingLabel left)",
                            style = MaterialTheme.typography.labelSmall,
                            color = LiveCoral,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Compact Participant Count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = "Participants",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "${room.participantCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Room Admin / App Admin compact controls or clean entry arrow
            if (canManageRoom) {
                IconButton(
                    onClick = onEditRoomClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("edit_room_${room.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit or delete room name",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onCloseRoomClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("close_room_${room.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PowerSettingsNew,
                        contentDescription = "Close room",
                        tint = LiveCoral,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "Enter room",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyRoomsCard(
    isSearchActive: Boolean,
    canCreate: Boolean,
    onCreateClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "No rooms",
                    tint = VoiceCyan,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = if (isSearchActive) "No matching live rooms" else stringResource(R.string.empty_rooms_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (isSearchActive) {
                    "Try a different search or launch a new room for this topic."
                } else {
                    stringResource(R.string.empty_rooms_subtitle)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onCreateClick,
                enabled = canCreate,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("empty_create_room_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Start Live Room")
            }
        }
    }
}

@Composable
private fun CreateRoomSheetContent(
    isSubmitting: Boolean,
    existingLiveRoomTitle: String? = null,
    onCancel: () -> Unit,
    onSubmit: (
        title: String,
        topic: String,
        category: RoomCategory,
        coverKey: RoomCoverKey,
        visibility: RoomVisibility,
        videoEnabled: Boolean,
        autoModEnabled: Boolean,
        autoModBlockMode: Boolean,
        linksBlocked: Boolean,
        slowModeSeconds: Long,
        maxSpeakers: Long,
        pinLocked: Boolean,
        roomPinCode: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var topic by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(RoomCategory.VOICE_VIDEO) }
    var coverKey by remember { mutableStateOf(RoomCoverKey.HERO) }
    var visibility by remember { mutableStateOf(RoomVisibility.PUBLIC) }
    var videoEnabled by remember { mutableStateOf(true) }
    var autoModEnabled by remember { mutableStateOf(true) }
    var autoModBlockMode by remember { mutableStateOf(false) }
    var linksBlocked by remember { mutableStateOf(true) }
    var slowModeSeconds by remember { mutableLongStateOf(0L) }
    var pinLocked by remember { mutableStateOf(false) }
    var roomPinCode by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Launch a Live Stage",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "As the creator, you are the Room Administrator and can appoint Co-Admins, edit or delete the room name, and moderate members.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!existingLiveRoomTitle.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = LiveCoral.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_room_active_limit_banner")
            ) {
                Text(
                    text = "1-Room Limit: You already have an active room live (\"$existingLiveRoomTitle\"). Close your active public or private room before creating another.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = LiveCoral,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = {
                title = it.take(60)
                validationError = null
            },
            label = { Text("Room Title (3–60 chars)") },
            placeholder = { Text("Late Night Acoustic & Voice Lounge") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_room_title_input")
        )

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it.take(160) },
            label = { Text("Welcome Topic & Room Rules") },
            placeholder = { Text("Be respectful, raise hand to join stage, no spam.") },
            minLines = 2,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_room_topic_input")
        )

        Text("Category", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoomCategory.entries.forEach { cat ->
                FilterChip(
                    selected = category == cat,
                    onClick = {
                        category = cat
                        coverKey = when (cat) {
                            RoomCategory.MUSIC -> RoomCoverKey.MUSIC
                            RoomCategory.TECH, RoomCategory.GAMING -> RoomCoverKey.TECH
                            RoomCategory.LOUNGE, RoomCategory.LANGUAGE, RoomCategory.SAFE_SPACE -> RoomCoverKey.DEBATE
                            RoomCategory.VOICE_VIDEO -> RoomCoverKey.HERO
                        }
                    },
                    label = { Text(cat.label) }
                )
            }
        }

        Text("Stage Artwork", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoomCoverKey.entries.forEach { key ->
                FilterChip(
                    selected = coverKey == key,
                    onClick = { coverKey = key },
                    label = { Text(key.label) }
                )
            }
        }

        Text("Visibility", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoomVisibility.entries.forEach { vis ->
                FilterChip(
                    selected = visibility == vis,
                    onClick = { visibility = vis },
                    label = { Text(vis.label) },
                    modifier = Modifier.testTag("create_room_visibility_${vis.name.lowercase()}")
                )
            }
        }

        if (visibility == RoomVisibility.PRIVATE) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("private_room_tier_caps_card")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Private Room Capacity by Membership",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Free: 25 users · VOX Pro: 50 users · VIP Pro: 75 users · Elite Pro: 100 users",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Integrated Moderation & Media Controls",
                    style = MaterialTheme.typography.titleSmall,
                    color = SafetyEmerald
                )

                ToggleSettingRow(
                    title = "Enable Live Video Streaming",
                    subtitle = "Allow stage speakers to broadcast live CameraX video",
                    checked = videoEnabled,
                    onCheckedChange = { videoEnabled = it }
                )
                ToggleSettingRow(
                    title = "Auto-Mod Keyword Filter",
                    subtitle = "Automatically catch profanity & abusive terms in chat",
                    checked = autoModEnabled,
                    onCheckedChange = { autoModEnabled = it }
                )
                if (autoModEnabled) {
                    ToggleSettingRow(
                        title = "Strict Block Mode",
                        subtitle = "Block flagged messages completely instead of masking",
                        checked = autoModBlockMode,
                        onCheckedChange = { autoModBlockMode = it }
                    )
                }
                ToggleSettingRow(
                    title = "Block External Links",
                    subtitle = "Prevent phishing or spam URLs in real-time chat",
                    checked = linksBlocked,
                    onCheckedChange = { linksBlocked = it }
                )

                ToggleSettingRow(
                    title = "Lock Room with 4-Digit PIN",
                    subtitle = "Require a 4-digit PIN code for guests to enter your room",
                    checked = pinLocked,
                    onCheckedChange = { enabled ->
                        pinLocked = enabled
                        if (!enabled) roomPinCode = ""
                        validationError = null
                    }
                )
                if (pinLocked) {
                    OutlinedTextField(
                        value = roomPinCode,
                        onValueChange = { raw ->
                            roomPinCode = raw.filter { it.isDigit() }.take(4)
                            validationError = null
                        },
                        label = { Text("4-Digit Room PIN") },
                        placeholder = { Text("e.g. 2468") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("${roomPinCode.length}/4 digits") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_room_pin_input")
                    )
                }

                Text("Slow Mode Cooldown", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L to "Off", 3L to "3s", 10L to "10s", 30L to "30s").forEach { (sec, label) ->
                        FilterChip(
                            selected = slowModeSeconds == sec,
                            onClick = { slowModeSeconds = sec },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (validationError != null) {
            Text(
                text = validationError!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onCancel,
                enabled = !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (title.trim().length < 3) {
                        validationError = "Please enter a room title (at least 3 characters)."
                    } else if (pinLocked && (roomPinCode.length != 4 || !roomPinCode.all { it.isDigit() })) {
                        validationError = "Please enter a valid 4-digit PIN code (0000–9999)."
                    } else {
                        onSubmit(
                            title.trim(),
                            topic.trim(),
                            category,
                            coverKey,
                            visibility,
                            videoEnabled,
                            autoModEnabled,
                            autoModBlockMode,
                            linksBlocked,
                            slowModeSeconds,
                            6L,
                            pinLocked,
                            if (pinLocked) roomPinCode else ""
                        )
                    }
                },
                enabled = !isSubmitting && existingLiveRoomTitle.isNullOrBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LiveCoral,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1.4f)
                    .height(50.dp)
                    .testTag("confirm_create_room_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Go Live Now")
                }
            }
        }
    }
}

@Composable
fun ToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
