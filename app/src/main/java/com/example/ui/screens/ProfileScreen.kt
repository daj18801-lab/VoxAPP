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
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockPerson
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CreatorSubscription
import com.example.data.DmAccessPass
import com.example.data.DmPolicy
import com.example.data.PointTransaction
import com.example.data.PointTransactionType
import com.example.data.PostComment
import com.example.data.ProfilePost
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.data.formatUsdCents
import com.example.notifications.DirectMessageNotificationHelper
import com.example.notifications.DirectMessageNotificationPrefs
import com.example.notifications.DirectMessageSyncWorker
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.PostCommentsDialog
import com.example.ui.components.ProfilePostCard
import com.example.ui.components.SharePostDialog
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.encodeImageUriToBase64Jpeg
import com.example.ui.components.parseHexColor
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.components.usernameColor
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
        invisibleModePreferred: Boolean
    ) -> Unit,
    onSaveBio: (String, () -> Unit) -> Unit = { _, _ -> },
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) } // 0 = My Posts, 1 = Settings & Points
    var showFullAvatar by remember { mutableStateOf(false) }
    var showEditBioDialog by remember { mutableStateOf(false) }
    var bioDialogDraft by remember { mutableStateOf("") }
    var showNeedPointsForBoostDialog by remember { mutableStateOf(false) }
    var activeCommentsPost by remember { mutableStateOf<ProfilePost?>(null) }
    var activeSharePost by remember { mutableStateOf<ProfilePost?>(null) }

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
    var lockPostPhoto by remember { mutableStateOf(false) }
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

    LaunchedEffect(userProfile?.userId, userProfile?.updatedAt) {
        if (userProfile != null) {
            displayName = userProfile.displayName
            handle = userProfile.handle
            bio = userProfile.bio
            avatarColorHex = userProfile.avatarColorHex
            pushToTalkPreferred = userProfile.pushToTalkPreferred
            nightModeEnabled = userProfile.nightModeEnabled
            invisibleModePreferred = userProfile.invisibleModePreferred
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
                Text(
                    text = "My Profile",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground
                )
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
                                    photoBase64 = currentPhotoBase64
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
                                    Text(
                                        text = displayName.ifBlank { "Studio Host" },
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = usernameColor(staffCapabilities.tier)
                                    )
                                    StaffTierBadge(tier = staffCapabilities.tier)
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
                                    text = "${userProfile?.pointsBalance ?: 0L} pts",
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
                                                lockPostPhoto = false
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

                                // Lock Photo Toggle (18+ · 2 pts / $2 unlock · $1 to creator, $1 to app owner)
                                val isAdultVerified = userProfile?.isAdult18Plus() == true
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (lockPostPhoto && isAdultVerified) {
                                        ModAmber.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (lockPostPhoto && isAdultVerified) ModAmber.copy(alpha = 0.55f)
                                        else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!lockPostPhoto && !isAdultVerified) {
                                                onRequestAgeVerification()
                                            } else {
                                                lockPostPhoto = !lockPostPhoto
                                            }
                                        }
                                        .testTag("lock_post_photo_toggle_row")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Lock,
                                            contentDescription = "Lock photo (18+)",
                                            tint = if (lockPostPhoto && isAdultVerified) ModAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Lock Photo (18+ · 2 pts / \$2 to unlock)",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = if (lockPostPhoto && isAdultVerified) ModAmber else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (isAdultVerified) {
                                                    "You earn \$1 & app owner gets \$1 per unlock, or viewers subscribe \$15/mo"
                                                } else {
                                                    "18+ age verification required — tap to verify your age first"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = lockPostPhoto && isAdultVerified,
                                            onCheckedChange = { wantLock ->
                                                if (wantLock && !isAdultVerified) {
                                                    onRequestAgeVerification()
                                                } else {
                                                    lockPostPhoto = wantLock
                                                }
                                            },
                                            modifier = Modifier.testTag("lock_post_photo_switch")
                                        )
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
                                            text = "Boost to Top of Discovery (5 pts / \$5)",
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
                                            lockPostPhoto && draftPostPhotoBase64.isNotBlank(),
                                            boostPostOnCreate,
                                            { showNeedPointsForBoostDialog = true },
                                            {
                                                postText = ""
                                                draftPostPhotoBase64 = ""
                                                lockPostPhoto = false
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
                                    Text(if (boostPostOnCreate) "Post & Boost (5 pts)" else "Post")
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
                                    onSharePost = if (!post.locked) {
                                        { activeSharePost = post }
                                    } else {
                                        null
                                    },
                                    onToggleLike = { onToggleLikePost(post) },
                                    onDelete = { onDeletePost(post) }
                                )
                            }
                        }
                    }
                }
            } else {
                // TAB 1: POINTS WALLET (BUY POINTS), PRIVATE MESSAGE PRIVACY ($10 PAID DM), CREATOR EARNINGS & PROFILE SETTINGS
                item(key = "points_wallet_card") {
                    PointsWalletSettingsCard(
                        currentUserId = currentUserId,
                        userProfile = userProfile,
                        isAdmin = staffCapabilities.isAdmin,
                        activeSubscriptions = mySubscriptions.filter { it.isCurrentlyActive() },
                        recentTransactions = myTransactions.take(6),
                        appOwnerRevenueCents = allTransactionsForAdmin.sumOf { it.platformShareCents },
                        isProcessingWallet = isProcessingWallet,
                        playBillingManager = playBillingManager,
                        onBuyPoints = onBuyPoints
                    )
                }

                item(key = "private_message_privacy_card") {
                    PrivateMessageSettingsCard(
                        currentPolicy = userProfile?.dmPolicyEnum() ?: DmPolicy.OPEN,
                        paidMembers = paidDmMembersForMe,
                        onSelectPolicy = onSetDmPolicy,
                        onKickMember = onKickPaidDmMember
                    )
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
                                        "Born ${userProfile?.birthYear} · Eligible to lock & unlock 18+ photos"
                                    } else {
                                        "Verify 18+ via your Google Account or Play Age Signals to lock or view locked photos"
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

                // RESTRICTIONS, HARDWARE READINESS, PREFERENCES & SIGN OUT
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

                            Button(
                                onClick = {
                                    onSaveProfile(
                                        displayName,
                                        handle,
                                        bio,
                                        avatarColorHex,
                                        pushToTalkPreferred,
                                        nightModeEnabled,
                                        invisibleModePreferred
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
                            contentDescription = "Sign out",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign Out")
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
private fun PointsWalletSettingsCard(
    currentUserId: String,
    userProfile: UserProfile?,
    isAdmin: Boolean,
    activeSubscriptions: List<CreatorSubscription>,
    recentTransactions: List<PointTransaction>,
    appOwnerRevenueCents: Long,
    isProcessingWallet: Boolean,
    playBillingManager: com.example.billing.PlayBillingManager? = null,
    onBuyPoints: (Long) -> Unit
) {
    val pointsBalance = userProfile?.pointsBalance ?: 0L
    val creatorEarningsCents = userProfile?.creatorEarningsCents ?: 0L
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
                            text = "$pointsBalance pts",
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
                            text = "Creator Earnings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatUsdCents(creatorEarningsCents),
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
                                text = "App Owner Revenue Share",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ModAmber
                            )
                            Text(
                                text = "\$1 photo unlock · \$5 Discovery boost · \$5 paid DM · \$7.50 sub",
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
                text = "Buy Points via Google Play Store (2 pts = \$2 · 5 pts = \$5). Locked photos cost 2 pts (\$1 owner / \$1 app), Discovery Boost costs 5 pts (\$5), and locked Paid DMs cost \$10 (\$5 user / \$5 app).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                    text = "Recent Points & Earnings",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
                recentTransactions.forEach { tx ->
                    val isCreatorCredit = tx.creatorId == currentUserId
                    val summary = when (tx.typeEnum()) {
                        PointTransactionType.BUY_POINTS ->
                            "Bought +${tx.pointsDelta} pts (${formatUsdCents(tx.grossCents)})"
                        PointTransactionType.UNLOCK_PHOTO ->
                            if (isCreatorCredit) {
                                "${tx.buyerName} unlocked your photo (+${formatUsdCents(tx.creatorShareCents)} to you · \$1 app)"
                            } else {
                                "Unlocked ${tx.creatorName}'s photo (-2 pts · \$1 owner / \$1 app)"
                            }
                        PointTransactionType.SUBSCRIBE_CREATOR ->
                            if (isCreatorCredit) {
                                "${tx.buyerName} subscribed \$15/mo (+${formatUsdCents(tx.creatorShareCents)} to you)"
                            } else {
                                "Subscribed to ${tx.creatorName} (\$15.00/mo)"
                            }
                        PointTransactionType.BOOST_POST ->
                            "Boosted post to top of Discovery (-5 pts / \$5.00)"
                        PointTransactionType.UNLOCK_DM ->
                            if (isCreatorCredit) {
                                "${tx.buyerName} unlocked Private Messages (+${formatUsdCents(tx.creatorShareCents)} to you · \$5 app)"
                            } else {
                                "Unlocked Private Messages with ${tx.creatorName} (\$10.00 · \$5 user / \$5 app)"
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
                            tint = if (isCreatorCredit || tx.typeEnum() == PointTransactionType.BUY_POINTS) {
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
                text = "Control who can send you private messages. Lock DMs to paid users (\$10 to message you — \$5 to you, \$5 to app owner) and kick any paid member anytime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DmPolicyOptionRow(
                title = "Allow Private Messages (Everyone)",
                subtitle = "Anyone on VoxStage can start a private chat with you",
                icon = Icons.Rounded.ChatBubbleOutline,
                selected = currentPolicy == DmPolicy.OPEN,
                accentColor = MaterialTheme.colorScheme.primary,
                onClick = { onSelectPolicy(DmPolicy.OPEN) },
                testTag = "dm_policy_open"
            )

            DmPolicyOptionRow(
                title = "Lock to Paid Users (\$10 to DM)",
                subtitle = "Users pay \$10 to message you (\$5 to you · \$5 to app owner). You can kick any paid member.",
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
                        text = "No paid DM members yet. When someone pays \$10 to message you, they will appear here and you can kick them anytime.",
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
                                            "Paid \$10 (\$5 earned by you)"
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
