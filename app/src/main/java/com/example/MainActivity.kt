package com.example

import android.Manifest
import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhonelinkRing
import androidx.compose.material.icons.rounded.RecordVoiceOver
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.auth.AuthScreen
import com.example.auth.SubAccountLoginPrefs
import com.example.auth.TrialSession
import com.example.auth.TrialSessionManager
import com.example.auth.authStateFlow
import com.example.billing.PlayBillingEvent
import com.example.billing.PlayBillingManager
import com.example.data.AdminPreviewMode
import com.example.data.AlertSeverity
import com.example.data.AppBroadcast
import com.example.data.AppConfig
import com.example.data.ChatRoom
import com.example.data.CreatorSubscription
import com.example.data.DirectoryEntry
import com.example.data.DmAccessPass
import com.example.data.Friendship
import com.example.data.StaffTier
import com.example.data.DmPolicy
import com.example.data.PointTransaction
import com.example.data.PostComment
import com.example.data.PresenceStatus
import com.example.data.ProTier
import com.example.data.RoomCategory
import com.example.data.RoomCoverKey
import com.example.data.RoomVisibility
import com.example.data.ProfilePost
import com.example.data.PttChirpStyle
import com.example.data.PttThemeStyle
import com.example.data.ReferralRecord
import com.example.data.SpotlightFrameColor
import com.example.data.UserSex
import com.example.data.ReportStatus
import com.example.data.RestrictableFeature
import com.example.data.SuspensionRequestStatus
import com.example.data.UiState
import com.example.data.UserAlert
import com.example.data.UserProfile
import com.example.data.VoxStageRepository
import com.example.data.WorkOrderStatus
import com.example.media.LivePttSessionManager
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.notifications.DirectMessageSyncWorker
import com.example.security.DeviceBanManager
import com.example.ui.components.SuspendedAccountLoginPopup
import com.example.security.MessageSpamTracker
import com.example.ui.components.FeatureRestrictedPlaceholder
import com.example.ui.components.FloatingPttOverlayBubble
import com.example.ui.components.LocalBusinessUserIds
import com.example.ui.components.LocalCustomNameColors
import com.example.ui.components.LocalProUserIds
import com.example.ui.components.LocalSpotlightFrameByUserId
import com.example.ui.components.LocalViewerIsStaff
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.showTopMessage
import com.example.ui.screens.ChatsHomeScreen
import com.example.ui.screens.CompanyTeamConsoleScreen
import com.example.ui.screens.DirectChatScreen
import com.example.ui.screens.DiscoverUsersScreen
import com.example.ui.screens.EmployeeWorkHubScreen
import com.example.ui.screens.FriendsScreen
import com.example.ui.screens.LobbyScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RoomScreen
import com.example.ui.screens.SafetyScreen
import com.example.ui.screens.UserProfileScreen
import com.example.ui.screens.TrialVoxStageApp
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.VoxStageTheme
import com.example.viewmodel.DirectChatViewModel
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.RoomViewModel
import com.example.viewmodel.UserProfileViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val ROUTE_CHATS = "chats"
const val ROUTE_REELS = "reels"
const val ROUTE_DISCOVER_USERS = "discover_users"
const val ROUTE_FRIENDS = "friends"
const val ROUTE_DISCOVER = "discover"
const val ROUTE_SAFETY = "safety"
const val ROUTE_PROFILE = "profile"
const val ROUTE_WORK_HUB = "work_hub"
const val ROUTE_COMPANY_TEAM = "company_team"
const val ROUTE_USER_PROFILE = "user/{userId}"
const val ROUTE_DM = "dm/{threadId}?invisible={invisible}"
const val ROUTE_ROOM = "room/{roomId}"

private fun dmRoute(threadId: String, invisible: Boolean = false): String =
    "dm/$threadId?invisible=$invisible"

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
    TopDestination(ROUTE_PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.PersonOutline, "nav_profile")
)

private val subAccountTopDestinations = listOf(
    TopDestination(ROUTE_CHATS, "Team DM", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chats"),
    TopDestination(ROUTE_WORK_HUB, "Work", Icons.AutoMirrored.Rounded.Assignment, Icons.AutoMirrored.Outlined.Assignment, "nav_work_hub"),
    TopDestination(ROUTE_FRIENDS, "Team", Icons.Rounded.Groups, Icons.Outlined.Groups, "nav_team_friends"),
    TopDestination(ROUTE_PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.PersonOutline, "nav_profile")
)

private val businessOwnerTopDestinations = listOf(
    TopDestination(ROUTE_CHATS, "Chats", Icons.Rounded.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chats"),
    TopDestination(ROUTE_COMPANY_TEAM, "Company", Icons.Rounded.Business, Icons.Outlined.Business, "nav_company_team"),
    TopDestination(ROUTE_FRIENDS, "Friends", Icons.Rounded.Groups, Icons.Outlined.Groups, "nav_friends"),
    TopDestination(ROUTE_PROFILE, "Profile", Icons.Rounded.Person, Icons.Outlined.PersonOutline, "nav_profile")
)

private fun triggerUrgentBuzzVibration(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(700L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(700L)
            }
        }
    } catch (e: Exception) {
        Log.e("MainActivity", "Buzz vibration failed", e)
    }
}

class MainActivity : ComponentActivity() {
    private val _pendingDmThreadId = MutableStateFlow<String?>(null)
    private val pendingDmThreadId = _pendingDmThreadId.asStateFlow()
    private var isVolumeDownPttHeld = false
    private var isActivityForeground = false
    private var lastLockScreenLaunchMs = 0L
    private var screenOffReceiverRegistered = false

    private val foregroundScreenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            if (!isActivityForeground) return
            maybeLaunchLockScreenPttFromForeground()
        }
    }

    private fun maybeLaunchLockScreenPttFromForeground() {
        if (!DirectMessageNotificationPrefs.isLockScreenPttEnabled(this)) return
        if (LivePttSessionManager.activeThreadId.value.isNullOrBlank()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLockScreenLaunchMs < 1_500L) return
        lastLockScreenLaunchMs = now
        runCatching {
            val lockIntent = Intent(this, com.example.media.LockScreenPttActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(lockIntent)
        }.onFailure { err ->
            Log.w("MainActivity", "Unable to launch LockScreenPttActivity on screen lock", err)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LivePttSessionManager.syncAudioPrefs(this)
        if (!screenOffReceiverRegistered) {
            runCatching {
                ContextCompat.registerReceiver(
                    this,
                    foregroundScreenOffReceiver,
                    IntentFilter(Intent.ACTION_SCREEN_OFF),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                screenOffReceiverRegistered = true
            }
        }
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

    override fun onDestroy() {
        if (screenOffReceiverRegistered) {
            screenOffReceiverRegistered = false
            runCatching { unregisterReceiver(foregroundScreenOffReceiver) }
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            DirectMessageNotificationPrefs.isVolumeDownPttEnabled(this)
        ) {
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
        } else if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && isVolumeDownPttHeld) {
            releaseVolumeDownPttIfHeld()
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
        isActivityForeground = true
        LivePttSessionManager.onAppForegrounded(this)
    }

    override fun onPause() {
        releaseVolumeDownPttIfHeld()
        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        val km = getSystemService(KEYGUARD_SERVICE) as? KeyguardManager
        if (pm?.isInteractive == false || km?.isKeyguardLocked == true) {
            maybeLaunchLockScreenPttFromForeground()
        }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) {
            releaseVolumeDownPttIfHeld()
        }
    }

    override fun onStop() {
        isActivityForeground = false
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
    var pendingAdminRelogin by remember { mutableStateOf(false) }

    val rootLifecycleOwner = LocalLifecycleOwner.current
    var showFirstOpenOverlayDialog by remember(context) {
        mutableStateOf(
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !Settings.canDrawOverlays(context) &&
                !DirectMessageNotificationPrefs.hasPromptedOverlayOnFirstOpen(context)
        )
    }
    DisposableEffect(rootLifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                Settings.canDrawOverlays(context)
            ) {
                DirectMessageNotificationPrefs.setPromptedOverlayOnFirstOpen(context, true)
                showFirstOpenOverlayDialog = false
            }
        }
        rootLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { rootLifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
                    pendingAdminRelogin = pendingAdminRelogin,
                    onAdminReloginHandled = { pendingAdminRelogin = false },
                    onAuthSuccess = {
                        trialNoticeMessage = null
                        activeTrialSession = null
                        pendingAdminRelogin = false
                    },
                    onTrialLoginStarted = { startedSession ->
                        trialNoticeMessage = null
                        activeTrialSession = startedSession
                    }
                )
            }
            if (showFirstOpenOverlayDialog) {
                FirstOpenOverlayPermissionDialog(
                    onOpenSettings = {
                        DirectMessageNotificationPrefs.setPromptedOverlayOnFirstOpen(context, true)
                        showFirstOpenOverlayDialog = false
                        openAlwaysOnTopSettings(context)
                    },
                    onDismiss = {
                        DirectMessageNotificationPrefs.setPromptedOverlayOnFirstOpen(context, true)
                        showFirstOpenOverlayDialog = false
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
                onConsumePendingDmThread = onConsumePendingDmThread,
                onRequestAdminRelogin = {
                    pendingAdminRelogin = true
                    trialNoticeMessage = "Admin preview closed — signing you back in as Admin."
                }
            )
        }
        if (showFirstOpenOverlayDialog) {
            VoxStageTheme(darkTheme = false) {
                FirstOpenOverlayPermissionDialog(
                    onOpenSettings = {
                        DirectMessageNotificationPrefs.setPromptedOverlayOnFirstOpen(context, true)
                        showFirstOpenOverlayDialog = false
                        openAlwaysOnTopSettings(context)
                    },
                    onDismiss = {
                        DirectMessageNotificationPrefs.setPromptedOverlayOnFirstOpen(context, true)
                        showFirstOpenOverlayDialog = false
                    }
                )
            }
        }
    }
}

private fun openAlwaysOnTopSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val pkgIntent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).apply {
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching {
        context.startActivity(pkgIntent)
    }.isSuccess
    if (!opened) {
        val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}

@Composable
private fun FirstOpenOverlayPermissionDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("first_open_overlay_permission_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Rounded.Layers,
                contentDescription = "Display over other apps",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = "Allow Always On Top",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Text(
                text = "Enable \"Display over other apps\" (Always on top) in your phone settings so VOX Floating Push-to-Talk and urgent alerts can stay accessible while you use other apps.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("first_open_overlay_open_settings_button")
            ) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("first_open_overlay_not_now_button")
            ) {
                Text("Not Now")
            }
        }
    )
}

