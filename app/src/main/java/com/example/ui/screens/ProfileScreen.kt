package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CreatorSubscription
import com.example.data.DirectoryEntry
import com.example.data.DmAccessPass
import android.widget.Toast
import com.example.data.AdminPreviewMode
import com.example.data.DmPolicy
import com.example.data.PointTransaction
import com.example.data.PointTransactionType
import com.example.data.PostComment
import com.example.data.PresenceStatus
import com.example.data.ProTier
import com.example.data.ProfilePost
import com.example.data.PttChirpStyle
import com.example.data.PttThemeStyle
import com.example.data.ReferralRecord
import com.example.data.SpotlightFrameColor
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.UserSex
import com.example.data.formatUsdCents
import com.example.media.LivePttSessionManager
import com.example.media.LockScreenPttActivity
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.notifications.DirectMessageSyncWorker
import androidx.compose.ui.text.style.TextOverflow
import com.example.ui.components.DailyLoginStreakCard
import com.example.ui.components.DeleteAccountConfirmDialog
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.GiftedProPassStoreCard
import com.example.ui.components.InviteFriendReferralCard
import com.example.ui.components.PostCommentsDialog
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.ProMemberBadge
import com.example.ui.components.ProfilePostCard
import com.example.ui.components.PttCustomizationShopCard
import com.example.ui.components.SharePostDialog
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.SpotlightFrameShopCard
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopBarSafetyShieldButton
import com.example.ui.components.VipProUsernameColorCard
import com.example.ui.components.encodeImageUriToBase64Jpeg
import com.example.ui.components.parseHexColor
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.components.usernameColor
import com.example.widget.VoxPttWidgetHelper
import com.example.ui.theme.AvatarAccentHexPalette
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(
    currentUserId: String,
    userProfile: UserProfile?,
    userEmail: String,
    staffCapabilities: StaffCapabilities,
    friendsCount: Int,
    followersCount: Int = 0,
    followingCount: Int = 0,
    myPostsState: UiState<List<ProfilePost>>,
    mySubscriptions: List<CreatorSubscription>,
    myTransactions: List<PointTransaction>,
    allTransactionsForAdmin: List<PointTransaction>,
    paidDmMembersForMe: List<DmAccessPass>,
    isPublishingPost: Boolean,
    isUpdatingPhoto: Boolean,
    isProcessingWallet: Boolean,
    initialTabIndex: Int = 0,
    onUpdateProfilePhoto: (String) -> Unit,
    onCreatePost: (
        text: String,
        imageBase64: String,
        locked: Boolean,
        boostToTop: Boolean,
        onInsufficientPoints: () -> Unit,
        onPosted: () -> Unit
    ) -> Unit,
    onBoostPost: (ProfilePost, onInsufficientPoints: () -> Unit) -> Unit,
    onObservePostComments: (String) -> Flow<List<PostComment>>,
    onAddPostComment: (ProfilePost, String, () -> Unit) -> Unit,
    onDeletePostComment: (String, String) -> Unit,
    onSharePostToFeed: (ProfilePost, String, () -> Unit) -> Unit,
    onExternalSharePost: (ProfilePost) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onToggleLikePost: (ProfilePost) -> Unit,
    onDeletePost: (ProfilePost) -> Unit,
    onBuyPoints: (Long) -> Unit,
    onUpgradeToProPlay: () -> Unit = {},
    onUpgradeToVipProPlay: () -> Unit = {},
    onActivateProWithPoints: () -> Unit = {},
    onBoostMyProfile: (onInsufficientPoints: () -> Unit) -> Unit = {},
    onSelectOrBuyPttTheme: (PttThemeStyle, onInsufficientPoints: () -> Unit) -> Unit = { _, _ -> },
    onSelectOrBuyPttChirp: (PttChirpStyle, onInsufficientPoints: () -> Unit) -> Unit = { _, _ -> },
    onBuySpotlightFrame: (SpotlightFrameColor, onInsufficientPoints: () -> Unit) -> Unit = { _, _ -> },
    profileViews: List<com.example.data.ProfileViewRecord> = emptyList(),
    onUnlockProfileViewers: (onInsufficientPoints: () -> Unit) -> Unit = {},
    onSetPriorityChirpOptIn: (Boolean) -> Unit = {},
    incomingReferral: ReferralRecord? = null,
    invitedReferrals: List<ReferralRecord> = emptyList(),
    userEmailVerified: Boolean = false,
    onRedeemReferralCode: (String) -> Unit = {},
    playBillingManager: com.example.billing.PlayBillingManager? = null,
    onSetDmPolicy: (DmPolicy) -> Unit,
    onKickPaidDmMember: (senderId: String, senderName: String) -> Unit,
    onRequestAgeVerification: () -> Unit,
    onSaveProfile: (
        displayName: String,
        handle: String,
        bio: String,
        avatarColorHex: String,
        pushToTalkPreferred: Boolean,
        darkThemePreferred: Boolean,
        invisibleModePreferred: Boolean,
        sex: UserSex,
        age: Long
    ) -> Unit,
    onSelectVipProNameColor: (String) -> Unit = {},
    onSaveBio: (String, () -> Unit) -> Unit = { _, _ -> },
    onChangeAppearanceStatus: (PresenceStatus) -> Unit = {},
    onSignOut: () -> Unit,
    safetyBadgeCount: Int = 0,
    onOpenSafety: () -> Unit = {},
    realStaffCapabilities: StaffCapabilities = staffCapabilities,
    realUserProfile: UserProfile? = userProfile,
    adminPreviewMode: AdminPreviewMode = AdminPreviewMode.ADMIN,
    onSelectAdminPreviewMode: (AdminPreviewMode) -> Unit = {},
    onSaveAdminCustomIdentity: (String, String, String) -> Unit = { _, _, _ -> },
    canManageCompany: Boolean = false,
    isCompanySupervisor: Boolean = false,
    onOpenCompanyConsole: () -> Unit = {},
    onJoinCompanySubAccount: (subName: String, accessCode: String) -> Unit = { _, _ -> },
    directoryEntries: List<DirectoryEntry> = emptyList(),
    acceptedFriendIds: Set<String> = emptySet(),
    liveRoomHostIds: Set<String> = emptySet(),
    onGiftProPass: (
        targetUserId: String,
        targetName: String,
        giftNote: String,
        onInsufficientPoints: () -> Unit,
        onGifted: () -> Unit
    ) -> Unit = { _, _, _, _, _ -> },
    onDeleteAccount: (
        onCompleted: (hadReports: Boolean) -> Unit,
        onError: (String) -> Unit
    ) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) } // 0 = My Posts, 1 = Settings & Points
    var showJoinCompanyDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var isDeletingAccount by remember { mutableStateOf(false) }
    var showFullAvatar by remember { mutableStateOf(false) }
    var showEditBioDialog by remember { mutableStateOf(false) }
    var bioDialogDraft by remember { mutableStateOf("") }
    var showNeedPointsForBoostDialog by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<ProfilePost?>(null) }
    var activeSharePost by remember { mutableStateOf<ProfilePost?>(null) }
    var settingsSubTab by remember { mutableIntStateOf(0) } // 0=Profile, 1=VIP & Points, 2=PTT & Alerts, 3=Privacy & Team

    var dmNotificationsEnabled by remember {
        mutableStateOf(DirectMessageNotificationPrefs.isDmNotificationsEnabled(context))
    }
    var quickReplyPopupEnabled by remember {
        mutableStateOf(DirectMessageNotificationPrefs.isQuickReplyPopupEnabled(context))
    }
    var friendAttentionAlertsEnabled by remember {
        mutableStateOf(DirectMessageNotificationPrefs.isFriendAttentionAlertsEnabled(context))
    }
    var readReceiptsEnabled by remember {
        mutableStateOf(DirectMessageNotificationPrefs.isReadReceiptsEnabled(context))
    }
    val volumeDownPttEnabled by LivePttSessionManager.isVolumeDownPttEnabled.collectAsStateWithLifecycle()
    val lockScreenPttEnabled by LivePttSessionManager.isLockScreenPttEnabled.collectAsStateWithLifecycle()
    var lockScreenRecentDmEnabled by remember {
        mutableStateOf(DirectMessageNotificationPrefs.isLockScreenRecentDmEnabled(context))
    }
    var pttBeepSilenced by remember {
        mutableStateOf(DirectMessageNotificationPrefs.isPttBeepSilenced(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(DirectMessageNotificationHelper.hasNotificationPermission(context))
    }
    val dmNotifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
        if (granted) {
            dmNotificationsEnabled = true
            DirectMessageNotificationPrefs.setDmNotificationsEnabled(context, true)
            DirectMessageNotificationHelper.ensureChannel(context)
            DirectMessageSyncWorker.schedule(context)
        }
    }

    // Post composer state
    var postText by remember { mutableStateOf("") }
    var draftPostPhotoBase64 by remember { mutableStateOf("") }
    var boostPostOnCreate by remember { mutableStateOf(false) }
    var isEncodingPostPhoto by remember { mutableStateOf(false) }

    // Profile editor state
    var displayName by remember { mutableStateOf(userProfile?.displayName ?: "") }
    var handle by remember { mutableStateOf(userProfile?.handle ?: "") }
    var bio by remember { mutableStateOf(userProfile?.bio ?: "") }
    var avatarColorHex by remember { mutableStateOf(userProfile?.avatarColorHex ?: "#0088FF") }
    var pushToTalkPreferred by remember { mutableStateOf(userProfile?.pushToTalkPreferred ?: true) }
    var nightModeEnabled by remember { mutableStateOf(userProfile?.nightModeEnabled ?: false) }
    var invisibleModePreferred by remember { mutableStateOf(userProfile?.invisibleModePreferred ?: false) }
    var selectedSex by remember { mutableStateOf(userProfile?.sexEnum() ?: UserSex.UNSPECIFIED) }
    var ageInput by remember {
        mutableStateOf(
            userProfile?.effectiveAge()?.takeIf { it in 13..120 }?.toString().orEmpty()
        )
    }

    LaunchedEffect(userProfile?.userId, userProfile?.updatedAt) {
        if (userProfile != null) {
            displayName = userProfile.displayName
            handle = userProfile.handle
            bio = userProfile.bio
            avatarColorHex = userProfile.avatarColorHex
            pushToTalkPreferred = userProfile.pushToTalkPreferred
            nightModeEnabled = userProfile.nightModeEnabled
            invisibleModePreferred = userProfile.invisibleModePreferred
            selectedSex = userProfile.sexEnum()
            ageInput = userProfile.effectiveAge().takeIf { it in 13..120 }?.toString().orEmpty()
        }
    }

    // Zero-permission Android Photo Picker for Profile Picture
    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val encoded = encodeImageUriToBase64Jpeg(
                    context = context,
                    uri = uri,
                    maxDimensionPx = 280,
                    squareCrop = true,
                    maxBase64Chars = 115_000
                )
                if (!encoded.isNullOrBlank()) {
                    onUpdateProfilePhoto(encoded)
                }
            }
        }
    }

    // Zero-permission Android Photo Picker for Post Picture
    val postPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isEncodingPostPhoto = true
                val encoded = encodeImageUriToBase64Jpeg(
                    context = context,
                    uri = uri,
                    maxDimensionPx = 720,
                    squareCrop = false,
                    maxBase64Chars = 380_000
                )
                isEncodingPostPhoto = false
                if (!encoded.isNullOrBlank()) {
                    draftPostPhotoBase64 = encoded
                }
            }
        }
    }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var camGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    val camLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> camGranted = granted }

    val myPosts = (myPostsState as? UiState.Success)?.data.orEmpty()
    val currentPhotoBase64 = userProfile?.photoBase64.orEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "profile_title") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "My Profile",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TopBarSafetyShieldButton(
                        badgeCount = safetyBadgeCount,
                        onClick = onOpenSafety
                    )
                }
            }

            // Profile Identity Card with Profile Picture Upload
            item(key = "profile_hero") {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clickable {
                                        if (currentPhotoBase64.isNotBlank()) {
                                            showFullAvatar = true
                                        } else {
                                            profilePhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    }
                                    .testTag("profile_avatar_box")
                            ) {
                                SpeakingAvatarCircle(
                                    displayName = displayName.ifBlank { "Host" },
                                    colorHex = avatarColorHex,
                                    speakingLevel = 0.35f,
                                    micActive = false,
                                    size = 80.dp,
                                    photoBase64 = currentPhotoBase64,
                                    userId = currentUserId
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(32.dp)
                                        .clickable(enabled = !isUpdatingPhoto) {
                                            profilePhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .testTag("change_profile_photo_fab")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        if (isUpdatingPhoto) {
                                            CircularProgressIndicator(
                                                color = Color.White,
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.CameraAlt,
                                                contentDescription = "Change profile picture",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val isMyPro = userProfile?.isProSubscriberOnly() == true
                                    Text(
                                        text = displayName.ifBlank { "Studio Host" },
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = usernameColor(
                                            tier = staffCapabilities.tier,
                                            isPro = isMyPro,
                                            userId = currentUserId
                                        )
                                    )
                                    StaffTierBadge(tier = staffCapabilities.tier)
                                    if (isMyPro) {
                                        ProMemberBadge()
                                    }
                                }
                                if (!staffCapabilities.isAdmin) {
                                    Text(
                                        text = "@${handle.ifBlank { "studio" }}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (userEmail.isNotBlank() && !staffCapabilities.isAdmin) {
                                    Text(
                                        text = userEmail,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Dedicated Bio Section with Inline Edit Bio Button
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_bio_section")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Bio",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    TextButton(
                                        onClick = {
                                            bioDialogDraft = bio
                                            showEditBioDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.testTag("profile_edit_bio_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Edit,
                                            contentDescription = "Edit bio",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = if (bio.isBlank()) "Add Bio" else "Edit Bio",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                }
                                Text(
                                    text = bio.ifBlank { "Write a short description about yourself…" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (bio.isBlank()) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    modifier = Modifier.testTag("profile_bio_text")
                                )
                            }
                        }

                        // Followers / Following / Posts / Friends Counts Strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_followers_count")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$followersCount",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Followers",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("profile_following_count")
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$followingCount",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Following",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${myPosts.size}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Posts",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$friendsCount",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Friends",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Time Spent on App Strip
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_time_spent_badge")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Schedule,
                                        contentDescription = "Time spent on app",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Time Spent on App",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = userProfile?.formattedTimeSpent() ?: "0s",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // Appearance Status Picker (Online / Idle / Offline)
                        val selectedAppearance = userProfile?.appearanceStatusEnum() ?: PresenceStatus.ONLINE
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("profile_appearance_status_section")
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Status",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        PresenceStatusDot(status = selectedAppearance, size = 9.dp)
                                        Text(
                                            text = "Appearing ${selectedAppearance.label}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PresenceStatus.entries.forEach { statusOption ->
                                        val isSelected = selectedAppearance == statusOption
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onChangeAppearanceStatus(statusOption) },
                                            leadingIcon = {
                                                PresenceStatusDot(status = statusOption, size = 9.dp)
                                            },
                                            label = {
                                                Text(
                                                    text = statusOption.label,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                                )
                                            },
                                            shape = RoundedCornerShape(999.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("profile_status_option_${statusOption.name}")
                                        )
                                    }
                                }
                                Text(
                                    text = "Choose how you appear to other members. Admins & Support always see your true status.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Points Pill + Change/Remove Photo Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = SafetyEmerald.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { selectedTab = 1 }
                                    .testTag("profile_points_pill")
                            ) {
                                Text(
                                    text = userProfile?.formattedPointsBalance() ?: "0 pts",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SafetyEmerald,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        profilePhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isUpdatingPhoto,
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("change_profile_photo_button")
                                ) {
                                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        if (currentPhotoBase64.isBlank()) "Add Photo" else "Change Photo",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                if (currentPhotoBase64.isNotBlank()) {
                                    TextButton(
                                        onClick = { onUpdateProfilePhoto("") },
                                        enabled = !isUpdatingPhoto,
                                        modifier = Modifier.testTag("remove_profile_photo_button")
                                    ) {
                                        Text("Remove", color = LiveCoral, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Who Viewed My Profile" Teaser Card (Blurred for Free, Unlocked for VOX Pro / 2 pts)
            item(key = "who_viewed_my_profile_card") {
                com.example.ui.components.WhoViewedMyProfileCard(
                    userProfile = userProfile,
                    staffTier = staffCapabilities.tier,
                    profileViews = profileViews,
                    isProcessing = isProcessingWallet,
                    onUnlockWithPoints = {
                        onUnlockProfileViewers { showNeedPointsForBoostDialog = true }
                    },
                    onUpgradeToPro = { selectedTab = 1 },
                    onOpenUserProfile = onOpenUserProfile
                )
            }

            // Segmented Switcher: My Posts vs Edit & Studio
            item(key = "profile_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        label = { Text("My Posts (${myPosts.size})") },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("profile_tab_posts")
                    )
                    FilterChip(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        label = { Text("Settings & Points") },
                        shape = RoundedCornerShape(999.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("profile_tab_studio")
                    )
                }
            }

            if (selectedTab == 0) {
                // CREATE POST COMPOSER CARD
                item(key = "create_post_composer") {
                    val draftBitmap = rememberBase64ImageBitmap(draftPostPhotoBase64)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Post an Update",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )

                            OutlinedTextField(
                                value = postText,
                                onValueChange = { postText = it.take(1000) },
                                placeholder = { Text(stringResource(R.string.hint_post_update)) },
                                minLines = 2,
                                maxLines = 5,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("post_composer_input")
                            )

                            if (isEncodingPostPhoto) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Preparing photo…",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else if (draftBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 140.dp, max = 260.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Image(
                                        bitmap = draftBitmap,
                                        contentDescription = "Selected post photo preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(36.dp)
                                    ) {
                                        IconButton(
                                            onClick = {
                                                draftPostPhotoBase64 = ""
                                            },
                                            modifier = Modifier.testTag("remove_draft_post_photo")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "Remove attached photo",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Boost to Top of Discovery Toggle (5 pts / $5)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (boostPostOnCreate) {
                                    ModAmber.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (boostPostOnCreate) ModAmber.copy(alpha = 0.55f)
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { boostPostOnCreate = !boostPostOnCreate }
                                    .testTag("boost_post_toggle_row")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RocketLaunch,
                                        contentDescription = "Boost to top of Discovery",
                                        tint = if (boostPostOnCreate) ModAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (staffCapabilities.isStaff) {
                                                "Boost to Top of Discovery (FREE · Staff Perk)"
                                            } else {
                                                "Boost to Top of Discovery (5 pts / \$5)"
                                            },
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = if (boostPostOnCreate) ModAmber else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Pin this post at the top of Discovery for 24h so more people notice you",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = boostPostOnCreate,
                                        onCheckedChange = { boostPostOnCreate = it },
                                        modifier = Modifier.testTag("boost_post_switch")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        postPhotoPicker.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    enabled = !isPublishingPost && !isEncodingPostPhoto,
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier.testTag("attach_post_photo_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = "Add picture to post",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (draftPostPhotoBase64.isBlank()) "Add Picture" else "Change Picture")
                                }

                                Button(
                                    onClick = {
                                        onCreatePost(
                                            postText,
                                            draftPostPhotoBase64,
                                            false,
                                            boostPostOnCreate,
                                            { showNeedPointsForBoostDialog = true },
                                            {
                                                postText = ""
                                                draftPostPhotoBase64 = ""
                                                boostPostOnCreate = false
                                            }
                                        )
                                    },
                                    enabled = !isPublishingPost && !isEncodingPostPhoto &&
                                        (postText.isNotBlank() || draftPostPhotoBase64.isNotBlank()),
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("publish_post_button")
                                ) {
                                    if (isPublishingPost) {
                                        CircularProgressIndicator(
                                            color = Color.White,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        when {
                                            boostPostOnCreate && staffCapabilities.isStaff -> "Post & Boost (FREE)"
                                            boostPostOnCreate -> "Post & Boost (5 pts)"
                                            else -> "Post"
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // MY POSTS FEED
                when (myPostsState) {
                    is UiState.Loading -> {
                        item(key = "my_posts_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(key = "my_posts_error") {
                            Text(
                                text = myPostsState.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (myPosts.isEmpty()) {
                            item(key = "my_posts_empty") {
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
                                            modifier = Modifier.size(32.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.empty_posts_title),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = stringResource(R.string.empty_posts_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(myPosts, key = { it.id }) { post ->
                                ProfilePostCard(
                                    post = post,
                                    currentUserId = currentUserId,
                                    canDelete = true,
                                    latestAuthorPhotoBase64 = currentPhotoBase64,
                                    viewerIsAdultVerified = userProfile?.isAdult18Plus() == true,
                                    isProcessingUnlock = isProcessingWallet,
                                    onAuthorClick = null,
                                    onOriginalAuthorClick = onOpenUserProfile,
                                    onRequestAgeVerification = onRequestAgeVerification,
                                    onBoostPost = {
                                        onBoostPost(post) { showNeedPointsForBoostDialog = true }
                                    },
                                    onOpenComments = { activeCommentsPost = post },
                                    onSharePost = { activeSharePost = post },
                                    isStaffFreePerks = staffCapabilities.isStaff,
                                    onToggleLike = { onToggleLikePost(post) },
                                    onDelete = { onDeletePost(post) }
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB 1: SETTINGS & POINTS — ORGANIZED INTO 4 CLEAN SUB-TABS
                item(key = "settings_sub_tabs") {
                    val subTabs = listOf(
                        Triple(0, "Profile", Icons.Rounded.Person),
                        Triple(1, "VIP & Points", Icons.Rounded.Stars),
                        Triple(2, "PTT & Alerts", Icons.Rounded.RecordVoiceOver),
                        Triple(3, "Privacy & Team", Icons.Rounded.Security)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .testTag("profile_settings_sub_tabs"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subTabs.forEach { (idx, label, icon) ->
                            val selected = settingsSubTab == idx
                            FilterChip(
                                selected = selected,
                                onClick = { settingsSubTab = idx },
                                label = {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                shape = RoundedCornerShape(999.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White,
                                    selectedLeadingIconColor = Color.White
                                ),
                                modifier = Modifier.testTag("profile_settings_subtab_$idx")
                            )
                        }
                    }
                }

                when (settingsSubTab) {
                    0 -> {
                        // SUB-TAB 0: PROFILE, HARDWARE READINESS, 18+ VERIFICATION, RESTRICTIONS & SIGN OUT
                        if (!staffCapabilities.isAdmin && userProfile != null) {
                            val activeRestrictions = buildList {
                                if (userProfile.globallyBanned) add("Account Banned")
                                if (userProfile.currentlySuspended()) add("Suspended (${userProfile.remainingSuspensionLabel()} left)")
                                if (userProfile.disableCreateRoom) add("Creating Rooms Disabled")
                                if (userProfile.disableEnterRoom) add("Entering Rooms Disabled")
                                if (userProfile.disableMic) add("Microphone Disabled")
                                if (userProfile.disableVideo) add("Video Streaming Disabled")
                            }
                            if (activeRestrictions.isNotEmpty()) {
                                item(key = "restrictions_card") {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = LiveCoral.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.45f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Active Account Restrictions",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = LiveCoral
                                            )
                                            Text(
                                                text = activeRestrictions.joinToString(" · "),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (staffCapabilities.isSupport && !staffCapabilities.canEnterInvisible) {
                            item(key = "support_inv_notice") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = usernameColor(StaffTier.SUPPORT).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, usernameColor(StaffTier.SUPPORT).copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VisibilityOff,
                                            contentDescription = "Invisible mode status",
                                            tint = usernameColor(StaffTier.SUPPORT)
                                        )
                                        Text(
                                            text = "Invisible room entry is currently off for your Support account. An Administrator can enable it for you anytime.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        item(key = "edit_profile_form") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Stage Profile & Preferences",
                                        style = MaterialTheme.typography.titleMedium
                                    )

                                    OutlinedTextField(
                                        value = displayName,
                                        onValueChange = { displayName = it.take(40) },
                                        label = { Text("Display Name") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_name_input")
                                    )

                                    OutlinedTextField(
                                        value = handle,
                                        onValueChange = { handle = it.replace(" ", "_").take(30) },
                                        label = { Text("Handle") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_handle_input")
                                    )

                                    OutlinedTextField(
                                        value = bio,
                                        onValueChange = { bio = it.take(200) },
                                        label = { Text("Bio") },
                                        minLines = 2,
                                        maxLines = 3,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_bio_input")
                                    )

                                    OutlinedTextField(
                                        value = ageInput,
                                        onValueChange = { raw ->
                                            ageInput = raw.filter { it.isDigit() }.take(3)
                                        },
                                        label = { Text("Age (13+ · used for Discovery filters)") },
                                        placeholder = { Text("e.g. 22") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("profile_age_input")
                                    )

                                    Text("Sex (used for Discovery filters)", style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(UserSex.MALE, UserSex.FEMALE, UserSex.UNSPECIFIED).forEach { sexOption ->
                                            FilterChip(
                                                selected = selectedSex == sexOption,
                                                onClick = { selectedSex = sexOption },
                                                label = { Text(sexOption.label) },
                                                shape = RoundedCornerShape(999.dp),
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = Color.White
                                                ),
                                                modifier = Modifier.testTag("profile_sex_chip_${sexOption.name}")
                                            )
                                        }
                                    }

                                    Text("Avatar Ring Color", style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        AvatarAccentHexPalette.forEach { hex ->
                                            val swatch = parseHexColor(hex)
                                            val selected = avatarColorHex.equals(hex, ignoreCase = true)
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(swatch)
                                                    .border(
                                                        width = if (selected) 3.dp else 0.dp,
                                                        color = if (selected) Color.White else Color.Transparent,
                                                        shape = CircleShape
                                                    )
                                                    .clickable { avatarColorHex = hex }
                                            )
                                        }
                                    }

                                    if (staffCapabilities.effectiveCanEnterInvisible) {
                                        ToggleSettingRow(
                                            title = "Default to Ghost Entry (Invisible Mode)",
                                            subtitle = if (staffCapabilities.isAdmin) {
                                                "Enter rooms unseen — Main Admin can still use the mic while invisible"
                                            } else {
                                                "Enter rooms unseen — switch to Visible inside a room to use the mic"
                                            },
                                            checked = invisibleModePreferred,
                                            onCheckedChange = { invisibleModePreferred = it }
                                        )
                                    }

                                    ToggleSettingRow(
                                        title = "Default to Push-to-Talk",
                                        subtitle = "Hold button to transmit voice on stage (classic Paltalk style)",
                                        checked = pushToTalkPreferred,
                                        onCheckedChange = { pushToTalkPreferred = it }
                                    )

                                    ToggleSettingRow(
                                        title = "Night Blue Dark Mode",
                                        subtitle = "Off = Crisp Blue & White (default) · On = Deep Navy Night theme",
                                        checked = nightModeEnabled,
                                        onCheckedChange = { nightModeEnabled = it }
                                    )

                                    Button(
                                        onClick = {
                                            val parsedAge = ageInput.trim().toLongOrNull()
                                                ?.takeIf { it in 13L..120L } ?: 0L
                                            onSaveProfile(
                                                displayName,
                                                handle,
                                                bio,
                                                avatarColorHex,
                                                pushToTalkPreferred,
                                                nightModeEnabled,
                                                invisibleModePreferred,
                                                selectedSex,
                                                parsedAge
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("save_profile_button")
                                    ) {
                                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Save Profile Preferences")
                                    }
                                }
                            }
                        }

                        // 18+ AGE VERIFICATION STATUS CARD
                        item(key = "age_verification_card") {
                            val isAdultVerified = userProfile?.isAdult18Plus() == true
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isAdultVerified) SafetyEmerald.copy(alpha = 0.5f)
                                    else ModAmber.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_age_verification_card")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isAdultVerified) SafetyEmerald.copy(alpha = 0.14f) else ModAmber.copy(alpha = 0.16f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.VerifiedUser,
                                                contentDescription = "18+ age verification status",
                                                tint = if (isAdultVerified) SafetyEmerald else ModAmber,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isAdultVerified) "18+ Verified" else "18+ Verification Required",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = if (isAdultVerified) SafetyEmerald else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isAdultVerified) {
                                                "Born ${userProfile?.birthYear} · Age verified for 18+ account features"
                                            } else {
                                                "Verify 18+ via your Google Account or Play Age Signals for age-restricted features"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (!isAdultVerified) {
                                        Button(
                                            onClick = onRequestAgeVerification,
                                            shape = RoundedCornerShape(999.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = ModAmber,
                                                contentColor = Color.Black
                                            ),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                            modifier = Modifier.testTag("settings_verify_age_button")
                                        ) {
                                            Text(
                                                text = "Verify 18+",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "hardware_readiness") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Audio & Video Readiness",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    HardwarePermissionRow(
                                        icon = Icons.Rounded.Mic,
                                        title = "Microphone (Voice Stage)",
                                        granted = micGranted,
                                        onRequest = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                                    )
                                    HardwarePermissionRow(
                                        icon = Icons.Rounded.Videocam,
                                        title = "Camera (Live Video Stream)",
                                        granted = camGranted,
                                        onRequest = { camLauncher.launch(Manifest.permission.CAMERA) }
                                    )
                                }
                            }
                        }

                        item(key = "sign_out_card") {
                            OutlinedButton(
                                onClick = onSignOut,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                                border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                                    .testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                                    contentDescription = "Sign Out",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign Out")
                            }
                        }

                        if (!realStaffCapabilities.isAdmin) {
                            item(key = "delete_account_card") {
                                Button(
                                    onClick = { showDeleteAccountDialog = true },
                                    enabled = !isDeletingAccount,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LiveCoral.copy(alpha = 0.14f),
                                        contentColor = LiveCoral
                                    ),
                                    border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.65f)),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("delete_account_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteForever,
                                        contentDescription = "Delete Account",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Delete Account",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        // SUB-TAB 1: VIP PRO MEMBERSHIP, NAME COLOR, POINTS WALLET, FRAMES, STREAKS & REFERRALS
                        item(key = "pro_membership_card") {
                            ProMembershipCard(
                                userProfile = userProfile,
                                staffCapabilities = staffCapabilities,
                                isProcessingWallet = isProcessingWallet,
                                playBillingManager = playBillingManager,
                                onUpgradeToProPlay = onUpgradeToProPlay,
                                onUpgradeToVipProPlay = onUpgradeToVipProPlay,
                                onActivateProWithPoints = onActivateProWithPoints
                            )
                        }

                        item(key = "vip_pro_username_color_card") {
                            VipProUsernameColorCard(
                                userProfile = userProfile,
                                staffTier = staffCapabilities.tier,
                                onSelectColorHex = onSelectVipProNameColor,
                                onUpgradeToVipPro = onUpgradeToVipProPlay
                            )
                        }

                        item(key = "points_wallet_card") {
                            PointsWalletSettingsCard(
                                currentUserId = currentUserId,
                                userProfile = userProfile,
                                isAdmin = staffCapabilities.isAdmin,
                                isStaffFreePerks = staffCapabilities.isStaff,
                                activeSubscriptions = mySubscriptions.filter { it.isCurrentlyActive() },
                                recentTransactions = myTransactions.take(6),
                                appOwnerRevenueCents = allTransactionsForAdmin.sumOf { it.platformShareCents },
                                isProcessingWallet = isProcessingWallet,
                                playBillingManager = playBillingManager,
                                onBuyPoints = onBuyPoints,
                                onBoostMyProfile = {
                                    onBoostMyProfile { showNeedPointsForBoostDialog = true }
                                }
                            )
                        }

                        item(key = "gifted_pro_pass_store_card") {
                            GiftedProPassStoreCard(
                                currentUserId = currentUserId,
                                userProfile = userProfile,
                                directoryEntries = directoryEntries,
                                acceptedFriendIds = acceptedFriendIds,
                                liveRoomHostIds = liveRoomHostIds,
                                isProcessingWallet = isProcessingWallet,
                                onGiftProPass = { targetUserId, targetName, giftNote, onFinished ->
                                    onGiftProPass(
                                        targetUserId,
                                        targetName,
                                        giftNote,
                                        {
                                            onFinished()
                                            showNeedPointsForBoostDialog = true
                                        },
                                        onFinished
                                    )
                                }
                            )
                        }

                        item(key = "spotlight_frame_shop_card") {
                            SpotlightFrameShopCard(
                                userProfile = userProfile,
                                isProcessingWallet = isProcessingWallet,
                                isStaffFreePerks = staffCapabilities.isStaff,
                                onBuySpotlightFrame = { color ->
                                    onBuySpotlightFrame(color) { showNeedPointsForBoostDialog = true }
                                }
                            )
                        }

                        item(key = "daily_login_streak_card") {
                            DailyLoginStreakCard(
                                userProfile = userProfile,
                                staffTier = staffCapabilities.tier
                            )
                        }

                        item(key = "invite_friend_referral_card") {
                            InviteFriendReferralCard(
                                currentUserId = currentUserId,
                                userProfile = userProfile,
                                isEmailVerified = userEmailVerified,
                                incomingReferral = incomingReferral,
                                invitedReferrals = invitedReferrals,
                                onRedeemReferralCode = onRedeemReferralCode
                            )
                        }
                    }

                    2 -> {
                        // SUB-TAB 2: PUSH-TO-TALK HARDWARE, LOCK-SCREEN, CUSTOMIZATION & NOTIFICATIONS
                        item(key = "user_ptt_volume_key_settings_card") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("user_ptt_hardware_settings_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VolumeUp,
                                                contentDescription = "PTT Hardware & Lock-Screen Controls",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Push-to-Talk & Lock-Screen Controls",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Hardware buttons, lock-screen radio & beep preferences",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Box(modifier = Modifier.testTag("toggle_volume_down_ptt")) {
                                        ToggleSettingRow(
                                            title = "Use Volume Key for Push-to-Talk",
                                            subtitle = if (volumeDownPttEnabled) {
                                                "On: Hold Volume Down to talk when PTT is active. Turn off to use your volume button normally."
                                            } else {
                                                "Off: Volume key will NOT trigger Push-to-Talk — your volume buttons adjust sound normally."
                                            },
                                            checked = volumeDownPttEnabled,
                                            onCheckedChange = { enable ->
                                                LivePttSessionManager.setVolumeDownPttEnabled(context, enable)
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.testTag("toggle_lock_screen_ptt")) {
                                        ToggleSettingRow(
                                            title = "Lock-Screen Push-to-Talk Button",
                                            subtitle = "Show a hold-to-talk PTT button over your lock screen if your phone locks during live PTT",
                                            checked = lockScreenPttEnabled,
                                            onCheckedChange = { enable ->
                                                LivePttSessionManager.setLockScreenPttEnabled(context, enable)
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.testTag("toggle_lock_screen_recent_dm")) {
                                        ToggleSettingRow(
                                            title = "Show Recent DM on Lock Screen",
                                            subtitle = "Display your last incoming DM on the lock screen so you can tap it and hold PTT to send a voice-to-text reply without unlocking",
                                            checked = lockScreenRecentDmEnabled,
                                            onCheckedChange = { enable ->
                                                lockScreenRecentDmEnabled = enable
                                                DirectMessageNotificationPrefs.setLockScreenRecentDmEnabled(context, enable)
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.testTag("toggle_silence_ptt_beep")) {
                                        ToggleSettingRow(
                                            title = "Silence PTT Beep Noise",
                                            subtitle = "Mute incoming Push-to-Talk beep pings (by default beeps still come through even in PTT Silent Mode)",
                                            checked = pttBeepSilenced,
                                            onCheckedChange = { silence ->
                                                pttBeepSilenced = silence
                                                LivePttSessionManager.setBeepSilenced(context, silence)
                                            }
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            context.startActivity(LockScreenPttActivity.createPreviewIntent(context))
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("profile_preview_lock_screen_ptt_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Open Lock-Screen PTT and Recent DM",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Open Lock-Screen PTT & Recent DM")
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val msg = VoxPttWidgetHelper.requestPinPttWidget(context)
                                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("profile_add_ptt_widget_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Mic,
                                            contentDescription = "Add Home Screen PTT Widget",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add Home Screen PTT Widget")
                                    }
                                }
                            }
                        }

                        item(key = "ptt_customization_shop_card") {
                            PttCustomizationShopCard(
                                userProfile = userProfile,
                                isProcessingWallet = isProcessingWallet,
                                onSelectOrBuyTheme = { theme ->
                                    onSelectOrBuyPttTheme(theme) { showNeedPointsForBoostDialog = true }
                                },
                                onSelectOrBuyChirp = { chirp ->
                                    onSelectOrBuyPttChirp(chirp) { showNeedPointsForBoostDialog = true }
                                }
                            )
                        }

                        item(key = "dm_alerts_preferences_card") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dm_alerts_preferences_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.NotificationsActive,
                                                contentDescription = "Notifications and Alerts",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Notifications & Chirp Alerts",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Private message notifications, read receipts & close-friend alerts",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    ToggleSettingRow(
                                        title = "Private Message Notifications",
                                        subtitle = if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            "Tap to grant phone notification permission for background private messages"
                                        } else {
                                            "Send phone notifications for incoming private messages even in the background"
                                        },
                                        checked = dmNotificationsEnabled && hasNotificationPermission,
                                        onCheckedChange = { enable ->
                                            if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                !DirectMessageNotificationHelper.hasNotificationPermission(context)
                                            ) {
                                                dmNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                dmNotificationsEnabled = enable
                                                DirectMessageNotificationPrefs.setDmNotificationsEnabled(context, enable)
                                                if (enable) {
                                                    DirectMessageNotificationHelper.ensureChannel(context)
                                                    DirectMessageSyncWorker.schedule(context)
                                                }
                                            }
                                        }
                                    )

                                    Box(modifier = Modifier.testTag("toggle_dm_popup_quick_reply")) {
                                        ToggleSettingRow(
                                            title = "Background Message Pop-ups & Quick Reply",
                                            subtitle = "Pop open message details with inline Quick Reply when a private message arrives",
                                            checked = quickReplyPopupEnabled,
                                            onCheckedChange = { enable ->
                                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                    !DirectMessageNotificationHelper.hasNotificationPermission(context)
                                                ) {
                                                    dmNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                                quickReplyPopupEnabled = enable
                                                DirectMessageNotificationPrefs.setQuickReplyPopupEnabled(context, enable)
                                                if (enable) {
                                                    DirectMessageNotificationHelper.ensureChannel(context)
                                                }
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.testTag("toggle_friend_attention_alerts")) {
                                        ToggleSettingRow(
                                            title = "Friend Attention Alerts (Ring & Vibrate)",
                                            subtitle = "Allow accepted friends to ring and vibrate your phone from private chat (max 1 every 5 min)",
                                            checked = friendAttentionAlertsEnabled,
                                            onCheckedChange = { enable ->
                                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                    !DirectMessageNotificationHelper.hasNotificationPermission(context)
                                                ) {
                                                    dmNotifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                                }
                                                friendAttentionAlertsEnabled = enable
                                                DirectMessageNotificationPrefs.setFriendAttentionAlertsEnabled(context, enable)
                                                if (enable) {
                                                    DirectMessageNotificationHelper.ensureChannel(context)
                                                }
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.testTag("toggle_dm_read_receipts")) {
                                        ToggleSettingRow(
                                            title = "Private Message Read Receipts",
                                            subtitle = "Let others see when you've read their private messages (can also be toggled per chat)",
                                            checked = readReceiptsEnabled,
                                            onCheckedChange = { enable ->
                                                readReceiptsEnabled = enable
                                                DirectMessageNotificationPrefs.setReadReceiptsEnabled(context, enable)
                                            }
                                        )
                                    }

                                    Box(modifier = Modifier.testTag("toggle_priority_chirp_opt_in")) {
                                        ToggleSettingRow(
                                            title = "Close Friends Priority Break-In Chirps",
                                            subtitle = "Allow close friends to spend 1–2 points to send a two-tone radio alert pinned to the top of your Chats inbox",
                                            checked = userProfile?.priorityChirpOptIn == true,
                                            onCheckedChange = { enable ->
                                                onSetPriorityChirpOptIn(enable)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // SUB-TAB 3: PRIVATE MESSAGE PRIVACY & COMPANY SUB-ACCOUNTS
                        item(key = "private_message_privacy_card") {
                            PrivateMessageSettingsCard(
                                currentPolicy = userProfile?.dmPolicyEnum() ?: DmPolicy.OPEN,
                                paidMembers = paidDmMembersForMe,
                                onSelectPolicy = onSetDmPolicy,
                                onKickMember = onKickPaidDmMember
                            )
                        }

                        item(key = "company_workspace_card") {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
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
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Groups,
                                                contentDescription = "Company Sub-Accounts",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Company Employee Sub-Accounts",
                                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (canManageCompany || isCompanySupervisor) {
                                                    "Create sub-names, appoint supervisors, send work orders/notes, and review employee requests."
                                                } else {
                                                    "VIP Pro unlocks Company Sub-Accounts with 60s PTT, Supervisors & Work Orders. Employees can link a sub-name code below."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (canManageCompany || isCompanySupervisor) {
                                            Button(
                                                onClick = onOpenCompanyConsole,
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("profile_open_company_console_button")
                                            ) {
                                                Text(
                                                    text = if (canManageCompany) "Manage Company Team" else "Supervisor Console",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        } else {
                                            OutlinedButton(
                                                onClick = { showJoinCompanyDialog = true },
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .testTag("profile_join_company_sub_account_button")
                                            ) {
                                                Text(
                                                    text = "Join Company Sub-Account",
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "profile_bottom_spacer") {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showFullAvatar && currentPhotoBase64.isNotBlank()) {
        FullScreenPhotoDialog(
            photoBase64 = currentPhotoBase64,
            contentDescription = "My profile picture",
            onDismiss = { showFullAvatar = false }
        )
    }

    if (showDeleteAccountDialog) {
        val hasActivePlan = userProfile?.isProActive(staffCapabilities.tier) == true ||
            mySubscriptions.any { it.isCurrentlyActive() }
        DeleteAccountConfirmDialog(
            hasActiveMembership = hasActivePlan,
            isDeleting = isDeletingAccount,
            onConfirmYes = {
                isDeletingAccount = true
                onDeleteAccount(
                    { _ ->
                        isDeletingAccount = false
                        showDeleteAccountDialog = false
                    },
                    { _ ->
                        isDeletingAccount = false
                    }
                )
            },
            onDismissNo = {
                showDeleteAccountDialog = false
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

    if (showEditBioDialog) {
        AlertDialog(
            onDismissRequest = { showEditBioDialog = false },
            title = { Text("Edit Bio") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bioDialogDraft,
                        onValueChange = { bioDialogDraft = it.take(200) },
                        label = { Text("Short Bio") },
                        placeholder = { Text("Tell everyone a little about yourself…") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_edit_bio_dialog_input")
                    )
                    Text(
                        text = "${bioDialogDraft.length} / 200",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = bioDialogDraft.trim()
                        bio = trimmed
                        onSaveBio(trimmed) {
                            showEditBioDialog = false
                        }
                    },
                    modifier = Modifier.testTag("profile_save_bio_dialog_button")
                ) {
                    Text("Save Bio")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBioDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showNeedPointsForBoostDialog) {
        val currentPts = userProfile?.pointsBalance ?: 0L
        AlertDialog(
            onDismissRequest = { showNeedPointsForBoostDialog = false },
            title = { Text("Need 5 Points to Boost") },
            text = {
                Text(
                    "You currently have $currentPts pts. Boosting a post to the top of Discovery costs 5 points (\$5). Buy points in Settings & Points to promote your post!"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNeedPointsForBoostDialog = false
                        selectedTab = 1
                    },
                    modifier = Modifier.testTag("profile_boost_go_buy_points")
                ) {
                    Text("Buy Points")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNeedPointsForBoostDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showJoinCompanyDialog) {
        JoinCompanySubAccountDialog(
            onDismiss = { showJoinCompanyDialog = false },
            onClaim = { subName, accessCode ->
                showJoinCompanyDialog = false
                onJoinCompanySubAccount(subName, accessCode)
            }
        )
    }
}

@Composable
private fun HardwarePermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    onRequest: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (granted) SafetyEmerald else ModAmber,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (granted) "Ready for live stage" else "Permission needed before going live",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted) SafetyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "Granted",
                tint = SafetyEmerald,
                modifier = Modifier.size(22.dp)
            )
        } else {
            OutlinedButton(onClick = onRequest) {
                Text("Enable")
            }
        }
    }
}

@Composable
private fun ProMembershipCard(
    userProfile: UserProfile?,
    staffCapabilities: StaffCapabilities,
    isProcessingWallet: Boolean,
    playBillingManager: com.example.billing.PlayBillingManager? = null,
    onUpgradeToProPlay: () -> Unit,
    onUpgradeToVipProPlay: () -> Unit = {},
    onActivateProWithPoints: () -> Unit
) {
    val isProSubscriber = userProfile?.isProSubscriberOnly() == true
    val hasProAccess = userProfile?.isProActive(staffCapabilities.tier) == true
    val activeTier = userProfile?.effectiveProTier(staffCapabilities.tier) ?: ProTier.FREE
    val daysLeft = userProfile?.proDaysRemaining() ?: 0
    val pointsBalance = userProfile?.pointsBalance ?: 0L
    val isPlayInFlight = playBillingManager?.isPurchaseInFlight?.collectAsStateWithLifecycle()?.value ?: false
    val proPrice = playBillingManager?.formattedPriceForTier(ProTier.PRO) ?: ProTier.PRO.priceLabel
    val vipProPrice = playBillingManager?.formattedPriceForTier(ProTier.VIP_PRO) ?: ProTier.VIP_PRO.priceLabel
    val canClick = !isProcessingWallet && !isPlayInFlight
    val proBlue = usernameColor(StaffTier.USER, isPro = true)
    val vipAccent = MaterialTheme.colorScheme.secondary

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (hasProAccess) 1.5.dp else 1.dp,
            color = proBlue.copy(alpha = if (hasProAccess) 0.75f else 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pro_membership_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stars,
                    contentDescription = "VOX Pro Plans",
                    tint = proBlue,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VOX Memberships",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = proBlue
                    )
                    Text(
                        text = "Current: ${activeTier.label} · Group DM up to ${activeTier.maxGroupDmMembers} · Private Room up to ${activeTier.maxPrivateRoomUsers} users",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("active_membership_tier_label")
                    )
                }
                if (userProfile?.proLifetime == true) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = proBlue.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, proBlue.copy(alpha = 0.65f))
                    ) {
                        Text(
                            text = "LIFETIME PRO",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = proBlue,
                            modifier = Modifier
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                                .testTag("lifetime_pro_badge")
                        )
                    }
                } else if (isProSubscriber) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = proBlue.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "${activeTier.label.uppercase()} · ${daysLeft}d",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = proBlue,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                } else if (staffCapabilities.isStaff) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = SafetyEmerald.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "STAFF INCLUDED",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = SafetyEmerald,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "FREE · Max 5 DM",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (staffCapabilities.isSupport && !staffCapabilities.isAdmin) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SafetyEmerald.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("support_staff_free_perks_banner")
                ) {
                    Text(
                        text = "Support Staff Full Perks Active — Free of Charge. All VOX Pro/VIP features, boosts, Spotlight Frames, PTT Themes & Chirps are unlocked for you at 0 pts. All perk usage is logged for App Admins; abuse results in Support Staff removal.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Tier 1: VOX Pro ($9.99/mo or 10 pts) — Group DMs up to 15 users
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = proBlue.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, proBlue.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "VOX Pro",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = proBlue,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = proPrice,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = proBlue
                        )
                    }
                    Text(
                        text = "• Group DMs up to 15 users (Free max 5)\n" +
                            "• Private Chat Rooms up to 50 users (Free max 25)\n" +
                            "• 1-minute Push-to-Talk clips (Free gets 5s), Reels, Who Viewed My Profile & Blue badge",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onUpgradeToProPlay,
                            enabled = canClick,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = proBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("pro_subscribe_play_button")
                        ) {
                            Text(
                                text = if (activeTier == ProTier.PRO) "Renew Pro · $proPrice" else "Go Pro · $proPrice",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        OutlinedButton(
                            onClick = onActivateProWithPoints,
                            enabled = canClick && pointsBalance >= UserProfile.PRO_REDEEM_POINTS,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, proBlue.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("pro_activate_with_points_button")
                        ) {
                            Text(
                                text = "Use ${UserProfile.PRO_REDEEM_POINTS} pts",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (pointsBalance >= UserProfile.PRO_REDEEM_POINTS) proBlue else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Tier 2: VIP Pro ($29.99/mo) — Group DMs up to 25 users
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = vipAccent.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, vipAccent.copy(alpha = 0.40f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "VIP Pro",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.ExtraBold),
                            color = vipAccent,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = vipProPrice,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = vipAccent
                        )
                    }
                    Text(
                        text = "• Company Code + Unique Employee PIN generator for your first 10 sub-accounts\n" +
                            "• Group DMs up to 25 users (required above 15 users) & Private Rooms up to 75 users\n" +
                            "• Admin Push-to-Talk speaker selection & all VOX Pro perks",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onUpgradeToVipProPlay,
                        enabled = canClick,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = vipAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("vip_pro_subscribe_play_button")
                    ) {
                        Text(
                            text = if (activeTier == ProTier.VIP_PRO) "Renew VIP Pro · $vipProPrice" else "Upgrade to VIP Pro · $vipProPrice",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PointsWalletSettingsCard(
    currentUserId: String,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    isStaffFreePerks: Boolean = false,
    activeSubscriptions: List<CreatorSubscription>,
    recentTransactions: List<PointTransaction>,
    appOwnerRevenueCents: Long,
    isProcessingWallet: Boolean,
    playBillingManager: com.example.billing.PlayBillingManager? = null,
    onBuyPoints: (Long) -> Unit,
    onBoostMyProfile: () -> Unit = {}
) {
    val pointsBalance = userProfile?.pointsBalance ?: 0L
    val creatorEarnedPoints = userProfile?.creatorEarnedPoints ?: 0L
    val billingState = playBillingManager?.connectionState?.collectAsStateWithLifecycle()?.value
        ?: com.example.billing.PlayBillingConnectionState.DISCONNECTED
    val billingBanner = playBillingManager?.statusBanner?.collectAsStateWithLifecycle()?.value
    val isPlayInFlight = playBillingManager?.isPurchaseInFlight?.collectAsStateWithLifecycle()?.value ?: false
    val playProducts = playBillingManager?.productDetailsByPoints?.collectAsStateWithLifecycle()?.value.orEmpty()
    val buyEnabled = !isProcessingWallet && !isPlayInFlight
    fun priceLabelFor(points: Long): String {
        return playProducts[points]?.oneTimePurchaseOfferDetails?.formattedPrice
            ?: playBillingManager?.formattedPriceFor(points)
            ?: formatUsdCents(points * 100L)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("points_wallet_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = "Points wallet",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Points & Creator Wallet",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = when (billingState) {
                        com.example.billing.PlayBillingConnectionState.CONNECTED -> SafetyEmerald.copy(alpha = 0.16f)
                        com.example.billing.PlayBillingConnectionState.CONNECTING -> ModAmber.copy(alpha = 0.16f)
                        else -> LiveCoral.copy(alpha = 0.14f)
                    }
                ) {
                    Text(
                        text = when (billingState) {
                            com.example.billing.PlayBillingConnectionState.CONNECTED -> "Google Play Ready"
                            com.example.billing.PlayBillingConnectionState.CONNECTING -> "Connecting Play…"
                            com.example.billing.PlayBillingConnectionState.UNAVAILABLE -> "Play Unavailable"
                            com.example.billing.PlayBillingConnectionState.DISCONNECTED -> "Play Offline"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when (billingState) {
                            com.example.billing.PlayBillingConnectionState.CONNECTED -> SafetyEmerald
                            com.example.billing.PlayBillingConnectionState.CONNECTING -> ModAmber
                            else -> LiveCoral
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (!billingBanner.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ModAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = billingBanner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Balance + Creator Earnings + (Admin) App Owner Revenue Summary
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Points Balance",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = userProfile?.formattedPointsBalance() ?: "0 pts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = SafetyEmerald.copy(alpha = 0.14f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Creator Points Earned",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$creatorEarnedPoints pts",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                            color = SafetyEmerald
                        )
                    }
                }
            }

            if (isAdmin) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ModAmber.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_app_owner_revenue_card")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "App Owner Revenue (100%)",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ModAmber
                            )
                            Text(
                                text = "All payments go to the app owner · Creators earn usable in-app points",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatUsdCents(appOwnerRevenueCents),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = ModAmber
                        )
                    }
                }
            }

            Text(
                text = "Earn usable in-app points (added directly to your Points Balance, never cash) from paid DMs (+1 pt per \$5 paid DM) and login streaks. Spend points on Pro Membership (10 pts), promoting posts, videos, or your profile (5 pts), Spotlight Frames, and Custom PTT Themes & Walkie Chirps!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Promote Profile to Top of Discovery (5 pts / 24h — FREE for Staff)
            val isProfileBoosted = userProfile?.isProfileBoosted() == true
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isProfileBoosted) SafetyEmerald.copy(alpha = 0.14f) else ModAmber.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isProfileBoosted) SafetyEmerald.copy(alpha = 0.55f) else ModAmber.copy(alpha = 0.45f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("promote_profile_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RocketLaunch,
                        contentDescription = "Promote profile to top of Discovery",
                        tint = if (isProfileBoosted) SafetyEmerald else ModAmber,
                        modifier = Modifier.size(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isProfileBoosted) {
                                "Profile Promoted · ${userProfile?.profileBoostRemainingLabel()}"
                            } else {
                                "Promote Your Profile to Top"
                            },
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isProfileBoosted) SafetyEmerald else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isStaffFreePerks) {
                                "Pin your profile at the top of Discover → People for 24 hours (FREE Staff Perk)"
                            } else {
                                "Pin your profile at the top of Discover → People for 24 hours (5 pts)"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onBoostMyProfile,
                        enabled = !isProcessingWallet,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isProfileBoosted || isStaffFreePerks) SafetyEmerald else ModAmber,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("promote_profile_button")
                    ) {
                        Text(
                            text = when {
                                isStaffFreePerks && isProfileBoosted -> "Extend · FREE"
                                isStaffFreePerks -> "FREE (Staff)"
                                isProfileBoosted -> "Extend · 5 pts"
                                else -> "5 pts"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }

            // Buy Points Packages via Google Play Store Billing
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BuyPointsPackageButton(
                    points = 2L,
                    priceLabel = priceLabelFor(2L),
                    enabled = buyEnabled,
                    onClick = { onBuyPoints(2L) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("buy_points_2")
                )
                BuyPointsPackageButton(
                    points = 5L,
                    priceLabel = priceLabelFor(5L),
                    enabled = buyEnabled,
                    onClick = { onBuyPoints(5L) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("buy_points_5")
                )
                BuyPointsPackageButton(
                    points = 10L,
                    priceLabel = priceLabelFor(10L),
                    enabled = buyEnabled,
                    onClick = { onBuyPoints(10L) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("buy_points_10")
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BuyPointsPackageButton(
                    points = 20L,
                    priceLabel = priceLabelFor(20L),
                    enabled = buyEnabled,
                    onClick = { onBuyPoints(20L) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("buy_points_20")
                )
                BuyPointsPackageButton(
                    points = 50L,
                    priceLabel = priceLabelFor(50L),
                    enabled = buyEnabled,
                    onClick = { onBuyPoints(50L) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("buy_points_50")
                )
            }

            // Active $15/mo Subscriptions
            if (activeSubscriptions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Active Creator Subscriptions (\$15/mo)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                activeSubscriptions.forEach { sub ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CardMembership,
                                contentDescription = null,
                                tint = SafetyEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = sub.creatorName,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Text(
                            text = "${sub.daysRemaining()}d left",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafetyEmerald
                        )
                    }
                }
            }

            // Recent Points & Earnings Activity
            if (recentTransactions.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Recent Points Activity",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                recentTransactions.forEach { tx ->
                    val isCreatorCredit = tx.creatorId == currentUserId
                    val summary = when (tx.typeEnum()) {
                        PointTransactionType.BUY_POINTS ->
                            "Bought +${tx.pointsDelta} pts (${formatUsdCents(tx.grossCents)})"
                        PointTransactionType.UNLOCK_PHOTO ->
                            if (isCreatorCredit) {
                                "${tx.buyerName} unlocked your photo (+1 usable point earned)"
                            } else {
                                "Unlocked ${tx.creatorName}'s photo (-2 pts)"
                            }
                        PointTransactionType.SUBSCRIBE_CREATOR ->
                            if (isCreatorCredit) {
                                "${tx.buyerName} subscribed (+8 usable points earned)"
                            } else {
                                "Subscribed to ${tx.creatorName} (\$15.00/mo)"
                            }
                        PointTransactionType.BOOST_POST ->
                            "Promoted photo post to top of Discovery (-5 pts)"
                        PointTransactionType.BOOST_REEL ->
                            "Promoted Reel video to top of Reels (-5 pts)"
                        PointTransactionType.BOOST_PROFILE ->
                            "Promoted profile to top of Discovery (-5 pts · 24h)"
                        PointTransactionType.BUY_PTT_ITEM ->
                            "Unlocked custom PTT item (${tx.postId.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }}) (${tx.pointsDelta} pts)"
                        PointTransactionType.PRO_MEMBERSHIP ->
                            if (tx.pointsDelta < 0) {
                                "Activated VOX Pro Membership (${tx.pointsDelta} pts · 30 days)"
                            } else {
                                "Activated VOX Pro Membership (${formatUsdCents(tx.grossCents)}/mo)"
                            }
                        PointTransactionType.VIP_PRO_MEMBERSHIP ->
                            "Activated VIP Pro Membership (${formatUsdCents(tx.grossCents)}/mo · up to 25 Group DM)"
                        PointTransactionType.BUSINESS_MEMBERSHIP ->
                            "Activated Business Account (${formatUsdCents(tx.grossCents)}/mo · up to 50 Sub-Accounts)"
                        PointTransactionType.UNLOCK_DM ->
                            if (isCreatorCredit) {
                                "${tx.buyerName} unlocked Private Messages (+1 usable point earned)"
                            } else {
                                "Unlocked Private Messages with ${tx.creatorName} (\$5.00)"
                            }
                        PointTransactionType.BUY_SPOTLIGHT_FRAME ->
                            "Activated 24-Hour Glowing Neon Spotlight Frame (-5 pts)"
                        PointTransactionType.DAILY_STREAK_BONUS ->
                            if (tx.pointsDelta >= 1L) {
                                "7-Day Login Streak Bonus (+0.5 pt → +1 full point)"
                            } else {
                                "7-Day Login Streak Bonus (+0.5 pt banked)"
                            }
                        PointTransactionType.REFERRAL_BONUS ->
                            if (isCreatorCredit) {
                                "Invite-a-Friend Bonus: ${tx.buyerName} verified & sent 1st PTT (+2 pts)"
                            } else {
                                "Invite-a-Friend Bonus with ${tx.creatorName} (+2 pts)"
                            }
                        PointTransactionType.UNLOCK_PROFILE_VIEWERS ->
                            "Unlocked Who Viewed My Profile list for 24h (-2 pts)"
                        PointTransactionType.PRIORITY_CHIRP ->
                            "Sent Priority Break-In Chirp to ${tx.creatorName.ifBlank { "friend" }} (${tx.pointsDelta} pts)"
                        PointTransactionType.DM_SCROLLING_BANNER ->
                            "Set 24-Hour DM Scrolling Banner (${tx.pointsDelta} pt)"
                        PointTransactionType.GIFT_PRO_PASS ->
                            if (isCreatorCredit) {
                                val noteSuffix = tx.postId.takeIf { it.isNotBlank() && it != "GIFT_PRO_30D" }
                                    ?.let { " — \"$it\"" }
                                    .orEmpty()
                                "${tx.buyerName} gifted you a 1-Month VOX Pro Pass!$noteSuffix"
                            } else {
                                "Gifted 1-Month VOX Pro Pass to ${tx.creatorName.ifBlank { "friend" }} (${tx.pointsDelta} pts)"
                            }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Stars,
                            contentDescription = null,
                            tint = if (isCreatorCredit ||
                                tx.typeEnum() == PointTransactionType.BUY_POINTS ||
                                tx.typeEnum() == PointTransactionType.DAILY_STREAK_BONUS ||
                                tx.typeEnum() == PointTransactionType.REFERRAL_BONUS
                            ) {
                                SafetyEmerald
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BuyPointsPackageButton(
    points: Long,
    priceLabel: String = formatUsdCents(points * 100L),
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        modifier = modifier.height(52.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$points Points",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = priceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun PrivateMessageSettingsCard(
    currentPolicy: DmPolicy,
    paidMembers: List<DmAccessPass>,
    onSelectPolicy: (DmPolicy) -> Unit,
    onKickMember: (senderId: String, senderName: String) -> Unit
) {
    var pendingKickPass by remember { mutableStateOf<DmAccessPass?>(null) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dm_privacy_settings_card")
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
                    imageVector = Icons.Rounded.LockPerson,
                    contentDescription = "Private message settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Private Messages Privacy & Paid DM",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = "Control who can send you private messages. Lock DMs to paid users (\$5 to message you — full \$5 goes to the app owner and you earn +1 usable point per user; 10 points = Pro Membership) and kick any paid member anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DmPolicyOptionRow(
                title = "Allow Private Messages (Everyone)",
                subtitle = "Anyone on VOX can start a private chat with you",
                icon = Icons.Rounded.ChatBubbleOutline,
                selected = currentPolicy == DmPolicy.OPEN,
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = { onSelectPolicy(DmPolicy.OPEN) },
                testTag = "dm_policy_open"
            )

            DmPolicyOptionRow(
                title = "Lock to Paid Users (\$5 to DM)",
                subtitle = "Users pay \$5 to message you (+1 usable in-app point per user; earn 10 pts for Pro or spend in-app). Kick anytime.",
                icon = Icons.Rounded.Lock,
                selected = currentPolicy == DmPolicy.PAID,
                accentColor = ModAmber,
                onClick = { onSelectPolicy(DmPolicy.PAID) },
                testTag = "dm_policy_paid"
            )

            DmPolicyOptionRow(
                title = "Turn Off Private Messages",
                subtitle = "Nobody can start new private messages with you",
                icon = Icons.Rounded.Block,
                selected = currentPolicy == DmPolicy.DISABLED,
                accentColor = LiveCoral,
                onClick = { onSelectPolicy(DmPolicy.DISABLED) },
                testTag = "dm_policy_disabled"
            )

            if (currentPolicy == DmPolicy.PAID || paidMembers.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Paid DM Members (${paidMembers.count { it.isCurrentlyAllowed() }} active)",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (paidMembers.isEmpty()) {
                    Text(
                        text = "No paid DM members yet. When someone pays \$5 to message you, you earn +1 usable in-app point and they appear here so you can kick them anytime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    paidMembers.forEach { pass ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("paid_dm_member_row_${pass.senderId}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = pass.senderName.ifBlank { "Member" },
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (pass.kicked || !pass.active) {
                                            "Kicked from messaging you"
                                        } else {
                                            "Paid \$5 (+1 usable point earned)"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (pass.kicked || !pass.active) LiveCoral else SafetyEmerald
                                    )
                                }

                                if (pass.isCurrentlyAllowed()) {
                                    OutlinedButton(
                                        onClick = { pendingKickPass = pass },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = LiveCoral),
                                        border = BorderStroke(1.dp, LiveCoral.copy(alpha = 0.55f)),
                                        shape = RoundedCornerShape(999.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.testTag("kick_paid_dm_${pass.senderId}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PersonRemove,
                                            contentDescription = "Kick ${pass.senderName}",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Kick", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(999.dp),
                                        color = LiveCoral.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "Kicked",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = LiveCoral,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingKickPass?.let { pass ->
        AlertDialog(
            onDismissRequest = { pendingKickPass = null },
            title = { Text("Kick ${pass.senderName}?") },
            text = {
                Text(
                    "Remove ${pass.senderName}'s paid private messaging access? They will immediately be blocked from sending you private messages."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onKickMember(pass.senderId, pass.senderName)
                        pendingKickPass = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LiveCoral,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_kick_paid_dm_button")
                ) {
                    Text("Kick Member")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingKickPass = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DmPolicyOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accentColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(
            width = if (selected) 1.5.dp else 1.dp,
            color = if (selected) accentColor.copy(alpha = 0.65f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = if (selected) accentColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(
                selected = selected,
                onClick = onClick
            )
        }
    }
}
