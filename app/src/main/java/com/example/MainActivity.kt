package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import com.example.auth.AuthScreen
import com.example.auth.TrialSession
import com.example.auth.TrialSessionManager
import com.example.auth.authStateFlow
import com.example.auth.signOutFromVoxStage
import com.example.billing.PlayBillingEvent
import com.example.billing.PlayBillingManager
import com.example.data.AlertSeverity
import com.example.data.AppConfig
import com.example.data.DirectThread
import com.example.data.ProTier
import com.example.data.ReportStatus
import com.example.data.SuspensionRequestStatus
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.VoxStageRepository
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.DirectChatScreen
import com.example.ui.screens.DiscoverUsersScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.IncomingAlertModalDialog
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReelsScreen
import com.example.ui.screens.RoomScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.screens.TrialVoxStageApp
import com.example.ui.components.AccountSuspendedGateScreen
import com.example.ui.components.Age13GateDialog
import com.example.ui.components.AgeVerificationDialog
import com.example.ui.components.FloatingPttOverlayBubble
import com.example.ui.components.LocalProUserIds
import com.example.ui.components.LocalSpotlightFrameByUserId
import com.example.ui.components.StrikeWarningModalDialog
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.showTopMessage
import com.example.ui.screens.UserProfileScreen
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.VoxStageTheme
import com.example.media.LivePttSessionManager
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.notifications.DirectMessageSyncWorker
import com.example.viewmodel.DirectChatViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RoomViewModel
import com.example.viewmodel.UserProfileViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

const val ROUTE_CHATS = "chats"
const val ROUTE_REELS = "reels"
const val ROUTE_DISCOVER_USERS = "discover_users"
const val ROUTE_FRIENDS = "friends"
const val ROUTE_DISCOVER = "discover"
const val ROUTE_SAFETY = "safety"
const val ROUTE_PROFILE = "profile"
const val ROUTE_USER_PROFILE = "user/{userId}"
const val ROUTE_DM = "dm/{threadId}"
const val ROUTE_ROOM = "room/{roomId}"