@Composable
private fun AuthenticatedVoxStageApp(
    currentUserId: String,
    initialDisplayName: String,
    initialEmail: String,
    initialEmailVerified: Boolean,
    pendingDmThreadId: String?,
    onConsumePendingDmThread: () -> Unit,
    onRequestAdminRelogin: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    @Suppress("UNUSED_VARIABLE")
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

    val realStaffCapabilities by mainViewModel.realStaffCapabilities.collectAsStateWithLifecycle()
    val staffCapabilities by mainViewModel.staffCapabilities.collectAsStateWithLifecycle()
    val adminPreviewMode by mainViewModel.adminPreviewMode.collectAsStateWithLifecycle()
    val realUserProfileState by mainViewModel.realUserProfileState.collectAsStateWithLifecycle()
    val userProfileState by mainViewModel.userProfileState.collectAsStateWithLifecycle()
    val directThreadsState by mainViewModel.directThreadsState.collectAsStateWithLifecycle()
    val friendships by mainViewModel.friendshipsState.collectAsStateWithLifecycle()
    val directoryEntries by mainViewModel.userDirectoryState.collectAsStateWithLifecycle()
    val isPublishingPost by mainViewModel.isPublishingPost.collectAsStateWithLifecycle()
    val isWalletBusy by mainViewModel.isProcessingWallet.collectAsStateWithLifecycle()
    val mySubscriptions by mainViewModel.mySubscriptionsState.collectAsStateWithLifecycle()
    val myTransactions by mainViewModel.myTransactionsState.collectAsStateWithLifecycle()
    val publicRoomsState by mainViewModel.publicRoomsState.collectAsStateWithLifecycle()
    val safetyReportsState by mainViewModel.safetyReportsState.collectAsStateWithLifecycle()
    val unreadAlerts by mainViewModel.unreadAlertsState.collectAsStateWithLifecycle()
    val allAlerts by mainViewModel.allAlertsState.collectAsStateWithLifecycle()
    val staffRoles by mainViewModel.staffRolesState.collectAsStateWithLifecycle()
    val staffInvites by mainViewModel.staffInvitesState.collectAsStateWithLifecycle()
    val appConfig by mainViewModel.appConfigState.collectAsStateWithLifecycle()
    val officialVoxChatRoom by mainViewModel.officialVoxChatRoomState.collectAsStateWithLifecycle()
    val refundReports by mainViewModel.refundReportsState.collectAsStateWithLifecycle()
    val perkGrants by mainViewModel.perkGrantsState.collectAsStateWithLifecycle()
    val allTransactions by mainViewModel.allTransactionsState.collectAsStateWithLifecycle()
    val allCreatorSubscriptions by mainViewModel.allCreatorSubscriptionsState.collectAsStateWithLifecycle()
    val allUsers by mainViewModel.allUsersState.collectAsStateWithLifecycle()
    val appBroadcasts by mainViewModel.appBroadcastsState.collectAsStateWithLifecycle()
    val pendingRoomTransfer by mainViewModel.pendingRoomTransferState.collectAsStateWithLifecycle()
    val incomingWorkOrders by mainViewModel.incomingWorkOrdersState.collectAsStateWithLifecycle()
    val teamWorkOrders by mainViewModel.teamWorkOrdersState.collectAsStateWithLifecycle()
    val myEmployeeRequests by mainViewModel.myEmployeeRequestsState.collectAsStateWithLifecycle()
    val teamEmployeeRequests by mainViewModel.teamEmployeeRequestsState.collectAsStateWithLifecycle()
    val myModerationFile by mainViewModel.myModerationFileState.collectAsStateWithLifecycle()
    val allModerationFiles by mainViewModel.allModerationFilesState.collectAsStateWithLifecycle()
    val suspensionRequests by mainViewModel.suspensionRequestsState.collectAsStateWithLifecycle()
    val adminNotifications by mainViewModel.adminNotificationsState.collectAsStateWithLifecycle()
    val allDeviceRecords by mainViewModel.allDeviceRecordsState.collectAsStateWithLifecycle()
    val supportTickets by mainViewModel.supportTicketsState.collectAsStateWithLifecycle()
    val companySubAccounts by mainViewModel.companySubAccountsState.collectAsStateWithLifecycle()
    val myCompanySubAccount by mainViewModel.myCompanySubAccountState.collectAsStateWithLifecycle()
    val companySubAccountDms by mainViewModel.companySubAccountDmsState.collectAsStateWithLifecycle()
    val myCompanyCodes by mainViewModel.myCompanyCodesState.collectAsStateWithLifecycle()
    val businessHelpThreads by mainViewModel.businessHelpThreadsState.collectAsStateWithLifecycle()
    val nowClockMs = rememberPresenceClockMillis()

    val playBillingManager = remember(context) { PlayBillingManager(context) }
    DisposableEffect(playBillingManager) {
        try {
            playBillingManager.startConnection()
        } catch (e: Exception) {
            Log.e("MainActivity", "PlayBilling startConnection failed", e)
        }
        onDispose {
            try {
                playBillingManager.endConnection()
            } catch (e: Exception) {
                Log.e("MainActivity", "PlayBilling endConnection failed", e)
            }
        }
    }

    val realUserProfile = (realUserProfileState as? UiState.Success)?.data
    val userProfile = (userProfileState as? UiState.Success)?.data
    val isSubAccount = userProfile?.companySubAccount == true
    val isDndActive = userProfile?.doNotDisturb == true
    val latestIsDndActive by rememberUpdatedState(isDndActive)
    val allDirectThreads = remember(directThreadsState) {
        (directThreadsState as? UiState.Success)?.data.orEmpty()
    }
    val companyThread = remember(allDirectThreads, isSubAccount, userProfile?.companyThreadId, currentUserId) {
        allDirectThreads.firstOrNull { thread ->
            if (isSubAccount) {
                thread.id == userProfile?.companyThreadId
            } else {
                thread.companyOwnerId == currentUserId || thread.isGroupSupervisor(currentUserId)
            }
        }
    }
    val isBusinessAccount = !isSubAccount &&
        !realStaffCapabilities.isAdmin &&
        userProfile?.effectiveProTier(staffCapabilities.tier) == ProTier.BUSINESS
    val canManageCompany = userProfile?.canManageCompanySubAccounts(staffCapabilities.tier) == true
    val isCompanySupervisor = companyThread?.isGroupSupervisor(currentUserId) == true
    val unackWorkOrdersCount = remember(incomingWorkOrders) {
        incomingWorkOrders.count { it.statusEnum() == WorkOrderStatus.OPEN }
    }
    val nightModeEnabled = userProfile?.nightModeEnabled ?: false
    @Suppress("UNUSED_VARIABLE")
    val subscribedCreatorIds = remember(mySubscriptions) {
        mySubscriptions.filter { it.isCurrentlyActive() }.map { it.creatorId }.toSet()
    }

    var activePendingBizReg by remember(currentUserId) {
        mutableStateOf<SubAccountLoginPrefs.PendingBusinessRegistration?>(null)
    }
    var launchedBizBillingOnce by remember(currentUserId) { mutableStateOf(false) }

    LaunchedEffect(currentUserId, realUserProfile?.userId) {
        val profile = realUserProfile ?: return@LaunchedEffect

        val pendingCodeLogin = SubAccountLoginPrefs.getPendingCompanyCodeLogin(context)
        if (pendingCodeLogin != null) {
            SubAccountLoginPrefs.clear(context)
            mainViewModel.joinCompanyWithCodeAndPin(
                companyCodeInput = pendingCodeLogin.companyCode,
                pinInput = pendingCodeLogin.pin,
                firstName = pendingCodeLogin.firstName,
                lastName = pendingCodeLogin.lastName,
                myEmail = initialEmail
            )
            return@LaunchedEffect
        }

        val pendingClaim = SubAccountLoginPrefs.getPendingClaim(context)
        if (pendingClaim != null) {
            SubAccountLoginPrefs.clear(context)
            if (!profile.companySubAccount) {
                mainViewModel.claimCompanySubAccount(
                    subNameInput = pendingClaim.subName,
                    accessCodeInput = pendingClaim.accessCode,
                    myEmail = initialEmail
                )
            }
            return@LaunchedEffect
        }

        val pendingBiz = SubAccountLoginPrefs.getPendingBusinessRegistration(context)
        if (pendingBiz != null) {
            activePendingBizReg = pendingBiz
            if (profile.effectiveProTier(realStaffCapabilities.tier) == ProTier.BUSINESS) {
                if (pendingBiz.companyCode.isNotBlank() && pendingBiz.pin.isNotBlank()) {
                    mainViewModel.saveCompanyCodeAndPin(
                        companyName = pendingBiz.companyName.ifBlank { "${profile.displayName} Team" },
                        companyCodeInput = pendingBiz.companyCode,
                        pinInput = pendingBiz.pin,
                        ownerEmail = pendingBiz.businessEmail.ifBlank { initialEmail }
                    )
                }
                SubAccountLoginPrefs.clearPendingBusinessRegistration(context)
                activePendingBizReg = null
            } else if (!launchedBizBillingOnce) {
                launchedBizBillingOnce = true
                delay(700L)
                val hostActivity = context as? Activity
                if (hostActivity != null) {
                    playBillingManager.launchSubscription(hostActivity, ProTier.BUSINESS)
                }
            }
        }
    }

    var lastAckBuzzMillis by remember(currentUserId) {
        mutableLongStateOf(System.currentTimeMillis() - 120_000L)
    }
    var activeBuzzAlert by remember(currentUserId) {
        mutableStateOf<Triple<String, String, Long>?>(null)
    }
    LaunchedEffect(
        isSubAccount,
        myCompanySubAccount?.lastBuzzAtMillis,
        myCompanySubAccount?.lastBuzzMessage,
        myCompanySubAccount?.lastBuzzByName
    ) {
        val sub = myCompanySubAccount ?: return@LaunchedEffect
        if (isSubAccount && sub.lastBuzzAtMillis > lastAckBuzzMillis) {
            lastAckBuzzMillis = sub.lastBuzzAtMillis
            triggerUrgentBuzzVibration(context)
            com.example.media.PttChirpSoundEngine.playPriorityBreakInChirp(urgent = true)
            activeBuzzAlert = Triple(
                sub.lastBuzzByName.ifBlank { "Company Management" },
                sub.lastBuzzMessage.ifBlank { "Management needs your immediate attention." },
                sub.lastBuzzAtMillis
            )
        }
    }

    var profileInitialTab by remember { mutableIntStateOf(0) }
    var showAge13PurchaseGate by remember { mutableStateOf(false) }
    var incomingQuickReplyThread by remember { mutableStateOf<com.example.data.DirectThread?>(null) }
    var quickReplyDraft by remember { mutableStateOf("") }
    var incomingFriendAlertThread by remember { mutableStateOf<com.example.data.DirectThread?>(null) }
    var incomingGroupUrgentAlertThread by remember { mutableStateOf<com.example.data.DirectThread?>(null) }

    val pttActiveThreadId by LivePttSessionManager.activeThreadId.collectAsStateWithLifecycle()
    val pttPeerName by LivePttSessionManager.activePeerName.collectAsStateWithLifecycle()
    val pttIsConnected by LivePttSessionManager.isConnected.collectAsStateWithLifecycle()
    val pttIsConnecting by LivePttSessionManager.isConnecting.collectAsStateWithLifecycle()
    val pttIsTransmitting by LivePttSessionManager.isTransmitting.collectAsStateWithLifecycle()
    val pttRemoteSpeakerName by LivePttSessionManager.remoteSpeakingName.collectAsStateWithLifecycle()
    val pttActiveTheme by LivePttSessionManager.activePttTheme.collectAsStateWithLifecycle()
    val pttIsListenOnly by LivePttSessionManager.isListenOnlyMode.collectAsStateWithLifecycle()
    val pttFloatingEnabled by LivePttSessionManager.isFloatingPttEnabled.collectAsStateWithLifecycle()
    val pttFloatingDockedThreadId by LivePttSessionManager.floatingButtonDockedThreadId.collectAsStateWithLifecycle()
    val pendingFloatingClosePrompt by LivePttSessionManager.pendingFloatingClosePrompt.collectAsStateWithLifecycle()
    LaunchedEffect(context) {
        try {
            LivePttSessionManager.syncAudioPrefs(context)
        } catch (e: Exception) {
            Log.e("MainActivity", "syncAudioPrefs failed", e)
        }
    }

    var canDrawSystemOverlay by remember {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context))
    }
    val overlayCheckLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(overlayCheckLifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canDrawSystemOverlay =
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
            }
        }
        overlayCheckLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { overlayCheckLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var dismissedNonUrgentAlertKeys by remember(currentUserId) {
        mutableStateOf(DirectMessageNotificationPrefs.getDismissedNonUrgentAlertKeys(context, currentUserId))
    }
    var activePopupAlert by remember(currentUserId) { mutableStateOf<UserAlert?>(null) }
    val nextPendingUserAlert = remember(unreadAlerts, dismissedNonUrgentAlertKeys, isDndActive) {
        unreadAlerts.firstOrNull { alert ->
            val key = "${alert.id}_${alert.updatedAt?.seconds ?: 0L}"
            val isUrgent = alert.severityEnum() == AlertSeverity.URGENT
            if (isDndActive && !isUrgent) false
            else isUrgent || key !in dismissedNonUrgentAlertKeys
        }
    }
    LaunchedEffect(nextPendingUserAlert?.id, nextPendingUserAlert?.updatedAt, activePopupAlert?.id) {
        if (activePopupAlert == null && nextPendingUserAlert != null) {
            activePopupAlert = nextPendingUserAlert
            if (nextPendingUserAlert.severityEnum() != AlertSeverity.URGENT) {
                val key = "${nextPendingUserAlert.id}_${nextPendingUserAlert.updatedAt?.seconds ?: 0L}"
                DirectMessageNotificationPrefs.markNonUrgentAlertDismissed(context, currentUserId, key)
                dismissedNonUrgentAlertKeys = dismissedNonUrgentAlertKeys + key
            }
        }
    }
    LaunchedEffect(unreadAlerts) {
        val current = activePopupAlert
        if (current != null && unreadAlerts.none { it.id == current.id }) {
            activePopupAlert = null
        }
    }

    var dismissedBroadcastIds by remember(currentUserId) {
        mutableStateOf(DirectMessageNotificationPrefs.getDismissedBroadcastIds(context, currentUserId))
    }
    var activePopupBroadcast by remember(currentUserId) { mutableStateOf<AppBroadcast?>(null) }
    val nextUnreadAppBroadcast = remember(appBroadcasts, dismissedBroadcastIds, isDndActive) {
        appBroadcasts.firstOrNull {
            it.id.isNotBlank() &&
                it.id !in dismissedBroadcastIds &&
                (!isDndActive || it.severityEnum() == AlertSeverity.URGENT)
        }
    }
    LaunchedEffect(activePopupAlert?.id, nextUnreadAppBroadcast?.id, activePopupBroadcast?.id) {
        if (activePopupAlert == null && activePopupBroadcast == null && nextUnreadAppBroadcast != null) {
            activePopupBroadcast = nextUnreadAppBroadcast
            if (nextUnreadAppBroadcast.severityEnum() != AlertSeverity.URGENT) {
                DirectMessageNotificationPrefs.markBroadcastDismissed(context, currentUserId, nextUnreadAppBroadcast.id)
                dismissedBroadcastIds = dismissedBroadcastIds + nextUnreadAppBroadcast.id
            }
        }
    }

    val viewerIsAdultVerified = userProfile?.isAdult18Plus() == true
    val unreadChatsCount = remember(directThreadsState, currentUserId) {
        (directThreadsState as? UiState.Success)?.data?.count { it.isUnreadFor(currentUserId) } ?: 0
    }
    val incomingRequestsCount = remember(friendships, currentUserId) {
        friendships.count { it.isIncomingFor(currentUserId) }
    }
    val liveRoomsCount = remember(publicRoomsState) {
        (publicRoomsState as? UiState.Success)?.data?.size ?: 0
    }
    @Suppress("UNUSED_VARIABLE")
    val directoryPhotoByUid = remember(directoryEntries) {
        directoryEntries.associate { it.userId to it.photoBase64 }
    }

    val proUserIds = remember(
        directoryEntries,
        userProfile?.proMember,
        userProfile?.proExpiresAtMillis,
        userProfile?.proLifetime,
        realUserProfile?.customMembershipTier,
        adminPreviewMode,
        currentUserId
    ) {
        buildSet {
            directoryEntries.forEach { entry ->
                if (entry.isProActive()) add(entry.userId)
            }
            val myDisguiseTier = realUserProfile?.customMembershipTier?.uppercase().orEmpty()
            val showMeAsPro = when {
                adminPreviewMode == AdminPreviewMode.FREE || adminPreviewMode == AdminPreviewMode.SUPPORT -> false
                adminPreviewMode == AdminPreviewMode.VOX_PRO ||
                    adminPreviewMode == AdminPreviewMode.VIP_PRO -> true
                myDisguiseTier in listOf("PRO", "VIP_PRO", "BUSINESS") -> true
                myDisguiseTier in listOf("FREE", "SUPPORT", "ADMIN") -> false
                else -> userProfile?.isProSubscriberOnly() == true
            }
            if (showMeAsPro) add(currentUserId) else remove(currentUserId)
        }
    }

    val businessUserIds = remember(
        directoryEntries,
        allUsers,
        userProfile?.proTier,
        userProfile?.proMember,
        userProfile?.proExpiresAtMillis,
        userProfile?.proLifetime,
        realUserProfile?.customMembershipTier,
        staffCapabilities.tier,
        currentUserId
    ) {
        buildSet {
            directoryEntries.forEach { entry ->
                if (entry.isBusinessAccount()) add(entry.userId)
            }
            allUsers.forEach { profile ->
                if (profile.isBusinessAccount()) add(profile.userId)
            }
            val myDisguiseTier = realUserProfile?.customMembershipTier?.uppercase().orEmpty()
            val showMeAsBusiness = when {
                myDisguiseTier == "BUSINESS" -> true
                myDisguiseTier.isNotBlank() -> false
                else -> userProfile?.isBusinessAccount(staffCapabilities.tier) == true
            }
            if (showMeAsBusiness && currentUserId.isNotBlank()) add(currentUserId) else remove(currentUserId)
        }
    }

    val customNameColorsByUserId = remember(
        directoryEntries,
        realUserProfile?.customNameColorHex,
        realUserProfile?.proMember,
        realUserProfile?.proLifetime,
        realUserProfile?.proExpiresAtMillis,
        realUserProfile?.proTier,
        realStaffCapabilities.isAdmin,
        staffCapabilities.tier,
        currentUserId
    ) {
        val nowMs = System.currentTimeMillis()
        buildMap {
            directoryEntries.forEach { entry ->
                val hex = entry.customNameColorHex.trim().uppercase()
                if (hex.isNotBlank()) {
                    val isEntryAdmin = StaffTier.fromRaw(entry.staffTier) == StaffTier.ADMIN
                    if (isEntryAdmin || (entry.canUseVipProNameColor(nowMs) && com.example.data.isAllowedVipProNameColorHex(hex))) {
                        put(entry.userId, hex)
                    }
                }
            }
            val myHex = realUserProfile?.customNameColorHex?.trim()?.uppercase().orEmpty()
            val canUseMyColor = realStaffCapabilities.isAdmin ||
                (realUserProfile?.canChooseVipProNameColor(staffCapabilities.tier, nowMs) == true &&
                    com.example.data.isAllowedVipProNameColorHex(myHex))
            if (myHex.isNotBlank() && canUseMyColor) put(currentUserId, myHex) else remove(currentUserId)
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
        ROUTE_WORK_HUB,
        ROUTE_DISCOVER_USERS,
        ROUTE_FRIENDS,
        ROUTE_DISCOVER,
        ROUTE_PROFILE
    ) || (isBusinessAccount && currentRoute == ROUTE_COMPANY_TEAM)

    LaunchedEffect(isSubAccount, currentRoute) {
        if (isSubAccount && currentRoute in listOf(
                ROUTE_REELS,
                ROUTE_DISCOVER_USERS,
                ROUTE_DISCOVER,
                ROUTE_ROOM,
                ROUTE_SAFETY,
                ROUTE_USER_PROFILE
            )
        ) {
            navController.navigate(ROUTE_CHATS) {
                popUpTo(ROUTE_CHATS) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(isBusinessAccount, currentRoute) {
        if (isBusinessAccount && currentRoute in listOf(
                ROUTE_REELS,
                ROUTE_DISCOVER_USERS,
                ROUTE_DISCOVER,
                ROUTE_ROOM
            )
        ) {
            navController.navigate(ROUTE_CHATS) {
                popUpTo(ROUTE_CHATS) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

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
    val pendingSuspensionAppealsCount = remember(supportTickets) {
        supportTickets.count { it.isSuspensionAppeal() && it.staffResponse.isBlank() }
    }
    val pendingFlaggedDevicesCount = remember(allDeviceRecords, nowClockMs) {
        allDeviceRecords.count { it.isPendingStaffReview() || it.isTimedSuspended(nowClockMs) }
    }
    val pendingDeletedAccountFilesCount = remember(allModerationFiles) {
        allModerationFiles.count { it.deletedAccount && !it.deletedFileDecision.equals("KEPT", ignoreCase = true) }
    }
    val unreadStaffSafetyAlertsCount = remember(adminNotifications) {
        adminNotifications.count {
            !it.read && it.typeEnum() in setOf(
                com.example.data.AdminNotificationType.ADULT_TO_MINOR_DM,
                com.example.data.AdminNotificationType.BOUNCE_EVASION_DEVICE_SUSPENSION,
                com.example.data.AdminNotificationType.AUTOMOD_ROOM_FLOOD_SUSPENSION,
                com.example.data.AdminNotificationType.RETURNING_REPORTED_DEVICE,
                com.example.data.AdminNotificationType.DELETED_ACCOUNT_WITH_REPORTS
            )
        }
    }
    val safetyBadgeCount = when {
        staffCapabilities.isAdmin ->
            openSafetyReportsCount + activeInvestigationsCount + pendingSuspensionRequestsCount + pendingSuspensionAppealsCount + unreadAdminNotificationsCount + pendingFlaggedDevicesCount + pendingDeletedAccountFilesCount + businessHelpThreads.size + unreadAlerts.size
        staffCapabilities.isStaff ->
            openSafetyReportsCount + activeInvestigationsCount + pendingSuspensionAppealsCount + unreadStaffSafetyAlertsCount + pendingFlaggedDevicesCount + businessHelpThreads.size + unreadAlerts.size
        else ->
            unreadAlerts.size + if (myModerationFile?.hasUnacknowledgedWarning() == true) 1 else 0
    }

    val deviceHash = remember(context) {
        try {
            DeviceBanManager.getDeviceHash(context)
        } catch (e: Exception) {
            Log.e("MainActivity", "getDeviceHash failed", e)
            "unknown_device"
        }
    }
    val bannedDevice by remember(deviceHash, currentUserId) {
        mainViewModel.observeBannedDevice(deviceHash)
    }.collectAsStateWithLifecycle(initialValue = null)
    val spamWarningMessage by MessageSpamTracker.spamWarningDialogMessage.collectAsStateWithLifecycle()

    LaunchedEffect(currentUserId, deviceHash) {
        mainViewModel.registerDeviceId(deviceHash)
    }

    LaunchedEffect(realUserProfile?.globallyBanned, realStaffCapabilities.isAdmin, bannedDevice, deviceHash) {
        val deviceBanRecord = bannedDevice
        when {
            !realStaffCapabilities.isAdmin && realUserProfile?.globallyBanned == true -> {
                DeviceBanManager.markLocalDeviceBanned(
                    context = context,
                    reason = realUserProfile.suspensionReason.ifBlank { "Account globally banned" }
                )
                mainViewModel.syncSelfDeviceBanIfGloballyBanned(deviceHash)
            }
            !realStaffCapabilities.isAdmin && deviceBanRecord?.active == true -> {
                DeviceBanManager.markLocalDeviceBanned(
                    context = context,
                    reason = deviceBanRecord.reason.ifBlank { "Device banned from VOX" }
                )
            }
            deviceBanRecord != null && !deviceBanRecord.active && realUserProfile?.globallyBanned != true -> {
                DeviceBanManager.clearLocalDeviceBan(context)
            }
        }
    }

    val isDeviceBannedNow = !realStaffCapabilities.isAdmin && (
        bannedDevice?.active == true ||
            (bannedDevice == null && DeviceBanManager.isLocallyDeviceBanned(context))
        )
    @Suppress("UNUSED_VARIABLE")
    val effectiveDeviceBanReason = bannedDevice?.reason?.ifBlank {
        DeviceBanManager.getLocalDeviceBanReason(context)
    } ?: DeviceBanManager.getLocalDeviceBanReason(context)

    val isCompanySubAccountLockedOut = isSubAccount &&
        myCompanySubAccount?.isLockedOut(nowClockMs) == true

    val isLoginFeatureRestricted = !realStaffCapabilities.isAdmin &&
        userProfile?.isFeatureRestricted(RestrictableFeature.LOGIN, nowClockMs) == true

    LaunchedEffect(currentUserId, deviceHash, myModerationFile?.reportCount, realStaffCapabilities.isAdmin) {
        val reports = myModerationFile?.reportCount ?: 0
        if (!realStaffCapabilities.isAdmin && reports > 0) {
            mainViewModel.syncReportedDeviceFlagIfNeeded(deviceHash, reports)
        }
    }

    val isReturningDeviceFlagged = !realStaffCapabilities.isStaff &&
        bannedDevice?.isFlaggedForUser(currentUserId) == true

    LaunchedEffect(currentUserId, deviceHash, bannedDevice, realStaffCapabilities.isStaff, userProfile?.displayName) {
        val dev = bannedDevice ?: return@LaunchedEffect
        if (!realStaffCapabilities.isStaff && dev.isFlaggedForUser(currentUserId)) {
            if (!dev.isPendingStaffReview() || dev.returningUserId != currentUserId) {
                mainViewModel.flagReturningUserOnReportedDevice(
                    deviceHash = deviceHash,
                    existingRecord = dev
                )
            }
        }
    }

    val isDeviceTimedSuspendedNow = !realStaffCapabilities.isAdmin &&
        bannedDevice?.isTimedSuspended(nowClockMs) == true

    val isSuspendedNow = !realStaffCapabilities.isAdmin && (
        isDeviceBannedNow ||
            isDeviceTimedSuspendedNow ||
            userProfile?.globallyBanned == true ||
            userProfile?.currentlySuspended(nowClockMs) == true ||
            myModerationFile?.isCurrentlySuspended(nowClockMs) == true ||
            isLoginFeatureRestricted
        )

    // Youth Safety Discovery & Search Filter:
    // 13-17 users are hidden from search and Discovery for 18+ non-staff users.
    val viewerIsAdult18Plus = userProfile?.isAdultViewer18Plus() == true
    val visibleDirectoryEntries = remember(directoryEntries, viewerIsAdult18Plus, staffCapabilities.isStaff, currentUserId) {
        directoryEntries.filter { entry ->
            entry.userId == currentUserId ||
                entry.isVisibleToViewer(
                    viewerIsAdult18Plus = viewerIsAdult18Plus,
                    viewerIsStaff = staffCapabilities.isStaff
                )
        }
    }

    LaunchedEffect(nightModeEnabled) {
        (context as? ComponentActivity)?.enableEdgeToEdge(
            statusBarStyle = if (!nightModeEnabled) {
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
        try {
            DirectMessageNotificationHelper.ensureChannel(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !DirectMessageNotificationHelper.hasNotificationPermission(context)
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                DirectMessageSyncWorker.schedule(context)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Notification/SyncWorker init failed", e)
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
    val foregroundSessionStartMs = remember(currentUserId) { AtomicLong(0L) }

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

    val isSubPttBlocked = (isSubAccount &&
        (myCompanySubAccount?.pttEnabled == false || myCompanySubAccount?.micEnabled == false)) ||
        (!realStaffCapabilities.isAdmin &&
            userProfile?.isFeatureRestricted(RestrictableFeature.PTT, nowClockMs) == true)

    LaunchedEffect(isSuspendedNow, isCompanySubAccountLockedOut) {
        if (isSuspendedNow || isCompanySubAccountLockedOut) {
            LivePttSessionManager.disconnect(context)
        }
    }

    LaunchedEffect(directThreadsState, userProfile, staffCapabilities.isAdmin, isSuspendedNow, isCompanySubAccountLockedOut, isSubPttBlocked, currentUserId) {
        if (isSuspendedNow || isCompanySubAccountLockedOut) {
            LivePttSessionManager.disconnect(context)
            return@LaunchedEffect
        }
        val threads = (directThreadsState as? UiState.Success)?.data ?: return@LaunchedEffect
        threads.forEach { thread ->
            LivePttSessionManager.syncActiveThreadPttFlags(
                context = context,
                thread = thread,
                currentUserId = currentUserId
            )
        }
        val hasMicPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val micBlocked = (!staffCapabilities.isAdmin && userProfile?.disableMic == true) || isSubPttBlocked
        val activeTid = LivePttSessionManager.activeThreadId.value
        val myName = userProfile?.effectiveDisplayName()?.ifBlank { initialDisplayName } ?: initialDisplayName

        val broadcastThreads = if (!micBlocked && hasMicPermission) {
            threads.filter { thread ->
                !thread.hasAnyBlock() &&
                    !thread.isUserKicked(currentUserId) &&
                    thread.isPttEnabledFor(currentUserId) &&
                    thread.hasAnyLiveAudioListenerOtherThan(currentUserId) &&
                    !thread.isPttVoiceToTextEnabledFor(currentUserId)
            }
        } else {
            emptyList()
        }
        val targetBroadcastThread = broadcastThreads.firstOrNull { it.id == activeTid } ?: broadcastThreads.firstOrNull()

        val listenOnlyThreads = threads.filter { thread ->
            !thread.hasAnyBlock() &&
                !thread.isUserKicked(currentUserId) &&
                thread.isPttListenOnlyEnabledFor(currentUserId)
        }
        val targetListenOnlyThread = listenOnlyThreads.firstOrNull { it.id == activeTid } ?: listenOnlyThreads.firstOrNull()

        if (targetBroadcastThread != null) {
            try {
                LivePttSessionManager.ensureConnected(
                    context = context,
                    threadId = targetBroadcastThread.id,
                    peerName = targetBroadcastThread.displayTitle(currentUserId),
                    myUserId = currentUserId,
                    myDisplayName = myName.ifBlank { "Member" }
                )
            } catch (e: Exception) {
                Log.e("VoxStagePTT", "Auto PTT connect skipped after login", e)
            }
        } else if (targetListenOnlyThread != null) {
            try {
                LivePttSessionManager.ensureConnectedListenOnly(
                    context = context,
                    threadId = targetListenOnlyThread.id,
                    peerName = targetListenOnlyThread.displayTitle(currentUserId),
                    myUserId = currentUserId,
                    myDisplayName = myName.ifBlank { "Member" }
                )
            } catch (e: Exception) {
                Log.e("VoxStagePTT", "Auto Live Audio listen-only connect skipped", e)
            }
        } else if (LivePttSessionManager.activeThreadId.value != null && !LivePttSessionManager.isTransmitting.value) {
            LivePttSessionManager.disconnect(context)
        }
    }

    DisposableEffect(currentUserId) {
        onDispose {
            LivePttSessionManager.disconnect(context)
        }
    }

    var lastAlertedPriorityChirpMs by remember(currentUserId) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(directThreadsState, currentUserId, isDndActive) {
        val threads = (directThreadsState as? UiState.Success)?.data ?: return@LaunchedEffect
        val newest = threads
            .filter {
                it.hasActivePriorityChirpFor(currentUserId) &&
                    it.lastPriorityChirpAtMillis > lastAlertedPriorityChirpMs &&
                    (!isDndActive || it.lastPriorityChirpPoints >= 2)
            }
            .maxByOrNull { it.lastPriorityChirpAtMillis }
        if (newest != null) {
            lastAlertedPriorityChirpMs = newest.lastPriorityChirpAtMillis
            com.example.media.PttChirpSoundEngine.playPriorityBreakInChirp(
                urgent = newest.lastPriorityChirpPoints >= 2
            )
            LivePttSessionManager.triggerPttHaptic(context)
            snackbarHostState.showTopMessage(
                "Priority Break-In Chirp from ${newest.lastPriorityChirpSenderName}: ${newest.lastPriorityChirpMessage}"
            )
        }
    }

    LaunchedEffect(
        userProfile?.proMember,
        userProfile?.proExpiresAtMillis,
        userProfile?.proLifetime,
        userProfile?.companySubAccount,
        staffCapabilities.tier
    ) {
        try {
            val maxSec = (userProfile ?: UserProfile()).pttMaxDurationSeconds(staffCapabilities.tier)
            LivePttSessionManager.setMaxDurationSeconds(maxSec)
        } catch (e: Exception) {
            Log.e("MainActivity", "setMaxDurationSeconds failed", e)
        }
    }

    LaunchedEffect(userProfile?.selectedPttTheme, userProfile?.selectedPttChirp) {
        try {
            val profile = userProfile ?: UserProfile()
            LivePttSessionManager.setPttCustomization(
                theme = profile.selectedPttThemeEnum(),
                chirp = profile.selectedPttChirpEnum()
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "setPttCustomization failed", e)
        }
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
                    if (purchasedTier == ProTier.BUSINESS) {
                        val bizReg = activePendingBizReg
                            ?: SubAccountLoginPrefs.getPendingBusinessRegistration(context)
                        if (bizReg != null) {
                            if (bizReg.companyCode.isNotBlank() && bizReg.pin.isNotBlank()) {
                                mainViewModel.saveCompanyCodeAndPin(
                                    companyName = bizReg.companyName.ifBlank {
                                        "${userProfile?.displayName ?: initialDisplayName} Team"
                                    },
                                    companyCodeInput = bizReg.companyCode,
                                    pinInput = bizReg.pin,
                                    ownerEmail = bizReg.businessEmail.ifBlank { initialEmail }
                                )
                            }
                            SubAccountLoginPrefs.clearPendingBusinessRegistration(context)
                            activePendingBizReg = null
                        }
                        navController.navigate(ROUTE_COMPANY_TEAM) { launchSingleTop = true }
                    }
                }
                is PlayBillingEvent.Message -> {
                    snackbarHostState.showTopMessage(event.text)
                }
            }
        }
    }

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

    LaunchedEffect(Unit) {
        DirectMessageNotificationHelper.inAppPopupEvents.collect { thread ->
            if (!latestIsDndActive) {
                incomingQuickReplyThread = thread
                quickReplyDraft = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        DirectMessageNotificationHelper.friendAlertEvents.collect { thread ->
            if (!latestIsDndActive) {
                incomingFriendAlertThread = thread
            }
        }
    }

    LaunchedEffect(Unit) {
        DirectMessageNotificationHelper.groupUrgentAlertEvents.collect { thread ->
            incomingGroupUrgentAlertThread = thread
        }
    }

    CompositionLocalProvider(
        LocalProUserIds provides proUserIds,
        LocalBusinessUserIds provides businessUserIds,
        LocalViewerIsStaff provides staffCapabilities.isStaff,
        LocalSpotlightFrameByUserId provides activeSpotlightFramesByUserId,
        LocalCustomNameColors provides customNameColorsByUserId
    ) {
        VoxStageTheme(
            darkTheme = nightModeEnabled,
            forceDarkStatusBarBackground = false
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (showTopNav && !useRail) {
                            val activeDestinations = when {
                                isSubAccount -> subAccountTopDestinations
                                isBusinessAccount -> businessOwnerTopDestinations
                                else -> topDestinations
                            }
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp
                            ) {
                                activeDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route ||
                                        (!isSubAccount && !isBusinessAccount && dest.route == ROUTE_DISCOVER_USERS && currentRoute == ROUTE_FRIENDS)
                                    val badgeCount = when (dest.route) {
                                        ROUTE_CHATS -> unreadChatsCount
                                        ROUTE_WORK_HUB -> unackWorkOrdersCount
                                        ROUTE_DISCOVER_USERS -> incomingRequestsCount
                                        ROUTE_FRIENDS -> if (isBusinessAccount) incomingRequestsCount else 0
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
                                top = if (showTopNav || currentRoute == ROUTE_SAFETY) {
                                    innerPadding.calculateTopPadding()
                                } else {
                                    0.dp
                                },
                                bottom = if (showTopNav && !useRail) innerPadding.calculateBottomPadding() else 0.dp
                            )
                    ) {
                        if (showTopNav && useRail) {
                            val activeDestinations = when {
                                isSubAccount -> subAccountTopDestinations
                                isBusinessAccount -> businessOwnerTopDestinations
                                else -> topDestinations
                            }
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                                activeDestinations.forEach { dest ->
                                    val selected = currentRoute == dest.route ||
                                        (!isSubAccount && !isBusinessAccount && dest.route == ROUTE_DISCOVER_USERS && currentRoute == ROUTE_FRIENDS)
                                    val badgeCount = when (dest.route) {
                                        ROUTE_CHATS -> unreadChatsCount
                                        ROUTE_WORK_HUB -> unackWorkOrdersCount
                                        ROUTE_DISCOVER_USERS -> incomingRequestsCount
                                        ROUTE_FRIENDS -> if (isBusinessAccount) incomingRequestsCount else 0
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
                                        directoryEntries = visibleDirectoryEntries,
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
                                        onRequestAgeVerification = {},
                                        onOpenBuyPoints = ::openProfileBuyPoints,
                                        onOpenUserProfile = ::openUserProfile,
                                        onOpenFriendsTab = { navigateToTopTab(ROUTE_FRIENDS) },
                                        onOpenRoomsTab = { navigateToTopTab(ROUTE_DISCOVER) },
                                        onOpenProfileTab = { navigateToTopTab(ROUTE_PROFILE) },
                                        safetyBadgeCount = safetyBadgeCount,
                                        onOpenSafety = ::openSafetyCenter,
                                        canManageCompany = canManageCompany,
                                        isCompanySupervisor = isCompanySupervisor,
                                        pendingWorkOrdersCount = unackWorkOrdersCount,
                                        onOpenCompanyConsole = {
                                            navController.navigate(ROUTE_COMPANY_TEAM) { launchSingleTop = true }
                                        },
                                        onOpenWorkHub = { navigateToTopTab(ROUTE_WORK_HUB) },
                                        onToggleDnd = mainViewModel::setDoNotDisturb,
                                        onAddUserToThread = { thread, candidate ->
                                            mainViewModel.addUserToPrivateChat(thread, candidate) { newTid ->
                                                navController.navigate("dm/$newTid")
                                            }
                                        },
                                        onRemoveUserFromThread = { thread, targetUid, targetName ->
                                            mainViewModel.removeUserFromDirectThread(context, thread, targetUid, targetName)
                                        },
                                        onRemoveDirectThread = { thread ->
                                            mainViewModel.removeDirectThread(context, thread)
                                        },
                                        onConnectPttForThread = { thread ->
                                            mainViewModel.connectPttForDirectThread(context, thread)
                                        },
                                        onDisconnectPttForThread = { thread ->
                                            mainViewModel.disconnectPttForDirectThread(context, thread)
                                        }
                                    )
                                }

                                composable(ROUTE_WORK_HUB) {
                                    val effectiveProfile = userProfile ?: UserProfile(
                                        userId = currentUserId,
                                        displayName = initialDisplayName.ifBlank { "Employee" }
                                    )
                                    EmployeeWorkHubScreen(
                                        userProfile = effectiveProfile,
                                        companyThread = companyThread,
                                        incomingWorkOrders = incomingWorkOrders,
                                        myRequests = myEmployeeRequests,
                                        onToggleDnd = mainViewModel::setDoNotDisturb,
                                        onOpenCompanyDm = {
                                            val tid = effectiveProfile.companyThreadId
                                            if (tid.isNotBlank()) {
                                                navController.navigate("dm/$tid") { launchSingleTop = true }
                                            }
                                        },
                                        onUpdateWorkOrderStatus = mainViewModel::updateWorkOrderStatus,
                                        onSubmitRequest = { ownerId, threadId, supervisors, category, subject, details, urgent ->
                                            mainViewModel.submitEmployeeRequest(
                                                companyOwnerId = ownerId,
                                                companyThreadId = threadId,
                                                supervisorUids = supervisors,
                                                category = category,
                                                subject = subject,
                                                details = details,
                                                urgent = urgent
                                            )
                                        }
                                    )
                                }

                                composable(ROUTE_COMPANY_TEAM) {
                                    CompanyTeamConsoleScreen(
                                        currentUserId = currentUserId,
                                        userProfile = userProfile,
                                        staffTier = staffCapabilities.tier,
                                        canCreateSubAccounts = canManageCompany,
                                        companyThread = companyThread,
                                        companyCode = myCompanyCodes.firstOrNull(),
                                        subAccounts = companySubAccounts,
                                        companySubAccountDms = companySubAccountDms,
                                        teamWorkOrders = teamWorkOrders,
                                        teamEmployeeRequests = teamEmployeeRequests,
                                        directoryEntries = directoryEntries,
                                        onBack = { navController.popBackStack() },
                                        onOpenCompanyDm = { companyName ->
                                            val existingId = companyThread?.id
                                            if (!existingId.isNullOrBlank()) {
                                                navController.navigate(dmRoute(existingId, false)) { launchSingleTop = true }
                                            } else {
                                                mainViewModel.openOrCreateCompanyGroupDm(companyName) { threadId ->
                                                    navController.navigate(dmRoute(threadId, false)) { launchSingleTop = true }
                                                }
                                            }
                                        },
                                        onOpenSubAccountDm = { threadId, invisible ->
                                            navController.navigate(dmRoute(threadId, invisible)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onSaveCompanyCodeAndPin = { companyName, code, pin ->
                                            mainViewModel.saveCompanyCodeAndPin(
                                                companyName = companyName,
                                                companyCodeInput = code,
                                                pinInput = pin,
                                                ownerEmail = initialEmail
                                            )
                                        },
                                        onUpgradeToBusinessPlay = {
                                            if (requireAge13ForPurchase()) {
                                                val hostActivity = context as? Activity
                                                if (hostActivity != null) {
                                                    playBillingManager.launchSubscription(hostActivity, ProTier.BUSINESS)
                                                } else {
                                                    scope.launch {
                                                        snackbarHostState.showTopMessage("Unable to open Google Play checkout right now.")
                                                    }
                                                }
                                            }
                                        },
                                        onCreateSubAccount = mainViewModel::createCompanySubAccount,
                                        onRevokeSubAccount = mainViewModel::revokeCompanySubAccount,
                                        onBanSubAccount = mainViewModel::banCompanySubAccount,
                                        onSuspendSubAccount = mainViewModel::suspendCompanySubAccount,
                                        onBuzzSubAccountPhone = mainViewModel::buzzCompanySubAccountPhone,
                                        onUpdateSubAccountCapabilities = mainViewModel::updateCompanySubAccountCapabilities,
                                        observeSubAccountFileNotes = mainViewModel::observeSubAccountFileNotes,
                                        onAddSubAccountFileNote = { sub, cat, note ->
                                            mainViewModel.addSubAccountFileNote(sub, cat, note)
                                        },
                                        onDeleteSubAccountFileNote = mainViewModel::deleteSubAccountFileNote,
                                        onSetSupervisor = mainViewModel::setCompanySubAccountSupervisor,
                                        onSendWorkOrder = { ownerId, threadId, supervisors, targetUid, targetName, orderType, priority, title, body ->
                                            mainViewModel.sendWorkOrder(
                                                companyOwnerId = ownerId,
                                                companyThreadId = threadId,
                                                supervisorUids = supervisors,
                                                targetUserId = targetUid,
                                                targetName = targetName,
                                                orderType = orderType,
                                                priority = priority,
                                                title = title,
                                                body = body
                                            )
                                        },
                                        onReviewRequest = { requestId, status, note ->
                                            mainViewModel.reviewEmployeeRequest(requestId, status, note)
                                        }
                                    )
                                }

                                composable(ROUTE_REELS) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        FeatureRestrictedPlaceholder(
                                            featureTitle = "Reels Removed",
                                            notice = "Reels have been removed from VOX.",
                                            isPermanent = true,
                                            testTagName = "reels_removed_placeholder"
                                        )
                                    }
                                }

                                composable(ROUTE_DISCOVER_USERS) {
                                    DiscoverUsersScreen(
                                        currentUserId = currentUserId,
                                        staffCapabilities = staffCapabilities,
                                        directoryEntries = visibleDirectoryEntries,
                                        friendships = friendships,
                                        recentPostsState = UiState.Success(emptyList<ProfilePost>()),
                                        myPointsBalance = userProfile?.pointsBalance ?: 0L,
                                        subscribedCreatorIds = subscribedCreatorIds,
                                        viewerIsAdultVerified = viewerIsAdultVerified,
                                        isProcessingWallet = false,
                                        onRequestAgeVerification = {},
                                        onOpenUserProfile = ::openUserProfile,
                                        onSendFriendRequest = { _ -> },
                                        onAcceptFriendRequest = { _ -> },
                                        onRemoveOrDeclineFriendship = { _ -> },
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
                                        onToggleLikePost = { _ -> },
                                        onDeletePost = { _ -> },
                                        onUnlockPost = { _, _ -> },
                                        onBoostPost = { _, _ -> },
                                        onObservePostComments = { _ -> kotlinx.coroutines.flow.flowOf(emptyList<PostComment>()) },
                                        onAddPostComment = { _, _, onSent -> onSent() },
                                        onDeletePostComment = { _, _ -> },
                                        onSharePostToFeed = { _, _, onShared -> onShared() },
                                        onExternalSharePost = { _ -> },
                                        onSubscribeCreator = { _, _ -> },
                                        onOpenBuyPoints = ::openProfileBuyPoints,
                                        blockedUserIds = userProfile?.blockedUserIds.orEmpty().toSet(),
                                        myUserProfile = userProfile,
                                        isPublishingPost = isPublishingPost,
                                        onCreateDiscoveryPhotoPost = { text, imageBase64, locked, boostToTop, onInsufficientPoints, onPosted ->
                                            mainViewModel.createProfilePost(
                                                text,
                                                imageBase64,
                                                locked,
                                                boostToTop,
                                                onInsufficientPoints,
                                                onPosted
                                            )
                                        },
                                        safetyBadgeCount = safetyBadgeCount,
                                        onOpenSafety = ::openSafetyCenter
                                    )
                                }

                                composable(ROUTE_FRIENDS) {
                                    FriendsScreen(
                                        currentUserId = currentUserId,
                                        friendships = friendships,
                                        directoryEntries = visibleDirectoryEntries,
                                        viewerIsStaff = staffCapabilities.isStaff,
                                        onSendFriendRequest = { _ -> },
                                        onAcceptFriendRequest = { _ -> },
                                        onRemoveOrDeclineFriendship = { _ -> },
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
                                        onOpenUserProfile = ::openUserProfile,
                                        userProfile = userProfile,
                                        companyThread = companyThread,
                                        onOpenCompanyDm = {
                                            val tid = companyThread?.id
                                            if (!tid.isNullOrBlank()) {
                                                navController.navigate(dmRoute(tid, false)) { launchSingleTop = true }
                                            }
                                        },
                                        onStartTeamOneOnOneDm = { otherUid, otherName, otherColor, otherTier ->
                                            mainViewModel.startPrivateChat(
                                                otherUserId = otherUid,
                                                otherName = otherName,
                                                otherColorHex = otherColor,
                                                otherTier = otherTier,
                                                onThreadReady = { threadId ->
                                                    navController.navigate("dm/$threadId")
                                                }
                                            )
                                        }
                                    )
                                }

                                composable(ROUTE_DISCOVER) {
                                    val roomSearchQuery by mainViewModel.searchQuery.collectAsStateWithLifecycle()
                                    val selectedRoomCategory by mainViewModel.selectedCategory.collectAsStateWithLifecycle()
                                    val showOnlyMyRooms by mainViewModel.showOnlyMyRooms.collectAsStateWithLifecycle()
                                    val isCreatingRoom by mainViewModel.isCreatingRoom.collectAsStateWithLifecycle()
                                    val myRoomsState by mainViewModel.myRoomsState.collectAsStateWithLifecycle()

                                    LobbyScreen(
                                        publicRoomsState = publicRoomsState,
                                        myRoomsState = myRoomsState,
                                        userProfile = userProfile,
                                        staffCapabilities = staffCapabilities,
                                        searchQuery = roomSearchQuery,
                                        selectedCategory = selectedRoomCategory,
                                        showOnlyMyRooms = showOnlyMyRooms,
                                        isCreatingRoom = isCreatingRoom,
                                        currentUserId = currentUserId,
                                        onSearchChange = mainViewModel::updateSearchQuery,
                                        onCategorySelected = mainViewModel::selectCategory,
                                        onToggleMyRooms = mainViewModel::setShowOnlyMyRooms,
                                        onToggleInvisiblePreference = mainViewModel::toggleInvisiblePreference,
                                        onOpenRoom = { room ->
                                            if (room.id.isNotBlank() && mainViewModel.canEnterRoomOrNotify(room)) {
                                                navController.navigate("room/${room.id}")
                                            }
                                        },
                                        onRoomPinVerified = mainViewModel::setVerifiedPinForRoom,
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

                                composable(ROUTE_PROFILE) {
                                    var isUpdatingPhoto by remember { mutableStateOf(false) }
                                    var isProcessingWallet by remember { mutableStateOf(false) }
                                    val acceptedFriendIds = remember(friendships, currentUserId) {
                                        friendships
                                            .filter { it.isAccepted() }
                                            .mapNotNull { it.otherUserId(currentUserId).takeIf { id -> id.isNotBlank() } }
                                            .toSet()
                                    }
                                    val liveRoomHostIds = remember(publicRoomsState) {
                                        (publicRoomsState as? UiState.Success)?.data
                                            .orEmpty()
                                            .mapNotNull { it.hostId.takeIf { id -> id.isNotBlank() } }
                                            .toSet()
                                    }


                                    ProfileScreen(
                                        currentUserId = currentUserId,
                                        userProfile = userProfile,
                                        userEmail = initialEmail,
                                        staffCapabilities = staffCapabilities,
                                        friendsCount = acceptedFriendIds.size,
                                        myPostsState = UiState.Success(emptyList<ProfilePost>()),
                                        mySubscriptions = mySubscriptions,
                                        myTransactions = myTransactions,
                                        allTransactionsForAdmin = allTransactions,
                                        paidDmMembersForMe = emptyList<DmAccessPass>(),
                                        isPublishingPost = isPublishingPost,
                                        isUpdatingPhoto = isUpdatingPhoto,
                                        isProcessingWallet = isProcessingWallet || isWalletBusy,
                                        initialTabIndex = profileInitialTab,
                                        onUpdateProfilePhoto = { _ -> isUpdatingPhoto = false },
                                        onCreatePost = { text, imageBase64, locked, boostToTop, onInsufficientPoints, onPosted ->
                                            mainViewModel.createProfilePost(
                                                text,
                                                imageBase64,
                                                locked,
                                                boostToTop,
                                                onInsufficientPoints,
                                                onPosted
                                            )
                                        },
                                        onBoostPost = { _, _ -> },
                                        onObservePostComments = { _ -> kotlinx.coroutines.flow.flowOf(emptyList<PostComment>()) },
                                        onAddPostComment = { _, _, onSent -> onSent() },
                                        onDeletePostComment = { _, _ -> },
                                        onSharePostToFeed = { _, _, onShared -> onShared() },
                                        onExternalSharePost = { _ -> },
                                        onOpenUserProfile = ::openUserProfile,
                                        onToggleLikePost = { _ -> },
                                        onDeletePost = { _ -> },
                                        onBuyPoints = { pts ->
                                            val hostActivity = context as? Activity
                                            if (hostActivity != null) {
                                                isProcessingWallet = true
                                                playBillingManager.launchPointsPurchase(hostActivity, pts)
                                                isProcessingWallet = false
                                            }
                                        },
                                        onUpgradeToProPlay = {
                                            val hostActivity = context as? Activity
                                            if (hostActivity != null) {
                                                playBillingManager.launchSubscription(hostActivity, ProTier.PRO)
                                            }
                                        },
                                        onUpgradeToVipProPlay = {
                                            val hostActivity = context as? Activity
                                            if (hostActivity != null) {
                                                playBillingManager.launchSubscription(hostActivity, ProTier.VIP_PRO)
                                            }
                                        },
                                        onActivateProWithPoints = {
                                            mainViewModel.activateProMembership(paidWithPoints = true)
                                        },
                                        onRequestAgeVerification = {},
                                        onSetDmPolicy = { _ -> },
                                        onKickPaidDmMember = { _, _ -> },
                                        onSaveProfile = mainViewModel::updateProfile,
                                        onSelectVipProNameColor = mainViewModel::setVipProNameColor,
                                        onSignOut = {
                                            Firebase.auth.signOut()
                                        },
                                        safetyBadgeCount = safetyBadgeCount,
                                        onOpenSafety = ::openSafetyCenter,
                                        realStaffCapabilities = realStaffCapabilities,
                                        realUserProfile = realUserProfile,
                                        adminPreviewMode = adminPreviewMode,
                                        onSelectAdminPreviewMode = mainViewModel::setAdminPreviewMode,
                                        onSaveAdminCustomIdentity = mainViewModel::setAdminCustomIdentity,
                                        canManageCompany = canManageCompany,
                                        isCompanySupervisor = isCompanySupervisor,
                                        onOpenCompanyConsole = {
                                            navController.navigate(ROUTE_COMPANY_TEAM) { launchSingleTop = true }
                                        },
                                        onJoinCompanySubAccount = { subName, accessCode ->
                                            mainViewModel.claimCompanySubAccount(
                                                subNameInput = subName,
                                                accessCodeInput = accessCode,
                                                myEmail = initialEmail
                                            )
                                        },
                                        playBillingManager = playBillingManager,
                                        userEmailVerified = initialEmailVerified,
                                        directoryEntries = visibleDirectoryEntries,
                                        acceptedFriendIds = acceptedFriendIds,
                                        liveRoomHostIds = liveRoomHostIds,
                                        onGiftProPass = { targetUid, targetName, giftNote, onInsufficient, onGifted ->
                                            mainViewModel.giftProPassToUser(
                                                targetUserId = targetUid,
                                                targetName = targetName,
                                                giftNote = giftNote,
                                                onInsufficientPoints = onInsufficient,
                                                onGifted = onGifted
                                            )
                                        },
                                        onDeleteAccount = { onCompleted, onError ->
                                            val activeTid = LivePttSessionManager.activeThreadId.value
                                            if (!activeTid.isNullOrBlank()) {
                                                LivePttSessionManager.endPttSessionAndDisableInThread(context, activeTid)
                                            }
                                            mainViewModel.deleteOwnAccount(
                                                deviceHash = deviceHash,
                                                onCompleted = { hadReports ->
                                                    onCompleted(hadReports)
                                                    onRequestAdminRelogin()
                                                },
                                                onError = onError
                                            )
                                        }
                                    )
                                }

                                composable(ROUTE_SAFETY) {
                                    val liveRoomsList = (publicRoomsState as? UiState.Success)?.data.orEmpty()
                                    val resolvedVoxRoom = officialVoxChatRoom
                                        ?: liveRoomsList.firstOrNull { it.isOfficialVoxChatRoom() }
                                    SafetyScreen(
                                        reportsState = safetyReportsState,
                                        userProfile = userProfile,
                                        currentUserId = currentUserId,
                                        staffCapabilities = staffCapabilities,
                                        staffRoles = staffRoles,
                                        staffInvites = staffInvites,
                                        allUsers = allUsers,
                                        allAlerts = if (staffCapabilities.isAdmin) allAlerts else unreadAlerts,
                                        appConfig = appConfig,
                                        liveRooms = liveRoomsList,
                                        onResolveReport = { reportId, status, note, onFinished ->
                                            mainViewModel.resolveReport(
                                                reportId = reportId,
                                                status = status,
                                                note = note,
                                                onFinished = onFinished
                                            )
                                        },
                                        onUnblockUser = mainViewModel::unblockUser,
                                        onAddStaffInvite = mainViewModel::addStaffInvite,
                                        onToggleSupportInviteInvisible = mainViewModel::toggleSupportInviteInvisible,
                                        onRemoveSupportInvite = mainViewModel::removeSupportInvite,
                                        onToggleSupportRoleInvisible = mainViewModel::toggleSupportRoleInvisible,
                                        onSetUserStaffTier = mainViewModel::setUserStaffTier,
                                        onAdminEditUser = mainViewModel::adminEditUser,
                                        onToggleUserMessagingDisabled = mainViewModel::setUserMessagingDisabled,
                                        onSuspendUser = mainViewModel::suspendUser,
                                        onSendDirectAlert = mainViewModel::sendDirectAlert,
                                        onAdminEditAlert = mainViewModel::adminEditAlert,
                                        onAdminDeleteAlert = mainViewModel::adminDeleteAlert,
                                        onUpdateAppConfig = mainViewModel::updateAppConfig,
                                        onSendUserToRoom = mainViewModel::sendUserToRoom,
                                        directoryEntries = directoryEntries,
                                        onSendUserToSupportChat = mainViewModel::sendUserToSupportChat,
                                        appBroadcasts = appBroadcasts,
                                        onSendAppBroadcast = mainViewModel::sendAppBroadcastToAllUsers,
                                        onSendMassVoxDm = mainViewModel::sendMassVoxPrivateMessage,
                                        onDeleteAppBroadcast = mainViewModel::deleteAppBroadcast,
                                        onBack = { navController.popBackStack() },
                                        moderationFiles = allModerationFiles,
                                        onObserveUserFileNotes = mainViewModel::observeUserFileNotes,
                                        onResolveInvestigation = mainViewModel::resolveUserInvestigation,
                                        suspensionRequests = suspensionRequests,
                                        onSubmitSuspensionRequest = { uid, name, reason, onSubmitted ->
                                            mainViewModel.submitSupportSuspensionRequest(
                                                targetUserId = uid,
                                                targetName = name,
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
                                        onIssueRefund = { uid, name, refundType, itemLabel, amountCents, txId, subId, reason, onIssued ->
                                            mainViewModel.issueRefund(
                                                targetUserId = uid,
                                                targetName = name,
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
                                        onGrantAppPerk = mainViewModel::grantAppPerk,
                                        realStaffCapabilities = realStaffCapabilities,
                                        realUserProfile = realUserProfile,
                                        adminPreviewMode = adminPreviewMode,
                                        onSelectAdminPreviewMode = mainViewModel::setAdminPreviewMode,
                                        onSaveAdminCustomIdentity = mainViewModel::setAdminCustomIdentity,
                                        businessHelpThreads = businessHelpThreads,
                                        onEnterBusinessHelpDm = { thread, invisible ->
                                            navController.navigate(dmRoute(thread.id, invisible)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onEnterReportedGroupDmById = { threadId, invisible ->
                                            navController.navigate(dmRoute(threadId, invisible)) {
                                                launchSingleTop = true
                                            }
                                        },
                                        onEndBusinessHelp = mainViewModel::endBusinessStaffHelp,
                                        onLoadUserProfileForRestrictions = mainViewModel::fetchUserProfileOnce,
                                        onUpdateUserFeatureRestrictions = mainViewModel::updateUserFeatureRestrictions,
                                        onRemoveUserFromBusinessAccount = { ownerId, uid, name, reportId ->
                                            mainViewModel.staffRemoveUserFromBusinessAccount(
                                                businessOwnerId = ownerId,
                                                targetUserId = uid,
                                                targetName = name,
                                                reason = "Removed from Business Account following safety review",
                                                reportId = reportId
                                            )
                                        },
                                        onAdminBanReportedDevice = mainViewModel::adminBanReportedUserDevice,
                                        onSubmitBusinessApprovalRequest = { uid, name, action, reason, onSubmitted ->
                                            mainViewModel.submitSupportSuspensionRequest(
                                                targetUserId = uid,
                                                targetName = name,
                                                reason = reason,
                                                requestType = action.requestType,
                                                isBusinessAccount = true,
                                                onSubmitted = onSubmitted
                                            )
                                        },
                                        onAdminRevokeBusinessAccount = mainViewModel::adminRevokeBusinessAccount,
                                        officialVoxChatRoom = resolvedVoxRoom,
                                        onUpdateOfficialVoxChat = mainViewModel::updateOfficialVoxChatControls,
                                        onOpenOfficialVoxChat = {
                                            if (resolvedVoxRoom != null) {
                                                navController.navigate("room/${resolvedVoxRoom.id}")
                                            }
                                        },
                                        supportTickets = supportTickets,
                                        onReplyToSupportTicket = mainViewModel::respondToSupportTicket,
                                        onAdminRemoveUserSuspension = { targetUid, targetName ->
                                            mainViewModel.adminRemoveUserSuspension(
                                                targetUserId = targetUid,
                                                targetName = targetName
                                            )
                                        },
                                        deviceRecords = allDeviceRecords,
                                        onAdminResolveDeletedFile = mainViewModel::adminResolveDeletedAccountFile,
                                        onStaffGrantFlaggedDeviceAccess = { devHash, returningName ->
                                            mainViewModel.staffGrantFlaggedDeviceAccess(
                                                deviceHash = devHash,
                                                returningUserName = returningName
                                            )
                                        },
                                        onStaffMarkFlaggedDeviceRestricted = mainViewModel::staffMarkFlaggedDeviceRestricted,
                                        onStaffBanFlaggedReturningUser = mainViewModel::staffBanFlaggedReturningUser,
                                        onStaffLiftDeviceSuspension = { devHash, targetUid, targetName ->
                                            mainViewModel.staffLiftDeviceSuspension(
                                                deviceHash = devHash,
                                                targetUserId = targetUid,
                                                targetName = targetName
                                            )
                                        }
                                    )
                                }

                                composable(ROUTE_DM) { backStack ->
                                    val threadId = backStack.arguments?.getString("threadId").orEmpty()
                                    val initialInvisible = backStack.arguments?.getString("invisible")?.toBooleanStrictOrNull() ?: false
                                    if (threadId.isNotBlank()) {
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
                                        val activeProfile = userProfile ?: UserProfile(
                                            userId = currentUserId,
                                            displayName = initialDisplayName.ifBlank { "Studio Host" }
                                        )
                                        DirectChatScreen(
                                            viewModel = dmViewModel,
                                            userProfile = activeProfile,
                                            myStaffTier = staffCapabilities.tier,
                                            directoryEntries = directoryEntries,
                                            friendships = friendships,
                                            onBlockContact = { targetUid, targetName ->
                                                mainViewModel.blockContact(targetUid, targetName)
                                            },
                                            onUnblockContact = mainViewModel::unblockUser,
                                            onSendFriendRequest = mainViewModel::sendFriendRequest,
                                            onAcceptFriendRequest = mainViewModel::acceptFriendRequest,
                                            onOpenPrivateMessage = { otherUid, otherName, otherColor, otherTier ->
                                                mainViewModel.startPrivateChat(
                                                    otherUserId = otherUid,
                                                    otherName = otherName,
                                                    otherColorHex = otherColor,
                                                    otherTier = otherTier,
                                                    onThreadReady = { nextThreadId ->
                                                        navController.navigate("dm/$nextThreadId")
                                                    }
                                                )
                                            },
                                            onOpenUserProfile = ::openUserProfile,
                                            onOpenBuyPoints = ::openProfileBuyPoints,
                                            onToggleDnd = mainViewModel::setDoNotDisturb,
                                            onOpenWorkHub = { navigateToTopTab(ROUTE_WORK_HUB) },
                                            onOpenCompanyConsole = {
                                                navController.navigate(ROUTE_COMPANY_TEAM) { launchSingleTop = true }
                                            },
                                            myCompanySubAccount = myCompanySubAccount,
                                            initialInvisibleOwnerMode = initialInvisible,
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }

                                composable(ROUTE_ROOM) { backStack ->
                                    val roomId = backStack.arguments?.getString("roomId").orEmpty()
                                    if (roomId.isNotBlank()) {
                                        val roomViewModel: RoomViewModel = viewModel(
                                            key = "room_${currentUserId}_$roomId",
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
                                        val activeProfile = userProfile ?: UserProfile(
                                            userId = currentUserId,
                                            displayName = initialDisplayName.ifBlank { "Studio Host" }
                                        )
                                        val liveRoomsList = (publicRoomsState as? UiState.Success)?.data.orEmpty()
                                        RoomScreen(
                                            viewModel = roomViewModel,
                                            userProfile = activeProfile,
                                            staffCapabilities = staffCapabilities,
                                            availableRooms = liveRoomsList,
                                            directoryEntries = directoryEntries,
                                            friendships = friendships,
                                            onSendFriendRequest = mainViewModel::sendFriendRequest,
                                            onAcceptFriendRequest = mainViewModel::acceptFriendRequest,
                                            onOpenPrivateMessage = { otherUid, otherName, otherColor, otherTier ->
                                                mainViewModel.startPrivateChat(
                                                    otherUserId = otherUid,
                                                    otherName = otherName,
                                                    otherColorHex = otherColor,
                                                    otherTier = otherTier,
                                                    onThreadReady = { nextThreadId ->
                                                        navController.navigate("dm/$nextThreadId")
                                                    }
                                                )
                                            },
                                            onOpenUserProfile = ::openUserProfile,
                                            onExitRoom = { navController.popBackStack() }
                                        )
                                    }
                                }

                                composable(ROUTE_USER_PROFILE) { backStack ->
                                    val targetUid = backStack.arguments?.getString("userId").orEmpty()
                                    if (targetUid.isNotBlank()) {
                                        val userProfileViewModel: UserProfileViewModel = viewModel(
                                            key = "user_${currentUserId}_$targetUid",
                                            factory = viewModelFactory {
                                                initializer {
                                                    val db = FirebaseFirestore.getInstance(firestoreDatabaseId)
                                                    UserProfileViewModel(
                                                        repository = VoxStageRepository(db),
                                                        currentUserId = currentUserId,
                                                        targetUserId = targetUid
                                                    )
                                                }
                                            }
                                        )
                                        val targetFriendship = friendships.firstOrNull {
                                            it.otherUserId(currentUserId) == targetUid
                                        }
                                        val targetSub = mySubscriptions.firstOrNull {
                                            it.creatorId == targetUid && it.isCurrentlyActive()
                                        }
                                        UserProfileScreen(
                                            viewModel = userProfileViewModel,
                                            staffCapabilities = staffCapabilities,
                                            friendship = targetFriendship,
                                            myPointsBalance = userProfile?.pointsBalance ?: 0L,
                                            activeSubscription = targetSub,
                                            viewerIsAdultVerified = viewerIsAdultVerified,
                                            isProcessingWallet = isWalletBusy,
                                            onRequestAgeVerification = {},
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
                                            onSendFriendRequest = { _ -> },
                                            onAcceptFriendRequest = { _ -> },
                                            onRemoveOrDeclineFriendship = { _ -> },
                                            onUnlockPost = { _, _ -> },
                                            onBoostPost = { _, _ -> },
                                            onObservePostComments = { _ -> kotlinx.coroutines.flow.flowOf(emptyList<PostComment>()) },
                                            onAddPostComment = { _, _, onSent -> onSent() },
                                            onDeletePostComment = { _, _ -> },
                                            onSharePostToFeed = { _, _, onShared -> onShared() },
                                            onExternalSharePost = { _ -> },
                                            onSubscribeCreator = { _, _ -> },
                                            onOpenBuyPoints = ::openProfileBuyPoints,
                                            supportStaffMembers = directoryEntries.filter { it.tierEnum().isStaff() },
                                            viewerProfile = userProfile,
                                            onGiftProPass = { targetUserId, targetName, giftNote, onInsufficient, onGifted ->
                                                mainViewModel.giftProPassToUser(
                                                    targetUserId = targetUserId,
                                                    targetName = targetName,
                                                    giftNote = giftNote,
                                                    onInsufficientPoints = onInsufficient,
                                                    onGifted = onGifted
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (showAge13PurchaseGate) {
                    AlertDialog(
                        onDismissRequest = { showAge13PurchaseGate = false },
                        title = { Text("13+ Required for Purchases") },
                        text = { Text("You must be at least 13 years old to buy points or memberships.") },
                        confirmButton = {
                            TextButton(onClick = { showAge13PurchaseGate = false }) {
                                Text("OK")
                            }
                        }
                    )
                }

                val buzzAlert = activeBuzzAlert
                if (buzzAlert != null) {
                    AlertDialog(
                        onDismissRequest = { activeBuzzAlert = null },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.PhonelinkRing,
                                contentDescription = null,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        title = { Text("Urgent Buzz from ${buzzAlert.first}") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(buzzAlert.second)
                                Text(
                                    text = formatTimestampShort(null),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = { activeBuzzAlert = null }) {
                                Text("Acknowledge")
                            }
                        }
                    )
                }

                val popupAlert = activePopupAlert
                if (popupAlert != null) {
                    AlertDialog(
                        onDismissRequest = {
                            if (popupAlert.severityEnum() != AlertSeverity.URGENT) {
                                activePopupAlert = null
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                        },
                        title = { Text(popupAlert.title.ifBlank { "Account Notice" }) },
                        text = { Text(popupAlert.message) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    activePopupAlert = null
                                    activePopupAlert = null
                                }
                            ) {
                                Text("Got It")
                            }
                        }
                    )
                }

                val popupBroadcast = activePopupBroadcast
                if (popupBroadcast != null) {
                    AlertDialog(
                        onDismissRequest = {
                            if (popupBroadcast.severityEnum() != AlertSeverity.URGENT) {
                                activePopupBroadcast = null
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Campaign,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                        },
                        title = { Text(popupBroadcast.title.ifBlank { "VOX Announcement" }) },
                        text = { Text(popupBroadcast.message) },
                        confirmButton = {
                            Button(onClick = { activePopupBroadcast = null }) {
                                Text("Close")
                            }
                        }
                    )
                }

                val spamMsg = spamWarningMessage
                if (!spamMsg.isNullOrBlank()) {
                    AlertDialog(
                        onDismissRequest = {},
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Block,
                                contentDescription = null,
                                modifier = Modifier.size(26.dp)
                            )
                        },
                        title = { Text("Slow Down Messages") },
                        text = { Text(spamMsg) },
                        confirmButton = {
                            TextButton(onClick = {}) {
                                Text("OK")
                            }
                        }
                    )
                }

                if (incomingQuickReplyThread != null || incomingFriendAlertThread != null || incomingGroupUrgentAlertThread != null) {
                    val alertThread = incomingGroupUrgentAlertThread
                        ?: incomingFriendAlertThread
                        ?: incomingQuickReplyThread
                    if (alertThread != null) {
                        AlertDialog(
                            onDismissRequest = {
                                incomingQuickReplyThread = null
                                incomingFriendAlertThread = null
                                incomingGroupUrgentAlertThread = null
                            },
                            title = { Text(alertThread.displayTitle(currentUserId)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = alertThread.lastMessageText.ifBlank { "New message alert" },
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (incomingQuickReplyThread != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = quickReplyDraft,
                                                onValueChange = { newText: String -> quickReplyDraft = newText.take(240) },
                                                placeholder = { Text("Quick reply...") },
                                                singleLine = true,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.surface)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val tid = alertThread.id
                                        incomingQuickReplyThread = null
                                        incomingFriendAlertThread = null
                                        incomingGroupUrgentAlertThread = null
                                        quickReplyDraft = ""
                                        if (tid.isNotBlank()) {
                                            navController.navigate("dm/$tid")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(if (quickReplyDraft.isNotBlank()) "Send" else "Open Chat")
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        incomingQuickReplyThread = null
                                        incomingFriendAlertThread = null
                                        incomingGroupUrgentAlertThread = null
                                        quickReplyDraft = ""
                                    }
                                ) {
                                    Text("Dismiss", textAlign = TextAlign.Center)
                                }
                            }
                        )
                    }
                }

                if (isSuspendedNow) {
                    val effectiveSuspensionReason = remember(
                        userProfile?.suspensionReason,
                        userProfile?.globallyBanned,
                        myModerationFile?.suspensionReason,
                        isDeviceBannedNow,
                        isDeviceTimedSuspendedNow,
                        bannedDevice?.reason,
                        effectiveDeviceBanReason,
                        isLoginFeatureRestricted
                    ) {
                        listOfNotNull(
                            bannedDevice?.reason?.trim()?.takeIf { isDeviceTimedSuspendedNow && it.isNotBlank() },
                            userProfile?.suspensionReason?.trim()?.takeIf { it.isNotBlank() },
                            myModerationFile?.suspensionReason?.trim()?.takeIf { it.isNotBlank() },
                            effectiveDeviceBanReason?.trim()?.takeIf { isDeviceBannedNow && it.isNotBlank() },
                            if (isLoginFeatureRestricted) "Login access is currently suspended by Staff/Admin." else null,
                            if (userProfile?.globallyBanned == true) "Your account has been globally banned by an Administrator." else null
                        ).firstOrNull() ?: "Your account is currently suspended."
                    }
                    val remainingSuspensionText = remember(
                        userProfile?.suspendedUntilMillis,
                        myModerationFile?.suspendedUntilMillis,
                        bannedDevice?.suspendedUntilMillis,
                        nowClockMs
                    ) {
                        val untilMs = maxOf(
                            userProfile?.suspendedUntilMillis ?: 0L,
                            myModerationFile?.suspendedUntilMillis ?: 0L,
                            bannedDevice?.suspendedUntilMillis ?: 0L
                        )
                        if (untilMs > nowClockMs) {
                            val remMin = ((untilMs - nowClockMs) / 60_000L).coerceAtLeast(1L)
                            when {
                                remMin >= 1440L -> "${remMin / 1440L}d ${(remMin % 1440L) / 60L}h"
                                remMin >= 60L -> "${remMin / 60L}h ${remMin % 60L}m"
                                else -> "${remMin}m"
                            }
                        } else if (myModerationFile?.suspendedUntilInvestigationResolved == true) {
                            "Pending Staff/Admin Review"
                        } else {
                            null
                        }
                    }
                    val activeSuspensionKey = remember(
                        userProfile?.suspendedUntilMillis,
                        userProfile?.globallyBanned,
                        myModerationFile?.suspendedUntilMillis,
                        bannedDevice?.suspendedUntilMillis,
                        isDeviceBannedNow,
                        nowClockMs
                    ) {
                        val untilMs = maxOf(
                            userProfile?.suspendedUntilMillis ?: 0L,
                            myModerationFile?.suspendedUntilMillis ?: 0L,
                            bannedDevice?.suspendedUntilMillis ?: 0L
                        )
                        when {
                            untilMs > nowClockMs -> untilMs.toString()
                            userProfile?.globallyBanned == true || isDeviceBannedNow -> "permanent"
                            else -> "active"
                        }
                    }
                    val expectedAppealDocId = remember(currentUserId, activeSuspensionKey) {
                        val safeKey = activeSuspensionKey.filter { it.isLetterOrDigit() || it == '_' }.take(32).ifBlank { "active" }
                        "susp_appeal_${currentUserId}_$safeKey".take(120)
                    }
                    val matchingSuspensionAppeal = remember(supportTickets, expectedAppealDocId, currentUserId) {
                        supportTickets.firstOrNull { it.id == expectedAppealDocId }
                            ?: supportTickets.firstOrNull {
                                it.requesterId == currentUserId && it.isSuspensionAppeal()
                            }
                    }
                    SuspendedAccountLoginPopup(
                        suspensionReason = effectiveSuspensionReason,
                        remainingTimeLabel = remainingSuspensionText,
                        existingAppeal = matchingSuspensionAppeal,
                        onSubmitReviewMessage = { reviewMsg, onFinished ->
                            mainViewModel.submitSuspensionReviewAppeal(
                                suspensionKey = activeSuspensionKey,
                                suspensionReason = effectiveSuspensionReason,
                                reviewMessage = reviewMsg,
                                onFinished = onFinished
                            )
                        },
                        onSignOut = onRequestAdminRelogin
                    )
                } else if (isReturningDeviceFlagged) {
                    val flaggedRecord = bannedDevice
                    if (flaggedRecord != null) {
                        com.example.ui.components.ReturningReportedDeviceLockOverlay(
                            deviceRecord = flaggedRecord,
                            onSignOut = onRequestAdminRelogin
                        )
                    }
                }

                // In-app floating PTT bubble fallback when system overlay permission isn't granted yet
                val showInAppFloatingBubble = !canDrawSystemOverlay &&
                    pttFloatingEnabled &&
                    !pttIsListenOnly &&
                    !pttActiveThreadId.isNullOrBlank() &&
                    (pttIsConnected || pttIsConnecting) &&
                    pttFloatingDockedThreadId != pttActiveThreadId
                if (showInAppFloatingBubble) {
                    val activeTid = pttActiveThreadId.orEmpty()
                    FloatingPttOverlayBubble(
                        peerName = pttPeerName.ifBlank { "Private Chat" },
                        isTransmitting = pttIsTransmitting,
                        remoteSpeakerName = pttRemoteSpeakerName,
                        onSetTransmitting = { tx ->
                            LivePttSessionManager.setTransmitting(
                                context = context,
                                transmitting = tx,
                                threadId = activeTid,
                                peerName = pttPeerName
                            )
                        },
                        onOpenChat = {
                            if (activeTid.isNotBlank()) {
                                navController.navigate(dmRoute(activeTid)) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onDismiss = {
                            LivePttSessionManager.setTransmitting(context, false)
                            LivePttSessionManager.requestFloatingPttClosePrompt(activeTid, pttPeerName)
                        },
                        pttTheme = pttActiveTheme,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }

                // Close Floating PTT Confirmation Dialog (End PTT Stream vs Keep Open & Dock Circle in DM)
                val closePttPrompt = pendingFloatingClosePrompt
                if (closePttPrompt != null) {
                    AlertDialog(
                        onDismissRequest = { LivePttSessionManager.dismissFloatingPttClosePrompt() },
                        modifier = Modifier.testTag("floating_ptt_close_confirm_dialog"),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.RecordVoiceOver,
                                contentDescription = null,
                                tint = LiveCoral,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        title = { Text("Close PTT Stream?") },
                        text = {
                            Text(
                                text = "Do you want to end the live Push-to-Talk stream with ${closePttPrompt.peerName}, or keep it open with a circle PTT button inside the Private DM? (Double-tap the circle button anytime to reopen floating PTT.)",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    LivePttSessionManager.endPttSessionAndDisableInThread(context, closePttPrompt.threadId)
                                    scope.launch {
                                        snackbarHostState.showTopMessage("Push-to-Talk stream ended.")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = LiveCoral,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.testTag("floating_ptt_confirm_end_button")
                            ) {
                                Text("Yes, End PTT")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    LivePttSessionManager.dockFloatingPttToDm(context, closePttPrompt.threadId)
                                    scope.launch {
                                        snackbarHostState.showTopMessage(
                                            "PTT stays open — circle PTT button docked in Private DM. Double-tap it to reopen floating PTT."
                                        )
                                    }
                                },
                                modifier = Modifier.testTag("floating_ptt_confirm_keep_button")
                            ) {
                                Text("No, Keep Open")
                            }
                        }
                    )
                }

                TopSnackbarOverlay(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}
