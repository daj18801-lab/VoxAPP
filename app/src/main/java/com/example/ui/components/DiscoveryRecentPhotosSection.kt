package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.DirectoryEntry
import com.example.data.ProfilePost
import com.example.data.StaffTier
import com.example.data.UserProfile
import com.example.data.UserSex
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import kotlinx.coroutines.launch

@Composable
fun DiscoveryRecentPhotosQuotaCard(
    myUserProfile: UserProfile?,
    myStaffTier: StaffTier,
    isPublishingPost: Boolean,
    onPostPhotoClick: () -> Unit,
    onOpenBuyPoints: () -> Unit
) {
    val isPaid = myUserProfile?.isPaidMember(myStaffTier) ?: myStaffTier.isStaff()
    val remaining = myUserProfile?.remainingFreeDiscoveryPhotos()
        ?: UserProfile.FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK
    val limitReached = !isPaid && remaining <= 0

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isPaid) SafetyEmerald.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isPaid) Icons.Rounded.Stars else Icons.Rounded.PhotoLibrary,
                            contentDescription = "Recent Discovery photos",
                            tint = if (isPaid) SafetyEmerald else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Recent Member Photos",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            isPaid -> "Paid / Staff Member · Unlimited Discovery photos"
                            limitReached -> "Free limit reached (0 of 2 left this week) · Upgrade for unlimited"
                            else -> "Free Member · $remaining of ${UserProfile.FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK} Discovery photos left this week"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (limitReached) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPostPhotoClick,
                    enabled = !isPublishingPost && !limitReached,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("discover_post_photo_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (limitReached) "Weekly Limit Reached" else "Post Photo to Discovery",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!isPaid) {
                    OutlinedButton(
                        onClick = onOpenBuyPoints,
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("discover_upgrade_photos_button")
                    ) {
                        Text("Unlimited", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryPhotoGridRow(
    pair: List<ProfilePost>,
    directoryByUid: Map<String, DirectoryEntry>,
    currentUserId: String,
    subscribedCreatorIds: Set<String>,
    viewerIsAdultVerified: Boolean,
    onSelectPost: (ProfilePost) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        pair.forEach { post ->
            val authorEntry = directoryByUid[post.authorId]
            val unlocked = post.isUnlockedFor(
                uid = currentUserId,
                subscribedCreatorIds = subscribedCreatorIds,
                viewerIsAdultVerified = viewerIsAdultVerified
            )
            DiscoveryPhotoTileCard(
                post = post,
                authorEntry = authorEntry,
                currentUserId = currentUserId,
                isUnlocked = unlocked,
                onClick = { onSelectPost(post) },
                modifier = Modifier.weight(1f)
            )
        }
        if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun DiscoveryPhotoTileCard(
    post: ProfilePost,
    authorEntry: DirectoryEntry?,
    currentUserId: String,
    isUnlocked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = rememberBase64ImageBitmap(post.imageBase64)
    val tier = post.authorTierEnum()
    val age = authorEntry?.effectiveAge() ?: 0
    val sex = authorEntry?.sexEnum() ?: UserSex.UNSPECIFIED
    val demographicBadge = remember(age, sex) {
        buildList {
            if (age >= 18) add("$age")
            if (sex != UserSex.UNSPECIFIED) add(sex.label)
        }.joinToString(" · ")
    }
    val likedByMe = post.isLikedBy(currentUserId)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("discover_recent_photo_${post.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.92f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Photo posted by ${post.authorName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom subtle scrim for legibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.58f))
                            )
                        )
                )

                if (demographicBadge.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Black.copy(alpha = 0.62f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = demographicBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTimestampShort(post.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (likedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Likes",
                            tint = if (likedByMe) LiveCoral else Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "${post.likedByUserIds.size}",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                SpeakingAvatarCircle(
                    displayName = post.authorName,
                    colorHex = post.authorColorHex,
                    speakingLevel = 0f,
                    micActive = false,
                    size = 24.dp,
                    photoBase64 = authorEntry?.photoBase64?.ifBlank { post.authorPhotoBase64 }
                        ?: post.authorPhotoBase64,
                    userId = post.authorId
                )
                Text(
                    text = post.authorName,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = usernameColor(tier),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DiscoveryPhotoPostDialog(
    myUserProfile: UserProfile?,
    myStaffTier: StaffTier,
    viewerIsAdultVerified: Boolean,
    isPublishingPost: Boolean,
    onRequestAgeVerification: () -> Unit,
    onOpenBuyPoints: () -> Unit,
    onSubmitPhotoPost: (caption: String, imageBase64: String, locked: Boolean, onPosted: () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var caption by remember { mutableStateOf("") }
    var imageBase64 by remember { mutableStateOf("") }
    var isEncoding by remember { mutableStateOf(false) }

    val isPaid = myUserProfile?.isPaidMember(myStaffTier) ?: myStaffTier.isStaff()
    val remaining = myUserProfile?.remainingFreeDiscoveryPhotos()
        ?: UserProfile.FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK
    val limitReached = !isPaid && remaining <= 0

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isEncoding = true
                val encoded = encodeImageUriToBase64Jpeg(
                    context = context,
                    uri = uri,
                    maxDimensionPx = 720,
                    squareCrop = false,
                    maxBase64Chars = 380_000
                )
                isEncoding = false
                if (!encoded.isNullOrBlank()) {
                    imageBase64 = encoded
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Post Photo to Discovery") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (limitReached) LiveCoral.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            isPaid -> "Paid / Staff Member: Unlimited Discovery photo uploads."
                            limitReached -> "You have used both of your 2 free Discovery photo uploads this week. Buy Points for unlimited photos."
                            else -> "Free Member quota: $remaining of ${UserProfile.FREE_MAX_DISCOVERY_PHOTOS_PER_WEEK} Discovery photos remaining this week."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (limitReached) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                if (imageBase64.isNotBlank()) {
                    val bmp = rememberBase64ImageBitmap(imageBase64)
                    if (bmp != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            Image(
                                bitmap = bmp,
                                contentDescription = "Selected Discovery photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth()
                            )
                            IconButton(
                                onClick = { imageBase64 = "" },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove selected photo",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isEncoding && !limitReached,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("discover_pick_photo_button")
                    ) {
                        if (isEncoding) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Preparing photo…")
                        } else {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Photo")
                        }
                    }
                }

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(300) },
                    label = { Text("Caption (optional)") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("discover_photo_caption_input")
                )
            }
        },
        confirmButton = {
            if (limitReached) {
                Button(
                    onClick = {
                        onDismiss()
                        onOpenBuyPoints()
                    },
                    modifier = Modifier.testTag("discover_limit_buy_points_button")
                ) {
                    Text("Buy Points")
                }
            } else {
                Button(
                    onClick = {
                        onSubmitPhotoPost(caption, imageBase64, false, onDismiss)
                    },
                    enabled = imageBase64.isNotBlank() && !isEncoding && !isPublishingPost,
                    modifier = Modifier.testTag("discover_submit_photo_post_button")
                ) {
                    if (isPublishingPost) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Post Photo")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DiscoveryPhotoPreviewDialog(
    post: ProfilePost,
    currentUserId: String,
    canDelete: Boolean,
    latestAuthorPhotoBase64: String?,
    isUnlocked: Boolean,
    viewerIsAdultVerified: Boolean,
    isProcessingUnlock: Boolean,
    onAuthorClick: () -> Unit,
    onOriginalAuthorClick: (String) -> Unit,
    onRequestAgeVerification: () -> Unit,
    onUnlockPost: () -> Unit,
    onSubscribeCreator: () -> Unit,
    onBoostPost: (() -> Unit)?,
    onOpenComments: () -> Unit,
    onSharePost: (() -> Unit)?,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Member Photo",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("discover_photo_preview_close")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close photo preview")
                    }
                }
                ProfilePostCard(
                    post = post,
                    currentUserId = currentUserId,
                    canDelete = canDelete,
                    latestAuthorPhotoBase64 = latestAuthorPhotoBase64,
                    isUnlocked = isUnlocked,
                    viewerIsAdultVerified = viewerIsAdultVerified,
                    isProcessingUnlock = isProcessingUnlock,
                    onAuthorClick = {
                        onDismiss()
                        onAuthorClick()
                    },
                    onOriginalAuthorClick = { uid ->
                        onDismiss()
                        onOriginalAuthorClick(uid)
                    },
                    onRequestAgeVerification = onRequestAgeVerification,
                    onUnlockPost = onUnlockPost,
                    onSubscribeCreator = onSubscribeCreator,
                    onBoostPost = onBoostPost,
                    onOpenComments = {
                        onDismiss()
                        onOpenComments()
                    },
                    onSharePost = onSharePost?.let { share ->
                        {
                            onDismiss()
                            share()
                        }
                    },
                    onToggleLike = onToggleLike,
                    onDelete = {
                        onDelete()
                        onDismiss()
                    }
                )
            }
        }
    }
}
