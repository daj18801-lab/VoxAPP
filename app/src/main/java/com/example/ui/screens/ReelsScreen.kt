package com.example.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.Base64
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Reel
import com.example.data.ReelChunk
import com.example.data.ReelComment
import com.example.data.StaffCapabilities
import com.example.data.UiState
import com.example.data.UserProfile
import com.example.media.PreparedReelVideo
import com.example.media.ReelVideoHelper
import com.example.ui.components.LocalProUserIds
import com.example.ui.components.ProMemberBadge
import com.example.ui.components.SpeakingAvatarCircle
import com.example.ui.components.StaffTierBadge
import com.example.ui.components.TopBarSafetyShieldButton
import com.example.ui.components.formatTimestampShort
import com.example.ui.components.usernameColor
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SnapBlue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsScreen(
    reelsState: UiState<List<Reel>>,
    currentUserId: String,
    myProfile: UserProfile,
    staffCapabilities: StaffCapabilities,
    isUploadingReel: Boolean,
    onUploadReel: (PreparedReelVideo, String, Boolean, () -> Unit) -> Unit,
    onLoadReelChunks: suspend (String) -> Result<List<ReelChunk>>,
    onToggleLikeReel: (Reel) -> Unit,
    onBoostReel: (Reel) -> Unit,
    onDeleteReel: (Reel) -> Unit,
    onObserveReelComments: (String) -> Flow<List<ReelComment>>,
    onAddReelComment: (String, String, () -> Unit) -> Unit,
    onDeleteReelComment: (String, String) -> Unit,
    onIncrementReelShareCount: (String) -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenUpgradeToPro: () -> Unit,
    onShowMessage: (String) -> Unit,
    safetyBadgeCount: Int = 0,
    onOpenSafety: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isProOrStaff = myProfile.isProActive(staffCapabilities.tier)

    var showProRequiredDialog by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var pendingPreparedVideo by remember { mutableStateOf<PreparedReelVideo?>(null) }
    var isPreparingVideo by remember { mutableStateOf(false) }
    var commentsReel by remember { mutableStateOf<Reel?>(null) }
    var confirmBoostReel by remember { mutableStateOf<Reel?>(null) }
    var confirmDeleteReel by remember { mutableStateOf<Reel?>(null) }
    var isMuted by remember { mutableStateOf(false) }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isPreparingVideo = true
            val prepared = ReelVideoHelper.prepareVideoForUpload(context, uri)
            isPreparingVideo = false
            prepared.onSuccess { video ->
                pendingPreparedVideo = video
                showUploadDialog = true
            }.onFailure { err ->
                onShowMessage(err.message ?: "Could not process selected video.")
            }
        }
    }

    val openUploadFlow = {
        if (!isProOrStaff) {
            showProRequiredDialog = true
        } else {
            videoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (reelsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = SnapBlue)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Loading Reels...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = reelsState.message,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            }

            is UiState.Success -> {
                val reels = reelsState.data
                if (reels.isEmpty()) {
                    EmptyReelsPlaceholder(
                        isProOrStaff = isProOrStaff,
                        onUploadClick = openUploadFlow,
                        onUpgradeClick = onOpenUpgradeToPro
                    )
                } else {
                    val pagerState = rememberPagerState(pageCount = { reels.size })
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("reels_vertical_pager"),
                        key = { index -> reels.getOrNull(index)?.id ?: index }
                    ) { page ->
                        val reel = reels[page]
                        val isCurrentPage = pagerState.currentPage == page
                        ReelVideoPage(
                            reel = reel,
                            isCurrentPage = isCurrentPage,
                            isMuted = isMuted,
                            currentUserId = currentUserId,
                            staffCapabilities = staffCapabilities,
                            onToggleMute = { isMuted = !isMuted },
                            onLoadReelChunks = onLoadReelChunks,
                            onLikeClick = { onToggleLikeReel(reel) },
                            onCommentClick = { commentsReel = reel },
                            onShareClick = {
                                onIncrementReelShareCount(reel.id)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Watch ${reel.authorName}'s Reel on VOX: ${reel.caption.ifBlank { "Check out this ${reel.formattedDuration()} Reel!" }}"
                                    )
                                }
                                runCatching {
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Reel"))
                                }
                            },
                            onBoostClick = { confirmBoostReel = reel },
                            onDeleteClick = { confirmDeleteReel = reel },
                            onAuthorClick = { onOpenUserProfile(reel.authorId) }
                        )
                    }
                }
            }
        }

        // Floating Top Bar overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.VideoLibrary,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Reels",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Swipe up · Max 30s · Pro uploads",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.78f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = openUploadFlow,
                    enabled = !isPreparingVideo && !isUploadingReel,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isProOrStaff) SnapBlue else ModAmber,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("upload_reel_button")
                ) {
                    if (isPreparingVideo || isUploadingReel) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isUploadingReel) "Uploading…" else "Preparing…")
                    } else {
                        Icon(
                            imageVector = if (isProOrStaff) Icons.Rounded.Add else Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isProOrStaff) "Upload Reel" else "Pro Upload")
                    }
                }
                TopBarSafetyShieldButton(
                    badgeCount = safetyBadgeCount,
                    onDarkBackground = true,
                    onClick = onOpenSafety
                )
            }
        }
    }

    // Pro Required Gate Dialog for Free Users
    if (showProRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showProRequiredDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = SnapBlue,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Pro Membership Required") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Only VOX Pro members can upload Reels (up to 30 seconds each).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Upgrade to VOX Pro for \$9.99/month to unlock:\n• Upload Reels up to 30 seconds\n• 1-minute Push-to-Talk clips (vs 5s Free)\n• Blue Pro username color\n• Unlimited Discovery photo posts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showProRequiredDialog = false
                        onOpenUpgradeToPro()
                    },
                    modifier = Modifier.testTag("reels_pro_upgrade_button")
                ) {
                    Text("Upgrade to Pro (\$9.99/mo)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProRequiredDialog = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    // Upload Reel Confirmation Dialog
    val prepared = pendingPreparedVideo
    if (showUploadDialog && prepared != null) {
        UploadReelDialog(
            preparedVideo = prepared,
            myPointsBalance = myProfile.pointsBalance,
            isUploading = isUploadingReel,
            onDismiss = {
                if (!isUploadingReel) {
                    showUploadDialog = false
                    pendingPreparedVideo = null
                }
            },
            onPublish = { caption, boostToTop ->
                onUploadReel(prepared, caption, boostToTop) {
                    showUploadDialog = false
                    pendingPreparedVideo = null
                }
            }
        )
    }

    // Confirm $5 Boost Dialog
    val boostTarget = confirmBoostReel
    if (boostTarget != null) {
        AlertDialog(
            onDismissRequest = { confirmBoostReel = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Bolt,
                    contentDescription = null,
                    tint = ModAmber,
                    modifier = Modifier.size(30.dp)
                )
            },
            title = { Text("Push Reel to Top (\$5)") },
            text = {
                Text(
                    "Spend 5 points (\$5) to pin your Reel to the very top of the Reels feed for 24 hours?\n\nYour current balance: ${myProfile.pointsBalance} pts."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = boostTarget
                        confirmBoostReel = null
                        onBoostReel(target)
                    },
                    modifier = Modifier.testTag("confirm_boost_reel_button")
                ) {
                    Text("Boost Now (5 pts)")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmBoostReel = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Delete Dialog
    val deleteTarget = confirmDeleteReel
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteReel = null },
            title = { Text("Delete Reel?") },
            text = { Text("This will permanently remove this Reel from the Reels feed.") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = deleteTarget
                        confirmDeleteReel = null
                        onDeleteReel(target)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveCoral)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteReel = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Comments Bottom Sheet
    val activeCommentsReel = commentsReel
    if (activeCommentsReel != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { commentsReel = null },
            sheetState = sheetState
        ) {
            ReelCommentsSheetContent(
                reel = activeCommentsReel,
                currentUserId = currentUserId,
                staffCapabilities = staffCapabilities,
                commentsFlow = remember(activeCommentsReel.id) {
                    onObserveReelComments(activeCommentsReel.id)
                },
                onAddComment = { text, onSent ->
                    onAddReelComment(activeCommentsReel.id, text, onSent)
                },
                onDeleteComment = { commentId ->
                    onDeleteReelComment(activeCommentsReel.id, commentId)
                },
                onClose = { commentsReel = null }
            )
        }
    }
}

@Composable
private fun EmptyReelsPlaceholder(
    isProOrStaff: Boolean,
    onUploadClick: () -> Unit,
    onUpgradeClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A162E), Color(0xFF050A14))
                )
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SnapBlue.copy(alpha = 0.2f),
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = SnapBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Text(
                text = "No Reels Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Be the first to share a 30-second vertical Reel! Swipe up to watch, like, comment, share, or pay \$5 to boost your Reel to the top.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (isProOrStaff) {
                Button(
                    onClick = onUploadClick,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("empty_upload_reel_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload First Reel (Max 30s)")
                }
            } else {
                Button(
                    onClick = onUpgradeClick,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("empty_reels_upgrade_button")
                ) {
                    Icon(Icons.Rounded.WorkspacePremium, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upgrade to Pro (\$9.99/mo) to Upload")
                }
            }
        }
    }
}

