package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdminNotification
import com.example.data.AdminPreviewMode
import com.example.data.AlertSeverity
import com.example.data.AppBroadcast
import com.example.data.AppConfig
import com.example.data.AppPerkType
import com.example.data.BannedDevice
import com.example.data.ChatRoom
import com.example.data.CompanyCode
import com.example.data.CompanySubAccount
import com.example.data.CompanySubAccountFileNote
import com.example.data.CreatorSubscription
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.DmAccessPass
import com.example.data.DmPolicy
import com.example.data.EmployeeRequest
import com.example.data.EmployeeRequestCategory
import com.example.data.EmployeeRequestStatus
import com.example.data.Friendship
import com.example.data.PerkGrant
import com.example.data.PointTransaction
import com.example.data.PresenceStatus
import com.example.data.ProTier
import com.example.data.ProfilePost
import com.example.data.ProfileViewRecord
import com.example.data.PttChirpStyle
import com.example.data.PttThemeStyle
import com.example.data.Reel
import com.example.data.ReelChunk
import com.example.data.ReelComment
import com.example.data.ReferralRecord
import com.example.data.RefundReport
import com.example.data.RefundType
import com.example.data.ReportStatus
import com.example.data.RestrictableFeature
import com.example.data.RoomCategory
import com.example.data.RoomCoverKey
import com.example.data.RoomTransfer
import com.example.data.RoomVisibility
import com.example.data.SafetyReport
import com.example.data.SpotlightFrameColor
import com.example.data.formatUsdCents
import com.example.data.StaffCapabilities
import com.example.data.StaffInvite
import com.example.data.StaffRole
import com.example.data.StaffTier
import com.example.data.SupportTicket
import com.example.data.SupportTicketCategory
import com.example.data.SupportTicketStatus
import com.example.data.SuspensionRequest
import com.example.data.SuspensionRequestStatus
import com.example.data.UiState
import com.example.data.UserAlert
import com.example.data.UserFileNote
import com.example.data.UserFollow
import com.example.data.UserModerationFile
import com.example.data.UserProfile
import com.example.data.UserSex
import com.example.data.VoxStageRepository
import com.example.data.WorkOrder
import com.example.data.WorkOrderPriority
import com.example.data.WorkOrderStatus
import com.example.data.WorkOrderType
import com.example.data.unwrapFirestoreError
import com.example.media.LivePttSessionManager
import com.example.media.PreparedReelVideo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
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

    private val _adminPreviewMode = MutableStateFlow(AdminPreviewMode.ADMIN)
    val adminPreviewMode: StateFlow<AdminPreviewMode> = _adminPreviewMode.asStateFlow()

    val realStaffCapabilities: StateFlow<StaffCapabilities> =
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

    val staffCapabilities: StateFlow<StaffCapabilities> =
        combine(realStaffCapabilities, _adminPreviewMode) { real, mode ->
            if (!real.isAdmin || mode == AdminPreviewMode.ADMIN) {
                real
            } else if (mode == AdminPreviewMode.SUPPORT) {
                StaffCapabilities(StaffTier.SUPPORT, canEnterInvisible = true)
            } else {
                StaffCapabilities(StaffTier.USER, canEnterInvisible = false)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = if (isBootstrappedAdmin) {
                StaffCapabilities(StaffTier.ADMIN, true)
            } else {
                StaffCapabilities(StaffTier.USER, false)
            }
        )

    private val isAdminFlow = realStaffCapabilities
        .map { it.isAdmin }
        .distinctUntilChanged()

    private val isStaffFlow = realStaffCapabilities
        .map { it.isStaff }
        .distinctUntilChanged()

    val realUserProfileState: StateFlow<UiState<UserProfile?>> = repository.observeUserProfile(currentUserId)
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

    val userProfileState: StateFlow<UiState<UserProfile?>> =
        combine(realUserProfileState, realStaffCapabilities, _adminPreviewMode) { state, realCaps, mode ->
            if (state !is UiState.Success || state.data == null || !realCaps.isAdmin || !mode.isPreviewActive()) {
                state
            } else {
                val base = state.data
                val futureExpiry = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L
                val previewProfile = when (mode) {
                    AdminPreviewMode.ADMIN -> base
                    AdminPreviewMode.SUPPORT, AdminPreviewMode.FREE -> base.copy(
                        proMember = false,
                        proLifetime = false,
                        proTier = ProTier.FREE.name,
                        proExpiresAtMillis = 0L
                    )
                    AdminPreviewMode.VOX_PRO -> base.copy(
                        proMember = true,
                        proLifetime = false,
                        proTier = ProTier.PRO.name,
                        proExpiresAtMillis = futureExpiry
                    )
                    AdminPreviewMode.VIP_PRO -> base.copy(
                        proMember = true,
                        proLifetime = false,
                        proTier = ProTier.VIP_PRO.name,
                        proExpiresAtMillis = futureExpiry
                    )
                }
                UiState.Success(previewProfile)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    fun setAdminPreviewMode(mode: AdminPreviewMode) {
        if (!realStaffCapabilities.value.isAdmin) {
            _adminPreviewMode.value = AdminPreviewMode.ADMIN
            return
        }
        _adminPreviewMode.value = mode
        val msg = when (mode) {
            AdminPreviewMode.ADMIN -> "Returned to real Admin account view."
            AdminPreviewMode.SUPPORT -> "Switched to Support Staff test mode — moderation tools now simulate Support Staff."
            else -> "Previewing VOX as ${mode.label}."
        }
        _snackbarEvents.tryEmit(msg)
    }

    fun setAdminCustomIdentity(
        customDisplayName: String,
        customNameColorHex: String,
        customMembershipTier: String
    ) {
        if (!realStaffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only App Admins can customize display identity.")
            return
        }
        viewModelScope.launch {
            repository.setAdminCustomIdentity(
                customDisplayName = customDisplayName,
                customNameColorHex = customNameColorHex,
                customMembershipTier = customMembershipTier
            ).onSuccess {
                val cleared = customDisplayName.isBlank() && customNameColorHex.isBlank() && customMembershipTier.isBlank()
                _snackbarEvents.tryEmit(
                    if (cleared) "Restored your normal Admin username, color, and badge."
                    else "Custom Admin identity applied across VOX!"
                )
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update custom identity")
            }
        }
    }

    val appConfigState: StateFlow<AppConfig> = repository.observeAppConfig()
        .catch { emit(AppConfig()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = AppConfig()
        )

    val appBroadcastsState: StateFlow<List<AppBroadcast>> = repository.observeAppBroadcasts()
        .catch { error ->
            Log.w(TAG, "Error observing app broadcasts", unwrapFirestoreError(error))
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val reelsState: StateFlow<UiState<List<Reel>>> = repository.observeReels()
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing Reels", root)
            emit(UiState.Error(root.message ?: "Failed to load Reels"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    private val _isUploadingReel = MutableStateFlow(false)
    val isUploadingReel: StateFlow<Boolean> = _isUploadingReel.asStateFlow()

    val followerCountsByUserId: StateFlow<Map<String, Int>> = repository.observeAllUserFollows()
        .map { follows ->
            follows.groupingBy { it.followingId }.eachCount()
        }
        .catch { error ->
            Log.w(TAG, "Error observing all user follows for Top 10", unwrapFirestoreError(error))
            emit(emptyMap())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyMap()
        )

    val pendingRoomTransferState: StateFlow<RoomTransfer?> = repository.observePendingRoomTransfer(currentUserId)
        .catch { emit(null) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
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

    val profileViewsState: StateFlow<List<ProfileViewRecord>> =
        repository.observeMyProfileViews(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing profile views", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val myModerationFileState: StateFlow<UserModerationFile?> =
        repository.observeMyModerationFile(currentUserId)
            .catch { emit(null) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val allModerationFilesState: StateFlow<List<UserModerationFile>> =
        isStaffFlow.flatMapLatest { staff ->
            if (!staff) flowOf(emptyList())
            else repository.observeAllModerationFiles().catch { emit(emptyList()) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val suspensionRequestsState: StateFlow<List<SuspensionRequest>> =
        isStaffFlow.flatMapLatest { staff ->
            if (!staff) flowOf(emptyList())
            else repository.observeSuspensionRequests().catch { emit(emptyList()) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    private val verifiedPinsByRoomId = mutableMapOf<String, String>()

    fun setVerifiedPinForRoom(roomId: String, pin: String) {
        val clean = pin.filter { it.isDigit() }.take(4)
        if (clean.length == 4) {
            verifiedPinsByRoomId[roomId] = clean
        }
    }

    fun getVerifiedPinForRoom(roomId: String): String =
        verifiedPinsByRoomId[roomId].orEmpty()

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

    val officialVoxChatRoomState: StateFlow<ChatRoom?> = repository.observeOfficialVoxChatRoom()
        .catch { error ->
            Log.w(TAG, "Error observing official VOX CHAT room", unwrapFirestoreError(error))
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null
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
        isStaffFlow.flatMapLatest { isStaffNow ->
            repository.observeSafetyReports(currentUserId, isStaffNow)
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

    val businessHelpThreadsState: StateFlow<List<DirectThread>> =
        isStaffFlow.flatMapLatest { isStaffNow ->
            if (!isStaffNow) flowOf(emptyList())
            else repository.observeBusinessHelpThreads().catch { emit(emptyList()) }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val supportTicketsState: StateFlow<List<SupportTicket>> =
        isStaffFlow.flatMapLatest { isStaffNow ->
            repository.observeSupportTickets(currentUserId, isStaffNow)
                .catch { error ->
                    Log.w(TAG, "Error observing support tickets", unwrapFirestoreError(error))
                    emit(emptyList())
                }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val myFollowersState: StateFlow<List<UserFollow>> =
        repository.observeFollowers(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing my followers", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val myFollowingState: StateFlow<List<UserFollow>> =
        repository.observeFollowing(currentUserId)
            .catch { error ->
                Log.w(TAG, "Error observing my following", unwrapFirestoreError(error))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
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
        isStaffFlow.flatMapLatest { isStaffNow ->
            if (isStaffNow) {
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

    val allCreatorSubscriptionsState: StateFlow<List<CreatorSubscription>> =
        isStaffFlow.flatMapLatest { isStaffNow ->
            if (isStaffNow) {
                repository.observeAllCreatorSubscriptionsForStaff().catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val refundReportsState: StateFlow<List<RefundReport>> =
        isStaffFlow.flatMapLatest { isStaffNow ->
            if (isStaffNow) {
                repository.observeRefundReports().catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val perkGrantsState: StateFlow<List<PerkGrant>> =
        isStaffFlow.flatMapLatest { isStaffNow ->
            if (isStaffNow) {
                repository.observePerkGrants().catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val adminNotificationsState: StateFlow<List<AdminNotification>> =
        isAdminFlow.flatMapLatest { isAdminNow ->
            if (isAdminNow) {
                repository.observeAdminNotifications().catch { emit(emptyList()) }
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val incomingReferralState: StateFlow<ReferralRecord?> =
        repository.observeMyIncomingReferral(currentUserId)
            .catch { emit(null) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val invitedReferralsState: StateFlow<List<ReferralRecord>> =
        repository.observeMyInvitedReferrals(currentUserId)
            .catch { emit(emptyList()) }
            .stateIn(
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
            ).onSuccess {
                repository.updateOnlinePresence(true)
                repository.ensureOfficialVoxChatRoomExists()
                repository.ensureMyReferralCode()
                repository.recordDailyLoginStreak(
                    buyerName = safeName,
                    staffTier = staffCapabilities.value.tier
                ).onSuccess { outcome ->
                    if (outcome != null && outcome.awardedHalfPoint) {
                        val msg = if (outcome.convertedToFullPoint) {
                            "${outcome.streakDays}-Day Login Streak! +0.5 pt rolled into +1 full spendable point!"
                        } else {
                            "${outcome.streakDays}-Day Login Streak! Free member bonus: +0.5 pt banked!"
                        }
                        _snackbarEvents.tryEmit(msg)
                    }
                }
            }.onFailure { err ->
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
                    staffTier = caps.tier,
                    proTier = profile.effectiveProTier(caps.tier)
                )
            }
        }
        // Keep acceptedFriendIds synced on our userDirectory entry for Friends-of-Friends suggestions
        viewModelScope.launch {
            var lastSyncedFriends: List<String>? = null
            friendshipsState
                .map { list ->
                    list.filter { it.isAccepted() }
                        .map { it.otherUserId(currentUserId) }
                        .filter { it.isNotBlank() && it != currentUserId }
                        .distinct()
                        .sorted()
                }
                .distinctUntilChanged()
                .collect { acceptedIds ->
                    if (lastSyncedFriends != acceptedIds) {
                        lastSyncedFriends = acceptedIds
                        repository.syncAcceptedFriendIdsToDirectory(acceptedIds)
                    }
                }
        }
        // Track mutual DM Talk Streaks & trigger Invite-a-Friend +2 pt bonus on first live PTT clip
        viewModelScope.launch {
            LivePttSessionManager.completedPttThreadEvents.collect { threadId ->
                repository.recordLivePttTalkStreak(threadId)
                repository.completeReferralBonusOnFirstPttClip(effectiveMyProfile().displayName)
                    .onSuccess { award ->
                        if (award != null) {
                            _snackbarEvents.tryEmit(
                                "Invite bonus unlocked! You and ${award.inviterName} each earned +${award.bonusPointsEach} points!"
                            )
                        }
                    }
            }
        }
    }

    fun setOnlinePresence(online: Boolean) {
        viewModelScope.launch {
            repository.updateOnlinePresence(online)
        }
    }

    suspend fun setOnlinePresenceNow(online: Boolean) {
        repository.updateOnlinePresence(online)
    }

    private fun currentActorName(): String =
        (userProfileState.value as? UiState.Success)?.data?.effectiveDisplayName()?.ifBlank { "Administrator" }
            ?: "Administrator"

    private fun effectiveMyProfile(): UserProfile {
        val p = (userProfileState.value as? UiState.Success)?.data ?: UserProfile(
            userId = currentUserId,
            displayName = currentActorName(),
            handle = initialEmail.substringBefore("@").ifBlank { "member" },
            avatarColorHex = "#0088FF"
        )
        return p.copy(displayName = p.effectiveDisplayName())
    }

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
        val myProfile = (userProfileState.value as? UiState.Success)?.data
        if (!isAdmin && myProfile != null) {
            myProfile.featureRestrictionNotice(RestrictableFeature.DM)?.let { notice ->
                _snackbarEvents.tryEmit(notice)
                return
            }
        }
        val myBlocked = myProfile?.blockedUserIds.orEmpty()
        if (myBlocked.contains(otherUserId)) {
            _snackbarEvents.tryEmit("You have blocked $otherName. Unblock them in Safety Center to message again.")
            return
        }
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
                _snackbarEvents.tryEmit("Unlocked private chat with ${target.displayName} (\$5)! (${target.displayName} earned +1 usable point)")
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
                    DmPolicy.PAID -> "Private messages LOCKED to \$5 paid users (you earn +1 usable point per user)"
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
                profile.isFeatureRestricted(RestrictableFeature.ENTER_ROOM) -> {
                    _snackbarEvents.tryEmit(
                        profile.featureRestrictionNotice(RestrictableFeature.ENTER_ROOM)
                            ?: "Entering chat rooms is currently restricted on your account."
                    )
                    return false
                }
            }
        }
        if (room != null) {
            if (room.isOfficialVoxChatRoom() && room.statusEnum() != com.example.data.RoomStatus.LIVE) {
                _snackbarEvents.tryEmit("VOX CHAT is currently disabled by an Administrator.")
                return false
            }
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

    fun updateOfficialVoxChatControls(
        enabled: Boolean,
        hiddenFromLobby: Boolean,
        pinLocked: Boolean,
        roomPinCode: String,
        onSaved: () -> Unit = {}
    ) {
        if (!realStaffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only an App Administrator can enable, disable, hide, or configure VOX CHAT.")
            return
        }
        viewModelScope.launch {
            repository.updateOfficialVoxChatControls(
                enabled = enabled,
                hiddenFromLobby = hiddenFromLobby,
                pinLocked = pinLocked,
                roomPinCode = roomPinCode,
                actorName = currentActorName()
            ).onSuccess {
                val statusLabel = if (enabled) "Enabled" else "Disabled"
                val visLabel = if (hiddenFromLobby) "Hidden" else "Visible"
                val pinLabel = if (pinLocked) "PIN Locked ($roomPinCode)" else "No PIN"
                _snackbarEvents.tryEmit("VOX CHAT updated: $statusLabel · $visLabel · $pinLabel")
                onSaved()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update VOX CHAT settings")
            }
        }
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
        pinLocked: Boolean = false,
        roomPinCode: String = "",
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
        val privateCap = if (visibility == RoomVisibility.PRIVATE) {
            (currentProfile?.maxPrivateRoomUsers(caps.tier) ?: ChatRoom.FREE_MAX_PRIVATE_USERS).toLong()
        } else {
            0L
        }

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
                maxSpeakers = maxSpeakers,
                pinLocked = pinLocked,
                roomPinCode = roomPinCode,
                maxPrivateUsers = privateCap
            ).onSuccess { roomId ->
                _isCreatingRoom.value = false
                if (pinLocked && roomPinCode.length == 4) {
                    setVerifiedPinForRoom(roomId, roomPinCode)
                }
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
        invisibleModePreferred: Boolean,
        sex: UserSex = UserSex.UNSPECIFIED,
        age: Long = 0L
    ) {
        val allowedInvisible = if (staffCapabilities.value.effectiveCanEnterInvisible) {
            invisibleModePreferred
        } else {
            false
        }
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        if (!staffCapabilities.value.isAdmin && currentProfile != null) {
            currentProfile.featureRestrictionNotice(RestrictableFeature.EDIT_PROFILE)?.let { notice ->
                _snackbarEvents.tryEmit(notice)
                return
            }
        }
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
                sex = sex,
                age = age,
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
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        if (!staffCapabilities.value.isAdmin && currentProfile != null) {
            currentProfile.featureRestrictionNotice(RestrictableFeature.EDIT_PROFILE)?.let { notice ->
                _snackbarEvents.tryEmit(notice)
                return
            }
        }
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
            currentProfile.featureRestrictionNotice(RestrictableFeature.POSTING)?.let { notice ->
                _snackbarEvents.tryEmit(notice)
                return
            }
        }
        if (boostToTop && !caps.isStaff) {
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
                locked = false,
                boostToTop = boostToTop
            ).onSuccess {
                _isPublishingPost.value = false
                _snackbarEvents.tryEmit(
                    when {
                        boostToTop && caps.isStaff -> "Posted & boosted to the top of Discovery for 24h! (Free Staff Perk)"
                        boostToTop -> "Posted & boosted to the top of Discovery for 24h! (-5 pts / \$5)"
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
        val caps = staffCapabilities.value
        val currentPoints = (userProfileState.value as? UiState.Success)?.data?.pointsBalance ?: 0L
        if (!caps.isStaff && currentPoints < 5L) {
            _snackbarEvents.tryEmit("You need 5 points (\$5) to boost your post to the top of Discovery.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.boostProfilePost(
                post = post,
                buyerName = effectiveMyProfile().displayName,
                staffTier = caps.tier
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(
                    if (caps.isStaff) "Boosted to the top of Discovery for 24h! (Free Staff Perk)"
                    else "Boosted to the top of Discovery for 24h! (-5 pts / \$5)"
                )
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

    fun buyPoints(
        pointsToBuy: Long,
        playOrderId: String = "",
        playProductId: String = ""
    ) {
        if (_isProcessingWallet.value) return
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.buyPoints(
                pointsToBuy = pointsToBuy,
                buyerName = effectiveMyProfile().displayName,
                playOrderId = playOrderId,
                playProductId = playProductId
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit("Google Play purchase complete! Added $pointsToBuy points (${formatUsdCents(pointsToBuy * 100L)}) to your wallet.")
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not complete points purchase")
            }
        }
    }

    fun verifyAge18Plus(
        birthYear: Int,
        verificationSource: String = "Google Account",
        onVerified: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.verifyUserAge18Plus(birthYear)
                .onSuccess {
                    _snackbarEvents.tryEmit("18+ age verified via $verificationSource! You can now lock and unlock 18+ photos.")
                    onVerified()
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Age verification failed")
                }
        }
    }

    fun unlockProfilePost(
        post: ProfilePost,
        onInsufficientPoints: () -> Unit = {}
    ) {
        if (_isProcessingWallet.value) return
        if (!effectiveMyProfile().isAdult18Plus()) {
            _snackbarEvents.tryEmit("You must verify that you are 18+ to view or unlock locked photos.")
            return
        }
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
                _snackbarEvents.tryEmit("Photo unlocked for 2 points! (${post.authorName} earned +1 usable point)")
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not unlock photo")
            }
        }
    }

    fun subscribeToCreator(creatorId: String, creatorName: String) {
        if (_isProcessingWallet.value) return
        if (!effectiveMyProfile().isAdult18Plus()) {
            _snackbarEvents.tryEmit("You must verify that you are 18+ to subscribe to locked photos.")
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.subscribeToCreator(
                creatorId = creatorId,
                creatorName = creatorName,
                buyerName = effectiveMyProfile().displayName
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit("Subscribed to $creatorName for \$15/month — all locked photos unlocked! ($creatorName earned +8 usable points)")
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

    fun registerDeviceId(deviceHash: String) {
        if (deviceHash.isBlank()) return
        viewModelScope.launch {
            repository.recordUserDeviceId(deviceHash)
        }
    }

    fun observeBannedDevice(deviceHash: String): Flow<BannedDevice?> =
        repository.observeBannedDevice(deviceHash)

    fun syncSelfDeviceBanIfGloballyBanned(deviceHash: String) {
        if (deviceHash.isBlank()) return
        val profile = (realUserProfileState.value as? UiState.Success)?.data ?: return
        if (!profile.globallyBanned) return
        viewModelScope.launch {
            repository.registerSelfDeviceBanIfGloballyBanned(
                deviceHash = deviceHash,
                displayName = profile.displayName,
                reason = profile.suspensionReason
            )
        }
    }

    suspend fun fetchUserProfileOnce(targetUserId: String): UserProfile? =
        repository.fetchUserProfileOnce(targetUserId).getOrNull()

    fun updateUserFeatureRestrictions(
        targetUserId: String,
        targetName: String,
        disabledFeatures: List<String>?,
        featureSuspensions: Map<String, Long>,
        reason: String,
        onSaved: () -> Unit = {}
    ) {
        val caps = staffCapabilities.value
        if (!caps.isStaff) {
            _snackbarEvents.tryEmit("Only Support Staff or App Admins can manage feature restrictions.")
            return
        }
        viewModelScope.launch {
            repository.updateUserFeatureRestrictions(
                targetUserId = targetUserId,
                actorTier = caps.tier,
                disabledFeatures = if (caps.isAdmin) disabledFeatures else null,
                featureSuspensions = featureSuspensions,
                reason = reason
            ).onSuccess {
                val summary = if (caps.isAdmin) {
                    "Updated feature restrictions for $targetName"
                } else {
                    "Updated timed feature suspensions for $targetName"
                }
                _snackbarEvents.tryEmit(summary)
                onSaved()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update feature restrictions")
            }
        }
    }

    fun setUserMessagingDisabled(targetUserId: String, targetName: String, disabled: Boolean) {
        val caps = staffCapabilities.value
        if (!caps.isStaff) {
            _snackbarEvents.tryEmit("Only Support Staff or App Admins can disable or enable messaging.")
            return
        }
        viewModelScope.launch {
            repository.setUserMessagingDisabled(
                targetUserId = targetUserId,
                disabled = disabled,
                actorTier = caps.tier
            )
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (disabled) "Messaging disabled for $targetName"
                        else "Messaging re-enabled for $targetName"
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update messaging setting")
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
        suspensionReason: String,
        pointsBalance: Long? = null,
        proLifetime: Boolean? = null,
        disableMessaging: Boolean = false
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
                actorName = currentActorName(),
                pointsBalance = pointsBalance,
                proLifetime = proLifetime,
                disableMessaging = disableMessaging
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

    fun blockContact(
        targetUserId: String,
        targetName: String,
        onBlocked: () -> Unit = {}
    ) {
        val currentBlocked = (userProfileState.value as? UiState.Success)?.data?.blockedUserIds.orEmpty()
        viewModelScope.launch {
            repository.setUserBlocked(
                targetUserId = targetUserId,
                block = true,
                currentBlocked = currentBlocked
            ).onSuccess {
                _snackbarEvents.tryEmit("Blocked $targetName — removed from friends & stopped incoming messages")
                onBlocked()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not block user")
            }
        }
    }

    fun sendQuickReplyToThread(
        thread: DirectThread,
        replyText: String,
        onSent: () -> Unit = {}
    ) {
        val clean = replyText.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            repository.sendDirectMessage(
                thread = thread,
                senderName = effectiveMyProfile().displayName,
                senderTier = staffCapabilities.value.tier,
                text = clean
            ).onSuccess {
                _snackbarEvents.tryEmit("Quick reply sent to ${thread.displayTitle(currentUserId)}")
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send quick reply")
            }
        }
    }

    fun sendFriendAttentionAlert(
        context: android.content.Context,
        thread: DirectThread,
        onAlertSent: () -> Unit = {}
    ) {
        val remainSec = com.example.notifications.DirectMessageNotificationPrefs
            .remainingAlertCooldownSeconds(context, currentUserId)
        if (remainSec > 0) {
            val mins = remainSec / 60
            val secs = remainSec % 60
            val waitLabel = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
            _snackbarEvents.tryEmit("You can only send 1 attention alert every 5 minutes. Wait $waitLabel.")
            return
        }
        viewModelScope.launch {
            repository.sendFriendAttentionAlert(
                thread = thread,
                senderName = effectiveMyProfile().displayName
            ).onSuccess {
                com.example.notifications.DirectMessageNotificationPrefs.recordAlertSentNow(
                    context = context,
                    uid = currentUserId
                )
                val targetName = thread.otherName(currentUserId).ifBlank { "your friend" }
                _snackbarEvents.tryEmit("Sent ring & vibrate attention alert to $targetName!")
                onAlertSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send attention alert")
            }
        }
    }

    fun updateAppConfig(
        bannerTitle: String,
        bannerMessage: String,
        bannerEnabled: Boolean,
        trialLoginsEnabled: Boolean
    ) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only Administrators can update app configuration.")
            return
        }
        viewModelScope.launch {
            repository.updateAppConfig(
                bannerTitle = bannerTitle,
                bannerMessage = bannerMessage,
                bannerEnabled = bannerEnabled,
                trialLoginsEnabled = trialLoginsEnabled,
                adminName = currentActorName()
            ).onSuccess {
                _snackbarEvents.tryEmit("Saved login banner & trial login settings")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update app settings")
            }
        }
    }

    fun sendUserToRoom(
        targetUserId: String,
        targetName: String,
        targetRoom: ChatRoom,
        fromRoomId: String = "",
        reason: String = ""
    ) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only Administrators can move users between rooms.")
            return
        }
        viewModelScope.launch {
            repository.sendUserToRoom(
                targetUserId = targetUserId,
                targetRoomId = targetRoom.id,
                targetRoomTitle = targetRoom.displayTitle(),
                fromRoomId = fromRoomId,
                adminName = currentActorName(),
                reason = reason
            ).onSuccess {
                _snackbarEvents.tryEmit("Sent $targetName to \"${targetRoom.displayTitle()}\"")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not transfer user to room")
            }
        }
    }

    fun consumeRoomTransfer() {
        viewModelScope.launch {
            repository.consumeRoomTransfer(currentUserId)
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
                _snackbarEvents.tryEmit("User unblocked — messaging enabled again")
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

    // --- SUPPORT TICKETS ---

    fun createSupportTicket(
        category: SupportTicketCategory,
        subject: String,
        details: String,
        assignedStaffId: String = "",
        assignedStaffName: String = "",
        onCreated: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createSupportTicket(
                requesterName = effectiveMyProfile().displayName,
                category = category,
                subject = subject,
                details = details,
                assignedStaffId = assignedStaffId,
                assignedStaffName = assignedStaffName
            ).onSuccess {
                _snackbarEvents.tryEmit("Support ticket submitted! Our Admin & Support team has been notified.")
                onCreated()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not submit support ticket")
            }
        }
    }

    fun respondToSupportTicket(
        ticketId: String,
        status: SupportTicketStatus,
        staffResponse: String,
        onUpdated: () -> Unit = {}
    ) {
        if (!staffCapabilities.value.isStaff) {
            _snackbarEvents.tryEmit("Only Admin or Support staff can respond to tickets.")
            return
        }
        viewModelScope.launch {
            repository.respondToSupportTicket(
                ticketId = ticketId,
                status = status,
                staffResponse = staffResponse,
                staffName = currentActorName()
            ).onSuccess {
                _snackbarEvents.tryEmit("Ticket updated (${status.label})")
                onUpdated()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update support ticket")
            }
        }
    }

    fun closeMySupportTicket(ticketId: String) {
        viewModelScope.launch {
            repository.closeMySupportTicket(ticketId)
                .onSuccess { _snackbarEvents.tryEmit("Support ticket marked as resolved") }
                .onFailure { err -> _snackbarEvents.tryEmit(err.message ?: "Could not close ticket") }
        }
    }

    fun updateBio(newBio: String, onSaved: () -> Unit = {}) {
        val currentProfile = (userProfileState.value as? UiState.Success)?.data
        if (!staffCapabilities.value.isAdmin && currentProfile != null) {
            currentProfile.featureRestrictionNotice(RestrictableFeature.EDIT_PROFILE)?.let { notice ->
                _snackbarEvents.tryEmit(notice)
                return
            }
        }
        viewModelScope.launch {
            val profile = effectiveMyProfile()
            repository.updateUserBio(
                bio = newBio,
                currentProfile = profile,
                currentStaffTier = staffCapabilities.value.tier
            ).onSuccess {
                _snackbarEvents.tryEmit("Bio updated")
                onSaved()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update bio")
            }
        }
    }

    fun setAppearanceStatus(status: PresenceStatus) {
        viewModelScope.launch {
            repository.updateAppearanceStatus(status)
                .onSuccess {
                    val note = if (status == PresenceStatus.ONLINE) {
                        "Status set to Online"
                    } else {
                        "Appearing ${status.label} (Admins & Support still see true status)"
                    }
                    _snackbarEvents.tryEmit(note)
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update status")
                }
        }
    }

    fun recordAppTimeSpent(deltaSeconds: Long) {
        if (deltaSeconds <= 0L) return
        viewModelScope.launch {
            repository.incrementTimeSpentOnApp(deltaSeconds)
        }
    }

    fun sendUserToSupportChat(
        targetUser: DirectoryEntry,
        supportMember: DirectoryEntry,
        reason: String = "",
        onSent: () -> Unit = {}
    ) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only Administrators can send users to Support private chats.")
            return
        }
        viewModelScope.launch {
            repository.sendUserToSupportPrivateChat(
                targetUser = targetUser,
                supportMember = supportMember,
                adminName = currentActorName(),
                reason = reason
            ).onSuccess {
                _snackbarEvents.tryEmit(
                    "Sent ${targetUser.displayName} to private support chat with ${supportMember.displayName}"
                )
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send user to Support chat")
            }
        }
    }

    fun sendAppBroadcastToAllUsers(
        title: String,
        message: String,
        severity: AlertSeverity,
        onSent: () -> Unit = {}
    ) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only Administrators can send app-wide messages to all users.")
            return
        }
        viewModelScope.launch {
            repository.sendAppBroadcastToAllUsers(
                senderName = currentActorName(),
                title = title,
                message = message,
                severity = severity
            ).onSuccess {
                _snackbarEvents.tryEmit("App message sent to all users!")
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send app message")
            }
        }
    }

    fun sendMassVoxPrivateMessage(
        title: String,
        message: String,
        isUrgentAlert: Boolean,
        onSent: () -> Unit = {}
    ) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only Administrators can send mass VOX private messages.")
            return
        }
        viewModelScope.launch {
            repository.sendMassVoxPrivateMessageToAllUsers(
                title = title,
                message = message,
                isUrgentAlert = isUrgentAlert
            ).onSuccess { count ->
                _snackbarEvents.tryEmit("VOX private message delivered to $count users (read-only)!")
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send mass VOX private message")
            }
        }
    }

    fun deleteAppBroadcast(broadcastId: String) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only Administrators can delete app-wide messages.")
            return
        }
        viewModelScope.launch {
            repository.deleteAppBroadcast(broadcastId)
                .onSuccess {
                    _snackbarEvents.tryEmit("App broadcast deleted.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not delete broadcast")
                }
        }
    }

    // --- PRO MEMBERSHIP ($9.99/MONTH OR 10 POINTS) ---

    fun activateProMembership(
        paidWithPoints: Boolean = false,
        playOrderId: String = "",
        playProductId: String = "",
        targetTier: ProTier = ProTier.PRO
    ) {
        if (_isProcessingWallet.value) return
        val resolvedTier = if (paidWithPoints) ProTier.PRO else targetTier
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.activateProMembership(
                buyerName = effectiveMyProfile().displayName,
                staffTier = staffCapabilities.value.tier,
                paidWithPoints = paidWithPoints,
                playOrderId = playOrderId,
                playProductId = playProductId,
                targetTier = resolvedTier
            ).onSuccess {
                _isProcessingWallet.value = false
                val msg = when (resolvedTier) {
                    ProTier.BUSINESS ->
                        "Welcome to Business Account! You can now manage up to 50 employee sub-accounts, monitor sub-account DMs, and unlock all VOX Pro perks for 30 days."
                    ProTier.VIP_PRO ->
                        "Welcome to VIP Pro! Company Code + Employee PIN generator for 10 sub-accounts, Group DMs up to 25 users, and all VOX Pro perks unlocked for 30 days."
                    else ->
                        "Welcome to VOX Pro! Group DMs up to 15 users, 1-minute PTT, 30s Reels uploads, Who Viewed My Profile, and Blue username unlocked for 30 days."
                }
                _snackbarEvents.tryEmit(msg)
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not activate membership")
            }
        }
    }

    // --- REELS ---

    fun uploadReel(
        preparedVideo: PreparedReelVideo,
        caption: String,
        boostToTop: Boolean,
        onUploaded: () -> Unit = {}
    ) {
        if (_isUploadingReel.value) return
        val profile = effectiveMyProfile()
        val tier = staffCapabilities.value.tier
        if (tier != StaffTier.ADMIN) {
            profile.featureRestrictionNotice(RestrictableFeature.POSTING)?.let { notice ->
                _snackbarEvents.tryEmit(notice)
                return
            }
        }
        if (!profile.isProActive(tier)) {
            _snackbarEvents.tryEmit("Pro Membership (\$9.99/mo) is required to upload Reels.")
            return
        }
        viewModelScope.launch {
            _isUploadingReel.value = true
            repository.createReelWithChunks(
                authorName = profile.displayName,
                authorHandle = profile.handle,
                authorColorHex = profile.avatarColorHex,
                authorPhotoBase64 = profile.photoBase64,
                authorTier = tier,
                caption = caption,
                durationSec = preparedVideo.durationSec,
                thumbnailBase64 = preparedVideo.thumbnailBase64,
                chunksBase64 = preparedVideo.chunksBase64,
                boostToTop = boostToTop
            ).onSuccess {
                _isUploadingReel.value = false
                _snackbarEvents.tryEmit(
                    when {
                        boostToTop && tier.isStaff() -> "Reel uploaded & boosted to top! (Free Staff Perk)"
                        boostToTop -> "Reel uploaded & boosted to top for \$5!"
                        else -> "Reel uploaded!"
                    }
                )
                onUploaded()
            }.onFailure { err ->
                _isUploadingReel.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not upload Reel")
            }
        }
    }

    suspend fun loadReelChunks(reelId: String): Result<List<ReelChunk>> =
        repository.loadReelChunks(reelId)

    fun boostReelToTop(reel: Reel, onInsufficientPoints: () -> Unit = {}) {
        if (_isProcessingWallet.value) return
        val caps = staffCapabilities.value
        val currentPoints = (userProfileState.value as? UiState.Success)?.data?.pointsBalance ?: 0L
        if (!caps.isStaff && currentPoints < Reel.BOOST_PRICE_POINTS) {
            _snackbarEvents.tryEmit("You need 5 points (\$5) to push this Reel to the top.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.boostReelToTop(
                reel = reel,
                buyerName = effectiveMyProfile().displayName,
                staffTier = caps.tier
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(
                    if (caps.isStaff) "Reel boosted to the top of Reels for 24h! (Free Staff Perk)"
                    else "Reel boosted to the top of Reels for 24h! (-5 pts)"
                )
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not boost Reel")
            }
        }
    }

    fun toggleLikeReel(reel: Reel) {
        viewModelScope.launch {
            repository.toggleLikeReel(reel)
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update like")
                }
        }
    }

    fun deleteReel(reel: Reel) {
        viewModelScope.launch {
            repository.deleteReel(reel.id)
                .onSuccess {
                    _snackbarEvents.tryEmit("Reel deleted.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not delete Reel")
                }
        }
    }

    fun observeReelComments(reelId: String): Flow<List<ReelComment>> =
        repository.observeReelComments(reelId)

    fun addReelComment(reelId: String, text: String, onSent: () -> Unit = {}) {
        val profile = effectiveMyProfile()
        val tier = staffCapabilities.value.tier
        viewModelScope.launch {
            repository.addReelComment(
                reelId = reelId,
                authorName = profile.displayName,
                authorHandle = profile.handle,
                authorColorHex = profile.avatarColorHex,
                authorTier = tier,
                text = text
            ).onSuccess {
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not post comment")
            }
        }
    }

    fun deleteReelComment(reelId: String, commentId: String) {
        viewModelScope.launch {
            repository.deleteReelComment(reelId, commentId)
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not delete comment")
                }
        }
    }

    fun incrementReelShareCount(reelId: String) {
        viewModelScope.launch {
            repository.incrementReelShareCount(reelId)
        }
    }

    fun boostMyProfile(onInsufficientPoints: () -> Unit = {}) {
        if (_isProcessingWallet.value) return
        val caps = staffCapabilities.value
        val currentPoints = (userProfileState.value as? UiState.Success)?.data?.pointsBalance ?: 0L
        if (!caps.isStaff && currentPoints < 5L) {
            _snackbarEvents.tryEmit("You need 5 points to promote your profile to the top of Discovery.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.boostMyProfileToTop(
                buyerName = effectiveMyProfile().displayName,
                staffTier = caps.tier
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(
                    if (caps.isStaff) "Profile promoted to the top of Discovery for 24h! (Free Staff Perk)"
                    else "Profile promoted to the top of Discovery for 24h! (-5 pts)"
                )
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not promote profile")
            }
        }
    }

    fun purchaseOrSelectPttTheme(theme: PttThemeStyle, onInsufficientPoints: () -> Unit = {}) {
        if (_isProcessingWallet.value) return
        val caps = staffCapabilities.value
        val profile = (userProfileState.value as? UiState.Success)?.data ?: effectiveMyProfile()
        if (profile.ownsPttTheme(theme, caps.tier)) {
            if (profile.selectedPttThemeEnum() == theme) return
            viewModelScope.launch {
                repository.selectPttTheme(theme, staffName = profile.displayName, staffTier = caps.tier)
                    .onSuccess {
                        _snackbarEvents.tryEmit(
                            if (caps.isStaff && theme.pricePoints > 0L) {
                                "Equipped ${theme.label} PTT button! (Free Staff Perk)"
                            } else {
                                "Equipped ${theme.label} PTT button!"
                            }
                        )
                    }
                    .onFailure { err ->
                        _snackbarEvents.tryEmit(err.message ?: "Could not switch PTT theme")
                    }
            }
            return
        }
        if (profile.pointsBalance < theme.pricePoints) {
            _snackbarEvents.tryEmit("You need ${theme.pricePoints} points to unlock ${theme.label}.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.purchasePttTheme(theme, profile.displayName, staffTier = caps.tier)
                .onSuccess {
                    _isProcessingWallet.value = false
                    _snackbarEvents.tryEmit("Unlocked & equipped ${theme.label}! (-${theme.pricePoints} pts)")
                }
                .onFailure { err ->
                    _isProcessingWallet.value = false
                    _snackbarEvents.tryEmit(err.message ?: "Could not unlock PTT theme")
                }
        }
    }

    fun purchaseOrSelectPttChirp(chirp: PttChirpStyle, onInsufficientPoints: () -> Unit = {}) {
        if (_isProcessingWallet.value) return
        val caps = staffCapabilities.value
        val profile = (userProfileState.value as? UiState.Success)?.data ?: effectiveMyProfile()
        if (profile.ownsPttChirp(chirp, caps.tier)) {
            if (profile.selectedPttChirpEnum() == chirp) return
            viewModelScope.launch {
                repository.selectPttChirp(chirp, staffName = profile.displayName, staffTier = caps.tier)
                    .onSuccess {
                        _snackbarEvents.tryEmit(
                            if (caps.isStaff && chirp.pricePoints > 0L) {
                                "Equipped ${chirp.label} walkie chirp! (Free Staff Perk)"
                            } else {
                                "Equipped ${chirp.label} walkie chirp!"
                            }
                        )
                    }
                    .onFailure { err ->
                        _snackbarEvents.tryEmit(err.message ?: "Could not switch walkie chirp")
                    }
            }
            return
        }
        if (profile.pointsBalance < chirp.pricePoints) {
            _snackbarEvents.tryEmit("You need ${chirp.pricePoints} points to unlock ${chirp.label}.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.purchasePttChirp(chirp, profile.displayName, staffTier = caps.tier)
                .onSuccess {
                    _isProcessingWallet.value = false
                    _snackbarEvents.tryEmit("Unlocked & equipped ${chirp.label}! (-${chirp.pricePoints} pts)")
                }
                .onFailure { err ->
                    _isProcessingWallet.value = false
                    _snackbarEvents.tryEmit(err.message ?: "Could not unlock walkie chirp")
                }
        }
    }

    fun buySpotlightFrame(color: SpotlightFrameColor, onInsufficientPoints: () -> Unit = {}) {
        if (_isProcessingWallet.value) return
        val caps = staffCapabilities.value
        val profile = (userProfileState.value as? UiState.Success)?.data ?: effectiveMyProfile()
        if (!caps.isStaff && profile.pointsBalance < SpotlightFrameColor.PRICE_POINTS) {
            _snackbarEvents.tryEmit("You need 5 points to activate a 24-Hour Glowing Neon Spotlight Frame.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.purchaseSpotlightFrame(
                color = color,
                buyerName = profile.displayName,
                staffTier = caps.tier
            ).onSuccess {
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(
                    if (caps.isStaff) "${color.label} Spotlight Frame active for 24 hours! (Free Staff Perk)"
                    else "${color.label} Spotlight Frame active for 24 hours across Discover & Chats! (-5 pts)"
                )
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not activate Spotlight Frame")
            }
        }
    }

    fun redeemReferralCode(codeInput: String) {
        if (_isProcessingWallet.value) return
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.redeemReferralCode(
                codeInput = codeInput,
                myName = effectiveMyProfile().displayName
            ).onSuccess { inviterName ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(
                    "Invite linked to $inviterName! Send your first PTT clip in any DM to unlock +2 points each."
                )
            }.onFailure { err ->
                _isProcessingWallet.value = false
                _snackbarEvents.tryEmit(err.message ?: "Could not redeem invite code")
            }
        }
    }

    fun unlockProfileViewers(onInsufficientPoints: () -> Unit = {}) {
        if (_isProcessingWallet.value) return
        val caps = staffCapabilities.value
        val profile = (userProfileState.value as? UiState.Success)?.data ?: effectiveMyProfile()
        if (!caps.isStaff && profile.pointsBalance < 2L) {
            _snackbarEvents.tryEmit("You need 2 points to unlock Who Viewed Your Profile.")
            onInsufficientPoints()
            return
        }
        viewModelScope.launch {
            _isProcessingWallet.value = true
            repository.unlockProfileViewers(profile.displayName, staffTier = caps.tier)
                .onSuccess {
                    _isProcessingWallet.value = false
                    _snackbarEvents.tryEmit(
                        if (caps.isStaff) "Unlocked Who Viewed Your Profile for 24 hours! (Free Staff Perk)"
                        else "Unlocked Who Viewed Your Profile for 24 hours! (-2 pts)"
                    )
                }
                .onFailure { err ->
                    _isProcessingWallet.value = false
                    _snackbarEvents.tryEmit(err.message ?: "Could not unlock profile viewers")
                }
        }
    }

    fun setPriorityChirpOptIn(optIn: Boolean) {
        viewModelScope.launch {
            repository.updatePriorityChirpOptIn(optIn)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (optIn) "Close friends can now send you Priority Break-In Chirps."
                        else "Priority Break-In Chirps turned off."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Priority Chirp setting")
                }
        }
    }

    fun verifyAge13Plus(birthYear: Int, onVerified: () -> Unit = {}) {
        viewModelScope.launch {
            repository.verifyUserAge13Plus(birthYear)
                .onSuccess { age ->
                    _snackbarEvents.tryEmit(
                        if (age >= 18) "Age verified ($age+): Full VOX access & purchases unlocked."
                        else "Age verified (13+): Welcome to VOX! Pro & Points purchases are enabled."
                    )
                    onVerified()
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Age verification failed")
                }
        }
    }

    fun observeUserFileNotes(targetUserId: String): Flow<List<UserFileNote>> =
        repository.observeUserFileNotes(targetUserId)

    fun acknowledgeStrikeWarning() {
        viewModelScope.launch {
            repository.acknowledgeStrikeWarning()
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not acknowledge warning")
                }
        }
    }

    fun resolveUserInvestigation(
        targetUserId: String,
        targetName: String,
        resolutionNote: String,
        resetReportCount: Boolean,
        onResolved: () -> Unit = {}
    ) {
        val caps = staffCapabilities.value
        if (!caps.isStaff) {
            _snackbarEvents.tryEmit("Only Support or Admin can resolve investigations.")
            return
        }
        viewModelScope.launch {
            repository.resolveUserInvestigation(
                targetUserId = targetUserId,
                targetName = targetName,
                resolverName = effectiveMyProfile().displayName,
                resolverTier = caps.tier,
                resolutionNote = resolutionNote,
                resetReportCount = resetReportCount
            ).onSuccess {
                _snackbarEvents.tryEmit("Investigation resolved for $targetName and logged to their file.")
                onResolved()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not resolve investigation")
            }
        }
    }

    fun submitSupportSuspensionRequest(
        targetUserId: String,
        targetName: String,
        reason: String,
        roomId: String = "",
        requestType: String = "SUSPENSION",
        isBusinessAccount: Boolean = false,
        onSubmitted: () -> Unit = {}
    ) {
        val caps = staffCapabilities.value
        if (!caps.isStaff) {
            _snackbarEvents.tryEmit("Only Support Staff or Admins can submit a moderation review request.")
            return
        }
        viewModelScope.launch {
            repository.submitSupportSuspensionRequest(
                targetUserId = targetUserId,
                targetName = targetName,
                requestedByName = effectiveMyProfile().displayName,
                requesterTier = caps.tier,
                reason = reason,
                roomId = roomId,
                requestType = requestType,
                isBusinessAccount = isBusinessAccount
            ).onSuccess {
                val msg = if (isBusinessAccount || requestType.startsWith("BUSINESS_")) {
                    "Business Account approval request sent to App Admins for $targetName (no changes applied yet)."
                } else {
                    "$targetName suspended immediately pending Admin review."
                }
                _snackbarEvents.tryEmit(msg)
                onSubmitted()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not submit review request")
            }
        }
    }

    fun adminRevokeBusinessAccount(
        targetUserId: String,
        targetName: String,
        reason: String
    ) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only App Administrators can remove or revoke a Business Account.")
            return
        }
        viewModelScope.launch {
            repository.adminRevokeBusinessAccount(
                targetUserId = targetUserId,
                targetName = targetName,
                adminName = effectiveMyProfile().displayName,
                reason = reason
            ).onSuccess {
                _snackbarEvents.tryEmit("Business Account removed for $targetName and company codes deactivated.")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not remove Business Account")
            }
        }
    }

    fun adminReviewSuspensionRequest(
        request: SuspensionRequest,
        decision: SuspensionRequestStatus,
        timedHours: Int = 24,
        adminNote: String,
        onReviewed: () -> Unit = {}
    ) {
        val caps = staffCapabilities.value
        if (!caps.isAdmin) {
            _snackbarEvents.tryEmit("Only App Administrators can review moderation approval requests.")
            return
        }
        viewModelScope.launch {
            repository.adminReviewSuspensionRequest(
                request = request,
                decision = decision,
                timedHours = timedHours,
                adminName = effectiveMyProfile().displayName,
                adminNote = adminNote
            ).onSuccess {
                _snackbarEvents.tryEmit("Admin decision saved (${decision.label}) for ${request.targetName}.")
                onReviewed()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not save Admin decision")
            }
        }
    }

    fun issueRefund(
        targetUserId: String,
        targetName: String,
        refundType: RefundType,
        itemLabel: String,
        amountCents: Long,
        transactionId: String = "",
        subscriptionId: String = "",
        dmPassId: String = "",
        reason: String,
        onIssued: () -> Unit = {}
    ) {
        val caps = staffCapabilities.value
        if (!caps.isStaff) {
            _snackbarEvents.tryEmit("Only App Admins and Support Staff can issue refunds.")
            return
        }
        viewModelScope.launch {
            repository.issueRefund(
                targetUserId = targetUserId,
                targetName = targetName,
                refundType = refundType,
                itemLabel = itemLabel,
                amountCents = amountCents,
                transactionId = transactionId,
                subscriptionId = subscriptionId,
                dmPassId = dmPassId,
                reason = reason,
                issuerName = effectiveMyProfile().displayName,
                issuerTier = caps.tier
            ).onSuccess {
                val revokedSuffix = if (refundType.revokesMembership) " Membership unsubscribed." else ""
                val reportSuffix = if (caps.tier == StaffTier.SUPPORT) " Record sent to App Admins." else ""
                _snackbarEvents.tryEmit(
                    "Refund issued (${formatUsdCents(amountCents)}) for $targetName.$revokedSuffix$reportSuffix"
                )
                onIssued()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not issue refund")
            }
        }
    }

    fun acknowledgeRefundReport(reportId: String) {
        val caps = staffCapabilities.value
        if (!caps.isAdmin) {
            _snackbarEvents.tryEmit("Only App Admins can acknowledge refund reports.")
            return
        }
        viewModelScope.launch {
            repository.acknowledgeRefundReport(reportId, effectiveMyProfile().displayName)
                .onSuccess {
                    _snackbarEvents.tryEmit("Refund report acknowledged for record keeping.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not acknowledge refund report")
                }
        }
    }

    fun markAdminNotificationRead(notifId: String) {
        if (!staffCapabilities.value.isAdmin) return
        viewModelScope.launch {
            repository.markAdminNotificationRead(notifId)
        }
    }

    fun grantAppPerk(
        targetUserId: String,
        targetName: String,
        perkType: AppPerkType,
        detailValue: String = "",
        pointsAmount: Long = 5L,
        reason: String = "",
        onGranted: () -> Unit = {}
    ) {
        val caps = staffCapabilities.value
        if (!caps.isStaff) {
            _snackbarEvents.tryEmit("Only App Admins and Support Staff can grant app perks.")
            return
        }
        viewModelScope.launch {
            repository.grantAppPerk(
                targetUserId = targetUserId,
                targetName = targetName,
                perkType = perkType,
                detailValue = detailValue,
                pointsAmount = pointsAmount,
                reason = reason,
                granterName = effectiveMyProfile().displayName,
                granterTier = caps.tier
            ).onSuccess { outcome ->
                val alertNote = when {
                    caps.tier == StaffTier.SUPPORT && outcome.proAlertTriggered ->
                        " (5-day Pro packages: ${outcome.staffProPackages5d} — App Admin notified)"
                    caps.tier == StaffTier.SUPPORT && outcome.pointsAlertTriggered ->
                        " (5-day Points given: ${outcome.staffPoints5d} pts — App Admin notified)"
                    caps.tier == StaffTier.SUPPORT && perkType.isProPackage() ->
                        " (Your 5d Pro packages: ${outcome.staffProPackages5d} / ${PerkGrant.SUPPORT_PRO_PACKAGE_THRESHOLD})"
                    caps.tier == StaffTier.SUPPORT && perkType == AppPerkType.BONUS_POINTS ->
                        " (Your 5d Points given: ${outcome.staffPoints5d} / ${PerkGrant.SUPPORT_POINTS_THRESHOLD} pts)"
                    else -> ""
                }
                _snackbarEvents.tryEmit("Granted ${perkType.label} to $targetName!$alertNote")
                onGranted()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not grant perk")
            }
        }
    }

    // --- VIP PRO COMPANY SUB-ACCOUNTS, SUPERVISORS, WORK ORDERS, REQUESTS & DND ---

    val myCompanySubAccountState: StateFlow<CompanySubAccount?> =
        realUserProfileState
            .map { state ->
                val prof = (state as? UiState.Success)?.data
                if (prof?.companySubAccount == true && prof.subAccountId.isNotBlank()) prof.subAccountId else ""
            }
            .distinctUntilChanged()
            .flatMapLatest { subId ->
                if (subId.isBlank()) {
                    flowOf(null)
                } else {
                    repository.observeMyCompanySubAccount(subId)
                        .catch { err ->
                            Log.w(TAG, "Error observing my company sub-account $subId", unwrapFirestoreError(err))
                            emit(null)
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null
            )

    val companySubAccountDmsState: StateFlow<List<DirectThread>> =
        combine(realUserProfileState, realStaffCapabilities) { profState, caps ->
            val prof = (profState as? UiState.Success)?.data
            prof != null && prof.canManageCompanySubAccounts(caps.tier)
        }
            .distinctUntilChanged()
            .flatMapLatest { canManage ->
                if (!canManage) {
                    flowOf(emptyList())
                } else {
                    repository.observeCompanySubAccountDms(currentUserId)
                        .catch { err ->
                            Log.w(TAG, "Error observing company sub-account DMs", unwrapFirestoreError(err))
                            emit(emptyList())
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val myCompanyCodesState: StateFlow<List<CompanyCode>> =
        combine(realUserProfileState, realStaffCapabilities) { profState, caps ->
            val prof = (profState as? UiState.Success)?.data
            prof != null && prof.canManageCompanySubAccounts(caps.tier)
        }
            .distinctUntilChanged()
            .flatMapLatest { canManage ->
                if (!canManage) {
                    flowOf(emptyList())
                } else {
                    repository.observeMyCompanyCodes(currentUserId)
                        .catch { err ->
                            Log.w(TAG, "Error observing company codes", unwrapFirestoreError(err))
                            emit(emptyList())
                        }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val companySubAccountsState: StateFlow<List<CompanySubAccount>> =
        combine(
            repository.observeOwnedCompanySubAccounts(currentUserId).catch { emit(emptyList()) },
            repository.observeSupervisedCompanySubAccounts(currentUserId).catch { emit(emptyList()) }
        ) { owned, supervised ->
            (owned + supervised)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val incomingWorkOrdersState: StateFlow<List<WorkOrder>> =
        repository.observeIncomingWorkOrders(currentUserId)
            .catch { err ->
                Log.w(TAG, "Error observing incoming work orders", unwrapFirestoreError(err))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val teamWorkOrdersState: StateFlow<List<WorkOrder>> =
        combine(
            repository.observeCompanyWorkOrders(currentUserId).catch { emit(emptyList()) },
            repository.observeSupervisedWorkOrders(currentUserId).catch { emit(emptyList()) }
        ) { owned, supervised ->
            (owned + supervised)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val myEmployeeRequestsState: StateFlow<List<EmployeeRequest>> =
        repository.observeMyEmployeeRequests(currentUserId)
            .catch { err ->
                Log.w(TAG, "Error observing my employee requests", unwrapFirestoreError(err))
                emit(emptyList())
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList()
            )

    val teamEmployeeRequestsState: StateFlow<List<EmployeeRequest>> =
        combine(
            repository.observeCompanyEmployeeRequests(currentUserId).catch { emit(emptyList()) },
            repository.observeSupervisedEmployeeRequests(currentUserId).catch { emit(emptyList()) }
        ) { owned, supervised ->
            (owned + supervised)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt?.toDate()?.time ?: 0L }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    fun openOrCreateCompanyGroupDm(
        companyName: String,
        onReady: (String) -> Unit
    ) {
        val me = effectiveMyProfile()
        val caps = staffCapabilities.value
        if (!me.canManageCompanySubAccounts(caps.tier)) {
            _snackbarEvents.tryEmit("VIP Pro or Business Account membership is required to create a Company Workspace.")
            return
        }
        viewModelScope.launch {
            repository.ensureOrCreateCompanyGroupThread(
                ownerProfile = me,
                ownerTier = caps.tier,
                companyName = companyName.ifBlank { "${me.effectiveDisplayName()} Team" }
            ).onSuccess { threadId ->
                onReady(threadId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not open Company Group DM")
            }
        }
    }

    fun createCompanySubAccount(
        companyName: String,
        subName: String,
        roleTitle: String,
        onCreated: (CompanySubAccount) -> Unit = {},
        onBusinessUpgradeRequired: () -> Unit = {}
    ) {
        val me = effectiveMyProfile()
        val caps = staffCapabilities.value
        if (!me.canManageCompanySubAccounts(caps.tier)) {
            _snackbarEvents.tryEmit("VIP Pro or Business Account membership is required to create sub-names.")
            return
        }
        viewModelScope.launch {
            repository.createCompanySubAccount(
                ownerProfile = me,
                ownerTier = caps.tier,
                companyName = companyName,
                subName = subName,
                roleTitle = roleTitle
            ).onSuccess { created ->
                _snackbarEvents.tryEmit(
                    "Created \"${created.subName}\" · Code: ${created.companyCode} · Unique PIN: ${created.joinPin}"
                )
                onCreated(created)
            }.onFailure { err ->
                val msg = err.message ?: "Could not create sub-name"
                if (msg.startsWith("BUSINESS_UPGRADE_REQUIRED")) {
                    onBusinessUpgradeRequired()
                } else {
                    _snackbarEvents.tryEmit(msg)
                }
            }
        }
    }

    fun updateCompanySubAccountCapabilities(
        subAccount: CompanySubAccount,
        pttEnabled: Boolean,
        messagingEnabled: Boolean,
        micEnabled: Boolean,
        videoEnabled: Boolean
    ) {
        viewModelScope.launch {
            repository.updateCompanySubAccountCapabilities(
                subAccount = subAccount,
                pttEnabled = pttEnabled,
                messagingEnabled = messagingEnabled,
                micEnabled = micEnabled,
                videoEnabled = videoEnabled
            ).onSuccess {
                _snackbarEvents.tryEmit("Updated permissions for \"${subAccount.subName}\".")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update sub-account permissions")
            }
        }
    }

    fun joinBusinessStaffHelp(
        thread: DirectThread,
        invisible: Boolean = false,
        onEntered: (String) -> Unit
    ) {
        if (!staffCapabilities.value.isStaff) {
            _snackbarEvents.tryEmit("Only VOX Support Staff and App Admins can enter Group DM Help & Report sessions.")
            return
        }
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.joinBusinessStaffHelp(thread, me.effectiveDisplayName(), invisible = invisible)
                .onSuccess {
                    val label = thread.groupName.ifBlank { "Group DM" }
                    _snackbarEvents.tryEmit(
                        if (invisible) "Entered $label in Invisible Staff Mode."
                        else "Entered $label visibly to assist."
                    )
                    onEntered(thread.id)
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not enter Group DM")
                }
        }
    }

    fun endBusinessStaffHelp(threadId: String) {
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.endBusinessStaffHelp(threadId, me.effectiveDisplayName())
                .onSuccess {
                    _snackbarEvents.tryEmit("Closed VOX Staff help session.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not close staff help session")
                }
        }
    }

    fun staffRemoveUserFromBusinessAccount(
        businessOwnerId: String,
        targetUserId: String,
        targetName: String,
        reason: String,
        reportId: String = ""
    ) {
        val caps = staffCapabilities.value
        if (!caps.isStaff) {
            _snackbarEvents.tryEmit("Only VOX Support Staff and App Admins can perform this action.")
            return
        }
        viewModelScope.launch {
            repository.staffRemoveUserFromBusinessAccount(
                businessOwnerId = businessOwnerId,
                targetUserId = targetUserId,
                targetName = targetName,
                reason = reason,
                actorTier = caps.tier
            ).onSuccess {
                if (reportId.isNotBlank()) {
                    repository.resolveSafetyReport(
                        reportId = reportId,
                        newStatus = ReportStatus.RESOLVED,
                        resolutionNote = "Removed $targetName from the Business Account and permanently revoked their unique PIN."
                    )
                }
                _snackbarEvents.tryEmit("Removed $targetName from the Business Account and disabled their unique PIN.")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not remove user from Business Account")
            }
        }
    }

    fun adminBanReportedUserDevice(
        targetUserId: String,
        targetName: String,
        reason: String,
        reportId: String = ""
    ) {
        if (!staffCapabilities.value.isAdmin) {
            _snackbarEvents.tryEmit("Only App Admins can ban a user's device.")
            return
        }
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.adminBanReportedUserDevice(
                targetUserId = targetUserId,
                targetName = targetName,
                reason = reason,
                adminName = me.effectiveDisplayName()
            ).onSuccess {
                if (reportId.isNotBlank()) {
                    repository.resolveSafetyReport(
                        reportId = reportId,
                        newStatus = ReportStatus.RESOLVED,
                        resolutionNote = "Device banned by App Admin (${me.effectiveDisplayName()}): $reason"
                    )
                }
                _snackbarEvents.tryEmit("Device banned for $targetName — they cannot sign in again on any email.")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not ban user's device")
            }
        }
    }

    fun startCompanySubOneOnOneDm(
        otherUserId: String,
        otherName: String,
        otherColorHex: String = "#0088FF",
        otherTier: StaffTier = StaffTier.USER,
        onThreadReady: (String) -> Unit
    ) {
        val me = effectiveMyProfile()
        if (!me.companySubAccount || me.parentCompanyOwnerId.isBlank() || me.companyThreadId.isBlank()) {
            _snackbarEvents.tryEmit("Only active company sub-accounts can start company 1-on-1 DMs.")
            return
        }
        viewModelScope.launch {
            repository.startOrGetCompanySubOneOnOneDm(
                myProfile = me,
                myTier = staffCapabilities.value.tier,
                companyOwnerId = me.parentCompanyOwnerId,
                otherUserId = otherUserId,
                otherName = otherName,
                otherColorHex = otherColorHex,
                otherTier = otherTier
            ).onSuccess { threadId ->
                onThreadReady(threadId)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not open 1-on-1 company DM")
            }
        }
    }

    fun banCompanySubAccount(subAccount: CompanySubAccount, reason: String) {
        viewModelScope.launch {
            repository.banCompanySubAccount(subAccount, reason)
                .onSuccess {
                    _snackbarEvents.tryEmit("Banned \"${subAccount.subName}\" from your Company Workspace.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not ban sub-account")
                }
        }
    }

    fun suspendCompanySubAccount(
        subAccount: CompanySubAccount,
        durationMillis: Long,
        reason: String
    ) {
        viewModelScope.launch {
            repository.suspendCompanySubAccount(subAccount, durationMillis, reason)
                .onSuccess {
                    val label = if (durationMillis == 0L) "Lifted suspension for \"${subAccount.subName}\"."
                    else "Suspended \"${subAccount.subName}\"."
                    _snackbarEvents.tryEmit(label)
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update sub-account suspension")
                }
        }
    }

    fun buzzCompanySubAccountPhone(subAccount: CompanySubAccount, message: String) {
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.buzzCompanySubAccountPhone(
                subAccount = subAccount,
                ownerName = me.effectiveDisplayName(),
                message = message
            ).onSuccess {
                _snackbarEvents.tryEmit("Buzzed ${subAccount.subName}'s phone!")
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not buzz employee phone")
            }
        }
    }

    fun observeSubAccountFileNotes(subAccountId: String): Flow<List<CompanySubAccountFileNote>> =
        repository.observeSubAccountFileNotes(subAccountId)
            .catch { err ->
                Log.w(TAG, "Error observing sub-account file notes $subAccountId", unwrapFirestoreError(err))
                emit(emptyList())
            }

    fun addSubAccountFileNote(
        subAccount: CompanySubAccount,
        category: String,
        note: String,
        onAdded: () -> Unit = {}
    ) {
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.addSubAccountFileNote(
                subAccount = subAccount,
                authorName = me.effectiveDisplayName(),
                category = category,
                note = note
            ).onSuccess {
                _snackbarEvents.tryEmit("Saved private file note for ${subAccount.subName}.")
                onAdded()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not save file note")
            }
        }
    }

    fun deleteSubAccountFileNote(subAccountId: String, noteId: String) {
        viewModelScope.launch {
            repository.deleteSubAccountFileNote(subAccountId, noteId)
                .onSuccess {
                    _snackbarEvents.tryEmit("File note removed.")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not delete file note")
                }
        }
    }

    fun claimCompanySubAccount(
        subNameInput: String,
        accessCodeInput: String,
        myEmail: String = initialEmail,
        onSuccess: (CompanySubAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.claimCompanySubAccount(
                subNameInput = subNameInput,
                accessCodeInput = accessCodeInput,
                myEmail = myEmail
            ).onSuccess { claimed ->
                _snackbarEvents.tryEmit("Signed into ${claimed.companyName} as ${claimed.subName}!")
                onSuccess(claimed)
            }.onFailure { err ->
                val msg = err.message ?: "Could not link company sub-account"
                _snackbarEvents.tryEmit(msg)
                onError(msg)
            }
        }
    }

    fun saveCompanyCodeAndPin(
        companyName: String,
        companyCodeInput: String,
        pinInput: String,
        ownerEmail: String = initialEmail,
        onSaved: (CompanyCode) -> Unit = {}
    ) {
        val me = effectiveMyProfile()
        val caps = staffCapabilities.value
        viewModelScope.launch {
            repository.saveCompanyCodeAndPin(
                ownerProfile = me,
                ownerTier = caps.tier,
                ownerEmail = ownerEmail.ifBlank { initialEmail },
                companyName = companyName,
                companyCodeInput = companyCodeInput,
                pinInput = pinInput
            ).onSuccess { saved ->
                _snackbarEvents.tryEmit("Company Code ${saved.companyCode} & PIN saved!")
                onSaved(saved)
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not save Company Code & PIN")
            }
        }
    }

    fun joinCompanyWithCodeAndPin(
        companyCodeInput: String,
        pinInput: String,
        firstName: String,
        lastName: String,
        myEmail: String = initialEmail,
        onSuccess: (CompanySubAccount) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.joinCompanyWithCodeAndPin(
                companyCodeInput = companyCodeInput,
                pinInput = pinInput,
                firstNameInput = firstName,
                lastNameInput = lastName,
                myEmail = myEmail
            ).onSuccess { joined ->
                _snackbarEvents.tryEmit("Joined ${joined.companyName} as ${joined.subName}!")
                onSuccess(joined)
            }.onFailure { err ->
                val msg = err.message ?: "Could not join company with Code & PIN"
                _snackbarEvents.tryEmit(msg)
                onError(msg)
            }
        }
    }

    fun revokeCompanySubAccount(subAccount: CompanySubAccount) {
        viewModelScope.launch {
            repository.revokeCompanySubAccount(subAccount)
                .onSuccess {
                    _snackbarEvents.tryEmit("Revoked sub-account \"${subAccount.subName}\".")
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not revoke sub-account")
                }
        }
    }

    fun setCompanySubAccountSupervisor(
        thread: DirectThread,
        targetUserId: String,
        targetName: String,
        makeSupervisor: Boolean
    ) {
        viewModelScope.launch {
            repository.setGroupDmSupervisor(thread, targetUserId, targetName, makeSupervisor)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (makeSupervisor) "$targetName appointed as Supervisor (Group Admin)."
                        else "Removed $targetName as Supervisor."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update supervisor")
                }
        }
    }

    fun setDoNotDisturb(enabled: Boolean) {
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.setDoNotDisturb(enabled, me.subAccountId)
                .onSuccess {
                    _snackbarEvents.tryEmit(
                        if (enabled) "Do Not Disturb ON — Urgent work alerts will still come through."
                        else "Do Not Disturb OFF — All alerts restored."
                    )
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update Do Not Disturb")
                }
        }
    }

    fun updateSubAccountDisplayName(newName: String, onSaved: () -> Unit = {}) {
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.updateSubAccountDisplayName(
                newDisplayName = newName,
                subAccountId = me.subAccountId,
                companyThreadId = me.companyThreadId
            )
                .onSuccess {
                    _snackbarEvents.tryEmit("Display name updated to \"${newName.trim()}\".")
                    onSaved()
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update display name")
                }
        }
    }

    fun sendWorkOrder(
        companyOwnerId: String,
        companyThreadId: String,
        supervisorUids: List<String>,
        targetUserId: String,
        targetName: String,
        orderType: WorkOrderType,
        priority: WorkOrderPriority,
        title: String,
        body: String,
        onSent: () -> Unit = {}
    ) {
        val me = effectiveMyProfile()
        val senderRole = if (companyOwnerId == currentUserId) "OWNER" else "SUPERVISOR"
        viewModelScope.launch {
            repository.sendWorkOrder(
                companyOwnerId = companyOwnerId,
                threadId = companyThreadId,
                supervisorUids = supervisorUids,
                senderName = me.effectiveDisplayName(),
                senderRole = senderRole,
                targetUserId = targetUserId,
                targetName = targetName,
                orderType = orderType,
                priority = priority,
                title = title,
                body = body
            ).onSuccess {
                val label = if (priority == WorkOrderPriority.URGENT) "URGENT ${orderType.label}" else orderType.label
                _snackbarEvents.tryEmit("$label sent to $targetName.")
                onSent()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not send work order")
            }
        }
    }

    fun updateWorkOrderStatus(orderId: String, status: WorkOrderStatus) {
        viewModelScope.launch {
            repository.updateWorkOrderStatus(orderId, status)
                .onSuccess {
                    val msg = when (status) {
                        WorkOrderStatus.ACKNOWLEDGED -> "Work order acknowledged."
                        WorkOrderStatus.COMPLETED -> "Work order marked completed!"
                        WorkOrderStatus.OPEN -> "Work order updated."
                    }
                    _snackbarEvents.tryEmit(msg)
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Could not update work order")
                }
        }
    }

    fun submitEmployeeRequest(
        companyOwnerId: String,
        companyThreadId: String,
        supervisorUids: List<String>,
        category: EmployeeRequestCategory,
        subject: String,
        details: String,
        urgent: Boolean,
        onSubmitted: () -> Unit = {}
    ) {
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.submitEmployeeRequest(
                companyOwnerId = companyOwnerId,
                threadId = companyThreadId,
                supervisorUids = supervisorUids,
                requesterName = me.effectiveDisplayName(),
                category = category,
                subject = subject,
                details = details,
                urgent = urgent
            ).onSuccess {
                _snackbarEvents.tryEmit("Request submitted to your Company Owner & Supervisors.")
                onSubmitted()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not submit request")
            }
        }
    }

    fun reviewEmployeeRequest(
        requestId: String,
        status: EmployeeRequestStatus,
        note: String,
        onReviewed: () -> Unit = {}
    ) {
        val me = effectiveMyProfile()
        viewModelScope.launch {
            repository.reviewEmployeeRequest(
                requestId = requestId,
                status = status,
                reviewerName = me.effectiveDisplayName(),
                note = note
            ).onSuccess {
                _snackbarEvents.tryEmit("Request marked as ${status.label}.")
                onReviewed()
            }.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Could not update request")
            }
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val STOP_TIMEOUT_MS = 5000L
    }
}
