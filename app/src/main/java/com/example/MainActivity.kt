package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
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
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.OutlinedTextField
import com.example.auth.AuthScreen
import com.example.auth.TrialSession
import com.example.auth.TrialSessionManager
import com.example.auth.authStateFlow
import com.example.auth.signOutFromVoxStage
import com.example.billing.PlayBillingEvent
import com.example.billing.PlayBillingManager
import com.example.data.AppConfig
import com.example.data.DirectThread
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
import com.example.ui.screens.RoomScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.screens.TrialVoxStageApp
import com.example.ui.components.AgeVerificationDialog
import com.example.ui.components.FloatingPttOverlayBubble
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.showTopMessage
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.ModAmber
import com.example.ui.theme.VoxStageTheme
import com.example.media.LivePttSessionManager
import com.example.notifications.DirectMessageNotificationHelper
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
    TopDestination(ROUTE_DISCOVER_USERS, "Discover", Icons.Rounded.Explore, Icons.Outlined.Explore, "nav_discover_users"),
    TopDestination(ROUTE_DISCOVER, "Rooms", Icons.Rounded.GraphicEq, Icons.Outlined.GraphicEq, "nav_discover"),
    TopDestination(ROUTE_SAFETY, "Safety", Icons.Rounded.Shield, Icons.Outlined.Shield, "nav_safety"),
    TopDestination(ROUTE_PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.PersonOutline, "nav_profile")
)

class MainActivity : ComponentActivity() {
    private val _pendingDmThreadId = MutableStateFlow<String?>(null)
    private val pendingDmThreadId = _pendingDmThreadId.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        enableEdgeToEdge()
        setContent {
            val targetDmThreadId by pendingDmThreadId.collectAsStateWithLifecycle()
            VoxStageRoot(
                pendingDmThreadId = targetDmThreadId,
                onConsumePendingDmThread = { _pendingDmThreadId.value = null }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        LivePttSessionManager.onAppForegrounded(this)
    }

    override fun onStop() {
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
    val appConfig by mainViewModel.appConfigState.collectAsStateWithLifecycle()
    val pendingRoomTransfer by mainViewModel.pendingRoomTransferState.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by mainViewModel.selectedCategory.collectAsStateWithLifecycle()
    val showOnlyMyRooms by mainViewModel.showOnlyMyRooms.collectAsStateWithLifecycle()
    val isCreatingRoom by mainViewModel.isCreatingRoom.collectAsStateWithLifecycle()

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

    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showTopNav = currentRoute in listOf(
        ROUTE_CHATS,
        ROUTE_DISCOVER_USERS,
        ROUTE_FRIENDS,
        ROUTE_DISCOVER,
        ROUTE_SAFETY,
        ROUTE_PROFILE
    )

    fun navigateToTopTab(route: String) {
        if (currentRoute != route) {
            navController.navigate(route) {
                popUpTo(ROUTE_CHATS) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
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
    DisposableEffect(lifecycleOwner, currentUserId) {
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            mainViewModel.setOnlinePresence(true)
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> mainViewModel.setOnlinePresence(true)
                Lifecycle.Event.ON_STOP -> mainViewModel.setOnlinePresence(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mainViewModel.setOnlinePresence(false)
        }
    }

    LaunchedEffect(lifecycleOwner, currentUserId) {
        while (true) {
            delay(60_000L)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mainViewModel.setOnlinePresence(true)
            }
        }
    }

    // Automatically manage the persistent Live Walkie-Talkie PTT channel whenever
    // BOTH users in a private chat have Push-to-Talk selected.
    LaunchedEffect(directThreadsState, userProfile, staffCapabilities.isAdmin, currentUserId) {
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

    LaunchedEffect(Unit) {
        mainViewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    // Credit points in Firestore ONLY after Google Play Store verifies & consumes the purchase
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
                is PlayBillingEvent.Message -> {
                    snackbarHostState.showTopMessage(event.text)
                }
            }
        }
    }

    // Automatically navigate user when an Admin sends them to a different room
    LaunchedEffect(pendingRoomTransfer?.targetRoomId, pendingRoomTransfer?.createdAt) {
        val transfer = pendingRoomTransfer ?: return@LaunchedEffect
        if (transfer.targetRoomId.isNotBlank()) {
            mainViewModel.consumeRoomTransfer()
            val note = if (transfer.reason.isNotBlank()) " (${transfer.reason})" else ""
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

    VoxStageTheme(darkTheme = nightModeEnabled) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
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
                                    label = { Text(dest.label) },
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
                            top = if (showTopNav) innerPadding.calculateTopPadding() else 0.dp,
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
                                    onOpenProfileTab = { navigateToTopTab(ROUTE_PROFILE) }
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
                                    onCloseSupportTicket = mainViewModel::closeMySupportTicket
                                )
                            }

                            composable(ROUTE_FRIENDS) {
                                FriendsScreen(
                                    currentUserId = currentUserId,
                                    friendships = friendships,
                                    directoryEntries = directoryEntries,
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
                                    onEditRoom = mainViewModel::editRoomDetails,
                                    onCloseRoom = mainViewModel::closeRoomFromLobby,
                                    onCreateRoom = { title, topic, category, coverKey, visibility, videoEnabled, autoModEnabled, autoModBlockMode, linksBlocked, slowModeSeconds, maxSpeakers ->
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
                                            onCreated = { newRoomId ->
                                                navController.navigate("room/$newRoomId")
                                            }
                                        )
                                    }
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
                                    onSendUserToRoom = mainViewModel::sendUserToRoom
                                )
                            }

                            composable(ROUTE_PROFILE) {
                                ProfileScreen(
                                    currentUserId = currentUserId,
                                    userProfile = userProfile,
                                    userEmail = initialEmail,
                                    staffCapabilities = staffCapabilities,
                                    friendsCount = acceptedFriendsCount,
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
                                        val hostActivity = context as? Activity
                                        if (hostActivity != null) {
                                            playBillingManager.launchPointsPurchase(hostActivity, pointsToBuy)
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showTopMessage("Unable to open Google Play checkout.")
                                            }
                                        }
                                    },
                                    playBillingManager = playBillingManager,
                                    onSetDmPolicy = mainViewModel::setDmPolicy,
                                    onKickPaidDmMember = mainViewModel::kickPaidDmMember,
                                    onRequestAgeVerification = { showAgeVerificationDialog = true },
                                    onSaveProfile = mainViewModel::updateProfile,
                                    onSignOut = {
                                        scope.launch {
                                            mainViewModel.setOnlinePresenceNow(false)
                                            signOutFromVoxStage(
                                                credentialManager = credentialManager,
                                                scope = scope
                                            )
                                        }
                                    }
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
                                    onOpenBuyPoints = ::openProfileBuyPoints
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
                                                initialStaffCapabilities = staffCapabilities
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

            // Global $10 Paid Private Message Unlock Prompt ($5 user / $5 app owner)
            pendingPaidDmTarget?.let { target ->
                AlertDialog(
                    onDismissRequest = mainViewModel::dismissPaidDmPrompt,
                    title = { Text("Unlock Private Messages (\$10)") },
                    text = {
                        Text(
                            "${target.displayName} has private messages locked to paid users. Pay \$10 to unlock private messaging with them (\$5 goes to ${target.displayName} and \$5 goes to the app owner). Note: ${target.displayName} can kick any paid member from messaging them at any time."
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
                            modifier = Modifier.testTag("confirm_pay_10_dm_button")
                        ) {
                            Text(if (isProcessingWallet) "Processing…" else "Pay \$10 & Message")
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
        }
    }
}

