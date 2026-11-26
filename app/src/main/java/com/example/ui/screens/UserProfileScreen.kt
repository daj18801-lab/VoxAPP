package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SupportAgent
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CreatorSubscription
import com.example.data.DirectoryEntry
import com.example.data.DmPolicy
import com.example.data.Friendship
import com.example.data.PostComment
import com.example.data.PresenceStatus
import com.example.data.ProfilePost
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.PostCommentsDialog
import com.example.ui.components.PresenceStatusDot
import com.example.ui.components.ProfilePostCard
import com.example.ui.components.SharePostDialog
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.rememberBase64ImageBitmap
import com.example.ui.components.rememberBlurredBase64ImageBitmap
import com.example.ui.components.rememberPresenceClockMillis
import com.example.ui.components.showTopMessage
import com.example.ui.components.ProMemberBadge
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.PresenceOnlineGreen
import com.example.ui.theme.SafetyEmerald
import com.example.viewmodel.UserProfileViewModel
import kotlinx.coroutines.flow.Flow

@Composable
fun UserProfileScreen(
    viewModel: UserProfileViewModel,
    staffCapabilities: StaffCapabilities,
    friendship: Friendship?,
    myPointsBalance: Long,
    activeSubscription: CreatorSubscription?,
    viewerIsAdultVerified: Boolean,
    isProcessingWallet: Boolean,
    onRequestAgeVerification: () -> Unit,
    onBack: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onMessageUser: (userId: String, name: String, colorHex: String, tier: StaffTier) -> Unit,
    onSendFriendRequest: (DirectoryEntry) -> Unit,
    onAcceptFriendRequest: (Friendship) -> Unit,
    onRemoveOrDeclineFriendship: (Friendship) -> Unit,
    onUnlockPost: (ProfilePost, onInsufficientPoints: () -> Unit) -> Unit,
    onBoostPost: (ProfilePost, onInsufficientPoints: () -> Unit) -> Unit,
    onObservePostComments: (String) -> Flow<List<PostComment>>,
    onAddPostComment: (ProfilePost, String, () -> Unit) -> Unit,
    onDeletePostComment: (String, String) -> Unit,
    onSharePostToFeed: (ProfilePost, String, () -> Unit) -> Unit,
    onExternalSharePost: (ProfilePost) -> Unit,
    onSubscribeCreator: (creatorId: String, creatorName: String) -> Unit,
    onOpenBuyPoints: () -> Unit,
    supportStaffMembers: List<DirectoryEntry> = emptyList(),
    onAdminSendToSupportChat: (
        targetUser: DirectoryEntry,
        supportMember: DirectoryEntry,
        reason: String,
        onSent: () -> Unit
    ) -> Unit = { _, _, _, _ -> },
    viewerProfile: com.example.data.UserProfile? = null
) {
    BackHandler(onBack = onBack)

    LaunchedEffect(viewerProfile?.userId, viewModel.targetUserId) {
        viewModel.recordProfileViewIfNeeded(viewerProfile, staffCapabilities.tier)
    }

    val entryState by viewModel.directoryEntryState.collectAsStateWithLifecycle()
    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val followers by viewModel.followersState.collectAsStateWithLifecycle()
    val following by viewModel.followingState.collectAsStateWithLifecycle()
    val isUpdatingFollow by viewModel.isUpdatingFollow.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedProfileTab by remember { mutableIntStateOf(0) } // 0 = Posts, 1 = Photos
    var showFullAvatar by remember { mutableStateOf(false) }
    var fullScreenPhotoBase64 by remember { mutableStateOf<String?>(null) }
    var showBuyPointsDialog by remember { mutableStateOf(false) }
    var buyPointsNeededCount by remember { mutableIntStateOf(2) }
    var activeCommentsPost by remember { mutableStateOf<ProfilePost?>(null) }
    var activeSharePost by remember { mutableStateOf<ProfilePost?>(null) }
    var showSendToSupportDialog by remember { mutableStateOf(false) }
    val nowMillis = rememberPresenceClockMillis()

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    val entry = (entryState as? UiState.Success)?.data
    val posts = (postsState as? UiState.Success)?.data.orEmpty()
    val photoPosts = remember(posts) { posts.filter { it.imageBase64.isNotBlank() } }
    val isSelf = viewModel.currentUserId == viewModel.targetUserId
    val isFollowing = remember(followers, viewModel.currentUserId) {
        followers.any { it.followerId == viewModel.currentUserId }
    }
    val tier = entry?.tierEnum() ?: StaffTier.USER
    val isSubscribed = activeSubscription?.isCurrentlyActive() == true
    val subscribedCreatorIds = remember(isSubscribed, viewModel.targetUserId) {
        if (isSubscribed) setOf(viewModel.targetUserId) else emptySet()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("user_profile_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry?.displayName?.ifBlank { "User Profile" } ?: "User Profile",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = usernameColor(tier, isPro = entry?.isProActive() == true, userId = entry?.userId),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry != null && tier != StaffTier.ADMIN) {
                                Text(
                                    text = "@${entry.handle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                // Profile Hero Header
                item(key = "user_profile_hero") {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (entryState) {
                            is UiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                }
                            }

                            is UiState.Error -> {
                                Text(
                                    text = (entryState as UiState.Error).message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(20.dp)
                                )
                            }

                            is UiState.Success -> {
                                val user = entry
                                if (user == null) {
                                    Text(
                                        text = "This profile is not available yet.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(20.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        SpeakingAvatarCircle(
                                            displayName = user.displayName,
                                            colorHex = user.avatarColorHex,
                                            speakingLevel = 0f,
                                            micActive = false,
                                            size = 92.dp,
                                            photoBase64 = user.photoBase64,
                                            userId = user.userId,
                                            spotlightColorHex = user.activeSpotlightColorHex(),
                                            modifier = Modifier
                                                .clickable(enabled = user.photoBase64.isNotBlank()) {
                                                    showFullAvatar = true
                                                }
                                                .testTag("user_profile_avatar")
                                        )

                                        val displayedStatus = user.displayedStatusFor(
                                            viewerIsStaff = staffCapabilities.isStaff,
                                            nowMillis = nowMillis
                                        )
                                        val isHidingTrueOnline = staffCapabilities.isStaff &&
                                            user.isHidingTrueOnlineStatus(nowMillis)

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            PresenceStatusDot(
                                                status = displayedStatus,
                                                size = 11.dp,
                                                modifier = Modifier.testTag("user_profile_presence_dot")
                                            )
                                            Text(
                                                text = user.displayName,
                                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = usernameColor(tier, isPro = user.isProActive(), userId = user.userId)
                                            )
                                            StaffTierBadge(tier = tier)
                                            if (user.isProActive()) {
                                                ProMemberBadge()
                                            }
                                        }

                                        if (isHidingTrueOnline) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = PresenceOnlineGreen.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, PresenceOnlineGreen.copy(alpha = 0.45f)),
                                                modifier = Modifier.testTag("user_profile_staff_true_status_badge")
                                            ) {
                                                Text(
                                                    text = "True Status: Online (Appearing ${user.appearanceStatusEnum().label})",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = PresenceOnlineGreen,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = displayedStatus.label,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = when (displayedStatus) {
                                                    PresenceStatus.ONLINE -> PresenceOnlineGreen
                                                    PresenceStatus.IDLE -> ModAmber
                                                    PresenceStatus.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
                                                },
                                                modifier = Modifier.testTag("user_profile_status_label")
                                            )
                                        }

                                        if (tier != StaffTier.ADMIN) {
                                            Text(
                                                text = "@${user.handle}",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        // Dedicated Bio Section
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("user_profile_bio_section")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "Bio",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = user.bio.ifBlank { "No bio added yet." },
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = if (user.bio.isBlank()) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    },
                                                    modifier = Modifier.testTag("user_profile_bio_text")
                                                )
                                            }
                                        }

                                        // Followers & Following Counts Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(14.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("user_profile_followers_count")
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "${followers.size}",
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
                                                    .testTag("user_profile_following_count")
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(
                                                        text = "${following.size}",
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
                                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("user_profile_time_spent")
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Schedule,
                                                            contentDescription = "Time on app",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Text(
                                                            text = user.formattedTimeSpent(),
                                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    Text(
                                                        text = "App Time",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        // Quick Stats & Pictures Button Row
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = if (selectedProfileTab == 0) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                                },
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable { selectedProfileTab = 0 }
                                                    .testTag("user_profile_posts_pill")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.DynamicFeed,
                                                        contentDescription = null,
                                                        tint = if (selectedProfileTab == 0) Color.White else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "${posts.size} ${if (posts.size == 1) "Post" else "Posts"}",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (selectedProfileTab == 0) Color.White else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            // Prominent Pictures Button -> Opens Photos Tab
                                            Surface(
                                                shape = RoundedCornerShape(999.dp),
                                                color = if (selectedProfileTab == 1) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                },
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable { selectedProfileTab = 1 }
                                                    .testTag("user_profile_pictures_button")
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PhotoLibrary,
                                                        contentDescription = "View all photos",
                                                        tint = if (selectedProfileTab == 1) Color.White else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "Pictures (${photoPosts.size})",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = if (selectedProfileTab == 1) Color.White else MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }

                                        // Action Buttons (for other users)
                                        if (!isSelf) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            // Follow / Following Toggle Button
                                            if (isFollowing) {
                                                OutlinedButton(
                                                    onClick = { viewModel.toggleFollow(currentlyFollowing = true) },
                                                    enabled = !isUpdatingFollow,
                                                    shape = RoundedCornerShape(999.dp),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("user_profile_follow_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Following")
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.toggleFollow(currentlyFollowing = false) },
                                                    enabled = !isUpdatingFollow,
                                                    shape = RoundedCornerShape(999.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.primary,
                                                        contentColor = Color.White
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("user_profile_follow_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PersonAdd,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Follow")
                                                }
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                val dmPolicy = user.dmPolicyEnum()
                                                Button(
                                                    onClick = {
                                                        onMessageUser(
                                                            user.userId,
                                                            user.displayName,
                                                            user.avatarColorHex,
                                                            tier
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(999.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = when (dmPolicy) {
                                                            DmPolicy.PAID -> ModAmber
                                                            DmPolicy.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
                                                            DmPolicy.OPEN -> MaterialTheme.colorScheme.primary
                                                        },
                                                        contentColor = when (dmPolicy) {
                                                            DmPolicy.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            else -> Color.White
                                                        }
                                                    ),
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(48.dp)
                                                        .testTag("user_profile_message_button")
                                                ) {
                                                    Icon(
                                                        imageVector = when (dmPolicy) {
                                                            DmPolicy.PAID -> Icons.Rounded.Lock
                                                            DmPolicy.DISABLED -> Icons.Rounded.Block
                                                            DmPolicy.OPEN -> Icons.Rounded.ChatBubble
                                                        },
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        when (dmPolicy) {
                                                            DmPolicy.PAID -> "DM (\$5)"
                                                            DmPolicy.DISABLED -> "DMs Off"
                                                            DmPolicy.OPEN -> "Message"
                                                        }
                                                    )
                                                }

                                                when {
                                                    friendship == null -> {
                                                        OutlinedButton(
                                                            onClick = { onSendFriendRequest(user) },
                                                            shape = RoundedCornerShape(999.dp),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(48.dp)
                                                                .testTag("user_profile_add_friend_button")
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.PersonAdd,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Add Friend")
                                                        }
                                                    }

                                                    friendship.isAccepted() -> {
                                                        OutlinedButton(
                                                            onClick = { onRemoveOrDeclineFriendship(friendship) },
                                                            shape = RoundedCornerShape(999.dp),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(48.dp)
                                                                .testTag("user_profile_friends_button")
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Friends")
                                                        }
                                                    }

                                                    friendship.isIncomingFor(viewModel.currentUserId) -> {
                                                        Button(
                                                            onClick = { onAcceptFriendRequest(friendship) },
                                                            shape = RoundedCornerShape(999.dp),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(48.dp)
                                                                .testTag("user_profile_accept_friend_button")
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.Check,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Accept")
                                                        }
                                                    }

                                                    else -> {
                                                        OutlinedButton(
                                                            onClick = { onRemoveOrDeclineFriendship(friendship) },
                                                            shape = RoundedCornerShape(999.dp),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(48.dp)
                                                                .testTag("user_profile_cancel_request_button")
                                                        ) {
                                                            Icon(
                                                                Icons.Rounded.HourglassTop,
                                                                contentDescription = null,
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text("Requested")
                                                        }
                                                    }
                                                }
                                            }

                                            // Admin Action: Send User to a Support Member's Private Message
                                            if (staffCapabilities.isAdmin) {
                                                OutlinedButton(
                                                    onClick = { showSendToSupportDialog = true },
                                                    shape = RoundedCornerShape(999.dp),
                                                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("admin_send_to_support_chat_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.SupportAgent,
                                                        contentDescription = "Send user to Support chat",
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Send User to Support Chat",
                                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
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

                // Segmented Posts / Photos Tabs
                item(key = "profile_content_tabs") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedProfileTab == 0,
                            onClick = { selectedProfileTab = 0 },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.DynamicFeed,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Posts (${posts.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier.testTag("user_profile_tab_posts")
                        )
                        FilterChip(
                            selected = selectedProfileTab == 1,
                            onClick = { selectedProfileTab = 1 },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(17.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = "Photos (${photoPosts.size})",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            shape = RoundedCornerShape(999.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            modifier = Modifier.testTag("user_profile_tab_photos")
                        )
                    }
                }

                when (postsState) {
                    is UiState.Loading -> {
                        item(key = "posts_loading") {
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
                        item(key = "posts_error") {
                            Text(
                                text = (postsState as UiState.Error).message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    is UiState.Success -> {
                        if (selectedProfileTab == 1) {
                            // PHOTOS GALLERY TAB (2-column grid with blurred locked photos + pay to unlock)
                            if (photoPosts.isEmpty()) {
                                item(key = "photos_empty") {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
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
                                                imageVector = Icons.Rounded.PhotoLibrary,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(34.dp)
                                            )
                                            Text(
                                                text = "No photos yet",
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                            )
                                            Text(
                                                text = "Photos posted by ${entry?.displayName ?: "this user"} will appear in this gallery.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            } else {
                                val photoRows = photoPosts.chunked(2)
                                items(photoRows, key = { row -> "photo_row_${row.first().id}" }) { rowPosts ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowPosts.forEach { photoPost ->
                                            UserProfilePhotoGridTile(
                                                post = photoPost,
                                                onClick = {
                                                    fullScreenPhotoBase64 = photoPost.imageBase64
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowPosts.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else if (posts.isEmpty()) {
                            item(key = "posts_empty") {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
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
                                            text = stringResource(R.string.empty_other_posts_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(posts, key = { it.id }) { post ->
                                val unlocked = post.isUnlockedFor(
                                    uid = viewModel.currentUserId,
                                    subscribedCreatorIds = subscribedCreatorIds,
                                    viewerIsAdultVerified = viewerIsAdultVerified
                                )
                                ProfilePostCard(
                                    post = post,
                                    currentUserId = viewModel.currentUserId,
                                    canDelete = post.authorId == viewModel.currentUserId || staffCapabilities.isStaff,
                                    latestAuthorPhotoBase64 = entry?.photoBase64,
                                    isUnlocked = unlocked,
                                    viewerIsAdultVerified = viewerIsAdultVerified,
                                    isProcessingUnlock = isProcessingWallet,
                                    onAuthorClick = null,
                                    onOriginalAuthorClick = onOpenUserProfile,
                                    onRequestAgeVerification = onRequestAgeVerification,
                                    onUnlockPost = {
                                        if (!viewerIsAdultVerified) {
                                            onRequestAgeVerification()
                                        } else {
                                            onUnlockPost(post) {
                                                buyPointsNeededCount = 2
                                                showBuyPointsDialog = true
                                            }
                                        }
                                    },
                                    onSubscribeCreator = {
                                        if (!viewerIsAdultVerified) {
                                            onRequestAgeVerification()
                                        } else {
                                            onSubscribeCreator(post.authorId, post.authorName)
                                        }
                                    },
                                    onBoostPost = if (post.authorId == viewModel.currentUserId) {
                                        {
                                            onBoostPost(post) {
                                                buyPointsNeededCount = 5
                                                showBuyPointsDialog = true
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    onOpenComments = { activeCommentsPost = post },
                                    onSharePost = { activeSharePost = post },
                                    isStaffFreePerks = staffCapabilities.isStaff,
                                    onToggleLike = { viewModel.toggleLikePost(post) },
                                    onDelete = { viewModel.deletePost(post) }
                                )
                            }
                        }
                    }
                }

                item(key = "user_profile_bottom_spacer") {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }
        }

        TopSnackbarOverlay(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }

    if (showFullAvatar && !entry?.photoBase64.isNullOrBlank()) {
        FullScreenPhotoDialog(
            photoBase64 = entry?.photoBase64.orEmpty(),
            contentDescription = "${entry?.displayName.orEmpty()} profile picture",
            onDismiss = { showFullAvatar = false }
        )
    }

    if (showBuyPointsDialog) {
        AlertDialog(
            onDismissRequest = { showBuyPointsDialog = false },
            title = { Text("Need More Points") },
            text = {
                Text(
                    "You currently have $myPointsBalance pts. Boosting a post to the top of Discovery costs 5 points (\$5) for 24 hours. Buy points in Profile Settings."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBuyPointsDialog = false
                        onOpenBuyPoints()
                    },
                    modifier = Modifier.testTag("dialog_go_buy_points")
                ) {
                    Text("Buy Points")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuyPointsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    activeCommentsPost?.let { post ->
        val commentsFlow = remember(post.id) { onObservePostComments(post.id) }
        PostCommentsDialog(
            post = post,
            commentsFlow = commentsFlow,
            currentUserId = viewModel.currentUserId,
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

    fullScreenPhotoBase64?.let { photo ->
        FullScreenPhotoDialog(
            photoBase64 = photo,
            contentDescription = "Photo by ${entry?.displayName ?: "user"}",
            onDismiss = { fullScreenPhotoBase64 = null }
        )
    }

    if (showSendToSupportDialog && entry != null && staffCapabilities.isAdmin && !isSelf) {
        val eligibleSupportMembers = remember(supportStaffMembers, entry.userId) {
            supportStaffMembers
                .filter { it.userId != entry.userId && it.tierEnum().isStaff() }
                .sortedWith(
                    compareByDescending<DirectoryEntry> { it.tierEnum() == StaffTier.SUPPORT }
                        .thenByDescending { it.isCurrentlyOnline(nowMillis) }
                        .thenBy { it.displayName.lowercase() }
                )
        }
        var selectedSupportId by remember(eligibleSupportMembers) {
            mutableStateOf(eligibleSupportMembers.firstOrNull()?.userId.orEmpty())
        }
        var supportReason by remember { mutableStateOf("") }
        var isSendingSupportHandoff by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!isSendingSupportHandoff) showSendToSupportDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SupportAgent,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Send to Support Chat",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Connect ${entry.displayName} directly to a Support member's private chat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (eligibleSupportMembers.isEmpty()) {
                        Text(
                            text = "No Support or Admin members are available right now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(eligibleSupportMembers, key = { "support_pick_${it.userId}" }) { staff ->
                                val isSelected = selectedSupportId == staff.userId
                                val staffOnline = staff.isCurrentlyOnline(nowMillis)
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { selectedSupportId = staff.userId }
                                        .testTag("admin_pick_support_${staff.userId}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PresenceStatusDot(
                                            status = if (staffOnline) PresenceStatus.ONLINE else PresenceStatus.OFFLINE
                                        )
                                        Text(
                                            text = staff.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = usernameColor(staff.tierEnum(), isPro = staff.isProActive(), userId = staff.userId),
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        StaffTierBadge(tier = staff.tierEnum())
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = supportReason,
                        onValueChange = { supportReason = it.take(180) },
                        label = { Text("Note / Topic (optional)") },
                        placeholder = { Text("e.g., Account help or moderation review") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_support_handoff_reason_input")
                    )
                }
            },
            confirmButton = {
                val chosenSupport = eligibleSupportMembers.firstOrNull { it.userId == selectedSupportId }
                Button(
                    onClick = {
                        if (chosenSupport != null) {
                            isSendingSupportHandoff = true
                            onAdminSendToSupportChat(entry, chosenSupport, supportReason) {
                                isSendingSupportHandoff = false
                                showSendToSupportDialog = false
                            }
                        }
                    },
                    enabled = chosenSupport != null && !isSendingSupportHandoff,
                    modifier = Modifier.testTag("admin_confirm_send_to_support_button")
                ) {
                    Text(if (isSendingSupportHandoff) "Sending…" else "Send Now")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSendToSupportDialog = false },
                    enabled = !isSendingSupportHandoff
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun UserProfilePhotoGridTile(
    post: ProfilePost,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sharpBitmap = rememberBase64ImageBitmap(post.imageBase64)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("user_profile_photo_tile_${post.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (sharpBitmap != null) {
                Image(
                    bitmap = sharpBitmap,
                    contentDescription = "Photo by ${post.authorName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (post.likedByUserIds.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.56f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = LiveCoral,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${post.likedByUserIds.size}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
