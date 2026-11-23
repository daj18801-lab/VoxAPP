package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DynamicFeed
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.CreatorSubscription
import com.example.data.DirectoryEntry
import com.example.data.DmPolicy
import com.example.data.Friendship
import com.example.data.PostComment
import com.example.data.ProfilePost
import com.example.data.StaffCapabilities
import com.example.data.StaffTier
import com.example.data.UiState
import com.example.ui.components.FullScreenPhotoDialog
import com.example.ui.components.PostCommentsDialog
import com.example.ui.components.ProfilePostCard
import com.example.ui.components.SharePostDialog
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopSnackbarOverlay
import com.example.ui.components.showTopMessage
import com.example.ui.components.usernameColor
import com.example.ui.theme.ModAmber
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
    isProcessingWallet: Boolean,
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
    onOpenBuyPoints: () -> Unit
) {
    BackHandler(onBack = onBack)

    val entryState by viewModel.directoryEntryState.collectAsStateWithLifecycle()
    val postsState by viewModel.postsState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFullAvatar by remember { mutableStateOf(false) }
    var showBuyPointsDialog by remember { mutableStateOf(false) }
    var buyPointsNeededCount by remember { mutableIntStateOf(2) }
    var activeCommentsPost by remember { mutableStateOf<ProfilePost?>(null) }
    var activeSharePost by remember { mutableStateOf<ProfilePost?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showTopMessage(msg)
        }
    }

    val entry = (entryState as? UiState.Success)?.data
    val posts = (postsState as? UiState.Success)?.data.orEmpty()
    val isSelf = viewModel.currentUserId == viewModel.targetUserId
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
                                color = usernameColor(tier),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry != null) {
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
                                            modifier = Modifier
                                                .clickable(enabled = user.photoBase64.isNotBlank()) {
                                                    showFullAvatar = true
                                                }
                                                .testTag("user_profile_avatar")
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = user.displayName,
                                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                color = usernameColor(tier)
                                            )
                                            StaffTierBadge(tier = tier)
                                        }

                                        Text(
                                            text = "@${user.handle}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        if (user.bio.isNotBlank()) {
                                            Text(
                                                text = user.bio,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Stats Pill
                                        Surface(
                                            shape = RoundedCornerShape(999.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DynamicFeed,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Text(
                                                    text = "${posts.size} ${if (posts.size == 1) "Post" else "Posts"}",
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        // Action Buttons (for other users)
                                        if (!isSelf) {
                                            Spacer(modifier = Modifier.height(2.dp))
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
                                                            DmPolicy.PAID -> "DM (\$10)"
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

                                            // $15/mo Creator Subscription CTA (unlocks all locked photos)
                                            if (isSubscribed) {
                                                Surface(
                                                    shape = RoundedCornerShape(999.dp),
                                                    color = SafetyEmerald.copy(alpha = 0.14f),
                                                    border = BorderStroke(1.dp, SafetyEmerald.copy(alpha = 0.45f)),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .testTag("user_profile_subscribed_badge")
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                        horizontalArrangement = Arrangement.Center,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.CardMembership,
                                                            contentDescription = null,
                                                            tint = SafetyEmerald,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = "Subscribed · All Locked Photos Unlocked (${activeSubscription?.daysRemaining() ?: 30}d left)",
                                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                            color = SafetyEmerald
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = { onSubscribeCreator(user.userId, user.displayName) },
                                                    enabled = !isProcessingWallet,
                                                    shape = RoundedCornerShape(999.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = MaterialTheme.colorScheme.secondary,
                                                        contentColor = Color.White
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .testTag("user_profile_subscribe_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.CardMembership,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Subscribe \$15/mo · Unlock All Locked Photos",
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

                item(key = "posts_section_header") {
                    Text(
                        text = "Posts (${posts.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                        if (posts.isEmpty()) {
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
                                    subscribedCreatorIds = subscribedCreatorIds
                                )
                                ProfilePostCard(
                                    post = post,
                                    currentUserId = viewModel.currentUserId,
                                    canDelete = post.authorId == viewModel.currentUserId || staffCapabilities.isStaff,
                                    latestAuthorPhotoBase64 = entry?.photoBase64,
                                    isUnlocked = unlocked,
                                    isProcessingUnlock = isProcessingWallet,
                                    onAuthorClick = null,
                                    onOriginalAuthorClick = onOpenUserProfile,
                                    onUnlockPost = {
                                        onUnlockPost(post) {
                                            buyPointsNeededCount = 2
                                            showBuyPointsDialog = true
                                        }
                                    },
                                    onSubscribeCreator = {
                                        onSubscribeCreator(post.authorId, post.authorName)
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
                                    onSharePost = if (!post.locked) {
                                        { activeSharePost = post }
                                    } else {
                                        null
                                    },
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
                    if (buyPointsNeededCount >= 5) {
                        "You currently have $myPointsBalance pts. Boosting a post to the top of Discovery costs 5 points (\$5) for 24 hours. Buy points in Profile Settings."
                    } else {
                        "You currently have $myPointsBalance pts. Unlocking a locked photo costs 2 points (\$2 — \$1 to the photo owner, \$1 to the app owner). You can buy points in Profile Settings or subscribe for \$15/month to unlock all photos."
                    }
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
}
