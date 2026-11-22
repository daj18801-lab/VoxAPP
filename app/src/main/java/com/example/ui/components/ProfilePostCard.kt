package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.ProfilePost
import com.example.ui.theme.LiveCoral
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
    onAuthorClick: (() -> Unit)? = null,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tier = post.authorTierEnum()
    val liked = post.isLikedBy(currentUserId)
    val effectiveAvatarPhoto = latestAuthorPhotoBase64?.ifBlank { post.authorPhotoBase64 } ?: post.authorPhotoBase64
    val postImageBitmap = rememberBase64ImageBitmap(post.imageBase64)
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

            // Optional Picture
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

            // Action Footer (Like button + count)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
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
