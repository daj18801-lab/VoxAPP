package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.CardMembership
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Share
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
import com.example.data.StaffTier
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePostCard(
    post: ProfilePost,
    currentUserId: String,
    canDelete: Boolean,
    latestAuthorPhotoBase64: String? = null,
    isUnlocked: Boolean = true,
    viewerIsAdultVerified: Boolean = true,
    isProcessingUnlock: Boolean = false,
    onAuthorClick: (() -> Unit)? = null,
    onOriginalAuthorClick: ((String) -> Unit)? = null,
    onRequestAgeVerification: (() -> Unit)? = null,
    onUnlockPost: (() -> Unit)? = null,
    onSubscribeCreator: (() -> Unit)? = null,
    onBoostPost: (() -> Unit)? = null,
    onOpenComments: (() -> Unit)? = null,
    onSharePost: (() -> Unit)? = null,
    isStaffFreePerks: Boolean = false,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tier = post.authorTierEnum()
    val liked = post.isLikedBy(currentUserId)
    val isBoosted = post.isCurrentlyBoosted()
    val effectiveAvatarPhoto = latestAuthorPhotoBase64?.ifBlank { post.authorPhotoBase64 } ?: post.authorPhotoBase64
    val postImageBitmap = rememberBase64ImageBitmap(post.imageBase64)
    var showFullPhoto by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isBoosted) 1.5.dp else 1.dp,
            color = if (isBoosted) ModAmber.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_post_card_${post.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Boosted Top-of-Discovery Banner
            if (isBoosted) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ModAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.45f)),
                    modifier = Modifier.testTag("boosted_post_badge_${post.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.RocketLaunch,
                            contentDescription = "Boosted to top of Discovery",
                            tint = ModAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Top of Discovery · Boosted (${post.boostRemainingLabel()})",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ModAmber
                        )
                    }
                }
            }

            // Shared-From Attribution Pill (for reposted unlocked posts)
            if (post.sharedFromPostId.isNotBlank() && post.sharedFromAuthorName.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier
                        .then(
                            if (onOriginalAuthorClick != null && post.sharedFromAuthorId.isNotBlank()) {
                                Modifier.clickable { onOriginalAuthorClick(post.sharedFromAuthorId) }
                            } else {
                                Modifier
                            }
                        )
                        .testTag("shared_from_label_${post.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Repeat,
                        contentDescription = "Shared post",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Shared from ${post.sharedFromAuthorName}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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
                    photoBase64 = effectiveAvatarPhoto,
                    userId = post.authorId
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
                            color = usernameColor(tier, userId = post.authorId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StaffTierBadge(tier = tier)
                    }
                    Text(
                        text = if (tier == StaffTier.ADMIN) {
                            formatPostDateTime(post.createdAt)
                        } else {
                            "@${post.authorHandle} · ${formatPostDateTime(post.createdAt)}"
                        },
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

            // Public Photo Attachment (youth-friendly: no locked photo paywall)
            if (postImageBitmap != null) {
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
                }
            }

            // Action Footer: Heart/Like · Comment · Share (unlocked only) · Boost to Top (author only)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Heart / Like Pill
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (liked) "Remove heart" else "Heart post",
                            tint = if (liked) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (post.likedByUserIds.isEmpty()) "Heart" else "${post.likedByUserIds.size}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (liked) LiveCoral else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Comment Pill
                if (onOpenComments != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onOpenComments)
                            .testTag("comment_post_${post.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Comment on post",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = if (post.commentCount <= 0L) "Comment" else "${post.commentCount}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Share Pill
                if (onSharePost != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onSharePost)
                            .testTag("share_post_${post.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share post",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = if (post.shareCount <= 0L) "Share" else "${post.shareCount}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Boost to Top of Discovery Pill (Author only · 5 pts / FREE for Staff)
                if (post.authorId == currentUserId && onBoostPost != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = ModAmber.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ModAmber.copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(enabled = !isProcessingUnlock, onClick = onBoostPost)
                            .testTag("boost_post_${post.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RocketLaunch,
                                contentDescription = "Boost to top of Discovery",
                                tint = ModAmber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = when {
                                    isStaffFreePerks && isBoosted -> "Re-boost · FREE (Staff)"
                                    isStaffFreePerks -> "Boost to Top · FREE (Staff)"
                                    isBoosted -> "Re-boost · 5 pts (\$5)"
                                    else -> "Boost to Top · 5 pts (\$5)"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = ModAmber
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFullPhoto && post.imageBase64.isNotBlank()) {
        FullScreenPhotoDialog(
            photoBase64 = post.imageBase64,
            contentDescription = "Full photo by ${post.authorName}",
            onDismiss = { showFullPhoto = false }
        )
    }
}