@Composable
private fun ReelVideoPage(
    reel: Reel,
    isCurrentPage: Boolean,
    isMuted: Boolean,
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    onToggleMute: () -> Unit,
    onLoadReelChunks: suspend (String) -> Result<List<ReelChunk>>,
    onLikeClick: () -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit,
    onBoostClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onAuthorClick: () -> Unit
) {
    val context = LocalContext.current
    var cachedVideoFile by remember(reel.id) { mutableStateOf<File?>(null) }
    var loadError by remember(reel.id) { mutableStateOf<String?>(null) }
    var isLoadingVideo by remember(reel.id) { mutableStateOf(true) }
    var userPaused by remember(reel.id) { mutableStateOf(false) }

    val thumbnailBitmap = remember(reel.thumbnailBase64) {
        if (reel.thumbnailBase64.isBlank()) null
        else runCatching {
            val bytes = Base64.decode(reel.thumbnailBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    LaunchedEffect(reel.id) {
        isLoadingVideo = true
        loadError = null
        val result = ReelVideoHelper.getOrAssembleCachedVideoFile(
            context = context,
            reelId = reel.id,
            loadChunks = { onLoadReelChunks(reel.id) }
        )
        isLoadingVideo = false
        result.onSuccess { file ->
            cachedVideoFile = file
        }.onFailure { err ->
            loadError = err.message ?: "Unable to load video."
        }
    }

    // Reset pause state whenever user swipes away and comes back
    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) userPaused = false
    }

    val shouldPlay = isCurrentPage && !userPaused
    val likedByMe = reel.isLikedBy(currentUserId)
    val isMine = reel.authorId == currentUserId
    val canDelete = isMine || staffCapabilities.isStaff
    val isBoosted = reel.isCurrentlyBoosted()
    val proIds = LocalProUserIds.current
    val authorIsPro = reel.authorId in proIds

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                userPaused = !userPaused
            }
    ) {
        // Cover thumbnail underneath video while buffering
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap,
                contentDescription = "Reel preview thumbnail",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Smooth TextureView + MediaPlayer looping video playback
        val videoFile = cachedVideoFile
        if (videoFile != null && videoFile.exists()) {
            ReelTextureVideoPlayer(
                videoFile = videoFile,
                shouldPlay = shouldPlay,
                isMuted = isMuted,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading / error overlay
        if (isLoadingVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(14.dp)
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
        } else if (loadError != null && videoFile == null) {
            Surface(
                color = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = loadError ?: "Unable to play video",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else if (userPaused) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Resume Reel",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Bottom scrim gradient for crisp contrast
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.86f))
                    )
                )
        )

        // Right-side vertical interaction column (Like, Comment, Share, Boost, Mute, Delete)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 12.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReelActionCircleButton(
                icon = if (likedByMe) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                label = reel.likedByUserIds.size.toString(),
                tint = if (likedByMe) LiveCoral else Color.White,
                contentDescription = if (likedByMe) "Unlike Reel" else "Like Reel",
                testTag = "reel_like_button_${reel.id}",
                onClick = onLikeClick
            )

            ReelActionCircleButton(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = reel.commentCount.toString(),
                tint = Color.White,
                contentDescription = "Comment on Reel",
                testTag = "reel_comment_button_${reel.id}",
                onClick = onCommentClick
            )

            ReelActionCircleButton(
                icon = Icons.Rounded.Share,
                label = reel.shareCount.toString(),
                tint = Color.White,
                contentDescription = "Share Reel",
                testTag = "reel_share_button_${reel.id}",
                onClick = onShareClick
            )

            if (isMine) {
                ReelActionCircleButton(
                    icon = Icons.Rounded.Bolt,
                    label = if (isBoosted) "Top" else "\$5",
                    tint = ModAmber,
                    contentDescription = "Boost Reel to Top for \$5",
                    testTag = "reel_boost_button_${reel.id}",
                    onClick = onBoostClick
                )
            }

            ReelActionCircleButton(
                icon = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                label = if (isMuted) "Muted" else "Sound",
                tint = Color.White,
                contentDescription = if (isMuted) "Unmute Reel" else "Mute Reel",
                testTag = "reel_mute_button_${reel.id}",
                onClick = onToggleMute
            )

            if (canDelete) {
                ReelActionCircleButton(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Delete",
                    tint = Color.White.copy(alpha = 0.85f),
                    contentDescription = "Delete Reel",
                    testTag = "reel_delete_button_${reel.id}",
                    onClick = onDeleteClick
                )
            }
        }

        // Bottom-left author & caption overlay
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 88.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isBoosted) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ModAmber.copy(alpha = 0.92f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "BOOSTED TO TOP · ${reel.boostRemainingLabel()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onAuthorClick)
                    .padding(vertical = 2.dp)
            ) {
                SpeakingAvatarCircle(
                    displayName = reel.authorName,
                    colorHex = reel.authorColorHex,
                    speakingLevel = 0f,
                    micActive = false,
                    photoBase64 = reel.authorPhotoBase64,
                    size = 40.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tier = reel.authorTierEnum()
                        // On dark video scrim, keep Free member names crisp White, Pro Blue, Support Green, Admin Red
                        val overlayNameColor = when {
                            tier.isStaff() -> usernameColor(tier)
                            authorIsPro -> Color(0xFF5CB0FF)
                            else -> Color.White
                        }
                        Text(
                            text = reel.authorName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = overlayNameColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (tier.isStaff()) {
                            StaffTierBadge(tier = tier)
                        } else if (authorIsPro) {
                            ProMemberBadge()
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!reel.authorTierEnum().isAdmin()) {
                            Text(
                                text = "@${reel.authorHandle}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = reel.formattedDuration(),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            if (reel.caption.isNotBlank()) {
                Text(
                    text = reel.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReelActionCircleButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

/**
 * Native TextureView + MediaPlayer wrapper that clips cleanly inside Compose VerticalPager,
 * loops seamlessly, and respects active-page playback and mute state.
 */
@Composable
private fun ReelTextureVideoPlayer(
    videoFile: File,
    shouldPlay: Boolean,
    isMuted: Boolean,
    modifier: Modifier = Modifier
) {
    val playerHolder = remember(videoFile.absolutePath) {
        object {
            var mediaPlayer: MediaPlayer? = null
            var surface: Surface? = null
            var prepared: Boolean = false
            var desiredPlay: Boolean = shouldPlay
            var desiredMute: Boolean = isMuted

            fun applyState() {
                val mp = mediaPlayer ?: return
                if (!prepared) return
                val vol = if (desiredMute) 0f else 1f
                runCatching { mp.setVolume(vol, vol) }
                if (desiredPlay) {
                    if (!runCatching { mp.isPlaying }.getOrDefault(false)) {
                        runCatching { mp.start() }
                    }
                } else {
                    if (runCatching { mp.isPlaying }.getOrDefault(false)) {
                        runCatching { mp.pause() }
                    }
                }
            }

            fun release() {
                prepared = false
                mediaPlayer?.let { mp ->
                    runCatching { if (mp.isPlaying) mp.stop() }
                    runCatching { mp.release() }
                }
                mediaPlayer = null
                surface?.let { runCatching { it.release() } }
                surface = null
            }
        }
    }

    LaunchedEffect(shouldPlay, isMuted) {
        playerHolder.desiredPlay = shouldPlay
        playerHolder.desiredMute = isMuted
        playerHolder.applyState()
    }

    DisposableEffect(videoFile.absolutePath) {
        onDispose {
            playerHolder.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        playerHolder.release()
                        val surf = Surface(st)
                        playerHolder.surface = surf
                        val mp = MediaPlayer()
                        playerHolder.mediaPlayer = mp
                        runCatching {
                            mp.setDataSource(videoFile.absolutePath)
                            mp.setSurface(surf)
                            mp.isLooping = true
                            mp.setOnPreparedListener {
                                playerHolder.prepared = true
                                playerHolder.applyState()
                            }
                            mp.setOnErrorListener { _, _, _ -> true }
                            mp.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        playerHolder.release()
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        }
    )
}

@Composable
private fun UploadReelDialog(
    preparedVideo: PreparedReelVideo,
    myPointsBalance: Long,
    isUploading: Boolean,
    onDismiss: () -> Unit,
    onPublish: (String, Boolean) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var boostToTop by remember { mutableStateOf(false) }
    val canAffordBoost = myPointsBalance >= Reel.BOOST_PRICE_POINTS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Publish Reel (0:${preparedVideo.durationSec.toString().padStart(2, '0')})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Video verified (${preparedVideo.durationSec}s / max 30s). Add a caption and choose whether to boost it to the top.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= 300) caption = it },
                    label = { Text("Caption (optional)") },
                    placeholder = { Text("Say something about your Reel…") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reel_caption_input")
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ModAmber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Push to Top (\$5 / 5 pts)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (canAffordBoost) {
                                    "Pin to top of Reels for 24h (Balance: $myPointsBalance pts)"
                                } else {
                                    "Need 5 points (Balance: $myPointsBalance pts)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = boostToTop && canAffordBoost,
                            onCheckedChange = { if (canAffordBoost) boostToTop = it },
                            enabled = canAffordBoost
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(caption, boostToTop && canAffordBoost) },
                enabled = !isUploading,
                modifier = Modifier.testTag("publish_reel_confirm_button")
            ) {
                if (isUploading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Uploading…")
                } else {
                    Text(if (boostToTop && canAffordBoost) "Publish & Boost (\$5)" else "Publish Reel")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isUploading) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ReelCommentsSheetContent(
    reel: Reel,
    currentUserId: String,
    staffCapabilities: StaffCapabilities,
    commentsFlow: Flow<List<ReelComment>>,
    onAddComment: (String, () -> Unit) -> Unit,
    onDeleteComment: (String) -> Unit,
    onClose: () -> Unit
) {
    val comments by commentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var commentDraft by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Comments (${comments.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close comments")
            }
        }
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No comments yet. Start the conversation!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    val canRemove = comment.authorId == currentUserId ||
                        reel.authorId == currentUserId ||
                        staffCapabilities.isStaff
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        SpeakingAvatarCircle(
                            displayName = comment.authorName,
                            colorHex = comment.authorColorHex,
                            speakingLevel = 0f,
                            micActive = false,
                            size = 34.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = comment.authorName,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = usernameColor(
                                        tier = comment.authorTierEnum(),
                                        userId = comment.authorId
                                    )
                                )
                                Text(
                                    text = formatTimestampShort(comment.createdAt),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = comment.text,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (canRemove) {
                            IconButton(
                                onClick = { onDeleteComment(comment.id) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete comment",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commentDraft,
                onValueChange = { if (it.length <= 400) commentDraft = it },
                placeholder = { Text("Add a comment…") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("reel_comment_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val trimmed = commentDraft.trim()
                    if (trimmed.isNotBlank()) {
                        onAddComment(trimmed) { commentDraft = "" }
                    }
                },
                enabled = commentDraft.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("reel_comment_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send comment",
                    tint = if (commentDraft.isNotBlank()) SnapBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}
