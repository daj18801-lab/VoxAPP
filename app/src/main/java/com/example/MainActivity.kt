package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
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
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutFromVoxStage
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
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.showTopMessage
import com.example.ui.screens.UserProfileScreen
import com.example.ui.theme.VoxStageTheme
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageSyncWorker
import com.example.viewmodel.DirectChatViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RoomViewModel
import com.example.viewmodel.UserProfileViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
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
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        VoxStageTheme(darkTheme = false) {
            AuthScreen(onAuthSuccess = {})
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

    val mainViewModel: MainViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val db = FirebaseFirestore.getInstance()
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
    val publicRoomsState by mainViewModel.publicRoomsState.collectAsStateWithLifecycle()
    val myRoomsState by mainViewModel.myRoomsState.collectAsStateWithLifecycle()
    val safetyReportsState by mainViewModel.safetyReportsState.collectAsStateWithLifecycle()
    val unreadAlerts by mainViewModel.unreadAlertsState.collectAsStateWithLifecycle()
    val staffRoles by mainViewModel.staffRolesState.collectAsStateWithLifecycle()
    val staffInvites by mainViewModel.staffInvitesState.collectAsStateWithLifecycle()
    val allUsers by mainViewModel.allUsersState.collectAsStateWithLifecycle()
    val allAlerts by mainViewModel.allAlertsState.collectAsStateWithLifecycle()
    val searchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by mainViewModel.selectedCategory.collectAsStateWithLifecycle()
    val showOnlyMyRooms by mainViewModel.showOnlyMyRooms.collectAsStateWithLifecycle()
    val isCreatingRoom by mainViewModel.isCreatingRoom.collectAsStateWithLifecycle()

    val userProfile = (userProfileState as? UiState.Success)?.data
    val nightModeEnabled = userProfile?.nightModeEnabled ?: false

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
            navigateToTopTab(ROUTE_PROFILE)
        } else if (targetUid.isNotBlank()) {
            navController.navigate("user/$targetUid")
        }
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

    LaunchedEffect(Unit) {
        mainViewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
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
                                    onDeletePost = mainViewModel::deleteProfilePost
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
                                    onOpenRoom = { roomId ->
                                        if (mainViewModel.canEnterRoomOrNotify()) {
                                            navController.navigate("room/$roomId")
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
                                    onAdminDeleteAlert = mainViewModel::adminDeleteAlert
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
                                    isPublishingPost = isPublishingPost,
                                    isUpdatingPhoto = isUpdatingPhoto,
                                    onUpdateProfilePhoto = mainViewModel::updateProfilePhoto,
                                    onCreatePost = mainViewModel::createProfilePost,
                                    onToggleLikePost = mainViewModel::toggleLikePost,
                                    onDeletePost = mainViewModel::deleteProfilePost,
                                    onSaveProfile = mainViewModel::updateProfile,
                                    onSignOut = {
                                        signOutFromVoxStage(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
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
                                            val db = FirebaseFirestore.getInstance()
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
                                UserProfileScreen(
                                    viewModel = profileViewModel,
                                    staffCapabilities = staffCapabilities,
                                    friendship = existingFriendship,
                                    onBack = { navController.popBackStack() },
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
                                    onRemoveOrDeclineFriendship = mainViewModel::removeOrDeclineFriendship
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
                                            val db = FirebaseFirestore.getInstance()
                                            DirectChatViewModel(
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
                                DirectChatScreen(
                                    viewModel = dmViewModel,
                                    userProfile = effectiveProfile,
                                    myStaffTier = staffCapabilities.tier,
                                    otherPhotoBase64 = directoryPhotoByUid[dmOtherUid].orEmpty(),
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
                                            val db = FirebaseFirestore.getInstance()
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

            // Global real-time Admin / Support Alert Modal
            unreadAlerts.firstOrNull()?.let { latestAlert ->
                IncomingAlertModalDialog(
                    alert = latestAlert,
                    onAcknowledge = { mainViewModel.acknowledgeAlert(latestAlert.id) }
                )
            }
        }
    }
}
