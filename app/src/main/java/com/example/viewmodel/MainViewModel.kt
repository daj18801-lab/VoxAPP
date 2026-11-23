package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AlertSeverity
import com.example.data.ChatRoom
import com.example.data.CreatorSubscription
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.DmAccessPass
import com.example.data.DmPolicy
import com.example.data.Friendship
import com.example.data.PointTransaction
import com.example.data.ProfilePost
import com.example.data.ReportStatus
import com.example.data.RoomCategory
import com.example.data.RoomCoverKey
import com.example.data.RoomVisibility
import com.example.data.SafetyReport
import com.example.data.formatUsdCents
import com.example.data.StaffCapabilities
import com.example.data.StaffInvite
import com.example.data.StaffRole
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserAlert
import com.example.data.UserProfile
import com.example.data.VoxStageRepository
import com.example.data.unwrapFirestoreError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PaidDmPromptTarget(
    val userId: String,
    val displayName: String,
    val colorHex: String,
    val tier: StaffTier,
    val onThreadReady: (String) -> Unit
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val repository: VoxStageRepository,
    val currentUserId: String,
    initialDisplayName: String,
    val initialEmail: String,
    val initialEmailVerified: Boolean
) : ViewModel() {

    private val isBootstrappedAdmin: Boolean =
        repository.isBootstrappedAdmin(initialEmail, initialEmailVerified)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<RoomCategory?>(null)
    val selectedCategory: StateFlow<RoomCategory?> = _selectedCategory.asStateFlow()

    private val _showOnlyMyRooms = MutableStateFlow(false)
    val showOnlyMyRooms: StateFlow<Boolean> = _showOnlyMyRooms.asStateFlow()

    private val _isCreatingRoom = MutableStateFlow(false)
    val isCreatingRoom: StateFlow<Boolean> = _isCreatingRoom.asStateFlow()

    private val _isPublishingPost = MutableStateFlow(false)
    val isPublishingPost: StateFlow<Boolean> = _isPublishingPost.asStateFlow()

    private val _isUpdatingPhoto = MutableStateFlow(false)
    val isUpdatingPhoto: StateFlow<Boolean> = _isUpdatingPhoto.asStateFlow()

    private val _isProcessingWallet = MutableStateFlow(false)
    val isProcessingWallet: StateFlow<Boolean> = _isProcessingWallet.asStateFlow()

    private val _pendingPaidDmTarget = MutableStateFlow<PaidDmPromptTarget?>(null)
    val pendingPaidDmTarget: StateFlow<PaidDmPromptTarget?> = _pendingPaidDmTarget.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 12)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val staffCapabilities: StateFlow<StaffCapabilities> =
        repository.observeMyStaffCapabilities(currentUserId, initialEmail, initialEmailVerified)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = if (isBootstrappedAdmin) {
                    StaffCapabilities(StaffTier.ADMIN, true)
                } else {
                    StaffCapabilities(StaffTier.USER, false)
                }
            )

    private val isAdminFlow = staffCapabilities
        .map { it.isAdmin }
        .distinctUntilChanged()

    val userProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing user profile", root)
            emit(UiState.Error(root.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    // --- SNAPCHAT-STYLE PRIVATE MESSAGES & FRIENDS LIST ---

    val userDirectoryState: StateFlow<List<DirectoryEntry>> = repository.observeUserDirectory()
        .catch { error ->
            Log.w(TAG, "Error observing public user directory", unwrapFirestoreError(error))
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val friendshipsState: StateFlow<List<Friendship>> = repository.observeMyFriendships(currentUserId)
        .catch { error ->
            Log.w(TAG, "Error observing friendships", unwrapFirestoreError(error))
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val directThreadsState: StateFlow<UiState<List<DirectThread>>> =
        repository.observeMyDirectThreads(currentUserId)
            .map<List<DirectThread>, UiState<List<DirectThread>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing direct threads", root)
                emit(UiState.Error(root.message ?: "Failed to load private chats"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val myDmAccessPassesState: StateFlow<List<DmAccessPass>> =
        repository.observeMyDmAccessPasses(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing my DM access passes", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val paidDmMembersForMeState: StateFlow<List<DmAccessPass>> =
        repository.observePaidDmMembersForMe(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing paid DM members for me", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    // --- PROFILE POSTS (MY POSTS & COMMUNITY DISCOVERY FEED) ---

    val myPostsState: StateFlow<UiState<List<ProfilePost>>> =
        repository.observePostsByUser(currentUserId)
            .map<List<ProfilePost>, UiState<List<ProfilePost>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing my profile posts", root)
                emit(UiState.Error(root.message ?: "Failed to load your posts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val recentPostsState: StateFlow<UiState<List<ProfilePost>>> =
        repository.observeRecentProfilePosts()
            .map<List<ProfilePost>, UiState<List<ProfilePost>>> { UiState.Success(it) }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                Log.w(TAG, "Error observing recent community posts", root)
                emit(UiState.Error(root.message ?: "Failed to load community posts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val publicRoomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observePublicRooms()
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing public rooms", root)
            emit(UiState.Error(root.message ?: "Failed to load live rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val myRoomsState: StateFlow<UiState<List<ChatRoom>>> = repository.observeMyRooms(currentUserId)
        .map<List<ChatRoom>, UiState<List<ChatRoom>>> { UiState.Success(it) }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing hosted rooms", root)
            emit(UiState.Error(root.message ?: "Failed to load your rooms"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val safetyReportsState: StateFlow<UiState<List<SafetyReport>>> =
        isAdminFlow.flatMapLatest { isAdminNow ->
            repository.observeSafetyReports(currentUserId, isAdminNow)
                .map<List<SafetyReport>, UiState<List<SafetyReport>>> { UiState.Success(it) }
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    Log.w(TAG, "Error observing safety reports", root)
                    emit(UiState.Error(root.message ?: "Failed to load safety reports"))
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    val unreadAlertsState: StateFlow<List<UserAlert>> =
        repository.observeMyUnreadAlerts(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing unread alerts", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    // Dynamic Admin-only collections (activates automatically for both Owner Admin and promoted App Admins)
    val staffRolesState: StateFlow<List<StaffRole>> =
        isAdminFlow.flatMapLatest { isAdminNow ->
            if (isAdminNow) {
                repository.observeAllStaffRoles().catch { error ->
                    Log.w(TAG, "Error observing staff roles", unwrapFirestoreError(error))
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val staffInvitesState: StateFlow<List<StaffInvite>> =
        isAdminFlow.flatMapLatest { isAdminNow ->
            if (isAdminNow) {
                repository.observeAllStaffInvites().catch { error ->
                    Log.w(TAG, "Error observing staff invites", unwrapFirestoreError(error))
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val allUsersState: StateFlow<List<UserProfile>> =
        isAdminFlow.flatMapLatest { isAdminNow ->
            if (isAdminNow) {
                repository.observeAllUsersForAdmin().catch { error ->
                    Log.w(TAG, "Error observing user directory", unwrapFirestoreError(error))
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val allAlertsState: StateFlow<List<UserAlert>> =
        isAdminFlow.flatMapLatest { isAdminNow ->
            if (isAdminNow) {
                repository.observeAllAlertsForAdmin().catch { error ->
                    Log.w(TAG, "Error observing all alerts", unwrapFirestoreError(error))
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    // --- POINTS WALLET, CREATOR SUBSCRIPTIONS & REVENUE LEDGER ---

    val mySubscriptionsState: StateFlow<List<CreatorSubscription>> =
        repository.observeMySubscriptions(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing subscriptions", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val myTransactionsState: StateFlow<List<PointTransaction>> =
        repository.observeMyTransactions(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing point transactions", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val allTransactionsState: StateFlow<List<PointTransaction>> =
        isAdminFlow.flatMapLatest { isAdminNow ->
            if (isAdminNow) {
                repository.observeAllTransactionsForAdmin().catch { error ->
                    Log.w(TAG, "Error observing platform transactions", unwrapFirestoreError(error))
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            val safeName = initialDisplayName.ifBlank { "Studio Host" }
            repository.ensureStaffRoleOnSignIn(
                displayName = safeName,
                email = initialEmail,
                emailVerified = initialEmailVerified
            )
            val emailPrefix = initialEmail.substringBefore("@").ifBlank { "studio" }
            repository.ensureUserProfile(
                displayName = safeName,
                emailPrefix = emailPrefix,
                staffTier = staffCapabilities.value.tier
            ).onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not initialize profile")
            }
        }
        // Keep public directory bio, photo & staffTier synced whenever our staffCapabilities changes
        viewModelScope.launch {
            staffCapabilities.collect { caps ->
                val profile = (userProfileState.value as? UiState.Success)?.data ?: return@collect
                repository.syncUserDirectoryEntry(
                    userId = currentUserId,
                    displayName = profile.displayName,
                    handle = profile.handle,
                    bio = profile.bio,
                    avatarColorHex = profile.avatarColorHex,
                    photoBase64 = profile.photoBase64,
                    staffTier = caps.tier
                )
            }
        }
    }

    private fun currentActorName(): String =
        (userProfileState.value as? UiState.Success)?.data?.displayName?.ifBlank { "Administrator" }
            ?: "Administrator"

    private fun effectiveMyProfile(): UserProfile =
        (userProfileState.value as? UiState.Success)?.data ?: UserProfile(
            userId = currentUserId,
            displayName = currentActorName(),
            handle = initialEmail.substringBefore("@").ifBlank { "member" },
            avatarColorHex = "#0088FF"
        )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCategory(category: RoomCategory?) {
        _selectedCategory.value = category
    }

    fun setShowOnlyMyRooms(onlyMine: Boolean) {
        _showOnlyMyRooms.value = onlyMine
    }

    // --- FRIENDS & PRIVATE MESSAGING ACTIONS ---

    fun sendFriendRequest(target: DirectoryEntry) {
        viewModelScope.launch {
            repository.sendFriendRequest(
                myProfile = effectiveMyProfile(),
                myTier = staffCapabilities.value.tier,
                target = target
            ).onSuccess {
                _snackbarEvents.tryEmit("Friend request sent to ${target.displayName}")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send friend request")
            }
        }
    }

    fun acceptFriendRequest(friendship: Friendship) {
        viewModelScope.launch {
            repository.acceptFriendRequest(
                friendshipId = friendship.id,
                myProfile = effectiveMyProfile(),
                myTier = staffCapabilities.value.tier
            ).onSuccess {
                _snackbarEvents.tryEmit("You and ${friendship.otherName(currentUserId)} are now friends!")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not accept friend request")
            }
        }
    }

    fun removeOrDeclineFriendship(friendship: Friendship) {
        viewModelScope.launch {
            repository.removeOrDeclineFriendship(friendship.id)
                .onSuccess {
                    val label = if (friendship.isAccepted()) {
                        "Removed ${friendship.otherName(currentUserId)} from friends"
                    } else if (friendship.isIncomingFor(currentUserId)) {
                        "Declined friend request"
                    } else {
                        "Cancelled friend request"
                    }
                    _snackbarEvents.tryEmit(label)
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Action failed")
                }
        }
    }

    fun startPrivateChat(
        otherUserId: String,
        otherName: String,
        otherColorHex: String,
        otherTier: StaffTier,
        onThreadReady: (String) -> Unit
    ) {
        val isAdmin = staffCapabilities.value.isAdmin
        val targetDir = userDirectoryState.value.firstOrNull { it.userId == otherUserId }
        val targetPolicy = targetDir?.dmPolicyEnum() ?: DmPolicy.OPEN

        if (!isAdmin) {
            if (targetPolicy == DmPolicy.DISABLED) {
                _snackbarEvents.tryEmit("$otherName has private messages turned off.")
                return
            }
            if (targetPolicy == DmPolicy.PAID) {
                val pass = myDmAccessPassesState.value.firstOrNull { it.recipientId == otherUserId }
                if (pass != null && pass.kicked) {
                    _snackbarEvents.tryEmit("$otherName removed you from private messaging them.")
                    return
                }
                if (pass == null || !pass.isCurrentlyAllowed()) {
                    _pendingPaidDmTarget.value = PaidDmPromptTarget(
                        userId = otherUserId,
                        displayName = otherName,
                        colorHex = otherColorHex,
                        tier = otherTier,
                        onThreadReady = onThreadReady
                    )
                    return
                }
            }
        }

        viewModelScope.launch {
            repository.openOrCreateDirectThread(
                myProfile = effectiveMyProfile(),
                myTier = staffCapabilities.value.tier,
                otherUserId = otherUserId,
                otherName = otherName,
                otherColorHex = otherColorHex,
                otherTier = otherTier
            ).onSuccess { threadId ->
                onThreadReady(threadId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not open private chat")
            }
        }
    }

    fun dismissPaidDmPrompt() {
        _pendingPaidDmTarget.value = null
    }

    fun confirmUnlockPaidDmAndOpenChat() {
        val target = _pendingPaidDmTarget.value ?: return
        if (_isProcessingWallet.value) return
        viewModelScope.launch {
            _isProcessingWallet.value = true
            val unlockResult = repository.unlockPaidDirectMessage(
                recipientId = target.userId,
                recipientName = target.displayName,
                buyerName = effectiveMyProfile().displayName
            )
            if (unlockResult.isFailure) {
                _isProcessingWallet.value = false
                _pendingPaidDmTarget.value = null
                _snackbarEvents.tryEmit(unlockResult.exceptionOrNull()?.message ?: "Could not unlock private messages")
                return@launch
            }
            val openResult = repository.openOrCreateDirectThread(
                myProfile = effectiveMyProfile(),
                myTier = staffCapabilities.value.tier,
                otherUserId = target.userId,
                otherName = target.displayName,
                otherColorHex = target.colorHex,
                otherTier = target.tier
            )
            _isProcessingWallet.value = false
            _pendingPaidDmTarget.value = null
            openResult.onSuccess { threadId ->
                _snackbarEvents.tryEmit("Paid \$10 to message ${target.displayName} (\$5 to ${target.displayName} · \$5 to App Owner)")
                target.onThreadReady(threadId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Unlocked, but could not open chat")
            }
        }
    }

    fun setDmPolicy(policy: DmPolicy) {
        viewModelScope.launch {
            repository.updateDmPolicy(
                policy = policy,
                currentProfile = effectiveMyProfile(),
                currentStaffTier = staffCapabilities.value.tier
            ).onSuccess {
                val msg = when (policy) {
                    DmPolicy.OPEN -> "Private messages are now OPEN to everyone"
                    DmPolicy.PAID -> "Private messages LOCKED to \$10 paid users (\$5 you / \$5 app)"
                    DmPolicy.DISABLED -> "Private messages turned OFF"
                }
                _snackbarEvents.tryEmit(msg)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update private message setting")
            }
        }
    }

    fun kickPaidDmMember(senderId: String, senderName: String) {
        viewModelScope.launch {
            repository.kickPaidDmMember(
                senderId = senderId,
                senderName = senderName,
                myName = effectiveMyProfile().displayName
            ).onSuccess {
                _snackbarEvents.tryEmit("Kicked $senderName from private messaging you")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not kick member")
            }
        }
    }

    fun addUserToPrivateChat(
        currentThread: DirectThread,
        addedUser: DirectoryEntry,
        onOpenThread: (String) -> Unit
    ) {
        viewModelScope.launch {
            repository.addUserToDirectChatOrCreateGroup(
                currentThread = currentThread,
                myProfile = effectiveMyProfile(),
                myTier = staffCapabilities.value.tier,
                addedUser = addedUser
            ).onSuccess { nextThreadId ->
                val verb = if (currentThread.isGroup) {
                    "Added ${addedUser.displayName} to the group chat"
                } else {
                    "Created group chat with ${addedUser.displayName}!"
                }
                _snackbarEvents.tryEmit(verb)
                if (nextThreadId != currentThread.id) {
                    onOpenThread(nextThreadId)
                }
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not add user to chat")
            }
        }
    }

    // --- LIVE ROOMS ACTIONS ---

    fun canEnterRoomOrNotify(room: ChatRoom? = null): Boolean {
        if (staffCapabilities.value.isAdmin) return true
        val profile = (userProfileState.value as? UiState.Success)?.data
        if (profile != null) {
            when {
                profile.globallyBanned -> {
                    _snackbarEvents.tryEmit("Your account is banned from entering rooms.")
                    return false
                }
                profile.currentlySuspended() -> {
                    val left = profile.remainingSuspensionLabel()
                    _snackbarEvents.tryEmit("Suspended for $left. ${profile.suspensionReason}".trim())
                    return false
                }
                profile.disableEnterRoom -> {
                    _snackbarEvents.tryEmit("Entering rooms has been disabled on your account by an Administrator.")
                    return false
                }
            }
        }
        if (room != null) {
            if (room.bannedUserIds.contains(currentUserId)) {
                _snackbarEvents.tryEmit("You are banned from this room.")
                return false
            }
            if (room.isBouncedOut(currentUserId)) {
                val left = room.remainingBounceLabel(currentUserId)
                _snackbarEvents.tryEmit("You were bounced from this room and cannot enter for $left.")
                return false
            }
        }
        return true
    }

    fun createRoom(
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
        onCreated: (String) -> Unit
    ) {
        if (_isCreatingRoom.value) return
        val caps = staffCapabilities.value
        val currentProfile = (userProfileState.value as? UiState.Success)?.data

        if (!caps.isAdmin && currentProfile != null) {
            if (currentProfile.globallyBanned) {
                _snackbarEvents.tryEmit("Your account is banned from creating rooms.")
                return
            }
            if (currentProfile.currentlySuspended()) {
                _snackbarEvents.tryEmit("You are suspended for ${currentProfile.remainingSuspensionLabel()}.")
                return
            }
            if (currentProfile.disableCreateRoom) {
                _snackbarEvents.tryEmit("Creating rooms has been disabled on your account by an Administrator.")
                return
            }
        }

        val hostName = currentProfile?.displayName ?: "Studio Host"

        viewModelScope.launch {
            _isCreatingRoom.value = true
            repository.createRoom(
                hostName = hostName,
                hostTier = caps.tier,
                title = title,
                topic = topic,
                category = category,
                coverKey = coverKey,
                visibility = visibility,
                videoEnabled = videoEnabled,
                autoModEnabled = autoModEnabled,
                autoModBlockMode = autoModBlockMode,
                linksBlocked = linksBlocked,
                slowModeSeconds = slowModeSeconds,
                maxSpeakers = maxSpeakers
            ).onSuccess { roomId ->
                _isCreatingRoom.value = false
                onCreated(roomId)
            }.onFailure { err ->
                _isCreatingRoom.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not create room")
            }
        }
    }

    fun editRoomDetails(room: ChatRoom, newTitle: String, newTopic: String) {
        viewModelScope.launch {
            repository.editRoomDetails(
                room = room,
                actorName = currentActorName(),
                actorTier = staffCapabilities.value.tier,
                newTitle = newTitle,
                newTopic = newTopic
            ).onSuccess {
                val trimmed = newTitle.trim()
                _snackbarEvents.tryEmit(if (trimmed.isEmpty()) "Room name deleted" else "Room renamed to \"$trimmed\"")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not edit room")
            }
        }
    }

    fun closeRoomFromLobby(room: ChatRoom) {
        viewModelScope.launch {
            repository.closeRoom(room.id, currentActorName())
                .onSuccess { _snackbarEvents.tryEmit("Closed room \"${room.displayTitle()}\"") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not close room") }
        }
    }

    fun updateProfile(
        displayName: String,
        handle: String,
        bio: String,
        avatarColorHex: String,
        pushToTalkPreferred: Boolean,
        nightModeEnabled: Boolean,
        invisibleModePreferred: Boolean
    ) {
        val allowedInvisible = if (staffCapabilities.value.effectiveCanEnterInvisible) {
            invisibleModePreferred
        } else {
            false
        }
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        val currentPhoto = currentProfile?.photoBase64.orEmpty()
        val currentPolicy = currentProfile?.dmPolicyEnum() ?: DmPolicy.OPEN
        viewModelScope.launch {
            repository.updateUserProfile(
                displayName = displayName,
                handle = handle,
                bio = bio,
                avatarColorHex = avatarColorHex,
                pushToTalkPreferred = pushToTalkPreferred,
                nightModeEnabled = nightModeEnabled,
                invisibleModePreferred = allowedInvisible,
                currentStaffTier = staffCapabilities.value.tier,
                currentPhotoBase64 = currentPhoto,
                currentDmPolicy = currentPolicy
            ).onSuccess {
                _snackbarEvents.tryEmit("Profile saved")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update profile")
            }
        }
    }

    fun updateProfilePhoto(photoBase64: String) {
        if (_isUpdatingPhoto.value) return
        viewModelScope.launch {
            _isUpdatingPhoto.value = true
            repository.updateUserProfilePhoto(
                photoBase64 = photoBase64,
                currentProfile = effectiveMyProfile(),
                currentStaffTier = staffCapabilities.value.tier
            ).onSuccess {
                _isUpdatingPhoto.value = false
                _snackbarEvents.tryEmit(
                    if (photoBase64.isBlank()) "Profile picture removed" else "Profile picture updated!"
                )
            }.onFailure { err ->
                _isUpdatingPhoto.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not update profile picture")
            }
        }
    }

    fun createProfilePost(
        text: String,
        imageBase64: String,
        locked: Boolean = false,
        boostToTop: Boolean = false,
        onInsufficientPoints: () -> Unit = {},
        onPosted: () -> Unit = {}
    ) {
        if (_isPublishingPost.value) return
        val caps = staffCapabilities.value
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        if (!caps.isAdmin && currentProfile != null) {
            if (currentProfile.globallyBanned) {
                _snackbarEvents.tryEmit("Your account is banned from posting updates.")
                return
            }
            if (currentProfile.currentlySuspended()) {
                _snackbarEvents.tryEmit("Suspended for ${currentProfile.remainingSuspensionLabel()}.")
                return
            }
        }
        if (boostToTop) {
            val currentPoints = currentProfile?.pointsBalance ?: 0L
            if (currentPoints < 5L) {
                _snackbarEvents.tryEmit("Boosting to the top of Discovery costs 5 points (\$5). Buy points in Profile Settings.")
                onInsufficientPoints()
                return
            }
        }
        viewModelScope.launch {
            _isPublishingPost.value = true
            repository.createProfilePost(
                myProfile = effectiveMyProfile(),
                myTier = caps.tier,
                text = text,
                imageBase64 = imageBase64,
                locked = locked,
                boostToTop = boostToTop
            ).onSuccess {
                _isPublishingPost.value = false
                _snackbarEvents.tryEmit(
                    when {
                        boostToTop -> "Posted & boosted to the top of Discovery for 24h! (-5 pts / \$5)"
                        locked && imageBase64.isNotBlank() -> "Locked photo posted (2 pts / \$2 to unlock)"
                        else -> "Post shared to your profile!"
                    }
                )
                onPosted()
            }.onFailure { err ->
                _isPublishingPost.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not publish post")
            }
        }
    }

    fun boostProfilePost(
        post: ProfilePost,
        onInsufficientPoints: () -> Unit = {}
    ) {
        if (_isProcessingWallet.value) return
        val currentPoints = (userProfileState.value as? UiState.Success)?.data?.pointsBalance ?: 0L
        if (currentPoints < 5L) {
            _snackbarEvents.tryEmit("You need 5 points (\$5) to boost your post to the top of Discovery.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.boostProfilePost(
                post = post,
                buyerName = effectiveMyProfile().displayName
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit("Boosted to the top of Discovery for 24h! (-5 pts / \$5)")
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not boost post")
            }
        }
    }

    fun observePostComments(postId: String) = repository.observePostComments(postId)

    fun addPostComment(
        post: ProfilePost,
        text: String,
        onSent: () -> Unit = {}
    ) {
        val caps = staffCapabilities.value
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        if (!caps.isAdmin && currentProfile != null) {
            if (currentProfile.globallyBanned) {
                _snackbarEvents.tryEmit("Your account is banned from commenting.")
                return
            }
            if (currentProfile.currentlySuspended()) {
                _snackbarEvents.tryEmit("Suspended for ${currentProfile.remainingSuspensionLabel()}.")
                return
            }
        }
        viewModelScope.launch {
            repository.addPostComment(
                post = post,
                myProfile = effectiveMyProfile(),
                myTier = caps.tier,
                text = text
            ).onSuccess {
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not post comment")
            }
        }
    }

    fun deletePostComment(postId: String, commentId: String) {
        viewModelScope.launch {
            repository.deletePostComment(postId, commentId)
                .onSuccess { _snackbarEvents.tryEmit("Comment deleted") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not delete comment") }
        }
    }

    fun shareProfilePostToFeed(
        post: ProfilePost,
        note: String = "",
        onShared: () -> Unit = {}
    ) {
        if (post.locked) {
            _snackbarEvents.tryEmit("Locked pictures cannot be shared.")
            return
        }
        val caps = staffCapabilities.value
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        if (!caps.isAdmin && currentProfile != null) {
            if (currentProfile.globallyBanned) {
                _snackbarEvents.tryEmit("Your account is banned from sharing posts.")
                return
            }
            if (currentProfile.currentlySuspended()) {
                _snackbarEvents.tryEmit("Suspended for ${currentProfile.remainingSuspensionLabel()}.")
                return
            }
        }
        viewModelScope.launch {
            repository.shareProfilePostToFeed(
                originalPost = post,
                myProfile = effectiveMyProfile(),
                myTier = caps.tier,
                note = note
            ).onSuccess {
                _snackbarEvents.tryEmit("Shared ${post.authorName}'s post to your profile & Discovery!")
                onShared()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not share post")
            }
        }
    }

    fun recordExternalPostShare(post: ProfilePost) {
        if (post.locked) {
            _snackbarEvents.tryEmit("Locked pictures cannot be shared.")
            return
        }
        viewModelScope.launch {
            repository.recordExternalPostShare(post)
        }
    }

    fun buyPoints(pointsToBuy: Long) {
        if (_isProcessingWallet.value) return
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.buyPoints(
                pointsToBuy = pointsToBuy,
                buyerName = effectiveMyProfile().displayName
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit("Added $pointsToBuy points (${formatUsdCents(pointsToBuy * 100L)}) to your wallet!")
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not complete points purchase")
            }
        }
    }

    fun unlockProfilePost(
        post: ProfilePost,
        onInsufficientPoints: () -> Unit = {}
    ) {
        if (_isProcessingWallet.value) return
        val currentPoints = (userProfileState.value as? UiState.Success)?.data?.pointsBalance ?: 0L
        if (currentPoints < 2L) {
            _snackbarEvents.tryEmit("You need 2 points (\$2) to unlock this photo. Buy points in Profile Settings.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.unlockProfilePost(
                post = post,
                buyerName = effectiveMyProfile().displayName
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit("Photo unlocked for 2 points! (\$1 to ${post.authorName}, \$1 to App Owner)")
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not unlock photo")
            }
        }
    }

    fun subscribeToCreator(creatorId: String, creatorName: String) {
        if (_isProcessingWallet.value) return
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.subscribeToCreator(
                creatorId = creatorId,
                creatorName = creatorName,
                buyerName = effectiveMyProfile().displayName
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit("Subscribed to $creatorName for \$15/month — all locked photos unlocked!")
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not activate subscription")
            }
        }
    }

    fun toggleLikePost(post: ProfilePost) {
        viewModelScope.launch {
            repository.toggleLikeProfilePost(post)
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update like")
                }
        }
    }

    fun deleteProfilePost(post: ProfilePost) {
        viewModelScope.launch {
            repository.deleteProfilePost(post)
                .onSuccess { _snackbarEvents.tryEmit("Post deleted") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not delete post") }
        }
    }

    fun toggleInvisiblePreference(enabled: Boolean) {
        if (enabled && !staffCapabilities.value.effectiveCanEnterInvisible) {
            _snackbarEvents.tryEmit("Invisible entry requires Admin authorization.")
            return
        }
        viewModelScope.launch {
            repository.setInvisibleModePreferred(enabled)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (enabled) "Ghost Entry ON — you will enter rooms invisibly"
                        else "Ghost Entry OFF — you will enter rooms visibly"
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Ghost Entry")
                }
        }
    }

    // --- ADMIN STAFF (APP ADMIN & SUPPORT), USER & ALERT MANAGEMENT ---

    fun addStaffInvite(
        email: String,
        displayName: String,
        staffTier: StaffTier,
        canEnterInvisible: Boolean
    ) {
        viewModelScope.launch {
            repository.addStaffInviteByEmail(
                email = email,
                displayName = displayName,
                staffTier = staffTier,
                canEnterInvisible = canEnterInvisible,
                adminName = currentActorName()
            ).onSuccess {
                val roleLabel = if (staffTier == StaffTier.ADMIN) "App Admin" else "Support Staff"
                _snackbarEvents.tryEmit("$roleLabel invite added for $email")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not add staff invite")
            }
        }
    }

    fun toggleSupportInviteInvisible(inviteId: String, allow: Boolean) {
        viewModelScope.launch {
            repository.updateSupportInviteInvisible(inviteId, allow)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (allow) "Invisible entry enabled for invite"
                        else "Invisible entry disabled for invite"
                    )
                }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Update failed") }
        }
    }

    fun removeSupportInvite(inviteId: String) {
        viewModelScope.launch {
            repository.removeSupportInvite(inviteId)
                .onSuccess { _snackbarEvents.tryEmit("Staff invitation removed") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not remove invite") }
        }
    }

    fun toggleSupportRoleInvisible(targetUserId: String, displayName: String, allow: Boolean) {
        viewModelScope.launch {
            repository.updateSupportInvisiblePermission(targetUserId, allow)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (allow) "Allowed $displayName to enter rooms invisibly"
                        else "Revoked invisible entry for $displayName"
                    )
                }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Update failed") }
        }
    }

    fun setUserStaffTier(
        targetUserId: String,
        targetName: String,
        tier: StaffTier,
        canEnterInvisible: Boolean
    ) {
        viewModelScope.launch {
            repository.setUserStaffRole(
                targetUserId = targetUserId,
                targetName = targetName,
                targetEmail = "",
                tier = tier,
                canEnterInvisible = canEnterInvisible,
                adminName = currentActorName()
            ).onSuccess {
                val msg = when (tier) {
                    StaffTier.ADMIN -> "$targetName is now an App Administrator"
                    StaffTier.SUPPORT -> "$targetName is now Support Staff"
                    StaffTier.USER -> "Removed staff privileges from $targetName"
                }
                _snackbarEvents.tryEmit(msg)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update staff role")
            }
        }
    }

    fun adminEditUser(
        targetUserId: String,
        displayName: String,
        handle: String,
        bio: String,
        globallyBanned: Boolean,
        disableCreateRoom: Boolean,
        disableEnterRoom: Boolean,
        disableMic: Boolean,
        disableVideo: Boolean,
        suspendedUntilMillis: Long,
        suspensionReason: String
    ) {
        viewModelScope.launch {
            repository.adminEditUserProfileAndRestrictions(
                targetUserId = targetUserId,
                displayName = displayName,
                handle = handle,
                bio = bio,
                globallyBanned = globallyBanned,
                disableCreateRoom = disableCreateRoom,
                disableEnterRoom = disableEnterRoom,
                disableMic = disableMic,
                disableVideo = disableVideo,
                suspendedUntilMillis = suspendedUntilMillis,
                suspensionReason = suspensionReason,
                actorName = currentActorName()
            ).onSuccess {
                _snackbarEvents.tryEmit("Saved changes for $displayName")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update user")
            }
        }
    }

    fun suspendUser(targetUserId: String, targetName: String, durationMinutes: Long, reason: String) {
        viewModelScope.launch {
            repository.suspendUserForMinutes(
                targetUserId = targetUserId,
                targetName = targetName,
                actorName = currentActorName(),
                actorTier = staffCapabilities.value.tier,
                durationMinutes = durationMinutes,
                reason = reason,
                room = null
            ).onSuccess {
                _snackbarEvents.tryEmit("Suspended $targetName for ${durationMinutes}m")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not suspend user")
            }
        }
    }

    fun sendDirectAlert(
        targetUserId: String,
        targetName: String,
        title: String,
        message: String,
        severity: AlertSeverity
    ) {
        viewModelScope.launch {
            repository.sendUserAlert(
                targetUserId = targetUserId,
                targetName = targetName,
                senderName = currentActorName(),
                senderTier = staffCapabilities.value.tier,
                roomId = "",
                title = title,
                message = message,
                severity = severity
            ).onSuccess {
                _snackbarEvents.tryEmit("Alert sent to $targetName")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send alert")
            }
        }
    }

    fun adminEditAlert(
        alertId: String,
        title: String,
        message: String,
        severity: AlertSeverity,
        reopenForUser: Boolean
    ) {
        viewModelScope.launch {
            repository.adminEditAlert(
                alertId = alertId,
                title = title,
                message = message,
                severity = severity,
                reopenForUser = reopenForUser
            ).onSuccess {
                _snackbarEvents.tryEmit("Alert updated")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not edit alert")
            }
        }
    }

    fun adminDeleteAlert(alertId: String) {
        viewModelScope.launch {
            repository.adminDeleteAlert(alertId)
                .onSuccess { _snackbarEvents.tryEmit("Alert deleted") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not delete alert") }
        }
    }

    fun acknowledgeAlert(alertId: String) {
        viewModelScope.launch {
            repository.acknowledgeAlert(alertId)
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not acknowledge alert") }
        }
    }

    fun unblockUser(targetUserId: String) {
        val currentBlocked = (userProfileState.value as? UiState.Success)?.data?.blockedUserIds.orEmpty()
        viewModelScope.launch {
            repository.setUserBlocked(
                targetUserId = targetUserId,
                block = false,
                currentBlocked = currentBlocked
            ).onSuccess {
                _snackbarEvents.tryEmit("User unblocked")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not unblock user")
            }
        }
    }

    fun resolveReport(reportId: String, status: ReportStatus, note: String) {
        viewModelScope.launch {
            repository.resolveSafetyReport(
                reportId = reportId,
                newStatus = status,
                resolutionNote = note
            ).onSuccess {
                _snackbarEvents.tryEmit("Report marked as ${status.label}")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update report")
            }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
