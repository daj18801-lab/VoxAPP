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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.PhonelinkErase
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.example.R
import com.example.data.AdminNotification
import com.example.data.AlertSeverity
import com.example.data.AppBroadcast
import com.example.data.AppConfig
import com.example.data.AdminPreviewMode
import com.example.data.AppPerkType
import com.example.data.BusinessApprovalAction
import com.example.data.ChatRoom
import com.example.data.CreatorSubscription
import com.example.data.DirectThread
import com.example.data.DirectoryEntry
import com.example.data.PerkGrant
import com.example.data.PointTransaction
import com.example.data.PresenceStatus
import com.example.data.ProTier
import com.example.data.RefundReport
import com.example.data.RefundType
import com.example.data.ReportStatus
import com.example.data.RoomStatus
import com.example.data.SafetyReport
import com.example.data.StaffCapabilities
import com.example.data.StaffInvite
import com.example.data.StaffRole
import com.example.data.StaffTier
import com.example.data.SuspensionRequest
import com.example.data.SuspensionRequestStatus
import com.example.data.UiState
import com.example.data.UserAlert
import com.example.data.UserFileNote
import com.example.data.UserModerationFile
import com.example.data.UserProfile
import com.example.ui.components.AdminNotificationsCard
import com.example.ui.components.AdminReviewSuspensionRequestDialog
import com.example.ui.components.AdminSandboxControlCard
import com.example.ui.components.AppPerkGrantsSection
import com.example.ui.components.BusinessAccountBadge
import com.example.ui.components.FeatureRestrictionsDialog
import com.example.ui.components.GrantAppPerkDialog
import com.example.ui.components.IssueRefundDialog
import com.example.ui.components.PendingSuspensionRequestsCard
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.RefundsManagementSection
import com.example.ui.components.RequestBusinessActionApprovalDialog
import com.example.ui.components.RequestSuspensionDialog
import com.example.ui.components.ResolveInvestigationDialog
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.SupportRequestSuspensionCard
import com.example.ui.components.SupportStaffPerkAuditSection
import com.example.ui.components.UserFileNotesDialog
import com.example.ui.components.UserModerationFilesCard
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.isBusinessGoldForStaff
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
import com.example.ui.theme.SafetyEmerald
import com.example.ui.theme.SnapBlue
import com.example.ui.theme.VoiceCyan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Composable
fun SafetyScreen(
    reportsState: UiState<List<SafetyReport>>,
    userProfile: UserProfile?,
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    staffRoles: List<StaffRole>,
    staffInvites: List<StaffInvite>,
    allUsers: List<UserProfile>,
    allAlerts: List<UserAlert>,
    appConfig: AppConfig = AppConfig(),
    liveRooms: List<ChatRoom> = emptyList(),
    onResolveReport: (reportId: String, status: ReportStatus, note: String) -> Unit,
    onUnblockUser: (String) -> Unit,
    onAddStaffInvite: (email: String, displayName: String, staffTier: StaffTier, canEnterInvisible: Boolean) -> Unit,
    onToggleSupportInviteInvisible: (inviteId: String, allow: Boolean) -> Unit,
    onRemoveSupportInvite: (inviteId: String) -> Unit,
    onToggleSupportRoleInvisible: (targetUserId: String, displayName: String, allow: Boolean) -> Unit,
    onSetUserStaffTier: (targetUserId: String, targetName: String, tier: StaffTier, canEnterInvisible: Boolean) -> Unit,
    onAdminEditUser: (
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
        pointsBalance: Long?,
        proLifetime: Boolean?,
        disableMessaging: Boolean
    ) -> Unit,
    onToggleUserMessagingDisabled: (targetUserId: String, targetName: String, disabled: Boolean) -> Unit = { _, _, _ -> },
    onSuspendUser: (targetUserId: String, targetName: String, minutes: Long, reason: String) -> Unit,
    onSendDirectAlert: (targetUserId: String, targetName: String, title: String, message: String, severity: AlertSeverity) -> Unit,
    onAdminEditAlert: (alertId: String, title: String, message: String, severity: AlertSeverity, reopenForUser: Boolean) -> Unit,
    onAdminDeleteAlert: (alertId: String) -> Unit,
    onUpdateAppConfig: (bannerTitle: String, bannerMessage: String, bannerEnabled: Boolean, trialLoginsEnabled: Boolean) -> Unit = { _, _, _, _ -> },
    onSendUserToRoom: (targetUserId: String, targetName: String, targetRoom: ChatRoom, fromRoomId: String, reason: String) -> Unit = { _, _, _, _, _ -> },
    directoryEntries: List<DirectoryEntry> = emptyList(),
    onSendUserToSupportChat: (
        targetUser: DirectoryEntry,
        supportMember: DirectoryEntry,
        reason: String,
        onSent: () -> Unit
    ) -> Unit = { _, _, _, _ -> },
    appBroadcasts: List<AppBroadcast> = emptyList(),
    onSendAppBroadcast: (
        title: String,
        message: String,
        severity: AlertSeverity,
        onSent: () -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onSendMassVoxDm: (
        title: String,
        message: String,
        isUrgentAlert: Boolean,
        onSent: () -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onDeleteAppBroadcast: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    moderationFiles: List<UserModerationFile> = emptyList(),
    onObserveUserFileNotes: (String) -> Flow<List<UserFileNote>> = { flowOf(emptyList()) },
    onResolveInvestigation: (
        targetUserId: String,
        targetName: String,
        resolutionNote: String,
        resetReportCount: Boolean,
        onResolved: () -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    suspensionRequests: List<SuspensionRequest> = emptyList(),
    onSubmitSuspensionRequest: (
        targetUserId: String,
        targetName: String,
        reason: String,
        onSubmitted: () -> Unit
    ) -> Unit = { _, _, _, _ -> },
    onAdminReviewSuspensionRequest: (
        request: SuspensionRequest,
        decision: SuspensionRequestStatus,
        timedHours: Int,
        adminNote: String,
        onReviewed: () -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    refundReports: List<RefundReport> = emptyList(),
    perkGrants: List<PerkGrant> = emptyList(),
    adminNotifications: List<AdminNotification> = emptyList(),
    allTransactions: List<PointTransaction> = emptyList(),
    allCreatorSubscriptions: List<CreatorSubscription> = emptyList(),
    onIssueRefund: (
        targetUserId: String,
        targetName: String,
        refundType: RefundType,
        itemLabel: String,
        amountCents: Long,
        transactionId: String,
        subscriptionId: String,
        reason: String,
        onIssued: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onAcknowledgeRefundReport: (String) -> Unit = {},
    onMarkAdminNotificationRead: (String) -> Unit = {},
    onGrantAppPerk: (
        targetUserId: String,
        targetName: String,
        perkType: AppPerkType,
        detailValue: String,
        pointsAmount: Long,
        reason: String,
        onGranted: () -> Unit
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    realStaffCapabilities: StaffCapabilities = staffCapabilities,
    realUserProfile: UserProfile? = userProfile,
    adminPreviewMode: AdminPreviewMode = AdminPreviewMode.ADMIN,
    onSelectAdminPreviewMode: (AdminPreviewMode) -> Unit = {},
    onSaveAdminCustomIdentity: (String, String, String) -> Unit = { _, _, _ -> },
    businessHelpThreads: List<DirectThread> = emptyList(),
    onEnterBusinessHelpDm: (DirectThread, Boolean) -> Unit = { _, _ -> },
    onEnterReportedGroupDmById: (threadId: String, invisible: Boolean) -> Unit = { _, _ -> },
    onEndBusinessHelp: (String) -> Unit = {},
    onLoadUserProfileForRestrictions: suspend (userId: String) -> UserProfile? = { null },
    onUpdateUserFeatureRestrictions: (
        targetUserId: String,
        targetName: String,
        disabledFeatures: List<String>?,
        featureSuspensions: Map<String, Long>,
        reason: String,
        onSaved: () -> Unit
    ) -> Unit = { _, _, _, _, _, _ -> },
    onRemoveUserFromBusinessAccount: (businessOwnerId: String, targetUserId: String, targetName: String, reportId: String) -> Unit = { _, _, _, _ -> },
    onAdminBanReportedDevice: (targetUserId: String, targetName: String, reason: String, reportId: String) -> Unit = { _, _, _, _ -> },
    onSubmitBusinessApprovalRequest: (
        targetUserId: String,
        targetName: String,
        action: BusinessApprovalAction,
        reason: String,
        onSubmitted: () -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onAdminRevokeBusinessAccount: (targetUserId: String, targetName: String, reason: String) -> Unit = { _, _, _ -> },
    officialVoxChatRoom: ChatRoom? = null,
    onUpdateOfficialVoxChat: (
        enabled: Boolean,
        hiddenFromLobby: Boolean,
        pinLocked: Boolean,
        roomPinCode: String,
        onSaved: () -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onOpenOfficialVoxChat: () -> Unit = {}
) {
    val reports = (reportsState as? UiState.Success)?.data.orEmpty()
    val openCount = reports.count { it.statusEnum() == ReportStatus.OPEN }
    val resolvedCount = reports.count { it.statusEnum() != ReportStatus.OPEN }
    val blockedUsers = userProfile?.blockedUserIds.orEmpty()
    val nowMillis = rememberPresenceClockMillis()
    val directoryByUid = remember(directoryEntries) {
        directoryEntries.associateBy { it.userId }
    }
    val staffDirectoryMembers = remember(directoryEntries) {
        directoryEntries.filter { it.tierEnum().isStaff() }
    }

    val staffTierByUid = remember(staffRoles) {
        staffRoles.associate { it.userId to it.tierEnum() }
    }

    var adminConsoleTab by remember { mutableIntStateOf(0) }
    var supportConsoleTab by remember { mutableIntStateOf(0) }
    var inviteEmail by remember { mutableStateOf("") }
    var inviteName by remember { mutableStateOf("") }
    var inviteTier by remember { mutableStateOf(StaffTier.SUPPORT) }
    var inviteAllowInvisible by remember { mutableStateOf(false) }
    var userSearch by remember { mutableStateOf("") }
    var businessSearch by remember { mutableStateOf("") }

    // App Banner & Trial Login Editor State (synced with Firestore AppConfig)
    var bannerTitleDraft by remember(appConfig.bannerTitle) { mutableStateOf(appConfig.bannerTitle) }
    var bannerMessageDraft by remember(appConfig.bannerMessage) { mutableStateOf(appConfig.bannerMessage) }
    var bannerEnabledDraft by remember(appConfig.bannerEnabled) { mutableStateOf(appConfig.bannerEnabled) }
    var trialLoginsEnabledDraft by remember(appConfig.trialLoginsEnabled) { mutableStateOf(appConfig.trialLoginsEnabled) }

    // Official VOX CHAT Room Admin Control Draft State
    var voxChatEnabledDraft by remember(officialVoxChatRoom?.status) {
        mutableStateOf(officialVoxChatRoom?.statusEnum() != RoomStatus.CLOSED)
    }
    var voxChatHiddenDraft by remember(officialVoxChatRoom?.hiddenFromLobby) {
        mutableStateOf(officialVoxChatRoom?.hiddenFromLobby == true)
    }
    var voxChatPinLockedDraft by remember(officialVoxChatRoom?.pinLocked) {
        mutableStateOf(officialVoxChatRoom?.pinLocked == true)
    }
    var voxChatPinDraft by remember(officialVoxChatRoom?.roomPinCode) {
        mutableStateOf(officialVoxChatRoom?.roomPinCode.orEmpty())
    }
    var voxChatPinError by remember { mutableStateOf<String?>(null) }

    var userBeingEdited by remember { mutableStateOf<UserProfile?>(null) }
    var userBeingSuspended by remember { mutableStateOf<UserProfile?>(null) }
    var userReceivingAlert by remember { mutableStateOf<UserProfile?>(null) }
    var userBeingTransferred by remember { mutableStateOf<UserProfile?>(null) }
    var userBeingSentToSupport by remember { mutableStateOf<UserProfile?>(null) }
    var alertBeingEdited by remember { mutableStateOf<UserAlert?>(null) }
    var showSendBroadcastDialog by remember { mutableStateOf(false) }
    var showMassVoxDmDialog by remember { mutableStateOf(false) }
    var inspectingModerationFile by remember { mutableStateOf<UserModerationFile?>(null) }
    var resolvingModerationFile by remember { mutableStateOf<UserModerationFile?>(null) }
    var supportRequestTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    var supportBusinessApprovalTarget by remember { mutableStateOf<Pair<DirectoryEntry, BusinessApprovalAction>?>(null) }
    var adminConfirmRevokeBusinessTarget by remember { mutableStateOf<DirectoryEntry?>(null) }
    var reviewingSuspensionRequest by remember { mutableStateOf<SuspensionRequest?>(null) }
    var showIssueRefundDialog by remember { mutableStateOf(false) }
    var showGrantPerkDialog by remember { mutableStateOf(false) }
    var confirmRemoveBizUserReport by remember { mutableStateOf<SafetyReport?>(null) }
    var confirmBanDeviceReport by remember { mutableStateOf<SafetyReport?>(null) }
    var featureRestrictionsTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var loadedRestrictionProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoadingRestrictionProfile by remember { mutableStateOf(false) }

    val profileByUid = remember(allUsers) { allUsers.associateBy { it.userId } }

    LaunchedEffect(featureRestrictionsTarget) {
        val target = featureRestrictionsTarget
        if (target == null) {
            loadedRestrictionProfile = null
            isLoadingRestrictionProfile = false
        } else {
            val cached = profileByUid[target.first]
            loadedRestrictionProfile = cached
            isLoadingRestrictionProfile = cached == null
            val fresh = onLoadUserProfileForRestrictions(target.first)
            if (fresh != null) {
                loadedRestrictionProfile = fresh
            }
            isLoadingRestrictionProfile = false
        }
    }

    // Derive all Business Accounts from directoryEntries (visible to BOTH Support Staff & App Admins)
    // and merge any Admin-loaded UserProfile records not yet in directoryEntries.
    val businessAccounts = remember(directoryEntries, allUsers, nowMillis, staffTierByUid) {
        val fromDir = directoryEntries.filter { it.isBusinessAccount(nowMillis) }
        val knownUids = fromDir.map { it.userId }.toSet()
        val extraFromUsers = allUsers
            .filter { prof ->
                val t = staffTierByUid[prof.userId] ?: StaffTier.USER
                prof.userId !in knownUids && prof.isBusinessAccount(t, nowMillis)
            }
            .map { prof ->
                DirectoryEntry(
                    id = prof.userId,
                    userId = prof.userId,
                    displayName = prof.displayName,
                    handle = prof.handle,
                    bio = prof.bio,
                    avatarColorHex = prof.avatarColorHex,
                    photoBase64 = prof.photoBase64,
                    proMember = prof.proMember,
                    proExpiresAtMillis = prof.proExpiresAtMillis,
                    proLifetime = prof.proLifetime,
                    proTier = prof.proTier,
                    disableMessaging = prof.disableMessaging,
                    companySubAccount = prof.companySubAccount
                )
            }
        (fromDir + extraFromUsers).sortedBy { it.displayName.lowercase() }
    }
    val businessUserIds = remember(businessAccounts) { businessAccounts.map { it.userId }.toSet() }
    val filteredBusinessAccounts = remember(businessAccounts, businessSearch) {
        val q = businessSearch.trim()
        if (q.isEmpty()) businessAccounts
        else businessAccounts.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                it.handle.contains(q, ignoreCase = true) ||
                it.bio.contains(q, ignoreCase = true)
        }
    }

    val filteredUsers = remember(allUsers, userSearch, staffTierByUid) {
        val q = userSearch.trim()
        if (q.isEmpty()) allUsers
        else allUsers.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                (staffTierByUid[it.userId] != StaffTier.ADMIN && it.handle.contains(q, ignoreCase = true))
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("safety_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (staffCapabilities.isAdmin) "Admin & Safety Command" else "Safety Center",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            StaffTierBadge(tier = staffCapabilities.tier)
                        }
                        Text(
                            text = if (staffCapabilities.isAdmin) {
                                "Manage Support Staff, user files, investigations, alerts, and safety reports"
                            } else {
                                "Incident reports, community shield & personal blocklist"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (realStaffCapabilities.isAdmin) {
                item(key = "admin_sandbox_control") {
                    AdminSandboxControlCard(
                        realProfile = realUserProfile,
                        previewMode = adminPreviewMode,
                        onSelectPreviewMode = onSelectAdminPreviewMode,
                        onSaveCustomIdentity = onSaveAdminCustomIdentity
                    )
                }
            }

            // Full Admin Command Console (only visible to Main App Owner Administrator)
            if (staffCapabilities.isAdmin) {
                item(key = "admin_console_header") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = usernameColor(StaffTier.ADMIN).copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, usernameColor(StaffTier.ADMIN).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AdminPanelSettings,
                                    contentDescription = "Admin console",
                                    tint = usernameColor(StaffTier.ADMIN),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "App Owner Admin Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = usernameColor(StaffTier.ADMIN)
                                )
                            }

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = adminConsoleTab == 0,
                                    onClick = { adminConsoleTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 5),
                                    modifier = Modifier.testTag("admin_tab_support")
                                ) {
                                    Text("Staff", maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = adminConsoleTab == 1,
                                    onClick = { adminConsoleTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 5),
                                    modifier = Modifier.testTag("admin_tab_users")
                                ) {
                                    Text("Users", maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = adminConsoleTab == 4,
                                    onClick = { adminConsoleTab = 4 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 5),
                                    modifier = Modifier.testTag("admin_tab_business")
                                ) {
                                    Text("Biz", maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = adminConsoleTab == 2,
                                    onClick = { adminConsoleTab = 2 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 3, count = 5),
                                    modifier = Modifier.testTag("admin_tab_alerts")
                                ) {
                                    Text("Alerts", maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = adminConsoleTab == 3,
                                    onClick = { adminConsoleTab = 3 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 4, count = 5),
                                    modifier = Modifier.testTag("admin_tab_app_config")
                                ) {
                                    Text("App", maxLines = 1)
                                }
                            }
                        }
                    }
                }

                when (adminConsoleTab) {
                    0 -> {
                        item(key = "add_support_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
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
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (inviteTier == StaffTier.ADMIN) {
                                                Icons.Rounded.AdminPanelSettings
                                            } else {
                                                Icons.Rounded.SupportAgent
                                            },
                                            contentDescription = "Add Staff",
                                            tint = usernameColor(inviteTier)
                                        )
                                        Text(
                                            text = "Invite App Admin or Support by Email",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }

                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = inviteTier == StaffTier.SUPPORT,
                                            onClick = { inviteTier = StaffTier.SUPPORT },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                            modifier = Modifier.testTag("invite_tier_support")
                                        ) {
                                            Text("Support Staff")
                                        }
                                        SegmentedButton(
                                            selected = inviteTier == StaffTier.ADMIN,
                                            onClick = { inviteTier = StaffTier.ADMIN },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                            modifier = Modifier.testTag("invite_tier_admin")
                                        ) {
                                            Text("App Admin")
                                        }
                                    }

                                    OutlinedTextField(
                                        value = inviteEmail,
                                        onValueChange = { inviteEmail = it.take(120) },
                                        label = { Text("Google Email Address") },
                                        placeholder = { Text("member@gmail.com") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("support_invite_email_input")
                                    )
                                    OutlinedTextField(
                                        value = inviteName,
                                        onValueChange = { inviteName = it.take(40) },
                                        label = { Text("Display Name (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("support_invite_name_input")
                                    )
                                    if (inviteTier == StaffTier.SUPPORT) {
                                        ToggleSettingRow(
                                            title = "Allow Invisible Room Entry",
                                            subtitle = "Let this Support member enter rooms invisibly (must be visible to use mic)",
                                            checked = inviteAllowInvisible,
                                            onCheckedChange = { inviteAllowInvisible = it }
                                        )
                                    } else {
                                        Text(
                                            text = "App Admins get full console control, user renaming, room editing, invisible entry, and exclusive mic priority.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = usernameColor(StaffTier.ADMIN)
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            if (inviteEmail.contains("@")) {
                                                onAddStaffInvite(
                                                    inviteEmail.trim(),
                                                    inviteName.trim(),
                                                    inviteTier,
                                                    inviteTier == StaffTier.ADMIN || inviteAllowInvisible
                                                )
                                                inviteEmail = ""
                                                inviteName = ""
                                                inviteAllowInvisible = false
                                            }
                                        },
                                        enabled = inviteEmail.contains("@"),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = usernameColor(inviteTier),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("add_support_invite_button")
                                    ) {
                                        Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            if (inviteTier == StaffTier.ADMIN) "Add App Admin" else "Add Support Staff"
                                        )
                                    }
                                }
                            }
                        }

                        val activeAdmins = staffRoles.filter { it.tierEnum() == StaffTier.ADMIN }
                        if (activeAdmins.isNotEmpty()) {
                            item(key = "active_admins_label") {
                                Text(
                                    text = "App Administrators (${activeAdmins.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = usernameColor(StaffTier.ADMIN)
                                )
                            }
                            items(activeAdmins, key = { "admin_role_${it.userId}" }) { role ->
                                val isOwnerAccount = role.email.equals("daj18801@gmail.com", ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, usernameColor(StaffTier.ADMIN).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = role.displayName.ifBlank { "App Admin" },
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = usernameColor(StaffTier.ADMIN)
                                                )
                                                StaffTierBadge(tier = StaffTier.ADMIN)
                                            }
                                            Text(
                                                text = if (isOwnerAccount) "Primary Owner · Details hidden" else "App Administrator · Details hidden",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (!isOwnerAccount && role.userId != currentUserId) {
                                            IconButton(
                                                onClick = {
                                                    onSetUserStaffTier(role.userId, role.displayName, StaffTier.USER, false)
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Remove App Admin",
                                                    tint = LiveCoral
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val activeSupport = staffRoles.filter { it.tierEnum() == StaffTier.SUPPORT }
                        if (activeSupport.isNotEmpty()) {
                            item(key = "active_support_label") {
                                Text(
                                    text = "Active Support Staff (${activeSupport.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = usernameColor(StaffTier.SUPPORT)
                                )
                            }
                            items(activeSupport, key = { "role_${it.userId}" }) { role ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = role.displayName.ifBlank { "Support Member" },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = usernameColor(StaffTier.SUPPORT)
                                                    )
                                                    StaffTierBadge(tier = StaffTier.SUPPORT)
                                                }
                                                if (role.email.isNotBlank()) {
                                                    Text(
                                                        text = role.email,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedButton(
                                                    onClick = {
                                                        onSetUserStaffTier(role.userId, role.displayName, StaffTier.ADMIN, true)
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Text("Make Admin", style = MaterialTheme.typography.labelSmall, color = usernameColor(StaffTier.ADMIN))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        onSetUserStaffTier(role.userId, role.displayName, StaffTier.USER, false)
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Delete,
                                                        contentDescription = "Remove support staff",
                                                        tint = LiveCoral
                                                    )
                                                }
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Allow Invisible Room Entry",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                            Switch(
                                                checked = role.canEnterInvisible,
                                                onCheckedChange = { allow ->
                                                    onToggleSupportRoleInvisible(role.userId, role.displayName, allow)
                                                }
                                            )
                                        }
                                        SupportStaffPerkAuditSection(
                                            staffUserId = role.userId,
                                            staffDisplayName = role.displayName.ifBlank { "Support Member" },
                                            perkGrants = perkGrants,
                                            isAdmin = staffCapabilities.isAdmin,
                                            onRemoveSupportStaff = {
                                                onSetUserStaffTier(role.userId, role.displayName, StaffTier.USER, false)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (staffInvites.isNotEmpty()) {
                            item(key = "pending_invites_label") {
                                Text(
                                    text = "Pending Staff Invites (${staffInvites.size})",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            items(staffInvites, key = { "invite_${it.id}" }) { invite ->
                                val invTier = invite.tierEnum()
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = if (invTier == StaffTier.ADMIN) {
                                                            invite.displayName.ifBlank { "Invited Admin" }
                                                        } else {
                                                            invite.displayName.ifBlank { invite.email }
                                                        },
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = usernameColor(invTier)
                                                    )
                                                    StaffTierBadge(tier = invTier)
                                                }
                                                Text(
                                                    text = if (invTier == StaffTier.ADMIN) {
                                                        "Pending Admin invite · Email hidden"
                                                    } else {
                                                        invite.email
                                                    },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = { onRemoveSupportInvite(invite.id) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Remove invite",
                                                    tint = LiveCoral
                                                )
                                            }
                                        }
                                        if (invTier == StaffTier.SUPPORT) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Allow Invisible Entry on Sign-In",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                Switch(
                                                    checked = invite.canEnterInvisible,
                                                    onCheckedChange = { allow ->
                                                        onToggleSupportInviteInvisible(invite.id, allow)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        item(key = "user_search") {
                            OutlinedTextField(
                                value = userSearch,
                                onValueChange = { userSearch = it.take(40) },
                                label = { Text("Search Users") },
                                placeholder = { Text("Filter by display name or handle…") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_user_search_input")
                            )
                        }

                        items(filteredUsers, key = { "user_${it.userId}" }) { user ->
                            val userTier = if (user.userId == currentUserId) {
                                StaffTier.ADMIN
                            } else {
                                staffTierByUid[user.userId] ?: StaffTier.USER
                            }
                            AdminUserDirectoryCard(
                                profile = user,
                                directoryEntry = directoryByUid[user.userId],
                                nowMillis = nowMillis,
                                tier = userTier,
                                isBusinessAccount = user.isBusinessAccount(userTier, nowMillis),
                                isSelf = user.userId == currentUserId,
                                staffPerkGrants = if (userTier == StaffTier.SUPPORT) perkGrants else emptyList(),
                                onEdit = { userBeingEdited = user },
                                onSuspend = { userBeingSuspended = user },
                                onOpenFeatureRestrictions = {
                                    featureRestrictionsTarget = user.userId to user.displayName
                                },
                                onAlert = { userReceivingAlert = user },
                                onSendToRoom = { userBeingTransferred = user },
                                onSendToSupport = { userBeingSentToSupport = user },
                                onToggleMessagingDisabled = {
                                    onToggleUserMessagingDisabled(
                                        user.userId,
                                        user.displayName,
                                        !user.disableMessaging
                                    )
                                },
                                onRevokeBusiness = {
                                    adminConfirmRevokeBusinessTarget = directoryByUid[user.userId] ?: DirectoryEntry(
                                        id = user.userId,
                                        userId = user.userId,
                                        displayName = user.displayName,
                                        handle = user.handle,
                                        proTier = ProTier.BUSINESS.name
                                    )
                                },
                                onSetStaffTier = { nextTier ->
                                    onSetUserStaffTier(
                                        user.userId,
                                        user.displayName,
                                        nextTier,
                                        nextTier == StaffTier.ADMIN
                                    )
                                }
                            )
                        }
                    }

                    2 -> {
                        item(key = "admin_send_app_broadcast_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
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
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Campaign,
                                                    contentDescription = "Broadcast app message",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "App Message to All Users",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Deliver a real-time in-app announcement to every signed-in member.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { showSendBroadcastDialog = true },
                                        shape = RoundedCornerShape(999.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("admin_send_app_broadcast_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Campaign,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Send App Message to All Users",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "admin_mass_vox_dm_card") {
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.2.dp, SnapBlue.copy(alpha = 0.55f)),
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
                                            color = SnapBlue.copy(alpha = 0.16f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Verified,
                                                    contentDescription = null,
                                                    tint = SnapBlue,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Mass VOX Private Message",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Send an official \"VOX\" private message directly to every user's inbox for updates and alerts. Users cannot reply.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = { showMassVoxDmDialog = true },
                                        shape = RoundedCornerShape(999.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = SnapBlue,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("admin_send_mass_vox_dm_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MarkEmailRead,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Send VOX Private Message to Everyone",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "admin_vox_chat_control_card") {
                            val isVoxEnabled = officialVoxChatRoom?.statusEnum() != RoomStatus.CLOSED
                            val isVoxHidden = officialVoxChatRoom?.hiddenFromLobby == true
                            val isVoxPinLocked = officialVoxChatRoom?.pinLocked == true
                            val voxOnlineCount = officialVoxChatRoom?.participantCount ?: 0L
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.2.dp,
                                    if (isVoxEnabled) SafetyEmerald.copy(alpha = 0.55f) else LiveCoral.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_vox_chat_control_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = SnapBlue.copy(alpha = 0.16f),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Forum,
                                                    contentDescription = "VOX CHAT official room",
                                                    tint = SnapBlue,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "VOX CHAT (Official Always-Open Room)",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "App-created permanent lounge controlled by Admins & Support Staff. Enable/disable, hide from Lobby, or PIN lock anytime.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (isVoxEnabled) SafetyEmerald.copy(alpha = 0.16f) else LiveCoral.copy(alpha = 0.16f)
                                        ) {
                                            Text(
                                                text = if (isVoxEnabled) "ENABLED" else "DISABLED",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isVoxEnabled) SafetyEmerald else LiveCoral,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (isVoxHidden) ModAmber.copy(alpha = 0.18f) else SnapBlue.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = if (isVoxHidden) "HIDDEN" else "VISIBLE",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isVoxHidden) ModAmber else SnapBlue,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = if (isVoxPinLocked) ModAmber.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = if (isVoxPinLocked) "PIN: ${officialVoxChatRoom?.roomPinCode.orEmpty()}" else "OPEN ACCESS",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = if (isVoxPinLocked) ModAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = "$voxOnlineCount online",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(
                                                text = "Enable VOX CHAT",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "When disabled, VOX CHAT is closed and members cannot join.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = voxChatEnabledDraft,
                                            onCheckedChange = { voxChatEnabledDraft = it },
                                            modifier = Modifier.testTag("admin_vox_chat_enable_switch")
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.VisibilityOff,
                                                    contentDescription = null,
                                                    tint = ModAmber,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "Hide from Lobby",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Hides VOX CHAT from regular users while Admins & Support can still access it.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = voxChatHiddenDraft,
                                            onCheckedChange = { voxChatHiddenDraft = it },
                                            modifier = Modifier.testTag("admin_vox_chat_hide_switch")
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Pin,
                                                    contentDescription = null,
                                                    tint = ModAmber,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "PIN Lock VOX CHAT",
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Text(
                                                text = "Require a 4-digit PIN for regular members to enter VOX CHAT.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = voxChatPinLockedDraft,
                                            onCheckedChange = { locked ->
                                                voxChatPinLockedDraft = locked
                                                if (!locked) {
                                                    voxChatPinDraft = ""
                                                    voxChatPinError = null
                                                }
                                            },
                                            modifier = Modifier.testTag("admin_vox_chat_pin_switch")
                                        )
                                    }

                                    if (voxChatPinLockedDraft) {
                                        OutlinedTextField(
                                            value = voxChatPinDraft,
                                            onValueChange = { raw ->
                                                voxChatPinDraft = raw.filter { it.isDigit() }.take(4)
                                                voxChatPinError = null
                                            },
                                            label = { Text("4-Digit VOX CHAT PIN") },
                                            placeholder = { Text("e.g. 1234") },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            supportingText = {
                                                Text(
                                                    text = voxChatPinError ?: "${voxChatPinDraft.length}/4 digits",
                                                    color = if (voxChatPinError != null) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            },
                                            isError = voxChatPinError != null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("admin_vox_chat_pin_input")
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                if (voxChatPinLockedDraft && voxChatPinDraft.length != 4) {
                                                    voxChatPinError = "Enter a 4-digit PIN before saving."
                                                } else {
                                                    voxChatPinError = null
                                                    onUpdateOfficialVoxChat(
                                                        voxChatEnabledDraft,
                                                        voxChatHiddenDraft,
                                                        voxChatPinLockedDraft,
                                                        if (voxChatPinLockedDraft) voxChatPinDraft else ""
                                                    ) {}
                                                }
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = SafetyEmerald,
                                                contentColor = Color.White
                                            ),
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .height(48.dp)
                                                .testTag("admin_vox_chat_save_button")
                                        ) {
                                            Text(
                                                text = "Save VOX CHAT",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = onOpenOfficialVoxChat,
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("admin_vox_chat_enter_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.MeetingRoom,
                                                contentDescription = "Enter VOX CHAT",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Enter",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (appBroadcasts.isNotEmpty()) {
                            item(key = "admin_broadcasts_header") {
                                Text(
                                    text = "Sent App-Wide Messages (${appBroadcasts.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                            items(appBroadcasts, key = { "bcast_${it.id}" }) { bcast ->
                                val severityColor = when (bcast.severityEnum()) {
                                    AlertSeverity.URGENT -> LiveCoral
                                    AlertSeverity.WARNING -> ModAmber
                                    AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
                                }
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, severityColor.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("admin_broadcast_card_${bcast.id}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = severityColor.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = bcast.severityEnum().name,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = severityColor,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = "ALL USERS",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Text(
                                                text = bcast.title,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = bcast.message,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Sent by ${bcast.senderName} · ${formatTimestampShort(bcast.createdAt)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeleteAppBroadcast(bcast.id) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("admin_delete_broadcast_${bcast.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete app broadcast",
                                                tint = LiveCoral
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "admin_direct_alerts_header") {
                            Text(
                                text = "Direct User Alerts (${allAlerts.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (allAlerts.isEmpty()) {
                            item(key = "alerts_empty") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "No direct user alerts sent yet. Send individual alerts from any room or the Users tab above.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(allAlerts, key = { "alert_${it.id}" }) { alert ->
                                AdminAlertHistoryCard(
                                    alert = alert,
                                    onEdit = { alertBeingEdited = alert },
                                    onDelete = { onAdminDeleteAlert(alert.id) }
                                )
                            }
                        }
                    }

                    3 -> {
                        item(key = "admin_app_banner_and_trial_card") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Campaign,
                                            contentDescription = "Login news banner",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Login Screen News Banner & Trial Access",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Show News & Updates Banner on Login",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Broadcast app news, maintenance notices, or updates on the main login screen",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = bannerEnabledDraft,
                                            onCheckedChange = { bannerEnabledDraft = it },
                                            modifier = Modifier.testTag("admin_toggle_login_banner")
                                        )
                                    }

                                    OutlinedTextField(
                                        value = bannerTitleDraft,
                                        onValueChange = { bannerTitleDraft = it.take(80) },
                                        label = { Text("Banner Headline") },
                                        placeholder = { Text("e.g., Welcome to VOX v2!") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("admin_banner_title_input")
                                    )

                                    OutlinedTextField(
                                        value = bannerMessageDraft,
                                        onValueChange = { bannerMessageDraft = it.take(400) },
                                        label = { Text("Updates / News Message") },
                                        placeholder = { Text("Write app news or feature updates shown on the login screen…") },
                                        minLines = 3,
                                        maxLines = 5,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("admin_banner_message_input")
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Allow 1-Day Trial Logins",
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "When disabled, guest trial logins are hidden and active trial sessions require Google Sign-In.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = trialLoginsEnabledDraft,
                                            onCheckedChange = { trialLoginsEnabledDraft = it },
                                            modifier = Modifier.testTag("admin_toggle_trial_logins")
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onUpdateAppConfig(
                                                bannerTitleDraft,
                                                bannerMessageDraft,
                                                bannerEnabledDraft,
                                                trialLoginsEnabledDraft
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("admin_save_banner_button")
                                    ) {
                                        Text("Save Login Banner & Trial Settings")
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        item(key = "admin_business_tab_header") {
                            BusinessAccountsTabHeaderCard(
                                businessCount = businessAccounts.size,
                                helpRequestCount = businessHelpThreads.size,
                                isAdmin = true,
                                searchQuery = businessSearch,
                                onSearchChange = { businessSearch = it.take(40) }
                            )
                        }
                        if (filteredBusinessAccounts.isEmpty()) {
                            item(key = "admin_business_empty") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (businessAccounts.isEmpty()) {
                                            "No active Business Accounts yet. Business Account holders ($99.99/mo) automatically appear here in bold gold."
                                        } else {
                                            "No Business Accounts match \"$businessSearch\"."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(filteredBusinessAccounts, key = { "admin_biz_${it.userId}" }) { bizEntry ->
                                val fullProf = profileByUid[bizEntry.userId]
                                BusinessAccountDirectoryCard(
                                    entry = bizEntry,
                                    fullProfile = fullProf,
                                    nowMillis = nowMillis,
                                    isAdmin = true,
                                    onAdminEdit = {
                                        userBeingEdited = fullProf ?: UserProfile(
                                            id = bizEntry.userId,
                                            userId = bizEntry.userId,
                                            displayName = bizEntry.displayName,
                                            handle = bizEntry.handle,
                                            bio = bizEntry.bio,
                                            proTier = ProTier.BUSINESS.name,
                                            disableMessaging = bizEntry.disableMessaging
                                        )
                                    },
                                    onAdminSuspend = {
                                        userBeingSuspended = fullProf ?: UserProfile(
                                            id = bizEntry.userId,
                                            userId = bizEntry.userId,
                                            displayName = bizEntry.displayName,
                                            handle = bizEntry.handle
                                        )
                                    },
                                    onAdminRevokeBusiness = {
                                        adminConfirmRevokeBusinessTarget = bizEntry
                                    },
                                    onAdminToggleMessaging = {
                                        val currentlyDisabled = fullProf?.disableMessaging ?: bizEntry.disableMessaging
                                        onToggleUserMessagingDisabled(
                                            bizEntry.userId,
                                            bizEntry.displayName,
                                            !currentlyDisabled
                                        )
                                    },
                                    onSendAlert = {
                                        userReceivingAlert = fullProf ?: UserProfile(
                                            id = bizEntry.userId,
                                            userId = bizEntry.userId,
                                            displayName = bizEntry.displayName,
                                            handle = bizEntry.handle
                                        )
                                    },
                                    onSupportRequestAdminApproval = {}
                                )
                            }
                        }
                    }
                }
            }

            // Support Staff Console Tabs (Safety Tools vs Business Accounts Tab)
            if (staffCapabilities.isSupport && !staffCapabilities.isAdmin) {
                item(key = "support_console_tabs") {
                    val businessGold = usernameColor(StaffTier.USER, isBusiness = true)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = usernameColor(StaffTier.SUPPORT).copy(alpha = 0.09f),
                        border = BorderStroke(1.dp, usernameColor(StaffTier.SUPPORT).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SupportAgent,
                                    contentDescription = "Support Staff Console",
                                    tint = usernameColor(StaffTier.SUPPORT),
                                    modifier = Modifier.size(22.dp)
                                )
                                Text(
                                    text = "Support Staff Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = usernameColor(StaffTier.SUPPORT)
                                )
                            }
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                SegmentedButton(
                                    selected = supportConsoleTab == 0,
                                    onClick = { supportConsoleTab = 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                    modifier = Modifier.testTag("support_tab_safety")
                                ) {
                                    Text("Safety Tools", maxLines = 1)
                                }
                                SegmentedButton(
                                    selected = supportConsoleTab == 1,
                                    onClick = { supportConsoleTab = 1 },
                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                    modifier = Modifier.testTag("support_tab_business")
                                ) {
                                    Text(
                                        text = "Business (${businessAccounts.size})",
                                        color = if (supportConsoleTab == 1) businessGold else Color.Unspecified,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                if (supportConsoleTab == 1) {
                    item(key = "support_business_tab_header") {
                        BusinessAccountsTabHeaderCard(
                            businessCount = businessAccounts.size,
                            helpRequestCount = businessHelpThreads.size,
                            isAdmin = false,
                            searchQuery = businessSearch,
                            onSearchChange = { businessSearch = it.take(40) }
                        )
                    }
                    if (filteredBusinessAccounts.isEmpty()) {
                        item(key = "support_business_empty") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (businessAccounts.isEmpty()) {
                                        "No active Business Accounts yet. Business Accounts appear here in bold gold."
                                    } else {
                                        "No Business Accounts match \"$businessSearch\"."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(filteredBusinessAccounts, key = { "support_biz_${it.userId}" }) { bizEntry ->
                            BusinessAccountDirectoryCard(
                                entry = bizEntry,
                                fullProfile = null,
                                nowMillis = nowMillis,
                                isAdmin = false,
                                onAdminEdit = {},
                                onAdminSuspend = {},
                                onAdminRevokeBusiness = {},
                                onAdminToggleMessaging = {},
                                onSendAlert = {
                                    userReceivingAlert = UserProfile(
                                        id = bizEntry.userId,
                                        userId = bizEntry.userId,
                                        displayName = bizEntry.displayName,
                                        handle = bizEntry.handle
                                    )
                                },
                                onSupportRequestAdminApproval = { action ->
                                    supportBusinessApprovalTarget = bizEntry to action
                                }
                            )
                        }
                    }
                }
            }

            // Staff-only Support Suspension Requests, Refunds, App Perks & Admin Alerts
            if (staffCapabilities.isStaff && (staffCapabilities.isAdmin || supportConsoleTab == 0)) {
                if (staffCapabilities.isAdmin) {
                    item(key = "admin_notifications_section") {
                        AdminNotificationsCard(
                            notifications = adminNotifications,
                            onMarkRead = onMarkAdminNotificationRead
                        )
                    }
                }
                item(key = "staff_refunds_section") {
                    RefundsManagementSection(
                        isAdmin = staffCapabilities.isAdmin,
                        isSupport = staffCapabilities.isSupport,
                        refundReports = refundReports,
                        onOpenIssueRefund = { showIssueRefundDialog = true },
                        onAcknowledgeReport = onAcknowledgeRefundReport
                    )
                }
                item(key = "staff_app_perks_section") {
                    AppPerkGrantsSection(
                        perkGrants = perkGrants,
                        onOpenGrantPerk = { showGrantPerkDialog = true }
                    )
                }
                item(key = "staff_suspension_requests_queue") {
                    PendingSuspensionRequestsCard(
                        requests = suspensionRequests,
                        isAdmin = staffCapabilities.isAdmin,
                        onReviewRequest = { req -> reviewingSuspensionRequest = req }
                    )
                }
                if (staffCapabilities.isSupport) {
                    item(key = "support_request_suspension_section") {
                        SupportRequestSuspensionCard(
                            directoryEntries = directoryEntries,
                            currentUserId = currentUserId,
                            onSelectTarget = { entry ->
                                if (entry.isBusinessAccount() && !staffCapabilities.isAdmin) {
                                    supportBusinessApprovalTarget = entry to BusinessApprovalAction.SUSPEND
                                } else {
                                    supportRequestTarget = entry
                                }
                            }
                        )
                    }
                }
                item(key = "staff_messaging_controls_section") {
                    StaffMessagingControlsCard(
                        directoryEntries = directoryEntries,
                        allUsers = allUsers,
                        currentUserId = currentUserId,
                        isAdmin = staffCapabilities.isAdmin,
                        nowMillis = nowMillis,
                        onOpenFeatureRestrictions = { uid, name ->
                            featureRestrictionsTarget = uid to name
                        },
                        onRequestBusinessAdminApproval = { entry ->
                            supportBusinessApprovalTarget = entry to BusinessApprovalAction.EDIT
                        }
                    )
                }
                item(key = "staff_moderation_files_section") {
                    UserModerationFilesCard(
                        moderationFiles = moderationFiles,
                        nowMillis = nowMillis,
                        isAdmin = staffCapabilities.isAdmin,
                        onInspectFile = { file -> inspectingModerationFile = file },
                        onResolveFile = { file -> resolvingModerationFile = file }
                    )
                }
            }

            item(key = "stats_row") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SafetyMetricCard(
                        label = "Open Reports",
                        value = "$openCount",
                        accent = if (openCount > 0) ModAmber else SafetyEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    SafetyMetricCard(
                        label = "Resolved",
                        value = "$resolvedCount",
                        accent = VoiceCyan,
                        modifier = Modifier.weight(1f)
                    )
                    SafetyMetricCard(
                        label = "Blocked",
                        value = "${blockedUsers.size}",
                        accent = LiveCoral,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item(key = "guidelines_banner") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = SafetyEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SafetyEmerald.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = "Active protection",
                                tint = SafetyEmerald,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Role-Coded Moderation Hierarchy",
                                style = MaterialTheme.typography.titleSmall,
                                color = SafetyEmerald
                            )
                            Text(
                                text = "Admins (Red) · Support Staff (Green) · Business (Bold Gold) · Pro (Blue) · Room Admins/Co-Admins (Purple). Report limit: up to 3 different users, 1 report per user per hour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (staffCapabilities.isStaff && businessHelpThreads.isNotEmpty()) {
                item(key = "business_help_heading") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SupportAgent,
                            contentDescription = "Group DM Help & Reported Groups",
                            tint = SafetyEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Group DM Help & Reported Groups (${businessHelpThreads.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = SafetyEmerald
                        )
                    }
                }
                items(businessHelpThreads, key = { "biz_help_${it.id}" }) { bizThread ->
                    val isReported = bizThread.reportStaffAccessOpen
                    val isHelp = bizThread.staffHelpRequested
                    val isBiz = bizThread.isCompanyThread()
                    val cardAccent = when {
                        bizThread.groupDisabled -> LiveCoral
                        isReported -> ModAmber
                        else -> SafetyEmerald
                    }
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = cardAccent.copy(alpha = 0.08f)),
                        border = BorderStroke(1.2.dp, cardAccent.copy(alpha = 0.55f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = bizThread.groupName.ifBlank {
                                        if (isBiz) "Business Group DM" else "Group DM (${bizThread.participantIds.size} members)"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isHelp) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = SafetyEmerald.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "HELP",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = SafetyEmerald,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    if (isReported) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = ModAmber.copy(alpha = 0.22f)
                                        ) {
                                            Text(
                                                text = "REPORTED",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = ModAmber,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                    if (bizThread.groupDisabled) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = LiveCoral.copy(alpha = 0.2f)
                                        ) {
                                            Text(
                                                text = "DISABLED",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = LiveCoral,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            if (bizThread.staffHelpReason.isNotBlank()) {
                                Text(
                                    text = "Help Request: ${bizThread.staffHelpReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (bizThread.lastReportSummary.isNotBlank()) {
                                Text(
                                    text = "Report: ${bizThread.lastReportSummary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ModAmber
                                )
                            }
                            if (bizThread.staffHelpReason.isBlank() && bizThread.lastReportSummary.isBlank()) {
                                Text(
                                    text = "Staff access is open for this Group DM. Enter visibly or invisibly to edit, mute, remove, ban, or disable the group.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onEnterBusinessHelpDm(bizThread, false) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SafetyEmerald,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("enter_business_help_dm_${bizThread.id}")
                                ) {
                                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Visible", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Button(
                                    onClick = { onEnterBusinessHelpDm(bizThread, true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary,
                                        contentColor = MaterialTheme.colorScheme.onSecondary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("enter_group_help_invisible_${bizThread.id}")
                                ) {
                                    Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Invisible", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                OutlinedButton(
                                    onClick = { onEndBusinessHelp(bizThread.id) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("end_business_help_dm_${bizThread.id}")
                                ) {
                                    Text("End", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            item(key = "reports_heading") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Flag,
                        contentDescription = null,
                        tint = ModAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Safety Reports Queue",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            when (reportsState) {
                is UiState.Loading -> {
                    item(key = "reports_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = VoiceCyan)
                        }
                    }
                }

                is UiState.Error -> {
                    item(key = "reports_error") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = reportsState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                is UiState.Success -> {
                    if (reports.isEmpty()) {
                        item(key = "reports_empty") {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "All clear",
                                        tint = SafetyEmerald,
                                        modifier = Modifier.size(34.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(R.string.empty_reports_title),
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.empty_reports_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(reports, key = { it.id }) { report ->
                            val targetDir = directoryByUid[report.targetUserId]
                            val targetUserProfile = allUsers.firstOrNull { it.userId == report.targetUserId }
                            val targetMessagingDisabled = targetUserProfile?.disableMessaging
                                ?: targetDir?.disableMessaging
                                ?: false
                            val targetIsAdmin = (staffTierByUid[report.targetUserId] ?: targetDir?.tierEnum()) == StaffTier.ADMIN
                            val targetIsBusiness = report.targetUserId in businessUserIds ||
                                targetDir?.isBusinessAccount(nowMillis) == true ||
                                targetUserProfile?.isBusinessAccount(nowMs = nowMillis) == true
                            val isBizReport = report.isBusinessDmReport()
                            val isGroupReport = report.isGroupDmReport()
                            val bizOwnerId = report.businessOwnerIdFromReport()
                            val supportBlockedByBusinessPolicy = !staffCapabilities.isAdmin &&
                                (targetIsBusiness || (isBizReport && report.targetUserId == bizOwnerId))
                            SafetyReportCard(
                                report = report,
                                isHostOrAdmin = report.roomHostId == currentUserId || staffCapabilities.isAdmin,
                                isAdminViewer = staffCapabilities.isAdmin,
                                targetIsBusinessAccount = targetIsBusiness,
                                canToggleMessaging = staffCapabilities.isAdmin && !targetIsAdmin && report.targetUserId != currentUserId,
                                targetMessagingDisabled = targetMessagingDisabled,
                                isBusinessDmReport = isBizReport,
                                isGroupDmReport = isGroupReport,
                                canEnterReportedGroupDm = staffCapabilities.isStaff && isGroupReport && report.roomId.isNotBlank(),
                                canManageFeatureRestrictions = staffCapabilities.isStaff && !targetIsAdmin && !supportBlockedByBusinessPolicy && report.targetUserId != currentUserId,
                                canRemoveFromBusiness = isBizReport && bizOwnerId.isNotBlank() &&
                                    staffCapabilities.isStaff && !targetIsAdmin && !supportBlockedByBusinessPolicy && report.targetUserId != currentUserId,
                                canRequestBusinessAdminApproval = supportBlockedByBusinessPolicy && report.targetUserId != currentUserId,
                                canBanDevice = staffCapabilities.isAdmin && !targetIsAdmin && report.targetUserId != currentUserId,
                                onToggleMessaging = {
                                    onToggleUserMessagingDisabled(
                                        report.targetUserId,
                                        report.targetName,
                                        !targetMessagingDisabled
                                    )
                                },
                                onEnterReportedGroupVisible = {
                                    onEnterReportedGroupDmById(report.roomId, false)
                                },
                                onEnterReportedGroupInvisible = {
                                    onEnterReportedGroupDmById(report.roomId, true)
                                },
                                onOpenFeatureRestrictions = {
                                    featureRestrictionsTarget = report.targetUserId to report.targetName
                                },
                                onRemoveFromBusiness = { confirmRemoveBizUserReport = report },
                                onRequestBusinessAdminApproval = {
                                    val entry = targetDir ?: DirectoryEntry(
                                        id = report.targetUserId,
                                        userId = report.targetUserId,
                                        displayName = report.targetName,
                                        proTier = ProTier.BUSINESS.name
                                    )
                                    supportBusinessApprovalTarget = entry to BusinessApprovalAction.BAN
                                },
                                onBanDevice = { confirmBanDeviceReport = report },
                                onResolve = {
                                    onResolveReport(report.id, ReportStatus.RESOLVED, "Reviewed and action taken")
                                },
                                onDismiss = {
                                    onResolveReport(report.id, ReportStatus.DISMISSED, "Reviewed — no violation found")
                                }
                            )
                        }
                    }
                }
            }

            item(key = "blocked_heading") {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = LiveCoral,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Blocked Users (${blockedUsers.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            if (blockedUsers.isEmpty()) {
                item(key = "blocked_empty") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You haven't blocked anyone. Tap any user or chat message in a room to block them.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(blockedUsers, key = { it }) { blockedUid ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "User ${blockedUid.take(14)}…",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Messages hidden across all rooms",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(
                                onClick = { onUnblockUser(blockedUid) },
                                modifier = Modifier.testTag("unblock_$blockedUid")
                            ) {
                                Text("Unblock")
                            }
                        }
                    }
                }
            }

            item(key = "bottom_pad") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    inspectingModerationFile?.let { file ->
        val notesFlow = remember(file.userId) { onObserveUserFileNotes(file.userId) }
        UserFileNotesDialog(
            file = file,
            notesFlow = notesFlow,
            isAdmin = staffCapabilities.isAdmin,
            onDismiss = { inspectingModerationFile = null },
            onResolveClick = {
                inspectingModerationFile = null
                resolvingModerationFile = file
            }
        )
    }

    resolvingModerationFile?.let { file ->
        ResolveInvestigationDialog(
            file = file,
            onConfirmResolve = { resolutionNote, resetReportCount ->
                onResolveInvestigation(
                    file.userId,
                    file.targetName.ifBlank { "User" },
                    resolutionNote,
                    resetReportCount
                ) {
                    resolvingModerationFile = null
                }
            },
            onDismiss = { resolvingModerationFile = null }
        )
    }

    supportRequestTarget?.let { target ->
        RequestSuspensionDialog(
            targetName = target.displayName.ifBlank { target.userId.take(10) },
            onSubmit = { reason ->
                onSubmitSuspensionRequest(
                    target.userId,
                    target.displayName.ifBlank { "User" },
                    reason
                ) {
                    supportRequestTarget = null
                }
            },
            onDismiss = { supportRequestTarget = null }
        )
    }

    supportBusinessApprovalTarget?.let { (target, initialAction) ->
        RequestBusinessActionApprovalDialog(
            targetName = target.displayName.ifBlank { target.userId.take(10) },
            initialAction = initialAction,
            onSubmit = { action, reason ->
                onSubmitBusinessApprovalRequest(
                    target.userId,
                    target.displayName.ifBlank { "Business Account" },
                    action,
                    reason
                ) {
                    supportBusinessApprovalTarget = null
                }
            },
            onDismiss = { supportBusinessApprovalTarget = null }
        )
    }

    adminConfirmRevokeBusinessTarget?.let { target ->
        var revokeReason by remember(target.userId) { mutableStateOf("") }
        val bizGold = usernameColor(StaffTier.USER, isBusiness = true)
        AlertDialog(
            onDismissRequest = { adminConfirmRevokeBusinessTarget = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Business,
                    contentDescription = "Remove Business Account",
                    tint = LiveCoral
                )
            },
            title = { Text("Remove Business Account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = target.displayName.ifBlank { target.userId.take(12) },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = bizGold
                        )
                        BusinessAccountBadge()
                    }
                    Text(
                        text = "This downgrades the account to Free, deactivates its Company Codes, and sends an urgent Admin notice.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = revokeReason,
                        onValueChange = { revokeReason = it.take(240) },
                        label = { Text("Admin Reason (Required)") },
                        placeholder = { Text("Explain why this Business Account is being removed…") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_revoke_business_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = revokeReason.trim()
                        if (clean.length >= 3) {
                            onAdminRevokeBusinessAccount(
                                target.userId,
                                target.displayName.ifBlank { "Business Account" },
                                clean
                            )
                            adminConfirmRevokeBusinessTarget = null
                        }
                    },
                    enabled = revokeReason.trim().length >= 3,
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_admin_revoke_business_button")
                ) {
                    Text("Remove Business", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { adminConfirmRevokeBusinessTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    reviewingSuspensionRequest?.let { req ->
        AdminReviewSuspensionRequestDialog(
            request = req,
            onSubmitDecision = { decision, timedHours, adminNote ->
                onAdminReviewSuspensionRequest(req, decision, timedHours, adminNote) {
                    reviewingSuspensionRequest = null
                }
            },
            onOpenEditBusinessUser = { targetUid ->
                val prof = profileByUid[targetUid] ?: directoryByUid[targetUid]?.let { d ->
                    UserProfile(
                        id = d.userId,
                        userId = d.userId,
                        displayName = d.displayName,
                        handle = d.handle,
                        bio = d.bio,
                        proTier = d.proTier,
                        disableMessaging = d.disableMessaging
                    )
                }
                if (prof != null) {
                    userBeingEdited = prof
                }
            },
            onDismiss = { reviewingSuspensionRequest = null }
        )
    }

    if (showIssueRefundDialog && staffCapabilities.isStaff) {
        IssueRefundDialog(
            directoryEntries = directoryEntries,
            allTransactions = allTransactions,
            allCreatorSubscriptions = allCreatorSubscriptions,
            isSupportOnly = staffCapabilities.isSupport && !staffCapabilities.isAdmin,
            onDismiss = { showIssueRefundDialog = false },
            onConfirmRefund = { targetUid, targetName, refundType, itemLabel, amountCents, txId, subId, reason ->
                onIssueRefund(targetUid, targetName, refundType, itemLabel, amountCents, txId, subId, reason) {
                    showIssueRefundDialog = false
                }
            }
        )
    }

    if (showGrantPerkDialog && staffCapabilities.isStaff) {
        GrantAppPerkDialog(
            directoryEntries = directoryEntries,
            onDismiss = { showGrantPerkDialog = false },
            onConfirmGrant = { targetUid, targetName, perkType, detailValue, pts, reason ->
                onGrantAppPerk(targetUid, targetName, perkType, detailValue, pts, reason) {
                    showGrantPerkDialog = false
                }
            }
        )
    }

    userBeingEdited?.let { target ->
        EditUserAdminDialog(
            profile = target,
            onSave = { displayName, handle, bio, globallyBanned, disableCreateRoom, disableEnterRoom, disableMic, disableVideo, suspendedUntilMillis, suspensionReason, pointsBalance, proLifetime, disableMessaging ->
                onAdminEditUser(
                    target.userId,
                    displayName,
                    handle,
                    bio,
                    globallyBanned,
                    disableCreateRoom,
                    disableEnterRoom,
                    disableMic,
                    disableVideo,
                    suspendedUntilMillis,
                    suspensionReason,
                    pointsBalance,
                    proLifetime,
                    disableMessaging
                )
            },
            onDismiss = { userBeingEdited = null }
        )
    }

    userBeingSuspended?.let { target ->
        SuspendUserDialog(
            targetName = target.displayName,
            onSuspend = { mins, reason ->
                onSuspendUser(target.userId, target.displayName, mins, reason)
            },
            onDismiss = { userBeingSuspended = null }
        )
    }

    userReceivingAlert?.let { target ->
        SendOrEditAlertDialog(
            existingAlert = null,
            targetName = target.displayName,
            onSave = { title, message, severity, _ ->
                onSendDirectAlert(target.userId, target.displayName, title, message, severity)
            },
            onDismiss = { userReceivingAlert = null }
        )
    }

    userBeingTransferred?.let { target ->
        SendUserToRoomDialog(
            targetName = target.displayName,
            availableRooms = liveRooms,
            onSendToRoom = { destRoom, reason ->
                onSendUserToRoom(target.userId, target.displayName, destRoom, "", reason)
                userBeingTransferred = null
            },
            onDismiss = { userBeingTransferred = null }
        )
    }

    userBeingSentToSupport?.let { target ->
        val eligibleSupport = remember(staffDirectoryMembers, target.userId, nowMillis) {
            staffDirectoryMembers
                .filter { it.userId != target.userId }
                .sortedWith(
                    compareByDescending<DirectoryEntry> { it.tierEnum() == StaffTier.SUPPORT }
                        .thenByDescending { it.isCurrentlyOnline(nowMillis) }
                        .thenBy { it.displayName.lowercase() }
                )
        }
        var selectedSupportId by remember(eligibleSupport) {
            mutableStateOf(eligibleSupport.firstOrNull()?.userId.orEmpty())
        }
        var reasonDraft by remember(target.userId) { mutableStateOf("") }
        var isSending by remember(target.userId) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSending) userBeingSentToSupport = null },
            title = {
                Text("Send ${target.displayName} to Support Chat")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose a Support or Admin team member to open a live private support chat with ${target.displayName}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (eligibleSupport.isEmpty()) {
                        Text(
                            text = "No Support or Admin members available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            eligibleSupport.take(8).forEach { staff ->
                                val selected = selectedSupportId == staff.userId
                                val online = staff.isCurrentlyOnline(nowMillis)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                    border = BorderStroke(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedSupportId = staff.userId }
                                        .testTag("safety_pick_support_${staff.userId}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PresenceStatusDot(
                                            status = if (online) PresenceStatus.ONLINE else PresenceStatus.OFFLINE
                                        )
                                        Text(
                                            text = staff.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = usernameColor(staff.tierEnum()),
                                            modifier = Modifier.weight(1f)
                                        )
                                        StaffTierBadge(tier = staff.tierEnum())
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = reasonDraft,
                        onValueChange = { reasonDraft = it.take(180) },
                        label = { Text("Note / Topic (optional)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("safety_support_handoff_reason")
                    )
                }
            },
            confirmButton = {
                val chosenSupport = eligibleSupport.firstOrNull { it.userId == selectedSupportId }
                Button(
                    onClick = {
                        if (chosenSupport != null) {
                            val targetEntry = directoryByUid[target.userId] ?: DirectoryEntry(
                                userId = target.userId,
                                displayName = target.displayName,
                                handle = target.handle,
                                bio = target.bio,
                                avatarColorHex = target.avatarColorHex,
                                staffTier = (staffTierByUid[target.userId] ?: StaffTier.USER).name
                            )
                            isSending = true
                            onSendUserToSupportChat(targetEntry, chosenSupport, reasonDraft) {
                                isSending = false
                                userBeingSentToSupport = null
                            }
                        }
                    },
                    enabled = chosenSupport != null && !isSending,
                    modifier = Modifier.testTag("safety_confirm_send_support_button")
                ) {
                    Text(if (isSending) "Sending…" else "Send Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { userBeingSentToSupport = null },
                    enabled = !isSending
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    alertBeingEdited?.let { existing ->
        SendOrEditAlertDialog(
            existingAlert = existing,
            targetName = existing.targetName.ifBlank { "Member" },
            onSave = { title, message, severity, reopen ->
                onAdminEditAlert(existing.id, title, message, severity, reopen)
            },
            onDelete = { onAdminDeleteAlert(existing.id) },
            onDismiss = { alertBeingEdited = null }
        )
    }

    if (showSendBroadcastDialog) {
        var broadcastTitle by remember { mutableStateOf("") }
        var broadcastBody by remember { mutableStateOf("") }
        var broadcastSeverity by remember { mutableStateOf(AlertSeverity.INFO) }
        var isSendingBroadcast by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSendingBroadcast) showSendBroadcastDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Send App Message to All Users",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This announcement will appear live for every signed-in user across VOX.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlertSeverity.entries.forEach { sev ->
                            val sevColor = when (sev) {
                                AlertSeverity.URGENT -> LiveCoral
                                AlertSeverity.WARNING -> ModAmber
                                AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
                            }
                            FilterChip(
                                selected = broadcastSeverity == sev,
                                onClick = { broadcastSeverity = sev },
                                label = { Text(sev.name, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = sevColor,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("admin_broadcast_severity_${sev.name}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = broadcastTitle,
                        onValueChange = { broadcastTitle = it.take(80) },
                        label = { Text("Headline") },
                        placeholder = { Text("e.g., New Feature or Maintenance Notice") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_broadcast_title_input")
                    )
                    OutlinedTextField(
                        value = broadcastBody,
                        onValueChange = { broadcastBody = it.take(500) },
                        label = { Text("Message to all users") },
                        placeholder = { Text("Write the announcement you want every user to see…") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_broadcast_body_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSendingBroadcast = true
                        onSendAppBroadcast(broadcastTitle, broadcastBody, broadcastSeverity) {
                            isSendingBroadcast = false
                            showSendBroadcastDialog = false
                        }
                    },
                    enabled = broadcastTitle.trim().isNotEmpty() &&
                        broadcastBody.trim().isNotEmpty() &&
                        !isSendingBroadcast,
                    modifier = Modifier.testTag("admin_confirm_send_broadcast_button")
                ) {
                    Text(if (isSendingBroadcast) "Sending…" else "Send to All Users")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSendBroadcastDialog = false },
                    enabled = !isSendingBroadcast
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showMassVoxDmDialog) {
        var massTitle by remember { mutableStateOf("") }
        var massBody by remember { mutableStateOf("") }
        var massIsUrgent by remember { mutableStateOf(false) }
        var isSendingMassDm by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSendingMassDm) showMassVoxDmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = null,
                    tint = SnapBlue
                )
            },
            title = {
                Text(
                    text = "Send VOX Private Message to All Users",
                    fontWeight = FontWeight.ExtraBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Delivers an official \"VOX\" private message into every user's Chats inbox. Recipients can read it on-app or lock screen, and replies are disabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !massIsUrgent,
                            onClick = { massIsUrgent = false },
                            label = { Text("Update", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SnapBlue,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("admin_mass_dm_type_update")
                        )
                        FilterChip(
                            selected = massIsUrgent,
                            onClick = { massIsUrgent = true },
                            label = { Text("Urgent Alert", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LiveCoral,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("admin_mass_dm_type_alert")
                        )
                    }
                    OutlinedTextField(
                        value = massTitle,
                        onValueChange = { massTitle = it.take(80) },
                        label = { Text("Subject (optional)") },
                        placeholder = { Text("e.g., VOX App Update or Important Alert") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_mass_dm_title_input")
                    )
                    OutlinedTextField(
                        value = massBody,
                        onValueChange = { massBody = it.take(400) },
                        label = { Text("Private message to every user") },
                        placeholder = { Text("Write your official VOX update or alert…") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_mass_dm_body_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isSendingMassDm = true
                        onSendMassVoxDm(massTitle, massBody, massIsUrgent) {
                            isSendingMassDm = false
                            showMassVoxDmDialog = false
                        }
                    },
                    enabled = massBody.trim().isNotEmpty() && !isSendingMassDm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (massIsUrgent) LiveCoral else SnapBlue,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("admin_confirm_send_mass_dm_button")
                ) {
                    Text(if (isSendingMassDm) "Sending…" else "Send to All Users")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showMassVoxDmDialog = false },
                    enabled = !isSendingMassDm
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    confirmRemoveBizUserReport?.let { rep ->
        val ownerId = rep.businessOwnerIdFromReport()
        AlertDialog(
            onDismissRequest = { confirmRemoveBizUserReport = null },
            icon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = LiveCoral) },
            title = { Text("Remove User from Business Account?") },
            text = {
                Text(
                    "Remove ${rep.targetName} from this Business Account and its Group DM? Their unique Employee PIN will be permanently revoked so they cannot sign back in under another name."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemoveBizUserReport = null
                        onRemoveUserFromBusinessAccount(ownerId, rep.targetUserId, rep.targetName, rep.id)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_remove_from_business_button")
                ) {
                    Text("Remove & Revoke PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveBizUserReport = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    confirmBanDeviceReport?.let { rep ->
        AlertDialog(
            onDismissRequest = { confirmBanDeviceReport = null },
            icon = { Icon(Icons.Rounded.PhonelinkErase, contentDescription = null, tint = LiveCoral) },
            title = { Text("Ban User's Device?") },
            text = {
                Text(
                    "Permanently ban ${rep.targetName} and their device from VOX? They will be blocked from signing in again on this device with any email."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmBanDeviceReport = null
                        onAdminBanReportedDevice(
                            rep.targetUserId,
                            rep.targetName,
                            "Device banned following reported message (${rep.categoryEnum().label})",
                            rep.id
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral, contentColor = Color.White),
                    modifier = Modifier.testTag("confirm_ban_reported_device_button")
                ) {
                    Text("Ban Device Permanently")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBanDeviceReport = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    featureRestrictionsTarget?.let { (targetUid, targetName) ->
        FeatureRestrictionsDialog(
            targetName = targetName,
            targetProfile = loadedRestrictionProfile ?: profileByUid[targetUid],
            isLoadingProfile = isLoadingRestrictionProfile,
            isAdmin = staffCapabilities.isAdmin,
            onSave = { disabledFeatures, featureSuspensions, reason ->
                onUpdateUserFeatureRestrictions(
                    targetUid,
                    targetName,
                    disabledFeatures,
                    featureSuspensions,
                    reason
                ) {
                    featureRestrictionsTarget = null
                }
            },
            onDismiss = { featureRestrictionsTarget = null }
        )
    }
}

@Composable
private fun AdminUserDirectoryCard(
    profile: UserProfile,
    directoryEntry: DirectoryEntry?,
    nowMillis: Long,
    tier: StaffTier,
    isBusinessAccount: Boolean = false,
    isSelf: Boolean,
    staffPerkGrants: List<PerkGrant> = emptyList(),
    onEdit: () -> Unit,
    onSuspend: () -> Unit,
    onOpenFeatureRestrictions: () -> Unit = {},
    onAlert: () -> Unit,
    onSendToRoom: () -> Unit,
    onSendToSupport: () -> Unit,
    onToggleMessagingDisabled: () -> Unit = {},
    onRevokeBusiness: () -> Unit = {},
    onSetStaffTier: (StaffTier) -> Unit
) {
    val badges = buildList {
        if (profile.globallyBanned) add("BANNED" to LiveCoral)
        if (profile.currentlySuspended()) add("SUSPENDED ${profile.remainingSuspensionLabel()}" to ModAmber)
        if (profile.disableCreateRoom) add("NO ROOM CREATE" to LiveCoral)
        if (profile.disableEnterRoom) add("NO ROOM ENTER" to LiveCoral)
        if (profile.disableMic) add("NO MIC" to ModAmber)
        if (profile.disableVideo) add("NO VIDEO" to ModAmber)
        if (profile.disableMessaging) add("NO MSG" to LiveCoral)
        profile.activeFeatureRestrictionBadges(nowMillis).forEach { (label, isPerm) ->
            add(label to if (isPerm) LiveCoral else ModAmber)
        }
    }.distinctBy { it.first }
    val bizGold = usernameColor(StaffTier.USER, isBusiness = true)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isBusinessAccount) 1.3.dp else 1.dp,
            color = if (isBusinessAccount) bizGold.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val trueOnline = directoryEntry?.isCurrentlyOnline(nowMillis) == true
            val appearingStatus = profile.appearanceStatusEnum()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresenceStatusDot(
                    status = if (trueOnline) PresenceStatus.ONLINE else PresenceStatus.OFFLINE
                )
                Text(
                    text = if (isSelf) "${profile.displayName} (You)" else profile.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isBusinessAccount) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    color = usernameColor(tier = tier, isBusiness = isBusinessAccount)
                )
                if (isBusinessAccount) {
                    BusinessAccountBadge()
                }
                StaffTierBadge(tier = tier)
            }
            Text(
                text = buildString {
                    if (tier != StaffTier.ADMIN) {
                        append("@${profile.handle} · ")
                    }
                    append("App Time: ${profile.formattedTimeSpent()} · ")
                    append(if (trueOnline) "True: Online" else "True: Offline")
                    if (trueOnline && appearingStatus != PresenceStatus.ONLINE) {
                        append(" (Appearing ${appearingStatus.label})")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (trueOnline) PresenceOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = profile.bio.ifBlank { if (tier == StaffTier.ADMIN) "Administrator" else "No bio" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (badges.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    badges.forEach { (label, tint) ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = tint.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = tint,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (!isSelf) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onEdit, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rename / Edit", style = MaterialTheme.typography.labelSmall)
                    }
                    if (isBusinessAccount) {
                        OutlinedButton(
                            onClick = onRevokeBusiness,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_revoke_biz_${profile.userId}")
                        ) {
                            Icon(Icons.Rounded.Business, contentDescription = null, tint = LiveCoral, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove Business", style = MaterialTheme.typography.labelSmall, color = LiveCoral)
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            onSetStaffTier(if (tier == StaffTier.ADMIN) StaffTier.USER else StaffTier.ADMIN)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = usernameColor(StaffTier.ADMIN),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (tier == StaffTier.ADMIN) "Remove Admin" else "Make Admin",
                            style = MaterialTheme.typography.labelSmall,
                            color = usernameColor(StaffTier.ADMIN)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onSetStaffTier(if (tier == StaffTier.SUPPORT) StaffTier.USER else StaffTier.SUPPORT)
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SupportAgent,
                            contentDescription = null,
                            tint = usernameColor(StaffTier.SUPPORT),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (tier == StaffTier.SUPPORT) "Remove Support" else "Make Support",
                            style = MaterialTheme.typography.labelSmall,
                            color = usernameColor(StaffTier.SUPPORT)
                        )
                    }
                    if (tier != StaffTier.ADMIN) {
                        OutlinedButton(onClick = onSuspend, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                            Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Suspend", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = onOpenFeatureRestrictions,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_feature_restrictions_${profile.userId}")
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, tint = ModAmber, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Features / Timers", style = MaterialTheme.typography.labelSmall, color = ModAmber)
                        }
                        OutlinedButton(
                            onClick = onToggleMessagingDisabled,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("admin_toggle_msg_${profile.userId}")
                        ) {
                            Icon(
                                Icons.Rounded.Block,
                                contentDescription = null,
                                tint = if (profile.disableMessaging) SafetyEmerald else LiveCoral,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (profile.disableMessaging) "Enable Msg" else "Disable Msg",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (profile.disableMessaging) SafetyEmerald else LiveCoral
                            )
                        }
                    }
                    OutlinedButton(onClick = onAlert, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Alert", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onSendToRoom,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("admin_send_room_${profile.userId}")
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, tint = VoiceCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send to Room", style = MaterialTheme.typography.labelSmall, color = VoiceCyan)
                    }
                    OutlinedButton(
                        onClick = onSendToSupport,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("admin_send_support_${profile.userId}")
                    ) {
                        Icon(Icons.Rounded.SupportAgent, contentDescription = null, tint = SafetyEmerald, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Send to Support", style = MaterialTheme.typography.labelSmall, color = SafetyEmerald)
                    }
                }
                if (tier == StaffTier.SUPPORT) {
                    SupportStaffPerkAuditSection(
                        staffUserId = profile.userId,
                        staffDisplayName = profile.displayName.ifBlank { "Support Member" },
                        perkGrants = staffPerkGrants,
                        isAdmin = true,
                        onRemoveSupportStaff = { onSetStaffTier(StaffTier.USER) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminAlertHistoryCard(
    alert: UserAlert,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val severity = alert.severityEnum()
    val tint = when (severity) {
        AlertSeverity.URGENT -> LiveCoral
        AlertSeverity.WARNING -> ModAmber
        AlertSeverity.INFO -> VoiceCyan
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = tint.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = "${severity.label.uppercase()} · ${if (alert.acknowledged) "READ" else "UNREAD"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit alert", tint = VoiceCyan, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete alert", tint = LiveCoral, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Text(text = alert.title, style = MaterialTheme.typography.titleSmall)
            Text(text = alert.message, style = MaterialTheme.typography.bodySmall)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "To: ${alert.targetName.ifBlank { alert.targetUserId.take(10) }} · From:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = alert.senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = usernameColor(alert.senderTierEnum())
                )
                Text(
                    text = "· ${formatTimestampShort(alert.createdAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SafetyMetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BusinessAccountsTabHeaderCard(
    businessCount: Int,
    helpRequestCount: Int,
    isAdmin: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit
) {
    val bizGold = usernameColor(StaffTier.USER, isBusiness = true)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = bizGold.copy(alpha = 0.11f),
        border = BorderStroke(1.2.dp, bizGold.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("business_accounts_tab_header")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Business,
                    contentDescription = "Business Accounts",
                    tint = bizGold,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Business Accounts ($businessCount)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = bizGold
                    )
                    Text(
                        text = if (isAdmin) {
                            "Business Account holders appear in bold gold for Staff & Admins. Active Group DM help requests: $helpRequestCount."
                        } else {
                            "Support Staff can view Business Accounts and send alerts, but cannot remove, ban, or edit them without App Admin approval."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search Business Accounts by name or @handle…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("business_tab_search_input")
            )
        }
    }
}

@Composable
private fun BusinessAccountDirectoryCard(
    entry: DirectoryEntry,
    fullProfile: UserProfile?,
    nowMillis: Long,
    isAdmin: Boolean,
    onAdminEdit: () -> Unit,
    onAdminSuspend: () -> Unit,
    onAdminRevokeBusiness: () -> Unit,
    onAdminToggleMessaging: () -> Unit,
    onSendAlert: () -> Unit,
    onSupportRequestAdminApproval: (BusinessApprovalAction) -> Unit
) {
    val bizGold = usernameColor(StaffTier.USER, isBusiness = true)
    val trueOnline = entry.isCurrentlyOnline(nowMillis)
    val msgDisabled = fullProfile?.disableMessaging ?: entry.disableMessaging

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.3.dp, bizGold.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("business_account_card_${entry.userId}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PresenceStatusDot(
                    status = if (trueOnline) PresenceStatus.ONLINE else PresenceStatus.OFFLINE
                )
                Text(
                    text = entry.displayName.ifBlank { entry.userId.take(12) },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = bizGold
                )
                BusinessAccountBadge()
            }
            Text(
                text = buildString {
                    if (entry.handle.isNotBlank()) append("@${entry.handle} · ")
                    append("Business Account (\$99.99/mo · up to 50 sub-accounts) · ")
                    append(if (trueOnline) "Online" else "Offline")
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (trueOnline) PresenceOnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (fullProfile?.globallyBanned == true || fullProfile?.currentlySuspended() == true || msgDisabled) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (fullProfile?.globallyBanned == true) {
                        Surface(shape = RoundedCornerShape(6.dp), color = LiveCoral.copy(alpha = 0.16f)) {
                            Text("BANNED", style = MaterialTheme.typography.labelSmall, color = LiveCoral, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                    if (fullProfile?.currentlySuspended() == true) {
                        Surface(shape = RoundedCornerShape(6.dp), color = ModAmber.copy(alpha = 0.16f)) {
                            Text("SUSPENDED", style = MaterialTheme.typography.labelSmall, color = ModAmber, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                    if (msgDisabled) {
                        Surface(shape = RoundedCornerShape(6.dp), color = LiveCoral.copy(alpha = 0.16f)) {
                            Text("NO MSG", style = MaterialTheme.typography.labelSmall, color = LiveCoral, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                        }
                    }
                }
            }

            if (isAdmin) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onAdminEdit,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("biz_admin_edit_${entry.userId}")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onAdminRevokeBusiness,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("biz_admin_remove_${entry.userId}")
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = LiveCoral, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove Business", style = MaterialTheme.typography.labelSmall, color = LiveCoral)
                    }
                    OutlinedButton(
                        onClick = onAdminSuspend,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("biz_admin_suspend_${entry.userId}")
                    ) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Suspend", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = onAdminToggleMessaging,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("biz_admin_toggle_msg_${entry.userId}")
                    ) {
                        Icon(
                            Icons.Rounded.Block,
                            contentDescription = null,
                            tint = if (msgDisabled) SafetyEmerald else LiveCoral,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (msgDisabled) "Enable Msg" else "Disable Msg",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (msgDisabled) SafetyEmerald else LiveCoral
                        )
                    }
                    OutlinedButton(
                        onClick = onSendAlert,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Alert", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Text(
                    text = "Protected Business Account · Edit, Remove, Suspend, or Ban requires App Admin approval.",
                    style = MaterialTheme.typography.labelSmall,
                    color = bizGold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSupportRequestAdminApproval(BusinessApprovalAction.EDIT) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ModAmber,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("biz_support_request_admin_${entry.userId}")
                    ) {
                        Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Request Admin Approval", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    OutlinedButton(
                        onClick = onSendAlert,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Alert", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffMessagingControlsCard(
    directoryEntries: List<DirectoryEntry>,
    allUsers: List<UserProfile>,
    currentUserId: String,
    isAdmin: Boolean = true,
    nowMillis: Long = System.currentTimeMillis(),
    onOpenFeatureRestrictions: (targetUserId: String, targetName: String) -> Unit,
    onRequestBusinessAdminApproval: (DirectoryEntry) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val profileByUid = remember(allUsers) { allUsers.associateBy { it.userId } }
    val candidates = remember(directoryEntries, allUsers, currentUserId, searchQuery, nowMillis) {
        val base = directoryEntries
            .filter { it.userId != currentUserId && it.tierEnum() != StaffTier.ADMIN }
            .sortedWith(
                compareByDescending<DirectoryEntry> { entry ->
                    val prof = profileByUid[entry.userId]
                    val badgeCount = prof?.activeFeatureRestrictionBadges(nowMillis)?.size ?: 0
                    badgeCount > 0 || entry.disableMessaging
                }.thenBy { it.displayName.lowercase() }
            )
        val q = searchQuery.trim()
        if (q.isEmpty()) base.take(8)
        else base.filter {
            it.displayName.contains(q, ignoreCase = true) ||
                it.handle.contains(q, ignoreCase = true)
        }.take(10)
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_messaging_controls_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isAdmin) Icons.Rounded.Tune else Icons.Rounded.Schedule,
                    contentDescription = "Feature restriction controls",
                    tint = if (isAdmin) LiveCoral else ModAmber,
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isAdmin) {
                            "Feature Restrictions & Timed Suspensions"
                        } else {
                            "Timed Feature Suspensions (Support)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isAdmin) {
                            "Permanently disable or temporarily suspend DMs, Chat Rooms, Discovery Tab, Rooms Tab, Buying Points, Editing Profile, Posting, Login, or PTT."
                        } else {
                            "Support Staff can temporarily suspend any of the 9 features for a set time. Permanent disables require an App Admin. Business Accounts require Admin approval."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it.take(40) },
                placeholder = { Text("Search user by name or @handle…") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("staff_messaging_search_input")
            )

            if (candidates.isEmpty()) {
                Text(
                    text = "No matching members found.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val bizGold = usernameColor(StaffTier.USER, isBusiness = true)
                candidates.forEach { entry ->
                    val prof = profileByUid[entry.userId]
                    val activeBadges = prof?.activeFeatureRestrictionBadges(nowMillis).orEmpty()
                    val isDisabled = prof?.disableMessaging ?: entry.disableMessaging
                    val isBiz = entry.isBusinessAccount()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = entry.displayName,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isBiz) FontWeight.ExtraBold else FontWeight.SemiBold
                                    ),
                                    color = if (isBiz) bizGold else MaterialTheme.colorScheme.onSurface
                                )
                                if (isBiz) {
                                    BusinessAccountBadge()
                                }
                            }
                            val statusSummary = when {
                                activeBadges.isNotEmpty() -> activeBadges.take(3).joinToString(" · ") { it.first }
                                isDisabled -> "Messaging DISABLED"
                                else -> "All 9 features active"
                            }
                            Text(
                                text = "@${entry.handle} · $statusSummary",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (activeBadges.isNotEmpty() || isDisabled) ModAmber else SafetyEmerald
                            )
                        }
                        if (isBiz && !isAdmin) {
                            OutlinedButton(
                                onClick = { onRequestBusinessAdminApproval(entry) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("staff_biz_admin_only_${entry.userId}")
                            ) {
                                Text(
                                    text = "Req. Admin",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = bizGold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onOpenFeatureRestrictions(entry.userId, entry.displayName)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("staff_feature_restrict_${entry.userId}")
                            ) {
                                Text(
                                    text = if (isAdmin) "Restrict / Timer" else "Timed Suspend",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isAdmin) LiveCoral else ModAmber
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SafetyReportCard(
    report: SafetyReport,
    isHostOrAdmin: Boolean,
    isAdminViewer: Boolean = false,
    targetIsBusinessAccount: Boolean = false,
    canToggleMessaging: Boolean = false,
    targetMessagingDisabled: Boolean = false,
    isBusinessDmReport: Boolean = false,
    isGroupDmReport: Boolean = false,
    canEnterReportedGroupDm: Boolean = false,
    canManageFeatureRestrictions: Boolean = false,
    canRemoveFromBusiness: Boolean = false,
    canRequestBusinessAdminApproval: Boolean = false,
    canBanDevice: Boolean = false,
    onToggleMessaging: () -> Unit = {},
    onEnterReportedGroupVisible: () -> Unit = {},
    onEnterReportedGroupInvisible: () -> Unit = {},
    onOpenFeatureRestrictions: () -> Unit = {},
    onRemoveFromBusiness: () -> Unit = {},
    onRequestBusinessAdminApproval: () -> Unit = {},
    onBanDevice: () -> Unit = {},
    onResolve: () -> Unit,
    onDismiss: () -> Unit
) {
    val status = report.statusEnum()
    val statusColor = when (status) {
        ReportStatus.OPEN -> ModAmber
        ReportStatus.RESOLVED -> SafetyEmerald
        ReportStatus.DISMISSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusColor.copy(alpha = 0.16f)
                ) {
                    Text(
                        text = status.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = formatTimestampShort(report.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isBusinessDmReport || isGroupDmReport) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ModAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isBusinessDmReport) {
                            "Business Group DM Report · Staff access unlocked (enter Visible or Invisible to moderate)"
                        } else {
                            "Group DM Report · Support & Admins can enter this Group DM (Visible or Invisible)"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = ModAmber,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            val bizGold = usernameColor(StaffTier.USER, isBusiness = true)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${report.categoryEnum().label} · Reported ${report.targetName}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (targetIsBusinessAccount) FontWeight.ExtraBold else FontWeight.SemiBold
                    ),
                    color = if (targetIsBusinessAccount) bizGold else MaterialTheme.colorScheme.onSurface
                )
                if (targetIsBusinessAccount) {
                    BusinessAccountBadge()
                }
            }
            Text(
                text = "Room: ${report.roomTitle} · Filed by ${report.reporterName}",
                style = MaterialTheme.typography.labelMedium,
                color = VoiceCyan
            )
            Text(
                text = report.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (report.resolutionNote.isNotBlank()) {
                Text(
                    text = "Outcome: ${report.resolutionNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SafetyEmerald
                )
            }

            if (canEnterReportedGroupDm) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onEnterReportedGroupVisible,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyEmerald,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("report_enter_group_visible_${report.id}")
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enter Visible", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Button(
                        onClick = onEnterReportedGroupInvisible,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("report_enter_group_invisible_${report.id}")
                    ) {
                        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Enter Invisible", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }

            if (canManageFeatureRestrictions) {
                OutlinedButton(
                    onClick = onOpenFeatureRestrictions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_feature_restrictions_${report.id}")
                ) {
                    Icon(
                        imageVector = if (isAdminViewer) Icons.Rounded.Tune else Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = if (isAdminViewer) LiveCoral else ModAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isAdminViewer) {
                            "Feature Restrictions / Timed Suspend (${report.targetName})"
                        } else {
                            "Timed Feature Suspension (${report.targetName})"
                        },
                        color = if (isAdminViewer) LiveCoral else ModAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (canRequestBusinessAdminApproval) {
                Button(
                    onClick = onRequestBusinessAdminApproval,
                    colors = ButtonDefaults.buttonColors(containerColor = ModAmber, contentColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_request_biz_admin_${report.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Request App Admin Approval (Business Account)", fontWeight = FontWeight.Bold)
                }
            }

            if (canRemoveFromBusiness) {
                Button(
                    onClick = onRemoveFromBusiness,
                    colors = ButtonDefaults.buttonColors(containerColor = ModAmber, contentColor = Color.Black),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_remove_from_business_${report.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonRemove,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove ${report.targetName} from Business Account", fontWeight = FontWeight.Bold)
                }
            }

            if (canBanDevice) {
                Button(
                    onClick = onBanDevice,
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_ban_device_${report.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhonelinkErase,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ban ${report.targetName}'s Device (Block All Logins)", fontWeight = FontWeight.Bold)
                }
            }

            if (canToggleMessaging) {
                OutlinedButton(
                    onClick = onToggleMessaging,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("report_toggle_messaging_${report.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = if (targetMessagingDisabled) SafetyEmerald else LiveCoral,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (targetMessagingDisabled) {
                            "Re-enable Messaging for ${report.targetName}"
                        } else {
                            "Disable Messaging for ${report.targetName}"
                        },
                        color = if (targetMessagingDisabled) SafetyEmerald else LiveCoral
                    )
                }
            }

            if (status == ReportStatus.OPEN) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dismiss_report_${report.id}")
                    ) {
                        Text("Dismiss")
                    }
                    Button(
                        onClick = onResolve,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyEmerald,
                            contentColor = Color(0xFF00261A)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("resolve_report_${report.id}")
                    ) {
                        Text(if (isHostOrAdmin) "Resolve" else "Mark Resolved")
                    }
                }
            }
        }
    }
}