private data class TopDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val topDestinations = listOf(
    TopDestination(ROUTE_CHATS, "Chats", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chats"),
    TopDestination(ROUTE_REELS, "Reels", Icons.Rounded.VideoLibrary, Icons.Outlined.VideoLibrary, "nav_reels"),
    TopDestination(ROUTE_DISCOVER_USERS, "Discover", Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_discover_users"),
    TopDestination(ROUTE_DISCOVER, "Rooms", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq, "nav_discover"),
    TopDestination(ROUTE_PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.PersonOutline, "nav_profile")
)

class MainActivity : ComponentActivity() {
    private val _pendingDmThreadId = MutableStateFlow<String?>(null)
    private val pendingDmThreadId = _pendingDmThreadId.asStateFlow()
    private var isVolumeDownPttHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            val targetDmThreadId by pendingDmThreadId.collectAsStateWithLifecycle()
            VoxStageRoot(
                pendingDmThreadId = targetDmThreadId,
                onConsumePendingDmThread = { _pendingDmThreadId.value = null }
            )
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        if (LivePttSessionManager.handleHardwarePttKey(this, pressed = true)) {
                            isVolumeDownPttHeld = true
                            LivePttSessionManager.triggerPttHaptic(this)
                            return true
                        }
                    } else if (isVolumeDownPttHeld) {
                        return true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (isVolumeDownPttHeld) {
                        isVolumeDownPttHeld = false
                        LivePttSessionManager.handleHardwarePttKey(this, pressed = false)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun releaseVolumeDownPttIfHeld() {
        if (isVolumeDownPttHeld) {
            isVolumeDownPttHeld = false
            LivePttSessionManager.handleHardwarePttKey(this, pressed = false)
        }
    }

    override fun onStart() {
        super.onStart()
        LivePttSessionManager.onAppForegrounded(this)
    }

    override fun onPause() {
        releaseVolumeDownPttIfHeld()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            releaseVolumeDownPttIfHeld()
        }
    }

    override fun onStop() {
        releaseVolumeDownPttIfHeld()
        super.onStop()
        LivePttSessionManager.onAppBackgrounded(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        val threadId = intent?.getStringExtra(DirectMessageNotificationHelper.EXTRA_DM_THREAD_ID)
        if (!threadId.isNullOrBlank()) {
            _pendingDmThreadId.value = threadId
            intent.removeExtra(DirectMessageNotificationHelper.EXTRA_DM_THREAD_ID)
        }
    }
}

@Composable
fun VoxStageRoot(
    pendingDmThreadId: String? = null,
    onConsumePendingDmThread: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    val firestoreDatabaseId = remember(context) {
        context.getString(R.string.firestore_database_id)
    }
    val publicConfigRepo = remember(firestoreDatabaseId) {
        VoxStageRepository(FirebaseFirestore.getInstance(firestoreDatabaseId))
    }
    val appConfig by publicConfigRepo.observeAppConfig().collectAsStateWithLifecycle(initialValue = AppConfig())

    var activeTrialSession by remember {
        mutableStateOf<TrialSession?>(TrialSessionManager.getActiveValidSession(context))
    }
    var trialNoticeMessage by remember { mutableStateOf<String?>(null) }

    if (user == null) {
        LaunchedEffect(Unit) {
            (context as? ComponentActivity)?.enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                ),
                navigationBarStyle = SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            )
        }
        val currentTrial = activeTrialSession
        VoxStageTheme(darkTheme = false) {
            if (currentTrial != null && appConfig.trialLoginsEnabled && !currentTrial.isExpired()) {
                TrialVoxStageApp(
                    trialSession = currentTrial,
                    appConfig = appConfig,
                    onExitToGoogleSignIn = { reason ->
                        TrialSessionManager.deactivateTrial(context)
                        activeTrialSession = null
                        trialNoticeMessage = reason
                    }
                )
            } else {
                if (currentTrial != null) {
                    TrialSessionManager.deactivateTrial(context)
                    activeTrialSession = null
                }
                AuthScreen(
                    appConfig = appConfig,
                    trialNoticeMessage = trialNoticeMessage,
                    onAuthSuccess = {
                        trialNoticeMessage = null
                        activeTrialSession = null
                    },
                    onTrialLoginStarted = { startedSession ->
                        trialNoticeMessage = null
                        activeTrialSession = startedSession
                    }
                )
            }
        }
    } else {
        val uid = user.uid
        key(uid) {
            AuthenticatedVoxStageApp(
                currentUserId = uid,
                initialDisplayName = user.displayName ?: "Studio Host",
                initialEmail = user.email ?: "",
                initialEmailVerified = user.isEmailVerified,
                pendingDmThreadId = pendingDmThreadId,
                onConsumePendingDmThread = onConsumePendingDmThread
            )
        }
    }
}

@Composable
private fun AuthenticatedVoxStageApp(
    currentUserId: String,
    initialDisplayName: String,
    initialEmail: String,
    initialEmailVerified: Boolean,
    pendingDmThreadId: String?,
    onConsumePendingDmThread: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val firestoreDatabaseId = remember(context) {
        context.getString(R.string.firestore_database_id)
    }

    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance(firestoreDatabaseId)
                MainViewModel(
                    repository = VoxStageRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName,
                    initialEmail = initialEmail,
                    initialEmailVerified = initialEmailVerified
                )
            }
        }
    )

    val staffCapabilities by mainViewModel.staffCapabilities.collectAsStateWithLifecycle()
    val userProfileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val directThreadsState by mainViewModel.directThreadsState.collectAsStateWithLifecycle()
    val friendships by mainViewModel.friendshipsState.collectAsStateWithLifecycle()
    val directoryEntries by mainViewModel.userDirectoryState.collectAsStateWithLifecycle()
    val myPostsState by mainViewModel.myPostsState.collectAsStateWithLifecycle()
    val recentPostsState by mainViewModel.recentPostsState.collectAsStateWithLifecycle()
    val isPublishingPost by mainViewModel.isPublishingPost.collectAsStateWithLifecycle()
    val isUpdatingPhoto by mainViewModel.isUpdatingPhoto.collectAsStateWithLifecycle()
    val isProcessingWallet by mainViewModel.isProcessingWallet.collectAsStateWithLifecycle()
    val mySubscriptions by mainViewModel.mySubscriptionsState.collectAsStateWithLifecycle()
    val myTransactions by mainViewModel.myTransactionsState.collectAsStateWithLifecycle()
    val allTransactions by mainViewModel.allTransactionsState.collectAsStateWithLifecycle()
    val paidDmMembersForMe by mainViewModel.paidDmMembersForMeState.collectAsStateWithLifecycle()
    val pendingPaidDmTarget by mainViewModel.pendingPaidDmTarget.collectAsStateWithLifecycle()
    val publicRoomsState by mainViewModel.publicRoomsState.collectAsStateWithLifecycle()
    val myRoomsState by mainViewModel.myRoomsState.collectAsStateWithLifecycle()
    val safetyReportsState by mainViewModel.safetyReportsState.collectAsStateWithLifecycle()
    val unreadAlerts by mainViewModel.unreadAlertsState.collectAsStateWithLifecycle()
    val staffRoles by mainViewModel.staffRolesState.collectAsStateWithLifecycle()
    val staffInvites by mainViewModel.staffInvitesState.collectAsStateWithLifecycle()
    val allUsers by mainViewModel.allUsersState.collectAsStateWithLifecycle()
    val allAlerts by mainViewModel.allAlertsState.collectAsStateWithLifecycle()
    val supportTickets by mainViewModel.supportTicketsState.collectAsStateWithLifecycle()
    val myFollowers by mainViewModel.myFollowersState.collectAsStateWithLifecycle()
    val myFollowing by mainViewModel.myFollowingState.collectAsStateWithLifecycle()
    val appConfig by mainViewModel.appConfigState.collectAsStateWithLifecycle()
    val appBroadcasts by mainViewModel.appBroadcastsState.collectAsStateWithLifecycle()
    val followerCountsByUserId by mainViewModel.followerCountsByUserId.collectAsStateWithLifecycle()
    val pendingRoomTransfer by mainViewModel.pendingRoomTransferState.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by mainViewModel.selectedCategory.collectAsStateWithLifecycle()
    val showOnlyMyRooms by mainViewModel.showOnlyMyRooms.collectAsStateWithLifecycle()
    val isCreatingRoom by mainViewModel.isCreatingRoom.collectAsStateWithLifecycle()
    val reelsState by mainViewModel.reelsState.collectAsStateWithLifecycle()
    val isUploadingReel by mainViewModel.isUploadingReel.collectAsStateWithLifecycle()
    val incomingReferral by mainViewModel.incomingReferralState.collectAsStateWithLifecycle()
    val invitedReferrals by mainViewModel.invitedReferralsState.collectAsStateWithLifecycle()
    val profileViews by mainViewModel.profileViewsState.collectAsStateWithLifecycle()
    val myModerationFile by mainViewModel.myModerationFileState.collectAsStateWithLifecycle()
    val allModerationFiles by mainViewModel.allModerationFilesState.collectAsStateWithLifecycle()
    val suspensionRequests by mainViewModel.suspensionRequestsState.collectAsStateWithLifecycle()
    val allCreatorSubscriptions by mainViewModel.allCreatorSubscriptionsState.collectAsStateWithLifecycle()
    val refundReports by mainViewModel.refundReportsState.collectAsStateWithLifecycle()
    val perkGrants by mainViewModel.perkGrantsState.collectAsStateWithLifecycle()
    val adminNotifications by mainViewModel.adminNotificationsState.collectAsStateWithLifecycle()
    val nowClockMs = rememberPresenceClockMillis()

    // Google Play Store Billing client for buying points
    val playBillingManager = remember(context) { PlayBillingManager(context) }
    DisposableEffect(playBillingManager) {
        playBillingManager.startConnection()
        onDispose { playBillingManager.endConnection() }
    }

    val userProfile = (userProfileState as? UiState.Success)?.data
    val nightModeEnabled = userProfile?.nightModeEnabled ?: false
    val myPointsBalance = userProfile?.pointsBalance ?: 0L
    val subscribedCreatorIds = remember(mySubscriptions) {
        mySubscriptions.filter { it.isCurrentlyActive() }.map { it.creatorId }.toSet()
    }
    var profileInitialTab by remember { mutableIntStateOf(0) }
    var showAgeVerificationDialog by remember { mutableStateOf(false) }
    var showAge13PurchaseGate by remember { mutableStateOf(false) }
    var incomingQuickReplyThread by remember { mutableStateOf<DirectThread?>(null) }
    var quickReplyDraft by remember { mutableStateOf("") }
    var incomingFriendAlertThread by remember { mutableStateOf<DirectThread?>(null) }
    val pttActiveThreadId by LivePttSessionManager.activeThreadId.collectAsStateWithLifecycle()
    val pttActivePeerName by LivePttSessionManager.activePeerName.collectAsStateWithLifecycle()
    val pttIsConnected by LivePttSessionManager.isConnected.collectAsStateWithLifecycle()
    val pttIsConnecting by LivePttSessionManager.isConnecting.collectAsStateWithLifecycle()
    val pttIsTransmitting by LivePttSessionManager.isTransmitting.collectAsStateWithLifecycle()
    val pttRemoteSpeaker by LivePttSessionManager.remoteSpeakingName.collectAsStateWithLifecycle()
    var canDrawSystemOverlay by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context))
    }
    var dismissedInAppFloatingPttThreadId by remember { mutableStateOf<String?>(null) }
    var dismissedBroadcastIds by remember(currentUserId) {
        mutableStateOf(DirectMessageNotificationPrefs.getDismissedBroadcastIds(context, currentUserId))
    }
    val nextUnreadAppBroadcast = remember(appBroadcasts, dismissedBroadcastIds) {
        appBroadcasts.firstOrNull { it.id.isNotBlank() && it.id !in dismissedBroadcastIds }
    }
    val appLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(appLifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawSystemOverlay =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
            }
        }
        appLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { appLifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(pttActiveThreadId) {
        if (pttActiveThreadId == null || pttActiveThreadId != dismissedInAppFloatingPttThreadId) {
            dismissedInAppFloatingPttThreadId = null
        }
    }
    val viewerIsAdultVerified = userProfile?.isAdult18Plus() == true
    val liveRoomsList = remember(publicRoomsState) {
        (publicRoomsState as? UiState.Success)?.data.orEmpty()
    }

    val unreadChatsCount = remember(directThreadsState, currentUserId) {
        (directThreadsState as? UiState.Success)?.data?.count { it.isUnreadFor(currentUserId) } ?: 0
    }
    val incomingRequestsCount = remember(friendships, currentUserId) {
        friendships.count { it.isIncomingFor(currentUserId) }
    }
    val acceptedFriendsCount = remember(friendships) {
        friendships.count { it.isAccepted() }
    }
    val liveRoomsCount = remember(publicRoomsState) {
        (publicRoomsState as? UiState.Success)?.data?.size ?: 0
    }
    val directoryPhotoByUid = remember(directoryEntries) {
        directoryEntries.associate { it.userId to it.photoBase64 }
    }
    val proUserIds = remember(directoryEntries, userProfile?.proMember, userProfile?.proExpiresAtMillis, currentUserId) {
        buildSet {
            directoryEntries.forEach { entry ->
                if (entry.isProActive()) add(entry.userId)
            }
            if (userProfile?.isProSubscriberOnly() == true) {
                add(currentUserId)
            }
        }
    }
    val activeSpotlightFramesByUserId = remember(
        directoryEntries,
        userProfile?.spotlightFrameUntilMillis,
        userProfile?.spotlightFrameColorHex,
        currentUserId
    ) {
        val nowMs = System.currentTimeMillis()
        buildMap {
            directoryEntries.forEach { entry ->
                entry.activeSpotlightColorHex(nowMs)?.let { hex ->
                    put(entry.userId, hex)
                }
            }
            userProfile?.activeSpotlightColorHex(nowMs)?.let { myHex ->
                put(currentUserId, myHex)
            }
        }
    }

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopNav = currentRoute in listOf(
        ROUTE_CHATS,
        ROUTE_REELS,
        ROUTE_DISCOVER_USERS,
        ROUTE_FRIENDS,
        ROUTE_DISCOVER,
        ROUTE_PROFILE
    )

    val openSafetyReportsCount = remember(safetyReportsState) {
        (safetyReportsState as? UiState.Success)?.data?.count { it.statusEnum() == ReportStatus.OPEN } ?: 0
    }
    val activeInvestigationsCount = remember(allModerationFiles) {
        allModerationFiles.count { it.investigationActive || it.suspendedUntilInvestigationResolved }
    }
    val pendingSuspensionRequestsCount = remember(suspensionRequests) {
        suspensionRequests.count { it.statusEnum() == SuspensionRequestStatus.PENDING }
    }
    val unreadAdminNotificationsCount = remember(adminNotifications) {
        adminNotifications.count { !it.read }
    }
    val safetyBadgeCount = when {
        staffCapabilities.isAdmin ->
            openSafetyReportsCount + activeInvestigationsCount + pendingSuspensionRequestsCount + unreadAdminNotificationsCount + unreadAlerts.size
        staffCapabilities.isStaff ->
            openSafetyReportsCount + activeInvestigationsCount + unreadAlerts.size
        else ->
            unreadAlerts.size + if (myModerationFile?.hasUnacknowledgedWarning() == true) 1 else 0
    }

    val isSuspendedNow = !staffCapabilities.isAdmin && (
        userProfile?.globallyBanned == true ||
            userProfile?.currentlySuspended(nowClockMs) == true ||
            myModerationFile?.isCurrentlySuspended(nowClockMs) == true
        )

    // Keep system status bar & navigation bar icon contrast crisp in Light Theme and Dark Theme
    val forceDarkStatusBar = currentRoute == ROUTE_REELS
    LaunchedEffect(nightModeEnabled, forceDarkStatusBar) {
        val useLightStatusBar = !nightModeEnabled && !forceDarkStatusBar
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (useLightStatusBar) {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            },
            navigationBarStyle = if (!nightModeEnabled) {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT
                )
            } else {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            }
        )
    }

    fun navigateToTopTab(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(ROUTE_CHATS) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun openSafetyCenter() {
        navController.navigate(ROUTE_SAFETY) {
            launchSingleTop = true
        }
    }

    fun requireAge13ForPurchase(): Boolean {
        if (staffCapabilities.isStaff || userProfile?.isAge13Plus() == true) return true
        showAge13PurchaseGate = true
        scope.launch {
            snackbarHostState.showTopMessage(
                "You must be 13 or older to buy VOX Pro membership or points."
            )
        }
        return false
    }

    fun openUserProfile(targetUid: String) {
        if (targetUid == currentUserId) {
            profileInitialTab = 0
            navigateToTopTab(ROUTE_PROFILE)
        } else if (targetUid.isNotBlank()) {
            navController.navigate("user/$targetUid")
        }
    }

    fun openProfileBuyPoints() {
        profileInitialTab = 1
        navigateToTopTab(ROUTE_PROFILE)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            DirectMessageNotificationHelper.ensureChannel(context)
            DirectMessageSyncWorker.schedule(context)
        }
    }

    LaunchedEffect(currentUserId) {
        DirectMessageNotificationHelper.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !DirectMessageNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            DirectMessageSyncWorker.schedule(context)
        }
    }

    LaunchedEffect(pendingDmThreadId) {
        val targetThread = pendingDmThreadId
        if (!targetThread.isNullOrBlank()) {
            DirectMessageNotificationHelper.cancelThreadNotification(context, targetThread)
            navController.navigate("dm/$targetThread") {
                launchSingleTop = true
            }
            onConsumePendingDmThread()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val foregroundSessionStartMs = remember(currentUserId) {
        java.util.concurrent.atomic.AtomicLong(0L)
    }

    fun flushForegroundAppTime(keepRunning: Boolean) {
        val start = foregroundSessionStartMs.get()
        if (start <= 0L) return
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - start) / 1_000L).coerceAtLeast(0L)
        if (elapsedSec >= 5L) {
            foregroundSessionStartMs.set(if (keepRunning) now else 0L)
            mainViewModel.recordAppTimeSpent(elapsedSec)
        } else if (!keepRunning) {
            foregroundSessionStartMs.set(0L)
        }
    }

    DisposableEffect(lifecycleOwner, currentUserId) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mainViewModel.setOnlinePresence(true)
        }
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            foregroundSessionStartMs.compareAndSet(0L, System.currentTimeMillis())
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mainViewModel.setOnlinePresence(true)
                Lifecycle.Event.ON_RESUME -> {
                    mainViewModel.setOnlinePresence(true)
                    foregroundSessionStartMs.compareAndSet(0L, System.currentTimeMillis())
                }
                Lifecycle.Event.ON_PAUSE -> flushForegroundAppTime(keepRunning = false)
                Lifecycle.Event.ON_STOP -> {
                    flushForegroundAppTime(keepRunning = false)
                    mainViewModel.setOnlinePresence(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            flushForegroundAppTime(keepRunning = false)
            mainViewModel.setOnlinePresence(false)
        }
    }

    LaunchedEffect(lifecycleOwner, currentUserId) {
        while (true) {
            delay(60_000L)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mainViewModel.setOnlinePresence(true)
            }
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                flushForegroundAppTime(keepRunning = true)
            }
        }
    }

    // Automatically disconnect live PTT if the user becomes suspended
    LaunchedEffect(isSuspendedNow) {
        if (isSuspendedNow) {
            LivePttSessionManager.disconnect(context)
        }
    }

    // Automatically manage the persistent Live Walkie-Talkie PTT channel whenever
    // BOTH users in a private chat have Push-to-Talk selected.
    LaunchedEffect(directThreadsState, userProfile, staffCapabilities.isAdmin, isSuspendedNow, currentUserId) {
        if (isSuspendedNow) {
            LivePttSessionManager.disconnect(context)
            return@LaunchedEffect
        }
        val threads = (directThreadsState as? UiState.Success)?.data ?: return@LaunchedEffect
        val micBlocked = !staffCapabilities.isAdmin && userProfile?.disableMic == true
        val activeTid = LivePttSessionManager.activeThreadId.value
        val mutualThreads = threads.filter { thread ->
            !thread.hasAnyBlock() &&
                !thread.isUserKicked(currentUserId) &&
                thread.isMutualPttActive(currentUserId)
        }
        val targetThread = mutualThreads.firstOrNull { it.id == activeTid } ?: mutualThreads.firstOrNull()
        if (targetThread != null && !micBlocked) {
            val myName = userProfile?.displayName?.ifBlank { initialDisplayName } ?: initialDisplayName
            LivePttSessionManager.ensureConnected(
                context = context,
                threadId = targetThread.id,
                peerName = targetThread.displayTitle(currentUserId),
                myUserId = currentUserId,
                myDisplayName = myName.ifBlank { "Member" }
            )
        } else if (LivePttSessionManager.activeThreadId.value != null) {
            LivePttSessionManager.disconnect(context)
        }
    }

    DisposableEffect(currentUserId) {
        onDispose {
            LivePttSessionManager.disconnect(context)
        }
    }

    // Play the distinct two-tone radio alert whenever a close friend sends a Priority Break-In Chirp
    var lastAlertedPriorityChirpMs by remember(currentUserId) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(directThreadsState, currentUserId) {
        val threads = (directThreadsState as? UiState.Success)?.data ?: return@LaunchedEffect
        val newest = threads
            .filter { it.hasActivePriorityChirpFor(currentUserId) && it.lastPriorityChirpAtMillis > lastAlertedPriorityChirpMs }
            .maxByOrNull { it.lastPriorityChirpAtMillis }
        if (newest != null) {
            lastAlertedPriorityChirpMs = newest.lastPriorityChirpAtMillis
            com.example.media.PttChirpSoundEngine.playPriorityBreakInChirp(
                urgent = newest.lastPriorityChirpPoints >= 2
            )
            LivePttSessionManager.triggerPttHaptic(context)
            snackbarHostState.showTopMessage(
                "🚨 Priority Break-In Chirp from ${newest.lastPriorityChirpSenderName}: ${newest.lastPriorityChirpMessage}"
            )
        }
    }

    // Keep Live PTT clip duration synchronized with Free (5s) vs Pro/Lifetime Pro/Staff (60s)
    LaunchedEffect(
        userProfile?.proMember,
        userProfile?.proExpiresAtMillis,
        userProfile?.proLifetime,
        staffCapabilities.tier
    ) {
        val maxSec = (userProfile ?: UserProfile()).pttMaxDurationSeconds(staffCapabilities.tier)
        LivePttSessionManager.setMaxDurationSeconds(maxSec)
    }

    // Keep Live PTT floating button theme & walkie chirp audio synchronized with equipped user items
    LaunchedEffect(userProfile?.selectedPttTheme, userProfile?.selectedPttChirp) {
        val profile = userProfile ?: UserProfile()
        LivePttSessionManager.setPttCustomization(
            theme = profile.selectedPttThemeEnum(),
            chirp = profile.selectedPttChirpEnum()
        )
    }

    LaunchedEffect(Unit) {
        LivePttSessionManager.limitReachedEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    LaunchedEffect(Unit) {
        mainViewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    // Credit points or Pro membership in Firestore ONLY after Google Play Store verifies purchase
    LaunchedEffect(playBillingManager) {
        playBillingManager.events.collect { event ->
            when (event) {
                is PlayBillingEvent.PurchaseVerifiedAndConsumed -> {
                    mainViewModel.buyPoints(
                        pointsToBuy = event.points,
                        playOrderId = event.orderId,
                        playProductId = event.productId
                    )
                }
                is PlayBillingEvent.ProSubscriptionVerified -> {
                    val purchasedTier = ProTier.fromProductId(event.productId)
                    mainViewModel.activateProMembership(
                        paidWithPoints = false,
                        playOrderId = event.orderId,
                        playProductId = event.productId,
                        targetTier = purchasedTier
                    )
                }
                is PlayBillingEvent.Message -> {
                    snackbarHostState.showTopMessage(event.text)
                }
            }
        }
    }

    // Automatically navigate user when an Admin sends them to a room or a Support member's private chat
    LaunchedEffect(
        pendingRoomTransfer?.transferType,
        pendingRoomTransfer?.targetRoomId,
        pendingRoomTransfer?.targetThreadId,
        pendingRoomTransfer?.createdAt
    ) {
        val transfer = pendingRoomTransfer ?: return@LaunchedEffect
        val note = if (transfer.reason.isNotBlank()) " (${transfer.reason})" else ""
        if (transfer.isSupportDmTransfer() && transfer.targetThreadId.isNotBlank()) {
            mainViewModel.consumeRoomTransfer()
            val supportLabel = transfer.targetSupportName.ifBlank { "Support" }
            snackbarHostState.showTopMessage(
                "${transfer.movedByAdminName} connected you with $supportLabel for support chat$note"
            )
            navController.navigate("dm/${transfer.targetThreadId}") {
                launchSingleTop = true
            }
        } else if (transfer.targetRoomId.isNotBlank()) {
            mainViewModel.consumeRoomTransfer()
            snackbarHostState.showTopMessage(
                "${transfer.movedByAdminName} moved you to \"${transfer.targetRoomTitle}\"$note"
            )
            navController.navigate("room/${transfer.targetRoomId}") {
                launchSingleTop = true
            }
        }
    }

    // Optional in-app Private Message Quick Reply Popup
    LaunchedEffect(Unit) {
        DirectMessageNotificationHelper.inAppPopupEvents.collect { thread ->
            incomingQuickReplyThread = thread
            quickReplyDraft = ""
        }
    }

    // Incoming Friend Attention Alert popup (paired with ring + vibrate)
    LaunchedEffect(Unit) {
        DirectMessageNotificationHelper.friendAlertEvents.collect { thread ->
            incomingFriendAlertThread = thread
        }
    }

    CompositionLocalProvider(
        LocalProUserIds provides proUserIds,
        LocalSpotlightFrameByUserId provides activeSpotlightFramesByUserId
    ) {
    VoxStageTheme(
        darkTheme = nightModeEnabled,
        forceDarkStatusBarBackground = forceDarkStatusBar
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = if (currentRoute == ROUTE_REELS) Color.Black else MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showTopNav && !useRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route ||
                                    (dest.route == ROUTE_DISCOVER_USERS && currentRoute == ROUTE_FRIENDS)
                                val badgeCount = when (dest.route) {
                                    ROUTE_CHATS -> unreadChatsCount
                                    ROUTE_DISCOVER_USERS -> incomingRequestsCount
                                    else -> 0
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTopTab(dest.route) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    icon = {
                                        if (badgeCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = Color.White
                                                    ) {
                                                        Text(if (badgeCount > 9) "9+" else "$badgeCount")
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = dest.label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = dest.label,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = if ((showTopNav && currentRoute != ROUTE_REELS) || currentRoute == ROUTE_SAFETY) {
                                innerPadding.calculateTopPadding()
                            } else {
                                0.dp
                            },
                            bottom = if (showTopNav && !useRail) innerPadding.calculateBottomPadding() else 0.dp
                        )
                ) {
                    if (showTopNav && useRail) {
                        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                            topDestinations.forEach { dest ->
                                val selected = currentRoute == dest.route ||
                                    (dest.route == ROUTE_DISCOVER_USERS && currentRoute == ROUTE_FRIENDS)
                                val badgeCount = when (dest.route) {
                                    ROUTE_CHATS -> unreadChatsCount
                                    ROUTE_DISCOVER_USERS -> incomingRequestsCount
                                    else -> 0
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateToTopTab(dest.route) },
                                    icon = {
                                        if (badgeCount > 0) {
                                            BadgedBox(
                                                badge = {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = Color.White
                                                    ) {
                                                        Text(if (badgeCount > 9) "9+" else "$badgeCount")
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = dest.label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        }
                                    },
                                    label = { Text(dest.label) },
                                    modifier = Modifier.testTag(dest.testTag)
                                )
                            }
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = ROUTE_CHATS
                        ) {
                            composable(ROUTE_CHATS) {
                                ChatsHomeScreen(
                                    currentUserId = currentUserId,
                                    userProfile = userProfile,
                                    staffCapabilities = staffCapabilities,
                                    directThreadsState = directThreadsState,
                                    friendships = friendships,
                                    directoryEntries = directoryEntries,
                                    liveRoomsCount = liveRoomsCount,
                                    isPublishingPost = isPublishingPost,
                                    viewerIsAdultVerified = viewerIsAdultVerified,
                                    onOpenThread = { threadId ->
                                        navController.navigate("dm/$threadId")
                                    },
                                    onStartChatWithUser = { otherUid, otherName, otherColor, otherTier ->
                                        mainViewModel.startPrivateChat(
                                            otherUserId = otherUid,
                                            otherName = otherName,
                                            otherColorHex = otherColor,
                                            otherTier = otherTier,
                                            onThreadReady = { threadId ->
                                                navController.navigate("dm/$threadId")
                                            }
                                        )
                                    },
                                    onCreateQuickPost = mainViewModel::createProfilePost,
                                    onRequestAgeVerification = { showAgeVerificationDialog = true },
                                    onOpenBuyPoints = ::openProfileBuyPoints,
                                    onOpenUserProfile = ::openUserProfile,
                                    onOpenFriendsTab = { navigateToTopTab(ROUTE_FRIENDS) },
                                    onOpenRoomsTab = { navigateToTopTab(ROUTE_DISCOVER) },
                                    onOpenProfileTab = { navigateToTopTab(ROUTE_PROFILE) },
                                    safetyBadgeCount = safetyBadgeCount,
                                    onOpenSafety = ::openSafetyCenter
                                )
                            }

                            composable(ROUTE_REELS) {
                                val effectiveProfile = userProfile ?: UserProfile(
                                    userId = currentUserId,
                                    displayName = initialDisplayName.ifBlank { "Studio Host" },
                                    handle = "studio"
                                )
                                ReelsScreen(
                                    reelsState = reelsState,
                                    currentUserId = currentUserId,
                                    myProfile = effectiveProfile,
                                    staffCapabilities = staffCapabilities,
                                    isUploadingReel = isUploadingReel,
                                    onUploadReel = { prepared, caption, boostToTop, onUploaded ->
                                        mainViewModel.uploadReel(
                                            preparedVideo = prepared,
                                            caption = caption,
                                            boostToTop = boostToTop,
                                            onUploaded = onUploaded
                                        )
                                    },
                                    onLoadReelChunks = mainViewModel::loadReelChunks,
                                    onToggleLikeReel = mainViewModel::toggleLikeReel,
                                    onBoostReel = { reel ->
                                        mainViewModel.boostReelToTop(reel) {
                                            openProfileBuyPoints()
                                        }
                                    },
                                    onDeleteReel = mainViewModel::deleteReel,
                                    onObserveReelComments = mainViewModel::observeReelComments,
                                    onAddReelComment = mainViewModel::addReelComment,
                                    onDeleteReelComment = mainViewModel::deleteReelComment,
                                    onIncrementReelShareCount = mainViewModel::incrementReelShareCount,
                                    onOpenUserProfile = ::openUserProfile,
                                    onOpenUpgradeToPro = ::openProfileBuyPoints,
                                    onShowMessage = { msg ->
                                        scope.launch { snackbarHostState.showTopMessage(msg) }
                                    },
                                    safetyBadgeCount = safetyBadgeCount,
                                    onOpenSafety = ::openSafetyCenter
                                )
                            }

                            composable(ROUTE_DISCOVER_USERS) {
                                DiscoverUsersScreen(
                                    currentUserId = currentUserId,
                                    staffCapabilities = staffCapabilities,
                                    directoryEntries = directoryEntries,
                                    friendships = friendships,
                                    recentPostsState = recentPostsState,
                                    myPointsBalance = myPointsBalance,
                                    subscribedCreatorIds = subscribedCreatorIds,
                                    viewerIsAdultVerified = viewerIsAdultVerified,
                                    isProcessingWallet = isProcessingWallet,
                                    onRequestAgeVerification = { showAgeVerificationDialog = true },
                                    onOpenUserProfile = ::openUserProfile,
                                    onSendFriendRequest = mainViewModel::sendFriendRequest,
                                    onAcceptFriendRequest = mainViewModel::acceptFriendRequest,
                                    onRemoveOrDeclineFriendship = mainViewModel::removeOrDeclineFriendship,
                                    onMessageUser = { otherUid, otherName, otherColor, otherTier ->
                                        mainViewModel.startPrivateChat(
                                            otherUserId = otherUid,
                                            otherName = otherName,
                                            otherColorHex = otherColor,
                                            otherTier = otherTier,
                                            onThreadReady = { threadId ->
                                                navController.navigate("dm/$threadId")
                                            }
                                        )
                                    },
                                    onToggleLikePost = mainViewModel::toggleLikePost,
                                    onDeletePost = mainViewModel::deleteProfilePost,
                                    onUnlockPost = mainViewModel::unlockProfilePost,
                                    onBoostPost = mainViewModel::boostProfilePost,
                                    onObservePostComments = mainViewModel::observePostComments,
                                    onAddPostComment = mainViewModel::addPostComment,
                                    onDeletePostComment = mainViewModel::deletePostComment,
                                    onSharePostToFeed = mainViewModel::shareProfilePostToFeed,
                                    onExternalSharePost = mainViewModel::recordExternalPostShare,
                                    onSubscribeCreator = mainViewModel::subscribeToCreator,
                                    onOpenBuyPoints = ::openProfileBuyPoints,
                                    blockedUserIds = userProfile?.blockedUserIds?.toSet().orEmpty(),
                                    supportTickets = supportTickets,
                                    onCreateSupportTicket = mainViewModel::createSupportTicket,
                                    onRespondToSupportTicket = mainViewModel::respondToSupportTicket,
                                    onCloseSupportTicket = mainViewModel::closeMySupportTicket,
                                    myUserProfile = userProfile,
                                    isPublishingPost = isPublishingPost,
                                    onCreateDiscoveryPhotoPost = mainViewModel::createProfilePost,
                                    followerCountsByUserId = followerCountsByUserId,
                                    safetyBadgeCount = safetyBadgeCount,
                                    onOpenSafety = ::openSafetyCenter
                                )
                            }

                            composable(ROUTE_FRIENDS) {
                                FriendsScreen(
                                    currentUserId = currentUserId,
                                    friendships = friendships,
                                    directoryEntries = directoryEntries,
                                    viewerIsStaff = staffCapabilities.isStaff,
                                    onSendFriendRequest = mainViewModel::sendFriendRequest,
                                    onAcceptFriendRequest = mainViewModel::acceptFriendRequest,
                                    onRemoveOrDeclineFriendship = mainViewModel::removeOrDeclineFriendship,
                                    onMessageUser = { otherUid, otherName, otherColor, otherTier ->
                                        mainViewModel.startPrivateChat(
                                            otherUserId = otherUid,
                                            otherName = otherName,
                                            otherColorHex = otherColor,
                                            otherTier = otherTier,
                                            onThreadReady = { threadId ->
                                                navController.navigate("dm/$threadId")
                                            }
                                        )
                                    },
                                    onOpenUserProfile = ::openUserProfile
                                )
                            }

                            composable(ROUTE_DISCOVER) {
                                LobbyScreen(
                                    publicRoomsState = publicRoomsState,
                                    myRoomsState = myRoomsState,
                                    userProfile = userProfile,
                                    staffCapabilities = staffCapabilities,
                                    searchQuery = searchQuery,
                                    selectedCategory = selectedCategory,
                                    showOnlyMyRooms = showOnlyMyRooms,
                                    isCreatingRoom = isCreatingRoom,
                                    currentUserId = currentUserId,
                                    onSearchChange = mainViewModel::updateSearchQuery,
                                    onCategorySelected = mainViewModel::selectCategory,
                                    onToggleMyRooms = mainViewModel::setShowOnlyMyRooms,
                                    onToggleInvisiblePreference = mainViewModel::toggleInvisiblePreference,
                                    onOpenRoom = { room ->
                                        if (mainViewModel.canEnterRoomOrNotify(room)) {
                                            navController.navigate("room/${room.id}")
                                        }
                                    },
                                    onRoomPinVerified = { rId, pin ->
                                        mainViewModel.setVerifiedPinForRoom(rId, pin)
                                    },
                                    onEditRoom = mainViewModel::editRoomDetails,
                                    onCloseRoom = mainViewModel::closeRoomFromLobby,
                                    onCreateRoom = { title, topic, category, coverKey, visibility, videoEnabled, autoModEnabled, autoModBlockMode, linksBlocked, slowModeSeconds, maxSpeakers, pinLocked, roomPinCode ->
                                        mainViewModel.createRoom(
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
                                            onCreated = { newRoomId ->
                                                navController.navigate("room/$newRoomId")
                                            }
                                        )
                                    },
                                    safetyBadgeCount = safetyBadgeCount,
                                    onOpenSafety = ::openSafetyCenter
                                )
                            }

                            composable(ROUTE_SAFETY) {
                                SafetyScreen(
                                    reportsState = safetyReportsState,
                                    userProfile = userProfile,
                                    currentUserId = currentUserId,
                                    staffCapabilities = staffCapabilities,
                                    staffRoles = staffRoles,
                                    staffInvites = staffInvites,
                                    allUsers = allUsers,
                                    allAlerts = allAlerts,
                                    appConfig = appConfig,
                                    liveRooms = liveRoomsList,
                                    onResolveReport = mainViewModel::resolveReport,
                                    onUnblockUser = mainViewModel::unblockUser,
                                    onAddStaffInvite = mainViewModel::addStaffInvite,
                                    onToggleSupportInviteInvisible = mainViewModel::toggleSupportInviteInvisible,
                                    onRemoveSupportInvite = mainViewModel::removeSupportInvite,
                                    onToggleSupportRoleInvisible = mainViewModel::toggleSupportRoleInvisible,
                                    onSetUserStaffTier = mainViewModel::setUserStaffTier,
                                    onAdminEditUser = mainViewModel::adminEditUser,
                                    onSuspendUser = mainViewModel::suspendUser,
                                    onSendDirectAlert = mainViewModel::sendDirectAlert,
                                    onAdminEditAlert = mainViewModel::adminEditAlert,
                                    onAdminDeleteAlert = mainViewModel::adminDeleteAlert,
                                    onUpdateAppConfig = mainViewModel::updateAppConfig,
                                    onSendUserToRoom = mainViewModel::sendUserToRoom,
                                    directoryEntries = directoryEntries,
                                    onSendUserToSupportChat = { target, support, reason, onSent ->
                                        mainViewModel.sendUserToSupportChat(
                                            targetUser = target,
                                            supportMember = support,
                                            reason = reason,
                                            onSent = onSent
                                        )
                                    },
                                    appBroadcasts = appBroadcasts,
                                    onSendAppBroadcast = mainViewModel::sendAppBroadcastToAllUsers,
                                    onDeleteAppBroadcast = mainViewModel::deleteAppBroadcast,
                                    onBack = { navController.popBackStack() },
                                    moderationFiles = allModerationFiles,
                                    onObserveUserFileNotes = mainViewModel::observeUserFileNotes,
                                    onResolveInvestigation = mainViewModel::resolveUserInvestigation,
                                    suspensionRequests = suspensionRequests,
                                    onSubmitSuspensionRequest = { targetUid, targetName, reason, onSubmitted ->
                                        mainViewModel.submitSupportSuspensionRequest(
                                            targetUserId = targetUid,
                                            targetName = targetName,
                                            reason = reason,
                                            onSubmitted = onSubmitted
                                        )
                                    },
                                    onAdminReviewSuspensionRequest = mainViewModel::adminReviewSuspensionRequest,
                                    refundReports = refundReports,
                                    perkGrants = perkGrants,
                                    adminNotifications = adminNotifications,
                                    allTransactions = allTransactions,
                                    allCreatorSubscriptions = allCreatorSubscriptions,
                                    onIssueRefund = { targetUid, targetName, refundType, itemLabel, amountCents, txId, subId, reason, onIssued ->
                                        mainViewModel.issueRefund(
                                            targetUserId = targetUid,
                                            targetName = targetName,
                                            refundType = refundType,
                                            itemLabel = itemLabel,
                                            amountCents = amountCents,
                                            transactionId = txId,
                                            subscriptionId = subId,
                                            reason = reason,
                                            onIssued = onIssued
                                        )
                                    },
                                    onAcknowledgeRefundReport = mainViewModel::acknowledgeRefundReport,
                                    onMarkAdminNotificationRead = mainViewModel::markAdminNotificationRead,
                                    onGrantAppPerk = { targetUid, targetName, perkType, detailValue, pts, reason, onGranted ->
                                        mainViewModel.grantAppPerk(
                                            targetUserId = targetUid,
                                            targetName = targetName,
                                            perkType = perkType,
                                            detailValue = detailValue,
                                            pointsAmount = pts,
                                            reason = reason,
                                            onGranted = onGranted
                                        )
                                    }
                                )
                            }

                            composable(ROUTE_PROFILE) {
                                ProfileScreen(
                                    currentUserId = currentUserId,
                                    userProfile = userProfile,
                                    userEmail = initialEmail,
                                    staffCapabilities = staffCapabilities,
                                    friendsCount = acceptedFriendsCount,
                                    followersCount = myFollowers.size,
                                    followingCount = myFollowing.size,
                                    myPostsState = myPostsState,
                                    mySubscriptions = mySubscriptions,
                                    myTransactions = myTransactions,
                                    allTransactionsForAdmin = allTransactions,
                                    paidDmMembersForMe = paidDmMembersForMe,
                                    isPublishingPost = isPublishingPost,
                                    isUpdatingPhoto = isUpdatingPhoto,
                                    isProcessingWallet = isProcessingWallet,
                                    initialTabIndex = profileInitialTab,
                                    onUpdateProfilePhoto = mainViewModel::updateProfilePhoto,
                                    onCreatePost = mainViewModel::createProfilePost,
                                    onBoostPost = mainViewModel::boostProfilePost,
                                    onObservePostComments = mainViewModel::observePostComments,
                                    onAddPostComment = mainViewModel::addPostComment,
                                    onDeletePostComment = mainViewModel::deletePostComment,
                                    onSharePostToFeed = mainViewModel::shareProfilePostToFeed,
                                    onExternalSharePost = mainViewModel::recordExternalPostShare,
                                    onOpenUserProfile = ::openUserProfile,
                                    onToggleLikePost = mainViewModel::toggleLikePost,
                                    onDeletePost = mainViewModel::deleteProfilePost,
                                    onBuyPoints = { pointsToBuy ->
                                        if (requireAge13ForPurchase()) {
                                            val hostActivity = context as? Activity
                                            if (hostActivity != null) {
                                                playBillingManager.launchPointsPurchase(hostActivity, pointsToBuy)
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showTopMessage("Unable to open Google Play checkout.")
                                                }
                                            }
                                        }
                                    },
                                    onUpgradeToProPlay = {
                                        if (requireAge13ForPurchase()) {
                                            val hostActivity = context as? Activity
                                            if (hostActivity != null) {
                                                playBillingManager.launchSubscription(hostActivity, ProTier.PRO)
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showTopMessage("Unable to open Google Play Pro checkout.")
                                                }
                                            }
                                        }
                                    },
                                    onUpgradeToVipProPlay = {
                                        if (requireAge13ForPurchase()) {
                                            val hostActivity = context as? Activity
                                            if (hostActivity != null) {
                                                playBillingManager.launchSubscription(hostActivity, ProTier.VIP_PRO)
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showTopMessage("Unable to open Google Play VIP Pro checkout.")
                                                }
                                            }
                                        }
                                    },
                                    onUpgradeToEliteProPlay = {
                                        if (requireAge13ForPurchase()) {
                                            val hostActivity = context as? Activity
                                            if (hostActivity != null) {
                                                playBillingManager.launchSubscription(hostActivity, ProTier.ELITE_PRO)
                                            } else {
                                                scope.launch {
                                                    snackbarHostState.showTopMessage("Unable to open Google Play Elite Pro checkout.")
                                                }
                                            }
                                        }
                                    },
                                    onActivateProWithPoints = {
                                        if (requireAge13ForPurchase()) {
                                            mainViewModel.activateProMembership(paidWithPoints = true)
                                        }
                                    },
                                    onBoostMyProfile = mainViewModel::boostMyProfile,
                                    onSelectOrBuyPttTheme = mainViewModel::purchaseOrSelectPttTheme,
                                    onSelectOrBuyPttChirp = mainViewModel::purchaseOrSelectPttChirp,
                                    onBuySpotlightFrame = mainViewModel::buySpotlightFrame,
                                    profileViews = profileViews,
                                    onUnlockProfileViewers = mainViewModel::unlockProfileViewers,
                                    onSetPriorityChirpOptIn = mainViewModel::setPriorityChirpOptIn,
                                    incomingReferral = incomingReferral,
                                    invitedReferrals = invitedReferrals,
                                    userEmailVerified = initialEmailVerified,
                                    onRedeemReferralCode = mainViewModel::redeemReferralCode,
                                    playBillingManager = playBillingManager,
                                    onSetDmPolicy = mainViewModel::setDmPolicy,
                                    onKickPaidDmMember = mainViewModel::kickPaidDmMember,
                                    onRequestAgeVerification = { showAgeVerificationDialog = true },
                                    onSaveProfile = mainViewModel::updateProfile,
                                    onSaveBio = mainViewModel::updateBio,
                                    onChangeAppearanceStatus = mainViewModel::setAppearanceStatus,
                                    onSignOut = {
                                        scope.launch {
                                            flushForegroundAppTime(keepRunning = false)
                                            mainViewModel.setOnlinePresenceNow(false)
                                            signOutFromVoxStage(
                                                credentialManager = credentialManager,
                                                scope = scope
                                            )
                                        }
                                    },
                                    safetyBadgeCount = safetyBadgeCount,
                                    onOpenSafety = ::openSafetyCenter
                                )
                            }

                            composable(
                                route = ROUTE_USER_PROFILE,
                                arguments = listOf(navArgument("userId") { type = NavType.StringType })
                            ) { entry ->
                                val targetUserId = entry.arguments?.getString("userId") ?: return@composable
                                val profileViewModel: UserProfileViewModel = viewModel(
                                    key = "user_profile_${currentUserId}_$targetUserId",
                                    factory = viewModelFactory {
                                        initializer {
                                            val db = FirebaseFirestore.getInstance(firestoreDatabaseId)
                                            UserProfileViewModel(
                                                repository = VoxStageRepository(db),
                                                currentUserId = currentUserId,
                                                targetUserId = targetUserId
                                            )
                                        }
                                    }
                                )
                                val existingFriendship = remember(friendships, targetUserId, currentUserId) {
                                    friendships.firstOrNull { it.otherUserId(currentUserId) == targetUserId }
                                }
                                val activeSubForCreator = remember(mySubscriptions, targetUserId) {
                                    mySubscriptions.firstOrNull {
                                        it.creatorId == targetUserId && it.isCurrentlyActive()
                                    }
                                }
                                UserProfileScreen(
                                    viewModel = profileViewModel,
                                    staffCapabilities = staffCapabilities,
                                    friendship = existingFriendship,
                                    myPointsBalance = myPointsBalance,
                                    activeSubscription = activeSubForCreator,
                                    viewerIsAdultVerified = viewerIsAdultVerified,
                                    isProcessingWallet = isProcessingWallet,
                                    onRequestAgeVerification = { showAgeVerificationDialog = true },
                                    onBack = { navController.popBackStack() },
                                    onOpenUserProfile = ::openUserProfile,
                                    onMessageUser = { otherUid, otherName, otherColor, otherTier ->
                                        mainViewModel.startPrivateChat(
                                            otherUserId = otherUid,
                                            otherName = otherName,
                                            otherColorHex = otherColor,
                                            otherTier = otherTier,
                                            onThreadReady = { threadId ->
                                                navController.navigate("dm/$threadId")
                                            }
                                        )
                                    },
                                    onSendFriendRequest = mainViewModel::sendFriendRequest,
                                    onAcceptFriendRequest = mainViewModel::acceptFriendRequest,
                                    onRemoveOrDeclineFriendship = mainViewModel::removeOrDeclineFriendship,
                                    onUnlockPost = mainViewModel::unlockProfilePost,
                                    onBoostPost = mainViewModel::boostProfilePost,
                                    onObservePostComments = mainViewModel::observePostComments,
                                    onAddPostComment = mainViewModel::addPostComment,
                                    onDeletePostComment = mainViewModel::deletePostComment,
                                    onSharePostToFeed = mainViewModel::shareProfilePostToFeed,
                                    onExternalSharePost = mainViewModel::recordExternalPostShare,
                                    onSubscribeCreator = mainViewModel::subscribeToCreator,
                                    onOpenBuyPoints = ::openProfileBuyPoints,
                                    supportStaffMembers = remember(directoryEntries) {
                                        directoryEntries.filter { it.tierEnum().isStaff() }
                                    },
                                    onAdminSendToSupportChat = { target, support, reason, onSent ->
                                        mainViewModel.sendUserToSupportChat(
                                            targetUser = target,
                                            supportMember = support,
                                            reason = reason,
                                            onSent = onSent
                                        )
                                    },
                                    viewerProfile = userProfile
                                )
                            }

                            composable(
                                route = ROUTE_DM,
                                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
                            ) { entry ->
                                val threadId = entry.arguments?.getString("threadId") ?: return@composable
                                val dmViewModel: DirectChatViewModel = viewModel(
                                    key = "dm_${currentUserId}_$threadId",
                                    factory = viewModelFactory {
                                        initializer {
                                            val db = FirebaseFirestore.getInstance(firestoreDatabaseId)
                                            DirectChatViewModel(
                                                context = context,
                                                repository = VoxStageRepository(db),
                                                currentUserId = currentUserId,
                                                threadId = threadId
                                            )
                                        }
                                    }
                                )
                                val effectiveProfile = userProfile ?: UserProfile(
                                    userId = currentUserId,
                                    displayName = initialDisplayName.ifBlank { "Studio Host" },
                                    handle = "studio"
                                )
                                val dmThreadState by dmViewModel.threadState.collectAsStateWithLifecycle()
                                val dmOtherUid = (dmThreadState as? UiState.Success)?.data
                                    ?.otherUserId(currentUserId).orEmpty()
                                val isAcceptedFriend = remember(friendships, dmOtherUid, currentUserId) {
                                    dmOtherUid.isNotBlank() && friendships.any {
                                        it.isAccepted() && it.otherUserId(currentUserId) == dmOtherUid
                                    }
                                }
                                DirectChatScreen(
                                    viewModel = dmViewModel,
                                    userProfile = effectiveProfile,
                                    myStaffTier = staffCapabilities.tier,
                                    otherPhotoBase64 = directoryPhotoByUid[dmOtherUid].orEmpty(),
                                    directoryEntries = directoryEntries,
                                    paidDmMembersForMe = paidDmMembersForMe,
                                    isAcceptedFriend = isAcceptedFriend,
                                    onAddUserToChat = { currentThread, addedUser ->
                                        mainViewModel.addUserToPrivateChat(
                                            currentThread = currentThread,
                                            addedUser = addedUser,
                                            onOpenThread = { newThreadId ->
                                                navController.navigate("dm/$newThreadId") {
                                                    launchSingleTop = true
                                                }
                                            }
                                        )
                                    },
                                    onKickPaidMember = mainViewModel::kickPaidDmMember,
                                    onBlockContact = { targetUid, targetName ->
                                        mainViewModel.blockContact(targetUid, targetName)
                                    },
                                    onUnblockContact = mainViewModel::unblockUser,
                                    onSendFriendAttentionAlert = { targetThread ->
                                        mainViewModel.sendFriendAttentionAlert(context, targetThread)
                                    },
                                    onOpenUserProfile = ::openUserProfile,
                                    onOpenBuyPoints = ::openProfileBuyPoints,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(
                                route = ROUTE_ROOM,
                                arguments = listOf(navArgument("roomId") { type = NavType.StringType })
                            ) { entry ->
                                val roomId = entry.arguments?.getString("roomId") ?: return@composable
                                val roomViewModel: RoomViewModel = viewModel(
                                    key = "${currentUserId}_$roomId",
                                    factory = viewModelFactory {
                                        initializer {
                                            val db = FirebaseFirestore.getInstance(firestoreDatabaseId)
                                            RoomViewModel(
                                                context = context,
                                                repository = VoxStageRepository(db),
                                                currentUserId = currentUserId,
                                                roomId = roomId,
                                                initialStaffCapabilities = staffCapabilities,
                                                enteredPin = mainViewModel.getVerifiedPinForRoom(roomId)
                                            )
                                        }
                                    }
                                )

                                val effectiveProfile = userProfile ?: UserProfile(
                                    userId = currentUserId,
                                    displayName = initialDisplayName.ifBlank { "Studio Host" },
                                    handle = "studio"
                                )
                                RoomScreen(
                                    viewModel = roomViewModel,
                                    userProfile = effectiveProfile,
                                    staffCapabilities = staffCapabilities,
                                    availableRooms = liveRoomsList,
                                    onSendUserToRoom = mainViewModel::sendUserToRoom,
                                    onExitRoom = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }

            TopSnackbarOverlay(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // In-app draggable Floating PTT button fallback when system overlay permission
            // isn't granted yet (when granted, PrivateCallForegroundService floats over all apps & in-app).
            val activePttTid = pttActiveThreadId
            if (!canDrawSystemOverlay &&
                !activePttTid.isNullOrBlank() &&
                (pttIsConnected || pttIsConnecting) &&
                dismissedInAppFloatingPttThreadId != activePttTid
            ) {
                FloatingPttOverlayBubble(
                    peerName = pttActivePeerName,
                    isTransmitting = pttIsTransmitting,
                    remoteSpeakerName = pttRemoteSpeaker,
                    onSetTransmitting = { transmitting ->
                        LivePttSessionManager.setTransmitting(
                            context = context,
                            transmitting = transmitting,
                            threadId = activePttTid,
                            peerName = pttActivePeerName
                        )
                    },
                    onOpenChat = {
                        navController.navigate("dm/$activePttTid") {
                            launchSingleTop = true
                        }
                    },
                    onDismiss = {
                        LivePttSessionManager.setTransmitting(context, false)
                        dismissedInAppFloatingPttThreadId = activePttTid
                    },
                    pttTheme = userProfile?.selectedPttThemeEnum() ?: com.example.data.PttThemeStyle.CLASSIC,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }

            // Global real-time Admin / Support Alert Modal
            unreadAlerts.firstOrNull()?.let { latestAlert ->
                IncomingAlertModalDialog(
                    alert = latestAlert,
                    onAcknowledge = { mainViewModel.acknowledgeAlert(latestAlert.id) }
                )
            }

            // Global real-time Admin App-Wide Broadcast Message to All Users
            if (unreadAlerts.isEmpty()) {
                nextUnreadAppBroadcast?.let { broadcast ->
                    val severityColor = when (broadcast.severityEnum()) {
                        AlertSeverity.URGENT -> LiveCoral
                        AlertSeverity.WARNING -> ModAmber
                        AlertSeverity.INFO -> MaterialTheme.colorScheme.primary
                    }
                    AlertDialog(
                        onDismissRequest = {
                            DirectMessageNotificationPrefs.markBroadcastDismissed(context, currentUserId, broadcast.id)
                            dismissedBroadcastIds = dismissedBroadcastIds + broadcast.id
                        },
                        title = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = severityColor.copy(alpha = 0.16f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Campaign,
                                            contentDescription = "App announcement",
                                            tint = severityColor
                                        )
                                        Text(
                                            text = "APP ANNOUNCEMENT · ${broadcast.severityEnum().name}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                            color = severityColor
                                        )
                                    }
                                }
                                Text(
                                    text = broadcast.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                        },
                        text = {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.testTag("app_broadcast_modal_dialog")
                            ) {
                                Text(
                                    text = broadcast.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "From ${broadcast.senderName} (App Admin) · ${formatTimestampShort(broadcast.createdAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    DirectMessageNotificationPrefs.markBroadcastDismissed(context, currentUserId, broadcast.id)
                                    dismissedBroadcastIds = dismissedBroadcastIds + broadcast.id
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = severityColor,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("app_broadcast_dismiss_button")
                            ) {
                                Text("Got It")
                            }
                        }
                    )
                }
            }

            // Global $5 Paid Private Message Unlock Prompt (100% to app owner; recipient earns +1 usable in-app point)
            pendingPaidDmTarget?.let { target ->
                AlertDialog(
                    onDismissRequest = mainViewModel::dismissPaidDmPrompt,
                    title = { Text("Unlock Private Messages (\$5)") },
                    text = {
                        Text(
                            "${target.displayName} has private messages locked to paid users. Pay \$5 to unlock private messaging with them (the full \$5 goes to the app owner and ${target.displayName} earns +1 usable in-app point). Note: ${target.displayName} can kick any paid member from messaging them at any time."
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = mainViewModel::confirmUnlockPaidDmAndOpenChat,
                            enabled = !isProcessingWallet,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ModAmber,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("confirm_pay_5_dm_button")
                        ) {
                            Text(if (isProcessingWallet) "Processing…" else "Pay \$5 & Message")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = mainViewModel::dismissPaidDmPrompt) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showAgeVerificationDialog) {
                AgeVerificationDialog(
                    userEmail = initialEmail.ifBlank { Firebase.auth.currentUser?.email },
                    onConfirmBirthYear = { year, source ->
                        mainViewModel.verifyAge18Plus(year, source) {
                            showAgeVerificationDialog = false
                        }
                    },
                    onDismiss = { showAgeVerificationDialog = false }
                )
            }

            // Optional In-App Private Message Quick Reply Popup
            incomingQuickReplyThread?.let { popupThread ->
                val senderTitle = popupThread.displayTitle(currentUserId)
                val previewMsg = popupThread.lastMessageText.ifBlank { "Sent you a private message" }
                AlertDialog(
                    onDismissRequest = { incomingQuickReplyThread = null },
                    title = { Text("New Message · $senderTitle") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = previewMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            OutlinedTextField(
                                value = quickReplyDraft,
                                onValueChange = { quickReplyDraft = it.take(300) },
                                label = { Text("Quick Reply") },
                                placeholder = { Text("Type a quick reply…") },
                                singleLine = false,
                                maxLines = 3,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dm_popup_quick_reply_input")
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val textToSend = quickReplyDraft.trim()
                                if (textToSend.isNotEmpty()) {
                                    mainViewModel.sendQuickReplyToThread(popupThread, textToSend) {
                                        incomingQuickReplyThread = null
                                        quickReplyDraft = ""
                                    }
                                } else {
                                    incomingQuickReplyThread = null
                                    navController.navigate("dm/${popupThread.id}") {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier.testTag("dm_popup_send_reply_button")
                        ) {
                            Text(if (quickReplyDraft.isNotBlank()) "Send Reply" else "Open Chat")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                incomingQuickReplyThread = null
                                quickReplyDraft = ""
                            },
                            modifier = Modifier.testTag("dm_popup_dismiss_button")
                        ) {
                            Text("Dismiss")
                        }
                    }
                )
            }

            // Incoming Friend Attention Alert Dialog (accompanied by ring & vibration)
            incomingFriendAlertThread?.let { alertThread ->
                val friendName = alertThread.lastAlertSenderName.ifBlank {
                    alertThread.otherName(currentUserId).ifBlank { "Your friend" }
                }
                AlertDialog(
                    onDismissRequest = { incomingFriendAlertThread = null },
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = "Friend attention alert",
                            tint = ModAmber
                        )
                    },
                    title = { Text("$friendName wants your attention!") },
                    text = {
                        Text(
                            "$friendName sent you a private chat ring & vibrate attention alert. Jump into the chat now?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                incomingFriendAlertThread = null
                                navController.navigate("dm/${alertThread.id}") {
                                    launchSingleTop = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ModAmber,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("friend_alert_open_chat_button")
                        ) {
                            Text("Open Chat")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { incomingFriendAlertThread = null }) {
                            Text("Dismiss")
                        }
                    }
                )
            }

            // Mandatory 13+ Age Gate before using VOX (non-staff users without 13+ verification)
            val needsMandatoryAge13Gate = !isSuspendedNow &&
                !staffCapabilities.isStaff &&
                userProfile != null &&
                !userProfile.isAge13Plus()
            if (needsMandatoryAge13Gate) {
                Age13GateDialog(
                    allowDismiss = false,
                    onConfirmBirthYear = { year ->
                        mainViewModel.verifyAge13Plus(year)
                    },
                    onSignOut = {
                        scope.launch {
                            flushForegroundAppTime(keepRunning = false)
                            mainViewModel.setOnlinePresenceNow(false)
                            signOutFromVoxStage(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        }
                    }
                )
            } else if (showAge13PurchaseGate) {
                Age13GateDialog(
                    reasonMessage = "You must be 13 or older to buy VOX Pro membership or points. Confirm your birth year to continue.",
                    allowDismiss = true,
                    onConfirmBirthYear = { year ->
                        mainViewModel.verifyAge13Plus(year) {
                            showAge13PurchaseGate = false
                        }
                    },
                    onSignOut = { showAge13PurchaseGate = false },
                    onDismiss = { showAge13PurchaseGate = false }
                )
            }

            // Automated Strike 2+ Formal Warning Modal
            val activeModFile = myModerationFile
            if (!isSuspendedNow &&
                !needsMandatoryAge13Gate &&
                !staffCapabilities.isAdmin &&
                activeModFile != null &&
                activeModFile.hasUnacknowledgedWarning()
            ) {
                StrikeWarningModalDialog(
                    moderationFile = activeModFile,
                    onAcknowledge = mainViewModel::acknowledgeStrikeWarning
                )
            }

            // Automated Strike 3 (1h), Strike 4 (2h), Strike 5+ (Pending Support/Admin Review), or Staff Suspension Gate
            if (isSuspendedNow) {
                AccountSuspendedGateScreen(
                    moderationFile = myModerationFile,
                    manualSuspendedUntilMillis = userProfile?.suspendedUntilMillis ?: 0L,
                    manualSuspensionReason = userProfile?.suspensionReason.orEmpty(),
                    globallyBanned = userProfile?.globallyBanned == true,
                    nowMillis = nowClockMs,
                    onSignOut = {
                        scope.launch {
                            flushForegroundAppTime(keepRunning = false)
                            mainViewModel.setOnlinePresenceNow(false)
                            signOutFromVoxStage(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        }
                    }
                )
            }
        }
    }
    }
}

