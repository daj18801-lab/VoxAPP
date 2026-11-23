package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ProfilePost
import com.example.ui.theme.LiveCoral
import com.example.ui.theme.ModAmber
import com.example.ui.theme.SafetyEmerald
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

fun formatPostDateTime(ts: Timestamp?): String {
    val date = (ts ?: Timestamp.now()).toDate()
    val formatter = SimpleDateFormat("MMM d · h:mm a", Locale.getDefault())
    return formatter.format(date)
}

@Composable
fun ProfilePostCard(
    post: ProfilePost,
    currentUserId: String,
    canDelete: Boolean,
    latestAuthorPhotoBase64: String? = null,
    isUnlocked: Boolean = true,
    isProcessingUnlock: Boolean = false,
    onAuthorClick: (() -> Unit)? = null,
    onUnlockPost: (() -> Unit)? = null,
    onSubscribeCreator: (() -> Unit)? = null,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tier = post.authorTierEnum()
    val liked = post.isLikedBy(currentUserId)
    val effectiveAvatarPhoto = latestAuthorPhotoBase64?.ifBlank { post.authorPhotoBase64 } ?: post.authorPhotoBase64
    val showLockedPaywall = post.locked && post.imageBase64.isNotBlank() && !isUnlocked
    val postImageBitmap = if (showLockedPaywall) null else rememberBase64ImageBitmap(post.imageBase64)
    val blurredBitmap = if (showLockedPaywall) rememberBlurredBase64ImageBitmap(post.imageBase64) else null
    var showFullPhoto by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_post_card_${post.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Author Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (onAuthorClick != null) Modifier.clickable(onClick = onAuthorClick) else Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpeakingAvatarCircle(
                    displayName = post.authorName,
                    colorHex = post.authorColorHex,
                    speakingLevel = 0f,
                    micActive = false,
                    size = 42.dp,
                    photoBase64 = effectiveAvatarPhoto
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = post.authorName,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = usernameColor(tier),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StaffTierBadge(tier = tier)
                    }
                    Text(
                        text = "@${post.authorHandle} · ${formatPostDateTime(post.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (canDelete) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("delete_post_${post.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete post",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Text Update Body
            if (post.text.isNotBlank()) {
                Text(
                    text = post.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Locked Photo Paywall OR Unlocked Photo
            if (showLockedPaywall) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0E1B33))
                        .testTag("locked_post_photo_${post.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (blurredBitmap != null) {
                        Image(
                            bitmap = blurredBitmap,
                            contentDescription = "Locked photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xA6081224))
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = "Locked photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Text(
                            text = "Locked Photo",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Unlock for 2 pts (\$2) or subscribe for \$15/mo to view all locked photos from ${post.authorName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            textAlign = TextAlign.Center
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                        ) {
                            if (onUnlockPost != null) {
                                Button(
                                    onClick = onUnlockPost,
                                    enabled = !isProcessingUnlock,
                                    shape = RoundedCornerShape(999.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("unlock_photo_button_${post.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Unlock · 2 pts (\$2)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                            if (onSubscribeCreator != null) {
                                OutlinedButton(
                                    onClick = onSubscribeCreator,
                                    enabled = !isProcessingUnlock,
                                    shape = RoundedCornerShape(999.dp),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color.White.copy(alpha = 0.14f),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.testTag("subscribe_creator_from_post_${post.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CardMembership,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Sub \$15/mo",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (postImageBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 340.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showFullPhoto = true }
                        .testTag("post_photo_${post.id}")
                ) {
                    Image(
                        bitmap = postImageBitmap,
                        contentDescription = "Photo posted by ${post.authorName}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (post.locked) {
                        val isOwner = post.authorId == currentUserId
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (isOwner) ModAmber.copy(alpha = 0.92f) else SafetyEmerald.copy(alpha = 0.92f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isOwner) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = if (isOwner) {
                                        "Locked · 2 pts (${post.unlockedByUserIds.size} unlocked)"
                                    } else {
                                        "Unlocked"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // Action Footer (Like button + count)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (liked) {
                        LiveCoral.copy(alpha = 0.14f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onToggleLike)
                        .testTag("like_post_${post.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (liked) "Unlike post" else "Like post",
                            tint = if (liked) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (post.likedByUserIds.isEmpty()) "Like" else "${post.likedByUserIds.size}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (liked) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (post.locked && post.authorId == currentUserId) {
                    Text(
                        text = "Earns \$1 per 2-pt unlock",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showFullPhoto && post.imageBase64.isNotBlank() && !showLockedPaywall) {
        FullScreenPhotoDialog(
            photoBase64 = post.imageBase64,
            contentDescription = "Full photo by ${post.authorName}",
            onDismiss = { showFullPhoto = false }
        )
    }
}
